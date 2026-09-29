package com.babasitaram.pro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-arms the daily reminder alarm after a phone restart (alarms don't survive reboot). */
public final class ReminderBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        ReminderScheduler.schedule(c);
    }
}
