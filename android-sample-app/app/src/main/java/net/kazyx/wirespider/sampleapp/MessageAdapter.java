package net.kazyx.wirespider.sampleapp;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MessageAdapter extends BaseAdapter {

    private final List<Message> mMessages = new ArrayList<>();

    private final LayoutInflater mInflater;

    private final Context mContext;

    public MessageAdapter() {
        mContext = SampleApp.getAppContext();
        mInflater = LayoutInflater.from(mContext);
    }

    @Override
    public int getCount() {
        return mMessages.size();
    }

    @Override
    public Object getItem(int position) {
        return mMessages.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.console_row, parent, false);
        }

        TextView view = (TextView) convertView;

        Message message = (Message) getItem(position);
        view.setText(message.text);
        switch (message.type) {
            case SENT:
                view.setTextColor(ContextCompat.getColor(mContext, R.color.to_message));
                break;
            case RECEIVED:
                view.setTextColor(ContextCompat.getColor(mContext, R.color.from_message));
                break;
            default:
                view.setTextColor(ContextCompat.getColor(mContext, android.R.color.white));
                break;
        }

        return view;
    }

    public void add(String text, Type type) {
        mMessages.add(new Message(text, type));
    }

    public enum Type {
        OTHER,
        SENT,
        RECEIVED
    }

    public static class Message {
        public Message(String text, Type type) {
            this.text = text;
            this.type = type;
        }

        public final String text;
        public final Type type;
    }
}
