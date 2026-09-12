package com.gag4.offlinetester;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {

    private final List<LogEntry> entries;

    public LogAdapter(List<LogEntry> entries) {
        this.entries = entries;
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

        int colorDir = Color.parseColor("#FFFFFF");
        int colorData = Color.parseColor("#FFFFFF");
        String dirText = "";

        switch (entry.getDirection()) {
            case TX:
                dirText = ">>";
                colorDir = Color.parseColor("#4CAF50");  // Green
                colorData = Color.parseColor("#4CAF50");
                break;
            case RX:
                dirText = "<<";
                colorDir = Color.parseColor("#03A9F4");  // Blue
                colorData = Color.parseColor("#03A9F4");
                break;
            case INFO:
                dirText = "--";
                colorDir = Color.parseColor("#9E9E9E");  // Gray
                colorData = Color.parseColor("#9E9E9E");
                break;
            case ERROR:
                dirText = "!!";
                colorDir = Color.parseColor("#F44336");  // Red
                colorData = Color.parseColor("#F44336");
                break;
            case VERDICT:
                dirText = "==";
                colorDir = Color.parseColor("#FFC107");  // Yellow
                colorData = Color.parseColor("#FFC107");
                break;
        }

        holder.tvDir.setText(dirText);
        holder.tvDir.setTextColor(colorDir);

        String data = entry.getData();
        if (entry.getNote() != null && !entry.getNote().isEmpty()) {
            data = data + "  # " + entry.getNote();
        }
        holder.tvData.setText(data);
        holder.tvData.setTextColor(colorData);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
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
