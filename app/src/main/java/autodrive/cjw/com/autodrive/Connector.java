package autodrive.cjw.com.autodrive;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by jaewoncho on 2016-09-18.
 */
public class Connector {
    private static final byte[] AUTODRIVE_PROTOCOL_HEADER_PATTERN = new byte[]{'a', 'u', 't', 'o', 'c', 'a', 'r', '!'};

    private static final int AUTODRIVE_PROTOCOL_HEADER_PATTERN_OFFSET = 0;
    private static final int AUTODRIVE_PROTOCOL_HEADER_PATTERN_SIZE = 8;

    private static final int AUTODRIVE_PROTOCOL_HEADER_VERSION_OFFSET = AUTODRIVE_PROTOCOL_HEADER_PATTERN_SIZE;
    private static final int AUTODRIVE_PROTOCOL_HEADER_VERSION_SIZE = 4;

    private static final int AUTODRIVE_PROTOCOL_HEADER_SEQUENCE_NUMBER_OFFSET = AUTODRIVE_PROTOCOL_HEADER_VERSION_OFFSET + AUTODRIVE_PROTOCOL_HEADER_VERSION_SIZE;
    private static final int AUTODRIVE_PROTOCOL_HEADER_SEQUENCE_NUMBER_SIZE = 4;

    private static final int AUTODRIVE_PROTOCOL_HEADER_ACTION_CODE_OFFSET = AUTODRIVE_PROTOCOL_HEADER_SEQUENCE_NUMBER_OFFSET + AUTODRIVE_PROTOCOL_HEADER_SEQUENCE_NUMBER_SIZE;
    private static final int AUTODRIVE_PROTOCOL_HEADER_ACTION_CODE_SIZE = 4;

    private static final int AUTODRIVE_PROTOCOL_HEADER_PAYLOAD_SIZE_OFFSET = AUTODRIVE_PROTOCOL_HEADER_ACTION_CODE_OFFSET + AUTODRIVE_PROTOCOL_HEADER_ACTION_CODE_SIZE;
    private static final int AUTODRIVE_PROTOCOL_HEADER_PAYLOAD_SIZE_SIZE = 4;

    private static final int AUTODRIVE_HEADER_SIZE = AUTODRIVE_PROTOCOL_HEADER_PAYLOAD_SIZE_OFFSET + AUTODRIVE_PROTOCOL_HEADER_PAYLOAD_SIZE_SIZE;

    private static final int AUTODRIVE_PROTOCOL_ACTION_CODE_CLIENT_TO_SERVER_PING = 0x00000000;
    private static final int AUTODRIVE_PROTOCOL_ACTION_CODE_SERVER_TO_CLIENT_PING = 0x00000001;

    private static final int AUTODRIVE_PROTOCOL_ACTION_CODE_CLIENT_TO_SERVER_INITIALIZE = 0x10000000;
    private static final int AUTODRIVE_PROTOCOL_ACTION_CODE_SERVER_TO_CLIENT_INITIALIZE = 0x10000001;
    private static final int AUTODRIVE_PROTOCOL_ACTION_CODE_SERVER_TO_CLIENT_INITIALIZE_FAILED = 0x10000002;


    private static final int READ_BUFFER_SIZE = 1024 * 1024;

    private BluetoothSocket mSocket;
    private BufferedInputStream mInput;
    private BufferedOutputStream mOutput;

    private int mReadPosition;
    private byte[] mReadBuffer;
    private ByteBuffer mWriteBuffer;

    private ConnectorThread mConnectorThread;
    private WriteThread mWriteThread;
    private ConnectionChecker mConnectionChecker;

    private LinkedBlockingDeque<WriteRequest> mQ;
    private AtomicInteger mSeqInt;

    BluetoothAdapter mAdapter;
    boolean mInit;
    boolean mDestroyed;

    Set<Integer> mCheckSet;

    Handler mHandler;
    Callback mCallback;

    private static final int MAX_TRY_CONNECT_COUNT = 10;
    private static final UUID AUTODRIVE_UUID = UUID.fromString("50038ec2-6485-4a54-a8ee-997d8e1edaa3");

    public static interface Callback {
        public void onInitialize();

        public void onDestroyed();
    }

    class InitPoster implements Runnable {

        @Override
        public void run() {
            if (mCallback != null) {
                mCallback.onInitialize();
            }
        }
    }

    class DestroyPoster implements Runnable {
        @Override
        public void run() {
            if (mCallback != null) {
                mCallback.onDestroyed();
            }
        }
    }

