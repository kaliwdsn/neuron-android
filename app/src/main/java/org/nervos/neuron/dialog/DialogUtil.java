package org.nervos.neuron.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import org.nervos.neuron.R;
import org.nervos.neuron.util.LogUtil;

import java.util.List;

public class DialogUtil {

    private static AlertDialog dialog;

    public static void showListDialog(Context context, String title, List<String> list,
                                      OnItemClickListener onItemClickListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater layoutInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View titleView = layoutInflater.inflate(R.layout.dialog_list_title_view, null);
        ((TextView)titleView.findViewById(R.id.dialog_title)).setText(title);
        titleView.findViewById(R.id.dialog_close_image).setOnClickListener(v -> {
            if (dialog != null) dialog.dismiss();
        });
        builder.setCustomTitle(titleView);
        String[] contents = new String[list.size()];
        list.toArray(contents);
        DialogAdapter adapter = new DialogAdapter(context, list);
        builder.setAdapter(adapter, (dialog, which) -> {
            if (onItemClickListener != null) {
                onItemClickListener.onItemClick(which);
            }
        });
        builder.setCancelable(true);
        dialog = builder.create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private static class DialogAdapter extends BaseAdapter {

        private List<String> list;
        private LayoutInflater layoutInflater;

        public DialogAdapter(Context context, List<String> list) {
            this.list = list;
            this.layoutInflater = LayoutInflater.from(context);
        }
        private class ViewHolder {
            TextView itemText;
            View separateView;
        }
        @Override
        public int getCount() {
            return list.size();
        }
        @Override
        public String getItem(int position) {
            return list.get(position);
        }
        @Override
        public long getItemId(int position) {
            return position;
        }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if(convertView == null) {
                holder = new ViewHolder();
                convertView = layoutInflater.inflate(R.layout.item_dialog_list, null);
                holder.itemText = convertView.findViewById(R.id.dialog_item_text);
                holder.separateView = convertView.findViewById(R.id.dialog_separate);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder)convertView.getTag();
            }
            holder.itemText.setText(list.get(position));
            if (position == list.size() -1) {
                holder.separateView.setVisibility(View.GONE);
            } else {
                holder.separateView.setVisibility(View.VISIBLE);
            }
            return convertView;
        }
    }

    public interface OnItemClickListener{
        void onItemClick(int which);
    }

}
