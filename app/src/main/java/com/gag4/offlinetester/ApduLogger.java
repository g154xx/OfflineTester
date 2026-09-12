package com.gag4.offlinetester;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe logger for APDU data.
 * - Sends broadcasts to MainActivity
 * - Writes logs to file asynchronously
 */
public class ApduLogger {

    public static final String ACTION_LOG = "com.gag4.offlinetester.APDU_LOG";
    public static final String EXTRA_DIRECTION = "direction";
    public static final String EXTRA_DATA = "data";
    public static final String EXTRA_NOTE = "note";
    public static final String EXTRA_TIMESTAMP = "timestamp";

    private static ApduLogger instance;
    private File logFile;
    private AtomicBoolean initialized = new AtomicBoolean(false);
    private LinkedBlockingQueue<LogTask> logQueue = new LinkedBlockingQueue<>();
    private LogWriterThread writerThread;

    private ApduLogger() {
        writerThread = new LogWriterThread();
        writerThread.start();
    }

    public static synchronized ApduLogger getInstance() {
        if (instance == null) {
            instance = new ApduLogger();
        }
        return instance;
    }

    public synchronized void init(Context context) {
        if (initialized.get()) return;
        if (context == null) return;

        try {
            File dir = new File(context.getExternalFilesDir(null), "logs");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            logFile = new File(dir, "log_" + timestamp + ".txt");
            initialized.set(true);
            android.util.Log.d("OfflineTester", "Logger initialized: " + logFile.getAbsolutePath());
        } catch (Exception e) {
            android.util.Log.e("OfflineTester", "Logger init error", e);
        }
    }

    public void log(Context context, LogEntry.Direction direction, String data, String note) {
        if (context != null && !initialized.get()) {
            init(context);
        }

        long ts = System.currentTimeMillis();

        // Send broadcast to UI
        if (context != null) {
            Intent intent = new Intent(ACTION_LOG);
            intent.setPackage(context.getPackageName());
            intent.putExtra(EXTRA_DIRECTION, direction.name());
            intent.putExtra(EXTRA_DATA, data);
            intent.putExtra(EXTRA_NOTE, note);
            intent.putExtra(EXTRA_TIMESTAMP, ts);
            try {
                context.sendBroadcast(intent);
            } catch (Exception e) {
                android.util.Log.e("OfflineTester", "Broadcast error", e);
            }
        }

        // Log to Logcat
        android.util.Log.d("OfflineTester", "[" + direction + "] " + data + (note != null ? " # " + note : ""));

        // Queue for async file write
        if (initialized.get()) {
            logQueue.offer(new LogTask(ts, direction, data, note));
        }
    }

    public File getLogFile() {
        return logFile;
    }

    public void shutdown() {
        if (writerThread != null) {
            writerThread.interrupt();
        }
    }

    // ===== Inner classes =====

    private static class LogTask {
        long timestamp;
        LogEntry.Direction direction;
        String data;
        String note;

        LogTask(long timestamp, LogEntry.Direction direction, String data, String note) {
            this.timestamp = timestamp;
            this.direction = direction;
            this.data = data;
            this.note = note;
        }
    }

    private class LogWriterThread extends Thread {
        @Override
        public void run() {
            while (!Thread.interrupted()) {
                try {
                    LogTask task = logQueue.poll();
                    if (task == null) {
                        // Wait a bit before polling again
                        Thread.sleep(100);
                        continue;
                    }

                    if (logFile != null && initialized.get()) {
                        writeToFile(task);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    android.util.Log.e("OfflineTester", "Log writer error", e);
                }
            }
            // Flush remaining tasks
            flushRemainingTasks();
        }

        private void flushRemainingTasks() {
            LogTask task;
            while ((task = logQueue.poll()) != null) {
                if (logFile != null) {
                    try {
                        writeToFile(task);
                    } catch (Exception e) {
                        android.util.Log.e("OfflineTester", "Flush error", e);
                    }
                }
            }
        }
    }

    private void writeToFile(LogTask task) {
        if (logFile == null) return;

        try (FileWriter fw = new FileWriter(logFile, true)) {
            String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date(task.timestamp));
            fw.write(time + " [" + task.direction + "] " + task.data);
            if (task.note != null && !task.note.isEmpty()) {
                fw.write("  # " + task.note);
            }
            fw.write("\n");
            fw.flush();
        } catch (IOException e) {
            android.util.Log.e("OfflineTester", "Log file write error", e);
        }
    }

    public void clear() {
        initialized.set(false);
        logFile = null;
    }
}