    class ConnectorThread extends Thread {
        @Override
        public void run() {
            BluetoothSocket socket = null;

            LOOP:
            for (int idx = 0; idx < MAX_TRY_CONNECT_COUNT; idx++) {
                for (BluetoothDevice device : mAdapter.getBondedDevices()) {

                    device.fetchUuidsWithSdp();
                    for (ParcelUuid uuid : device.getUuids()) {
                        Log.d("cjw", "Bluetooth device name " + device.getName() + " UUID " + uuid.getUuid().toString());
                        if (uuid.getUuid().compareTo(AUTODRIVE_UUID) == 0) {
                            Log.d("cjw", "Bluetooth device name " + device.getName() + " UUID matched");
                            try {
                                socket = device.createRfcommSocketToServiceRecord(uuid.getUuid());
                                socket.connect();
                                break LOOP;
                            } catch (IOException e) {
                                try {
                                    socket = (BluetoothSocket) device.getClass().getMethod("createRfcommSocket", new Class[] {int.class}).invoke(device,1);
                                } catch (IllegalAccessException e1) {
                                    e1.printStackTrace();
                                } catch (InvocationTargetException e1) {
                                    e1.printStackTrace();
                                } catch (NoSuchMethodException e1) {
                                    e1.printStackTrace();
                                }
                                try {
                                    socket.connect();
                                } catch (IOException e1) {
                                }
                                break LOOP;
                            }
                        }
                    }
                }
            }

            if (socket != null) {
                mSocket = socket;
                Log.d("cjw", "Socket connected " + mSocket.isConnected());
                try {
                    mInput = new BufferedInputStream(mSocket.getInputStream());
                    mOutput = new BufferedOutputStream(mSocket.getOutputStream());

                    sendData(mSeqInt.getAndIncrement(), AUTODRIVE_PROTOCOL_ACTION_CODE_CLIENT_TO_SERVER_INITIALIZE, null);

                    mWriteThread = new WriteThread();
                    mConnectionChecker = new ConnectionChecker();

                    mWriteThread.start();
                    mConnectionChecker.start();

                    doWork();
                } catch (IOException e) {
                    Log.d("cjw", "error", e);
                    Connector.this.destroy();
                }
            }
        }
    }

    class WriteRequest {
        int seq;
        int actionCode;
        byte[] payload;

        public WriteRequest(int seq, int actionCode, byte[] payload) {
            this.seq = seq;
            this.actionCode = actionCode;
            this.payload = payload;
        }
    }


    class WriteThread extends Thread {
        @Override
        public void run() {
            try {
                while (!mDestroyed) {
                    WriteRequest rq = mQ.take();

                    Log.d("cjw", "Write Thraed sends " + rq.seq + " with code " + rq.actionCode);
                    if (rq != null) {
                        sendDataToServer(rq.seq, rq.actionCode, rq.payload);
                    }
                }
            } catch (IOException e) {
                Connector.this.destroy();
            } catch (InterruptedException e) {
            }

        }
    }

    class ConnectionChecker extends Thread {
        @Override
        public void run() {
            doConnectionCheckWork();
        }
    }


    public Connector() {
        mAdapter = BluetoothAdapter.getDefaultAdapter();

        mSeqInt = new AtomicInteger();

        mReadBuffer = new byte[READ_BUFFER_SIZE];
        mWriteBuffer = ByteBuffer.allocate(AUTODRIVE_HEADER_SIZE);
        mWriteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        mQ = new LinkedBlockingDeque<>();

        mCheckSet = new HashSet<>();

        mHandler = new Handler();
    }

    public void start() {
        // if (mConnectorThread == null) {
            mConnectorThread = new ConnectorThread();
            mConnectorThread.start();
        // }
    }

    void handleAction(int seq, int actionCode, byte[] payload, int offset, int length) {
        ByteArrayInputStream bais;
        switch (actionCode) {
            case AUTODRIVE_PROTOCOL_ACTION_CODE_SERVER_TO_CLIENT_PING:
                synchronized (mCheckSet) {
                    mCheckSet.remove(seq);
                }
                break;
            case AUTODRIVE_PROTOCOL_ACTION_CODE_SERVER_TO_CLIENT_INITIALIZE:
                mInit = true;
                mHandler.post(new InitPoster());
                break;
            case AUTODRIVE_PROTOCOL_ACTION_CODE_SERVER_TO_CLIENT_INITIALIZE_FAILED:
                destroy();
                break;
            default:
                break;
        }
    }


    public void doWork() throws IOException {
        int byteReceived;
        ByteBuffer byteBuffer = ByteBuffer.allocate(AUTODRIVE_HEADER_SIZE);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);

