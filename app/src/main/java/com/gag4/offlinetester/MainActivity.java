package com.gag4.offlinetester;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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

        // Init ViewModel
        viewModel = new ViewModelProvider(this).get(TestViewModel.class);

        // Init UI components
        initViews();
        setupRecyclerView();
        observeViewModel();
        setupBroadcastReceiver();
        checkPermissions();
        checkNfc();

        // Init logger
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
        logAdapter = new LogAdapter(viewModel.getLogEntries().getValue());
        rvLog.setLayoutManager(new LinearLayoutManager(this));
        rvLog.setAdapter(logAdapter);
    }

    private void observeViewModel() {
        viewModel.getLogEntries().observe(this, entries -> {
            logAdapter.notifyDataSetChanged();
            if (entries.size() > 0) {
                rvLog.smoothScrollToPosition(entries.size() - 1);
            }
        });

        viewModel.getStatusText().observe(this, status -> {
            tvStatus.setText(status);
        });

        viewModel.getProgressPercent().observe(this, percent -> {
            pbTest.setProgress(percent);
        });

        viewModel.getTestVerdict().observe(this, verdict -> {
            if (verdict != null) {
                tvVerdict.setText(verdict.getDisplayText());
                // Color code verdict
                if (verdict == ApduAnalyzer.Verdict.SUPPORTS_OFFLINE) {
                    tvVerdict.setTextColor(getResources().getColor(android.R.color.holo_green_light));
                } else if (verdict == ApduAnalyzer.Verdict.REQUIRES_ONLINE || verdict == ApduAnalyzer.Verdict.DECLINED) {
                    tvVerdict.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                } else {
                    tvVerdict.setTextColor(getResources().getColor(android.R.color.holo_orange_light));
                }
            }
        });

        viewModel.getTestRunning().observe(this, running -> {
            testInProgress = running;
            btnStart.setEnabled(!running);
            if (!running) {
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
                    long timestamp = intent.getLongExtra(ApduLogger.EXTRA_TIMESTAMP, System.currentTimeMillis());

                    LogEntry.Direction dir = LogEntry.Direction.valueOf(direction);
                    LogEntry entry = new LogEntry(timestamp, dir, data, note);

                    // Update progress
                    long elapsed = System.currentTimeMillis() - viewModel.testStartTime;
                    int percent = (int) ((elapsed * 100) / Constants.TEST_TIMEOUT_MS);
                    percent = Math.min(percent, 99); // Cap at 99 until test ends
                    viewModel.updateProgress(percent);

                    viewModel.addLogEntry(entry);

                    // Analyze APDU if TX
                    if (dir == LogEntry.Direction.TX) {
                        byte[] apdu = HexUtils.fromHex(data);
                        viewModel.analyzeApdu(apdu);
                    }

                    // Check if test should end
                    if (elapsed >= Constants.TEST_TIMEOUT_MS && testInProgress) {
                        finishTest();
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(ApduLogger.ACTION_LOG);
        registerReceiver(logReceiver, filter);
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        PERMISSION_REQUEST_CODE);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE);
            }
        }
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

        // Set timeout
        timeoutRunnable = this::finishTest;
        uiHandler.postDelayed(timeoutRunnable, Constants.TEST_TIMEOUT_MS);

        Toast.makeText(this, "Test pornit. Apropie POS-ul!", Toast.LENGTH_SHORT).show();
    }

    private void finishTest() {
        if (!testInProgress) return;

        uiHandler.removeCallbacks(timeoutRunnable);

        ApduAnalyzer.Verdict verdict = viewModel.getAnalyzer().finalizeAnalysis();
        viewModel.finishTest(verdict);

        // Log verdict
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

        // Show path
        String msg = "Log salvat în:\n" + logFile.getAbsolutePath();
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();

        // Try to open file manager
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri uri = Uri.parse("file://" + logFile.getParent());
        intent.setData(uri);
        try {
            startActivity(intent);
        } catch (Exception e) {
            android.util.Log.e(TAG, "Could not open file manager", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (logReceiver != null) {
            unregisterReceiver(logReceiver);
        }
        ApduLogger.getInstance().shutdown();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, "Permisiuni necesare!", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
