package io.github.oldgodshen.airdropalwaysreceive;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            handler.postDelayed(this, 1000L);
        }
    };

    private Switch receiverSwitch;
    private TextView status;
    private TextView diagnostics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        receiverSwitch = findViewById(R.id.receiver_switch);
        status = findViewById(R.id.status);
        diagnostics = findViewById(R.id.diagnostics);
        Button refresh = findViewById(R.id.refresh);

        receiverSwitch.setChecked(Prefs.isEnabled(this));
        receiverSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Prefs.setEnabled(this, isChecked);
            if (isChecked) {
                startTargetForegroundService();
            }
            refreshStatus();
        });
        refresh.setOnClickListener(view -> refreshStatus());
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refreshTask);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refreshTask);
        super.onPause();
    }

    private void refreshStatus() {
        boolean enabled = Prefs.isEnabled(this);
        if (!enabled) {
            status.setText(R.string.status_disabled);
        } else if (Logs.diagnostics().contains("receiver authorization kept")) {
            status.setText(R.string.status_bound);
        } else {
            status.setText(R.string.status_waiting);
        }
        String localDiagnostics = Logs.diagnostics();
        String targetLog = localDiagnostics.isEmpty() ? RootLogReader.read() : localDiagnostics;
        diagnostics.setText(targetLog.isEmpty() ? "No diagnostics yet." : targetLog);
    }

    private void startTargetForegroundService() {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(
                    "com.miui.mishare.connectivity",
                    "com.miui.mishare.connectivity.MiShareService"));
            intent.setAction("com.miui.mishare.action.START_MISHARE_FOREGROUND");
            startForegroundService(intent);
        } catch (Throwable throwable) {
            Logs.warn("target foreground start failed", throwable);
        }
    }
}
