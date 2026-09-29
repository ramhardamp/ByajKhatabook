package com.babasitaram.pro;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * NEW FILE. AppCompatDelegate.setDefaultNightMode() only sticks for the current process, so
 * without this the dark-mode switch in Settings would silently reset to "system" every time the
 * app was killed and reopened. Reading the setting here, before any Activity is created, fixes
 * that with a single cheap LedgerStore open-and-discard.
 */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            LedgerStore store = new LedgerStore(this);
            if ("dark".equals(store.settings().optString("darkMode", "system"))) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            }
        } catch (Exception ignored) {
            // If the database can't be opened yet, MainActivity's own error handling covers it;
            // here we just fall back to following the system theme.
        }
    }
}