        while (!mDestroyed) {
            byteReceived = mInput.read(mReadBuffer, mReadPosition, mReadBuffer.length - mReadPosition);
            if (byteReceived > 0) {
                mReadPosition += byteReceived;

                int pos = 0;
                while (mReadPosition - pos >= AUTODRIVE_HEADER_SIZE) {

                    byte[] header = Arrays.copyOfRange(mReadBuffer, pos + AUTODRIVE_PROTOCOL_HEADER_PATTERN_OFFSET,
                            pos + AUTODRIVE_PROTOCOL_HEADER_PATTERN_OFFSET + AUTODRIVE_PROTOCOL_HEADER_PATTERN_SIZE);
                    byteBuffer.clear();
                    byteBuffer.put(mReadBuffer, pos + AUTODRIVE_PROTOCOL_HEADER_VERSION_OFFSET, AUTODRIVE_PROTOCOL_HEADER_VERSION_SIZE);
                    byteBuffer.put(mReadBuffer, pos + AUTODRIVE_PROTOCOL_HEADER_SEQUENCE_NUMBER_OFFSET, AUTODRIVE_PROTOCOL_HEADER_SEQUENCE_NUMBER_SIZE);
                    byteBuffer.put(mReadBuffer, pos + AUTODRIVE_PROTOCOL_HEADER_ACTION_CODE_OFFSET, AUTODRIVE_PROTOCOL_HEADER_ACTION_CODE_SIZE);
                    byteBuffer.put(mReadBuffer, pos + AUTODRIVE_PROTOCOL_HEADER_PAYLOAD_SIZE_OFFSET, AUTODRIVE_PROTOCOL_HEADER_PAYLOAD_SIZE_SIZE);
                    byteBuffer.position(0);

                    int version = byteBuffer.getInt();
                    int seq = byteBuffer.getInt();
                    int actionCode = byteBuffer.getInt();
                    int size = byteBuffer.getInt();

                    if (Arrays.equals(AUTODRIVE_PROTOCOL_HEADER_PATTERN, header)) {
                        if (mReadPosition - (pos + AUTODRIVE_HEADER_SIZE) >= size) {
                            handleAction(seq, actionCode, mReadBuffer, pos + AUTODRIVE_HEADER_SIZE, size);

                            pos += AUTODRIVE_HEADER_SIZE + size;
                        } else {
                            break;
                        }
                    } else {
                        // console.log('header failed');
                        int start = 0;
                        for (int idx = pos + 1; idx < mReadPosition; idx++) {
                            if (AUTODRIVE_PROTOCOL_HEADER_PATTERN[start] == mReadBuffer[idx]) {
                                start++;

                                if (start >= AUTODRIVE_PROTOCOL_HEADER_PATTERN_SIZE || idx == mReadPosition - 1) {
                                    pos = idx - (start - 1);
                                    break;
                                }
                            } else {
                                start = 0;
                            }

                            pos = idx + 1;
                        }

                        if (start >= AUTODRIVE_PROTOCOL_HEADER_PATTERN_SIZE) {
                            continue;
                        } else {
                            break;
                        }
                    }
                }

                if (pos > 0) {
                    if (mReadPosition > pos) {
                        System.arraycopy(mReadBuffer, pos, mReadBuffer, 0, mReadPosition - pos);
                        mReadPosition -= pos;
                    } else {
                        mReadPosition = 0;
                    }
                }
            }

        }
    }

    void doConnectionCheckWork() {
        while (!mDestroyed) {
            try {
                if (mCheckSet.size() > 0) {
                    // destroy();
                    // return;
                }

                Log.d("cjw", "Connection Checker doing work");
                int seq = mSeqInt.getAndIncrement();
                sendData(seq, AUTODRIVE_PROTOCOL_ACTION_CODE_CLIENT_TO_SERVER_PING, null);
                synchronized (mCheckSet) {
                    mCheckSet.add(seq);
                }
                Thread.sleep(1000l);
            } catch (InterruptedException e) {
            }
        }
    }


    synchronized void sendDataToServer(int seq, int actionCode, byte[] payload) throws IOException {
        final int payloadSize = payload != null? payload.length : 0;

        mWriteBuffer.clear();
        mWriteBuffer.put(AUTODRIVE_PROTOCOL_HEADER_PATTERN);
        mWriteBuffer.putInt(1);
        mWriteBuffer.putInt(seq);
        mWriteBuffer.putInt(actionCode);
        mWriteBuffer.putInt(payloadSize);
        mOutput.write(mWriteBuffer.array(), 0, AUTODRIVE_HEADER_SIZE);

        if (payload != null && payload.length > 0) {
            mOutput.write(payload, 0, payload.length);
        }

        mOutput.flush();
    }

    public void sendData(int seq, int actionCode, byte[] payload) {
        WriteRequest rq = new WriteRequest(seq, actionCode, payload);
        mQ.offer(rq);
    }


    public synchronized void destroy() {
        destroy(false);
    }

    public synchronized void destroy(boolean manual) {
        if (!mDestroyed) {
            for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
                Log.d("cjw", e.toString());
            }

            mDestroyed = true;

            if (mInput != null) {
                try {
                    mInput.close();
                } catch (IOException e) {
                }
            }

            if (mOutput != null) {
                try {
                    mOutput.close();
                } catch (IOException e) {
                }
            }

            if (mSocket != null) {
                try {
                    mSocket.close();
                } catch (IOException e) {
                }
            }

            if (mConnectorThread != null) {
                mConnectorThread.interrupt();
            }

            if (mWriteThread != null) {
                mWriteThread.interrupt();
            }

            if (mConnectionChecker != null) {
                mConnectionChecker.interrupt();
            }

            if (!manual) {
                mHandler.post(new DestroyPoster());
            }

        }

    }


}
