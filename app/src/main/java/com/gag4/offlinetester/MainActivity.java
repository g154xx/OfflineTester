package com.gag4.offlinetester;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String TAG = "MainActivity";

    private TestViewModel viewModel;
    private RecyclerView rvLog;
    private LogAdapter logAdapter;
    private TextView tvStatus, tvVerdict, tvSummary;
    private ProgressBar pbTest;
    private Button btnStart, btnClear, btnExport;

    private BroadcastReceiver logReceiver;
    private NfcAdapter nfcAdapter;
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;
    private boolean testInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewModel = new ViewModelProvider(this).get(TestViewModel.class);

        initViews();
        setupRecyclerView();
        observeViewModel();
        setupBroadcastReceiver();
        checkPermissions();
        checkNfc();

        ApduLogger.getInstance().init(this);
    }

    private void initViews() {
        rvLog = findViewById(R.id.rv_log);
        tvStatus = findViewById(R.id.tv_status);
        tvVerdict = findViewById(R.id.tv_verdict);
        tvSummary = findViewById(R.id.tv_summary);
        pbTest = findViewById(R.id.pb_test);
        btnStart = findViewById(R.id.btn_start);
        btnClear = findViewById(R.id.btn_clear);
        btnExport = findViewById(R.id.btn_export);

        btnStart.setOnClickListener(v -> startTest());
        btnClear.setOnClickListener(v -> clearLog());
        btnExport.setOnClickListener(v -> exportLog());
    }

    private void setupRecyclerView() {
        logAdapter = new LogAdapter();
        rvLog.setLayoutManager(new LinearLayoutManager(this));
        rvLog.setAdapter(logAdapter);
    }

    private void observeViewModel() {
        viewModel.getLogEntries().observe(this, entries -> {
            logAdapter.setEntries(entries);
            if (entries != null && entries.size() > 0) {
                rvLog.smoothScrollToPosition(entries.size() - 1);
            }
        });

        viewModel.getStatusText().observe(this, status -> {
            if (status != null) tvStatus.setText(status);
        });

        viewModel.getProgressPercent().observe(this, percent -> {
            if (percent != null) pbTest.setProgress(percent);
        });

        viewModel.getTestVerdict().observe(this, verdict -> {
            if (verdict != null) {
                tvVerdict.setText(verdict.getDisplayText());
                if (verdict == ApduAnalyzer.Verdict.SUPPORTS_OFFLINE) {
                    tvVerdict.setTextColor(getResources().getColor(android.R.color.holo_green_light));
                } else if (verdict == ApduAnalyzer.Verdict.REQUIRES_ONLINE
                        || verdict == ApduAnalyzer.Verdict.DECLINED) {
                    tvVerdict.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                } else {
                    tvVerdict.setTextColor(getResources().getColor(android.R.color.holo_orange_light));
                }
            }
        });

        viewModel.getTestRunning().observe(this, running -> {
            testInProgress = (running != null) && running;
            btnStart.setEnabled(!testInProgress);
            if (!testInProgress) {
                tvSummary.setText(viewModel.getTransactionSummary());
            }
        });
    }

    private void setupBroadcastReceiver() {
        logReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ApduLogger.ACTION_LOG.equals(intent.getAction())) {
                    String direction = intent.getStringExtra(ApduLogger.EXTRA_DIRECTION);
                    String data = intent.getStringExtra(ApduLogger.EXTRA_DATA);
                    String note = intent.getStringExtra(ApduLogger.EXTRA_NOTE);
                    long timestamp = intent.getLongExtra(ApduLogger.EXTRA_TIMESTAMP,
                            System.currentTimeMillis());

                    if (direction == null) return;

                    LogEntry.Direction dir;
                    try {
                        dir = LogEntry.Direction.valueOf(direction);
                    } catch (Exception e) {
                        dir = LogEntry.Direction.INFO;
                    }

                    LogEntry entry = new LogEntry(timestamp, dir, data, note);

                    // Update progress
                    long elapsed = System.currentTimeMillis() - viewModel.testStartTime;
                    int percent = (int) ((elapsed * 100) / Constants.TEST_TIMEOUT_MS);
                    percent = Math.min(percent, 99);
                    viewModel.updateProgress(percent);

                    viewModel.addLogEntry(entry);

                    // Analyze TX (APDU primit de la POS)
                    if (dir == LogEntry.Direction.TX && data != null) {
                        try {
                            byte[] apdu = HexUtils.fromHex(data);
                            viewModel.analyzeApdu(apdu);
                        } catch (Exception ignored) {}
                    }

                    // Auto-finish on timeout
                    if (elapsed >= Constants.TEST_TIMEOUT_MS && testInProgress) {
                        finishTest();
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(ApduLogger.ACTION_LOG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(logReceiver, filter);
        }
    }

    private void checkPermissions() {
        // NFC = permisiune de manifest (nu runtime)
        // WRITE/READ_EXTERNAL_STORAGE = eliminate pe Android 11+
        // Doar POST_NOTIFICATIONS necesită runtime pe Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        PERMISSION_REQUEST_CODE);
                return;
            }
        }

        tvStatus.setText("✓ Permisiuni OK - NFC activ");
        tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_light));
    }

    private void checkNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC nu e disponibil!", Toast.LENGTH_LONG).show();
        } else if (!nfcAdapter.isEnabled()) {
            Toast.makeText(this, "Activaţi NFC!", Toast.LENGTH_SHORT).show();
        }
    }

    private void startTest() {
        clearLog();
        viewModel.startTest();

        timeoutRunnable = this::finishTest;
        uiHandler.postDelayed(timeoutRunnable, Constants.TEST_TIMEOUT_MS);

        Toast.makeText(this, "Test pornit. Apropie POS-ul!", Toast.LENGTH_SHORT).show();
    }

    private void finishTest() {
        if (!testInProgress) return;

        uiHandler.removeCallbacks(timeoutRunnable);

        ApduAnalyzer.Verdict verdict = viewModel.getAnalyzer().finalizeAnalysis();
        viewModel.finishTest(verdict);

        viewModel.addLogFromApdu(
                LogEntry.Direction.VERDICT,
                verdict.getDisplayText(),
                "Test terminat"
        );

        testInProgress = false;
        Toast.makeText(this, "Verdict: " + verdict.getDisplayText(), Toast.LENGTH_LONG).show();
    }

    private void clearLog() {
        viewModel.clearLog();
        tvVerdict.setText("Nerezolvat");
        tvVerdict.setTextColor(getResources().getColor(android.R.color.holo_orange_light));
        tvSummary.setText("Aşteptând test...");
        pbTest.setProgress(0);
    }

    private void exportLog() {
        File logFile = ApduLogger.getInstance().getLogFile();
        if (logFile == null || !logFile.exists()) {
            Toast.makeText(this, "Nu sunt log-uri de exportat!", Toast.LENGTH_SHORT).show();
            return;
        }
        String msg = "Log salvat în:\n" + logFile.getAbsolutePath();
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (logReceiver != null) {
            try {
                unregisterReceiver(logReceiver);
            } catch (Exception ignored) {}
        }
        ApduLogger.getInstance().shutdown();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                tvStatus.setText("✓ Permisiuni OK - NFC activ");
                tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_light));
                Toast.makeText(this, "Permisiuni acordate!", Toast.LENGTH_SHORT).show();
            } else {
                tvStatus.setText("Permisiuni parțiale - app poate funcționa");
                tvStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_light));
                Toast.makeText(this, "Notificările sunt refuzate, dar aplicația poate rula.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}