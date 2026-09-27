package com.babasitaram.pro;
import android.app.*;import android.content.*;import java.util.*;
public final class ReminderScheduler {
    private ReminderScheduler(){}
    public static void schedule(Context c){Intent i=new Intent(c,ReminderReceiver.class);PendingIntent p=PendingIntent.getBroadcast(c,7001,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);Calendar x=Calendar.getInstance();x.set(Calendar.HOUR_OF_DAY,10);x.set(Calendar.MINUTE,0);x.set(Calendar.SECOND,0);x.set(Calendar.MILLISECOND,0);if(x.getTimeInMillis()<=System.currentTimeMillis())x.add(Calendar.DAY_OF_YEAR,1);a.setInexactRepeating(AlarmManager.RTC_WAKEUP,x.getTimeInMillis(),AlarmManager.INTERVAL_DAY,p);}
}
