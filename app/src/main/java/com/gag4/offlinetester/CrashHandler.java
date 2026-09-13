package com.gag4.offlinetester;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    public CrashHandler(Context context) {
        this.context = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    public static void install(Context context) {
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(context));
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            writeCrashToFile(throwable);
        } catch (Exception ignored) {}

        // Lăsăm sistemul să facă ce ar fi făcut oricum
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, throwable);
        }
    }

    private void writeCrashToFile(Throwable throwable) {
        try {
            File dir = context.getExternalFilesDir(null);
            if (dir == null) return;
            File crashFile = new File(dir, "crash_last.txt");

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            pw.println("=== CRASH at " + timestamp + " ===");
            pw.println("Thread: " + Thread.currentThread().getName());
            pw.println("App: " + context.getPackageName());
            pw.println();
            throwable.printStackTrace(pw);
            pw.flush();

            try (FileWriter fw = new FileWriter(crashFile, false)) {
                fw.write(sw.toString());
            }
        } catch (Exception e) {
            // nu putem face nimic dacă nu putem scrie
        }
    }
}