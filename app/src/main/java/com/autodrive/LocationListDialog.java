package com.autodrive;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.skp.Tmap.TMapPOIItem;

import java.util.List;

/**
 * Created by jaewoncho on 2016. 9. 23..
 */
public class LocationListDialog extends DialogFragment implements AdapterView.OnItemClickListener {

    ListView mListView;
    List<TMapPOIItem> mItems;
    OnLocationClickListener mOnLocationClickListener;

    public interface OnLocationClickListener {
        public void onLocationClicked(TMapPOIItem item);
    }

    public LocationListDialog() {

    }

    public LocationListDialog(List<TMapPOIItem> items) {
        mItems = items;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog dialog = new AlertDialog.Builder(getActivity()).create();
        mListView = new ListView(getActivity());
        mListView.setOnItemClickListener(this);

        ListAdapter adapter = new ArrayAdapter<TMapPOIItem>(getActivity(),
                android.R.layout.simple_list_item_1, android.R.id.text1, mItems);
        mListView.setAdapter(adapter);
        dialog.setView(mListView);
        return dialog;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (mOnLocationClickListener != null) {
            mOnLocationClickListener.onLocationClicked(mItems.get(position));
        }
    }

    public void setOnLocationClickListener(OnLocationClickListener l) {
        mOnLocationClickListener = l;
    }
}
