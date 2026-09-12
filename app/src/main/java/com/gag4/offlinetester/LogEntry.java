package com.gag4.offlinetester;

public class LogEntry {

    public enum Direction {
        TX,      // Transmission (app sends)
        RX,      // Reception (app receives)
        INFO,    // Information message
        ERROR,   // Error message
        VERDICT  // Final verdict
    }

    private final long timestamp;
    private final Direction direction;
    private final String data;
    private final String note;

    public LogEntry(Direction direction, String data, String note) {
        this.timestamp = System.currentTimeMillis();
        this.direction = direction;
        this.data = data;
        this.note = note;
    }

    public LogEntry(long timestamp, Direction direction, String data, String note) {
        this.timestamp = timestamp;
        this.direction = direction;
        this.data = data;
        this.note = note;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Direction getDirection() {
        return direction;
    }

    public String getData() {
        return data;
    }

    public String getNote() {
        return note;
    }

    public String getFormattedTime() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss.SSS");
        return sdf.format(new java.util.Date(timestamp));
    }

    @Override
    public String toString() {
        String note_str = (note != null && !note.isEmpty()) ? "  # " + note : "";
        return getFormattedTime() + " [" + direction + "] " + data + note_str;
    }
}
