package com.gag4.offlinetester;

import android.content.Context;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import java.util.ArrayList;
import java.util.List;

public class TestViewModel extends ViewModel {

    private MutableLiveData<List<LogEntry>> logEntries = new MutableLiveData<>();
    private MutableLiveData<ApduAnalyzer.Verdict> testVerdict = new MutableLiveData<>();
    private MutableLiveData<Boolean> testRunning = new MutableLiveData<>();
    private MutableLiveData<String> statusText = new MutableLiveData<>();
    private MutableLiveData<Integer> progressPercent = new MutableLiveData<>();

    private List<LogEntry> entries = new ArrayList<>();
    private ApduAnalyzer analyzer;
    private ApduLogger logger;
    private long testStartTime = 0;

    public TestViewModel() {
        logEntries.setValue(entries);
        testRunning.setValue(false);
        statusText.setValue("Inactiv");
        progressPercent.setValue(0);
        analyzer = new ApduAnalyzer();
        logger = ApduLogger.getInstance();
    }

    // ===== Public API =====

    public MutableLiveData<List<LogEntry>> getLogEntries() {
        return logEntries;
    }

    public MutableLiveData<ApduAnalyzer.Verdict> getTestVerdict() {
        return testVerdict;
    }

    public MutableLiveData<Boolean> getTestRunning() {
        return testRunning;
    }

    public MutableLiveData<String> getStatusText() {
        return statusText;
    }

    public MutableLiveData<Integer> getProgressPercent() {
        return progressPercent;
    }

    public void addLogEntry(LogEntry entry) {
        entries.add(entry);
        logEntries.setValue(new ArrayList<>(entries));
    }

    public void addLogFromApdu(LogEntry.Direction direction, String data, String note) {
        LogEntry entry = new LogEntry(direction, data, note);
        addLogEntry(entry);
    }

    public void clearLog() {
        entries.clear();
        logEntries.setValue(new ArrayList<>(entries));
        analyzer.reset();
    }

    public void startTest() {
        analyzer.reset();
        testRunning.setValue(true);
        statusText.setValue("Testul porneşte...");
        progressPercent.setValue(0);
        testStartTime = System.currentTimeMillis();
    }

    public void updateProgress(int percent) {
        progressPercent.setValue(percent);

        // Update status based on elapsed time
        long elapsed = System.currentTimeMillis() - testStartTime;
        long remaining = Math.max(0, (Constants.TEST_TIMEOUT_MS - elapsed) / 1000);
        statusText.setValue("În aşteptare... (rămas: " + remaining + "s)");
    }

    public void finishTest(ApduAnalyzer.Verdict verdict) {
        testVerdict.setValue(verdict);
        testRunning.setValue(false);
        progressPercent.setValue(100);
        statusText.setValue("Test complet: " + verdict.getDisplayText());
    }

    public ApduAnalyzer getAnalyzer() {
        return analyzer;
    }

    public String getTransactionSummary() {
        return analyzer.getTransactionSummary();
    }

    public ApduAnalyzer.Verdict getCurrentVerdict() {
        Verdict v = testVerdict.getValue();
        return v != null ? v : ApduAnalyzer.Verdict.UNKNOWN;
    }

    public void analyzeApdu(byte[] apdu) {
        if (apdu != null) {
            ApduAnalyzer.Verdict v = analyzer.analyzeTx(apdu);
            if (v != null && v != ApduAnalyzer.Verdict.UNKNOWN) {
                testVerdict.setValue(v);
            }
        }
    }
}
