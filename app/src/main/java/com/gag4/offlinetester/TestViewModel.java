package com.gag4.offlinetester;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;

public class TestViewModel extends AndroidViewModel {

    public static final long TEST_TIMEOUT_MS = 45000L;

    // LiveData pentru UI
    private final MutableLiveData<List<LogEntry>> logEntries = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> statusText = new MutableLiveData<>("Apropie telefonul de POS");
    private final MutableLiveData<Integer> progressPercent = new MutableLiveData<>(0);
    private final MutableLiveData<ApduAnalyzer.Verdict> testVerdict = new MutableLiveData<>(ApduAnalyzer.Verdict.UNKNOWN);

    // Analizor
    private final ApduAnalyzer analyzer = new ApduAnalyzer();

    // Timeout
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;

    // Timestamp start test - PUBLIC pentru a fi accesat din MainActivity
    public long testStartTime = 0L;
    private boolean testRunning = false;

    public TestViewModel(@NonNull Application application) {
        super(application);
        ApduLogger.getInstance().init(application);
    }

    // ---------- Getters pentru LiveData ----------
    public LiveData<List<LogEntry>> getLogEntries() {
        return logEntries;
    }

    public LiveData<String> getStatusText() {
        return statusText;
    }

    public LiveData<Integer> getProgressPercent() {
        return progressPercent;
    }

    public LiveData<ApduAnalyzer.Verdict> getTestVerdict() {
        return testVerdict;
    }

    // ---------- Getter pentru testStartTime (variantă recomandată) ----------
    public long getTestStartTime() {
        return testStartTime;
    }

    // ---------- Start test ----------
    public void startTest() {
        testRunning = true;
        testStartTime = System.currentTimeMillis();

        analyzer.reset();
        testVerdict.setValue(ApduAnalyzer.Verdict.UNKNOWN);
        statusText.setValue("Se verifică...");
        progressPercent.setValue(0);

        clearLogsInternal();

        // Timeout
        if (timeoutRunnable != null) {
            handler.removeCallbacks(timeoutRunnable);
        }
        timeoutRunnable = () -> {
            if (testRunning) {
                testRunning = false;
                ApduAnalyzer.Verdict v = analyzer.finalizeAnalysis();
                testVerdict.setValue(v);
                statusText.setValue("Test finalizat (timeout)");
                progressPercent.setValue(100);
                addLog(LogEntry.Direction.VERDICT, "Verdict: " + v.name(), "Timeout 45s");
            }
        };
        handler.postDelayed(timeoutRunnable, TEST_TIMEOUT_MS);
    }

    // ---------- Stop test ----------
    public void stopTest() {
        testRunning = false;
        if (timeoutRunnable != null) {
            handler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
        ApduAnalyzer.Verdict v = analyzer.finalizeAnalysis();
        testVerdict.setValue(v);
        statusText.setValue("Test oprit manual");
        progressPercent.setValue(100);
    }

    // ---------- Adaugă log din broadcast ----------
    public void addLog(LogEntry.Direction direction, String data, String note) {
        addLog(System.currentTimeMillis(), direction, data, note);
    }

    public void addLog(long timestamp, LogEntry.Direction direction, String data, String note) {
        List<LogEntry> current = logEntries.getValue();
        if (current == null) current = new ArrayList<>();
        else current = new ArrayList<>(current);

        LogEntry entry = new LogEntry(timestamp, direction, data, note);
        current.add(entry);
        logEntries.setValue(current);

        // Analizăm APDU-urile TX (de la POS)
        if (direction == LogEntry.Direction.TX && data != null) {
            try {
                byte[] apdu = HexUtils.fromHex(data);
                ApduAnalyzer.Verdict v = analyzer.analyzeTx(apdu);
                if (v != null) {
                    testVerdict.setValue(v);
                    addLog(LogEntry.Direction.VERDICT, "Verdict: " + v.name(), "Analiză APDU");
                }
            } catch (Exception ignored) {}
        }

        // Progress
        if (testRunning) {
            long elapsed = System.currentTimeMillis() - testStartTime;
            int progress = (int) Math.min(100, (elapsed * 100) / TEST_TIMEOUT_MS);
            progressPercent.setValue(progress);
        }
    }

    // ---------- Clear logs ----------
    public void clearLogs() {
        clearLogsInternal();
        analyzer.reset();
        testVerdict.setValue(ApduAnalyzer.Verdict.UNKNOWN);
        statusText.setValue("Apropie telefonul de POS");
        progressPercent.setValue(0);
    }

    private void clearLogsInternal() {
        logEntries.setValue(new ArrayList<>());
    }

    // ---------- Verdict curent ----------
    public ApduAnalyzer.Verdict getCurrentVerdict() {
        ApduAnalyzer.Verdict v = testVerdict.getValue();
        return (v != null) ? v : ApduAnalyzer.Verdict.UNKNOWN;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (timeoutRunnable != null) {
            handler.removeCallbacks(timeoutRunnable);
        }
    }
}