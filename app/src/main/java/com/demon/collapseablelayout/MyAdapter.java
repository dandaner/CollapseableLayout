package com.demon.collapseablelayout;

import android.content.Context;
import android.content.res.Resources;
import android.support.v7.widget.RecyclerView;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * author: demon.zhang
 * time  : 16/10/27 下午7:31
 */

public class MyAdapter extends RecyclerView.Adapter<MyAdapter.MyViewHolder> {

    private LayoutInflater mInflater;
    private List<Pair<String, String>> mDatas;
    private Resources mRes;

    private OnItemLongClickListener mListener;

    public interface OnItemLongClickListener {
        void onItemClick(View view, Object data);
    }

    public MyAdapter(Context context) {
        this.mInflater = LayoutInflater.from(context);
        this.mRes = context.getResources();
        this.mDatas = new ArrayList<>();
    }

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new MyViewHolder(mInflater.inflate(R.layout.password_list_item, parent, false), mListener);
    }

    @Override
    public int getItemCount() {
        return mDatas == null ? 0 : mDatas.size();
    }

    @Override
    public void onBindViewHolder(MyViewHolder holder, int position) {
        holder.onBindViewHolder(mRes, mDatas.get(position));
    }

    public void replaceAll(List<Pair<String, String>> newDatas) {
        mDatas.clear();
        mDatas.addAll(newDatas);
        notifyDataSetChanged();
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.mListener = listener;
    }

    static class MyViewHolder extends RecyclerView.ViewHolder implements View.OnLongClickListener {

        private OnItemLongClickListener mListener;
        private View cardItem;

        TextView wifiName;
        TextView wifiPassword;

        MyViewHolder(View itemView, OnItemLongClickListener listener) {
            super(itemView);
            this.mListener = listener;

            this.cardItem = itemView.findViewById(R.id.card);
            this.cardItem.setOnLongClickListener(this);
            this.wifiName = (TextView) itemView.findViewById(R.id.name);
            this.wifiPassword = (TextView) itemView.findViewById(R.id.pwd);
        }

        void onBindViewHolder(Resources res, Pair<String, String> item) {
            this.cardItem.setTag(item);

            this.wifiName.setText(item.first);
            this.wifiPassword.setText(item.second);
        }

        @Override
        public boolean onLongClick(View v) {
            if (mListener != null) {
                mListener.onItemClick(v, v.getTag());
            }
            return true;
        }
    }
}
