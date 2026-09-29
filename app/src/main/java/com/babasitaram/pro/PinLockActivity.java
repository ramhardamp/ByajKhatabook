package com.babasitaram.pro;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * NEW SCREEN. The original app let you set a PIN in Settings but never actually asked for it
 * anywhere, so it protected nothing. MainActivity now launches this first whenever a PIN is
 * set, and only opens the real app after LedgerStore.verifyPin() succeeds. Five wrong PINs
 * trigger a 30-second lockout instead of unlimited guesses.
 */
public class PinLockActivity extends AppCompatActivity {
    public static final String RESULT_UNLOCKED = "unlocked";
    private LedgerStore store;
    private final StringBuilder entered = new StringBuilder();
    private TextView dotsView, statusView;
    private int wrongAttempts = 0;
    private long lockedUntil = 0;
    private CountDownTimer timer;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { confirmExit(); }
        });
        try {
            store = new LedgerStore(this);
        } catch (Exception e) {
            // Data layer itself is broken; let MainActivity show the real recovery dialog.
            setResult(RESULT_OK, new Intent().putExtra(RESULT_UNLOCKED, true));
            finish();
            return;
        }
        if (!store.hasPin()) {
            setResult(RESULT_OK, new Intent().putExtra(RESULT_UNLOCKED, true));
            finish();
            return;
        }
        buildUi();
    }

    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + 0.5f); }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Theme.color(this, R.color.bk_surface_dim));
        root.setPadding(dp(24), dp(64), dp(24), dp(24));

        TextView title = new TextView(this);
        title.setText("🔒  Guru Shree");
        title.setTextSize(24);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(Theme.color(this, R.color.bk_text));
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        statusView = new TextView(this);
        statusView.setText("Enter your PIN to continue");
        statusView.setTextSize(14);
        statusView.setTextColor(Theme.color(this, R.color.bk_muted));
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, dp(8), 0, dp(28));
        root.addView(statusView);

        dotsView = new TextView(this);
        dotsView.setTextSize(30);
        dotsView.setLetterSpacing(0.3f);
        dotsView.setTextColor(Theme.color(this, R.color.bk_primary));
        dotsView.setGravity(Gravity.CENTER);
        root.addView(dotsView);
        renderDots();

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setPadding(0, dp(32), 0, 0);
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫"};
        for (String k : keys) {
            MaterialButton btn = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setText(k);
            btn.setTextSize(22);
            btn.setCornerRadius(dp(36));
            btn.setInsetTop(0);
            btn.setInsetBottom(0);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(72);
            lp.height = dp(72);
            lp.setMargins(dp(10), dp(10), dp(10), dp(10));
            btn.setLayoutParams(lp);
            if (k.isEmpty()) { btn.setVisibility(View.INVISIBLE); }
            else if (k.equals("⌫")) { btn.setOnClickListener(v -> backspace()); }
            else { btn.setOnClickListener(v -> digit(k)); }
            grid.addView(btn);
        }
        root.addView(grid);

        setContentView(root);
    }

    private void renderDots() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 6; i++) s.append(i < entered.length() ? "●" : "○").append(' ');
        dotsView.setText(s.toString().trim());
    }

    private void digit(String d) {
        if (System.currentTimeMillis() < lockedUntil) return;
        if (entered.length() >= 6) return;
        entered.append(d);
        renderDots();
        if (entered.length() >= 4) {
            // Auto-submit once a plausible PIN length is reached; the user can keep typing up to 6.
            if (store.verifyPin(entered.toString())) { unlock(); return; }
            if (entered.length() == 6) fail();
        }
    }

    private void backspace() {
        if (entered.length() > 0) { entered.deleteCharAt(entered.length() - 1); renderDots(); }
    }

    private void unlock() {
        setResult(RESULT_OK, new Intent().putExtra(RESULT_UNLOCKED, true));
        finish();
    }

    private void fail() {
        wrongAttempts++;
        entered.setLength(0);
        renderDots();
        if (wrongAttempts >= 5) {
            wrongAttempts = 0;
            lockedUntil = System.currentTimeMillis() + 30_000;
            if (timer != null) timer.cancel();
            timer = new CountDownTimer(30_000, 1000) {
                public void onTick(long left) { statusView.setText("Too many attempts • wait " + (left / 1000 + 1) + "s"); }
                public void onFinish() { statusView.setText("Enter your PIN to continue"); }
            }.start();
        } else {
            statusView.setText("Wrong PIN, try again (" + (5 - wrongAttempts) + " left)");
        }
    }

    private void confirmExit() {
        new MaterialAlertDialogBuilder(this).setTitle("Exit Guru Shree?")
                .setNegativeButton("Stay", null)
                .setPositiveButton("Exit", (d, w) -> finishAffinity())
                .show();
    }
}
