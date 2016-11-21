package com.autodrive;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Created by jaewoncho on 2016. 11. 19..
 */
public class PathLogSelector extends Activity implements AdapterView.OnItemClickListener {
    ListView mListView;

    static class LogItem {
        File f;

        LogItem(File file) {
            this.f = file;
        }

        @Override
        public String toString() {
            return f.getName();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mListView = new ListView(this);
        setContentView(mListView);

        List<LogItem> files = new ArrayList<>();

        File dir = getExternalFilesDir(null);
        if (dir != null) {
            File[] list = dir.listFiles();

            Arrays.sort(list, new Comparator<File>() {
                @Override
                public int compare(File lhs, File rhs) {
                    return rhs.compareTo(lhs);
                }
            });
            
            for (File f : list) {
                files.add(new LogItem(f));
            }
        }

        ArrayAdapter<LogItem> adapter = new ArrayAdapter<LogItem>(this, android.R.layout.simple_list_item_1,
                android.R.id.text1, files);
        mListView.setAdapter(adapter);
        mListView.setOnItemClickListener(this);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        ArrayAdapter<LogItem> items = (ArrayAdapter<LogItem>) parent.getAdapter();
        LogItem item = items.getItem(position);

        Intent intent = new Intent();
        intent.putExtra("path", item.f.getAbsolutePath());
        setResult(RESULT_OK, intent);
        finish();
    }
}
