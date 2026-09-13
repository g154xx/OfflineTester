package com.gag4.offlinetester;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {

    private final List<LogEntry> entries = new ArrayList<>();

    public LogAdapter() {
    }

    public LogAdapter(List<LogEntry> initialEntries) {
        if (initialEntries != null) {
            this.entries.addAll(initialEntries);
        }
    }

    public void setEntries(List<LogEntry> newEntries) {
        this.entries.clear();
        if (newEntries != null) {
            this.entries.addAll(newEntries);
        }
        super.notifyDataSetChanged();
    }

    /**
     * Metodă proprie pentru refresh (nu putem suprascrie notifyDataSetChanged,
     * pentru că este final în RecyclerView.Adapter).
     */
    public void refreshData() {
        super.notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_log, parent, false);
        return new LogViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        LogEntry entry = entries.get(position);
        holder.tvTime.setText(entry.getFormattedTime());

        switch (entry.getDirection()) {
            case TX:
                holder.tvDir.setText(">>");
                holder.tvDir.setTextColor(Color.parseColor("#4CAF50"));
                holder.tvData.setTextColor(Color.parseColor("#4CAF50"));
                break;
            case RX:
                holder.tvDir.setText("<<");
                holder.tvDir.setTextColor(Color.parseColor("#03A9F4"));
                holder.tvData.setTextColor(Color.parseColor("#03A9F4"));
                break;
            case INFO:
                holder.tvDir.setText("--");
                holder.tvDir.setTextColor(Color.parseColor("#BDBDBD"));
                holder.tvData.setTextColor(Color.parseColor("#BDBDBD"));
                break;
            case ERROR:
                holder.tvDir.setText("!!");
                holder.tvDir.setTextColor(Color.parseColor("#F44336"));
                holder.tvData.setTextColor(Color.parseColor("#F44336"));
                break;
            case VERDICT:
                holder.tvDir.setText("==");
                holder.tvDir.setTextColor(Color.parseColor("#FFC107"));
                holder.tvData.setTextColor(Color.parseColor("#FFC107"));
                break;
        }

        String data = entry.getData();
        if (entry.getNote() != null && !entry.getNote().isEmpty()) {
            data = data + "  # " + entry.getNote();
        }
        holder.tvData.setText(data);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        TextView tvTime, tvDir, tvData;

        LogViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvDir = itemView.findViewById(R.id.tv_dir);
            tvData = itemView.findViewById(R.id.tv_data);
        }
    }
}