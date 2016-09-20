package autodrive.cjw.com.autodrive;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.util.UUID;

/**
 * Created by jaewoncho on 2016-09-18.
 */
public class MainActivity extends Activity implements View.OnClickListener {

    Button mButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mButton = (Button) findViewById(R.id.main_bt_start);
        mButton.setOnClickListener(this);

    }

    @Override
    public void onClick(View view) {
        startService(new Intent(this, AutoDriveService.class));
    }
}
