package com.babasitaram.pro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.telephony.SmsManager;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * WHAT CHANGED FROM THE ORIGINAL:
 *  - Interest/due math now comes from Calc, the same class MainActivity uses, so a reminder can
 *    never disagree with what the app shows.
 *  - "lastReminderAt" used to be stamped only when an SMS actually went out, so with SMS off the
 *    notification fired again every single day. It's now split into lastReminderAt (SMS) and
 *    lastReminderNotifiedAt (notification), and both respect reminderDays.
 *  - A missing/malformed "khata" object on a customer no longer throws — per_customer failures
 *    are now caught individually so one bad record can't silently stop every reminder.
 */
public final class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "recovery";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            LedgerStore store = new LedgerStore(context);
            JSONObject s = store.settings();
            if (!s.optBoolean("reminderEnabled", true)) return;

            int days = Math.max(1, s.optInt("reminderDays", 7));
            double min = s.optDouble("minReminder", 100);
            boolean directSms = s.optBoolean("directSmsEnabled", false);
            long now = System.currentTimeMillis();

            JSONArray cs = store.customers();
            int count = 0;
            double total = 0;
            boolean changed = false;

            for (int i = 0; i < cs.length(); i++) {
                JSONObject c = cs.optJSONObject(i);
                if (c == null || Calc.isSupplier(c)) continue;
                try {
                    double due = Calc.totalDue(store, c, now);
                    if (due < min) continue;

                    if (directSms && eligible(c, "lastReminderAt", days, now)) {
                        if (trySms(context, c, due)) { c.put("lastReminderAt", Calc.now()); changed = true; }
                    }
                    if (eligible(c, "lastReminderNotifiedAt", days, now)) {
                        count++;
                        total += due;
                        c.put("lastReminderNotifiedAt", Calc.now());
                        changed = true;
                    }
                } catch (Exception perCustomer) {
                    // One malformed customer record must never stop reminders for everyone else.
                }
            }

            if (changed) store.save();
            if (count > 0) notifyUser(context, count, total);
        } catch (Exception ignored) {
            // A failed reminder run must never crash the app or corrupt data; just skip this tick.
        }
    }

    private boolean eligible(JSONObject c, String field, int days, long now) {
        long last = Calc.parse(c.optString(field, ""));
        if (last <= 0) return true;
        return (now - last) >= days * Calc.DAY;
    }

    private boolean trySms(Context c, JSONObject x, double due) {
        if (Build.VERSION.SDK_INT >= 23 && c.checkSelfPermission("android.permission.SEND_SMS") != 0) return false;
        String p = x.optString("phone", "").replaceAll("\\D", "");
        if (p.isEmpty()) return false;
        try {
            SmsManager.getDefault().sendTextMessage(p, null,
                    "नमस्ते " + x.optString("name", "Customer") + " जी 🙏\nबकाया: " + Calc.inr(due), null, null);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void notifyUser(Context c, int n, double total) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Recovery reminders", NotificationManager.IMPORTANCE_DEFAULT));
        }
        Intent i = new Intent(c, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(c, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.logo)
                .setContentTitle("Guru Shree Recovery")
                .setContentText(n + " reminder(s) due • " + Calc.inr(total))
                .setAutoCancel(true)
                .setContentIntent(pi);
        if (Build.VERSION.SDK_INT < 33 || c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == 0) {
            nm.notify(7001, b.build());
        }
    }
}
