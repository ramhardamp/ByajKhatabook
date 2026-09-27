package com.babasitaram.pro;
import android.content.*;
public final class ReminderBootReceiver extends BroadcastReceiver { public void onReceive(Context c,Intent i){ReminderScheduler.schedule(c);} }
