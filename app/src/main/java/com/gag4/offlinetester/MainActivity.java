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
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;
    private TextView tvVerdict;
    private TextView tvSummary;
    private ProgressBar pbTest;
    private RecyclerView rvLog;
    private LogAdapter logAdapter;
    private TestViewModel viewModel;

    private BroadcastReceiver logReceiver;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    Toast.makeText(this, "Permisiune NFC refuzată", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        tvVerdict = findViewById(R.id.tv_verdict);
        tvSummary = findViewById(R.id.tv_summary);
        pbTest = findViewById(R.id.pb_test);
        rvLog = findViewById(R.id.rv_log);
        Button btnClear = findViewById(R.id.btn_clear);
        Button btnStart = findViewById(R.id.btn_start);
        Button btnExport = findViewById(R.id.btn_export);

        logAdapter = new LogAdapter();
        rvLog.setLayoutManager(new LinearLayoutManager(this));
        rvLog.setAdapter(logAdapter);

        viewModel = new ViewModelProvider(this).get(TestViewModel.class);

        // Observers
        viewModel.getLogEntries().observe(this, entries -> {
            logAdapter.setEntries(entries);
            if (entries != null && !entries.isEmpty()) {
                rvLog.scrollToPosition(entries.size() - 1);
            }
        });

        viewModel.getStatusText().observe(this, text -> {
            if (text != null) tvStatus.setText(text);
        });

        viewModel.getProgressPercent().observe(this, progress -> {
            if (progress != null && pbTest != null) pbTest.setProgress(progress);
        });

        viewModel.getTestVerdict().observe(this, verdict -> {
            if (verdict != null) showVerdict(verdict);
        });

        // Verifică NFC și permisiuni
        checkPermissions();
        checkNfc();

        // Buton Start
        btnStart.setOnClickListener(v -> {
            viewModel.startTest();
            Toast.makeText(this, "Test pornit. Apropie telefonul de POS.", Toast.LENGTH_SHORT).show();
        });

        // Buton Clear
        btnClear.setOnClickListener(v -> {
            viewModel.clearLogs();
            logAdapter.refreshData();
        });

        // Buton Export
        btnExport.setOnClickListener(v -> exportLog());

        // Broadcast receiver pentru log-uri
        logReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String directionStr = intent.getStringExtra("direction");
                String data = intent.getStringExtra("data");
                String note = intent.getStringExtra("note");
                long ts = intent.getLongExtra("timestamp", System.currentTimeMillis());

                if (directionStr == null) return;

                LogEntry.Direction direction;
                try {
                    direction = LogEntry.Direction.valueOf(directionStr);
                } catch (Exception e) {
                    direction = LogEntry.Direction.INFO;
                }

                viewModel.addLog(ts, direction, data, note);
            }
        };

        IntentFilter filter = new IntentFilter(ApduLogger.ACTION_LOG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(logReceiver, filter);
        }

        Toast.makeText(this,
                "Apropie telefonul de POS. Log-urile apar mai jos.",
                Toast.LENGTH_LONG).show();
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.NFC)
                != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                permissionLauncher.launch(Manifest.permission.NFC);
            }
        }
    }

    private void checkNfc() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);
        if (adapter == null) {
            Toast.makeText(this, "Dispozitivul nu are NFC", Toast.LENGTH_LONG).show();
        } else if (!adapter.isEnabled()) {
            Toast.makeText(this, "NFC este dezactivat. Activează-l!", Toast.LENGTH_LONG).show();
        }
    }

    private void showVerdict(ApduAnalyzer.Verdict verdict) {
        switch (verdict) {
            case SUPPORTS_OFFLINE:
                tvVerdict.setText("fonduri insuficiente");
                tvVerdict.setTextColor(getResources().getColor(R.color.green_success));
                break;
            case REQUIRES_ONLINE:
            case DECLINED:
                tvVerdict.setText("fonduri insuficiente");
                tvVerdict.setTextColor(getResources().getColor(R.color.red_error));
                break;
            case INCOMPLETE:
                tvVerdict.setText("Verificare incompletă");
                tvVerdict.setTextColor(getResources().getColor(R.color.yellow_warning));
                break;
            case UNKNOWN:
            default:
                tvVerdict.setText(R.string.status_idle);
                tvVerdict.setTextColor(getResources().getColor(R.color.text_gray));
                break;
        }
    }

    private void exportLog() {
        try {
            java.io.File logFile = ApduLogger.getInstance().getLogFile();
            if (logFile != null && logFile.exists()) {
                Toast.makeText(this, "Log salvat: " + logFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Nu există log de exportat.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Eroare export: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (logReceiver != null) {
            try {
                unregisterReceiver(logReceiver);
            } catch (Exception ignored) {}
        }
    }
}