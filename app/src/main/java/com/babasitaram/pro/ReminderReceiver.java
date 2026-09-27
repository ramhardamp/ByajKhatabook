package com.babasitaram.pro;

import android.app.*;
import android.content.*;
import android.os.Build;
import android.telephony.SmsManager;
import org.json.*;
import java.util.*;

public final class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL="recovery";
    public void onReceive(Context context,Intent intent){
        try{
            LedgerStore store=new LedgerStore(context);JSONObject s=store.settings();if(!s.optBoolean("reminderEnabled",true))return;
            JSONArray cs=store.customers();int count=0;double total=0;
            for(int i=0;i<cs.length();i++){JSONObject c=cs.optJSONObject(i);if(c==null)continue;double due=Math.max(0,-c.optJSONObject("khata").optDouble("balance",0));JSONObject b=c.optJSONObject("byaaj");JSONArray ls=b==null?null:b.optJSONArray("loans");
                if(ls!=null)for(int j=0;j<ls.length();j++)due+=loanOutstanding(store,c,ls.optJSONObject(j));
                if(due>=s.optDouble("minReminder",100)&&eligible(c,s.optInt("reminderDays",7))){count++;total+=due;c.put("reminderEligible",true);if(s.optBoolean("directSmsEnabled",false))trySms(context,c,due);}
            }
            store.save();if(count>0)notifyUser(context,count,total);
        }catch(Exception ignored){}
    }
    private double loanOutstanding(LedgerStore st,JSONObject c,JSONObject l){if(l==null)return 0;double p=l.optDouble("principal",0),rate=l.optDouble("rate",0),paid=0;long start=parse(l.optString("rawStartDate",l.optString("startDate","")));double m=start<=0?0:Math.max(0,(System.currentTimeMillis()-start)/(30.4375*24*3600*1000));double interest="compound".equalsIgnoreCase(l.optString("interestType"))?p*(Math.pow(1+rate/100.0/Math.max(1,l.optInt("compFreq",12)),m*Math.max(1,l.optInt("compFreq",12))/12.0)-1):p*rate*m/100.0;JSONArray ps=st.loans();for(int i=0;i<ps.length();i++){JSONObject x=ps.optJSONObject(i);if(x!=null&&x.optInt("customerId")==c.optInt("id")&&x.optInt("loanId")==l.optInt("id"))paid+=x.optDouble("amount",0);}return Math.max(0,p+interest-paid);}
    private long parse(String s){for(String f:new String[]{"EEE MMM dd HH:mm:ss zzz yyyy","yyyy-MM-dd'T'HH:mm:ss.SSS'Z'","yyyy-MM-dd"})try{return new java.text.SimpleDateFormat(f,Locale.US).parse(s).getTime();}catch(Exception ignored){}return 0;}
    private void trySms(Context c,JSONObject x,double due){if(Build.VERSION.SDK_INT>=23&&c.checkSelfPermission("android.permission.SEND_SMS")!=0)return;String p=x.optString("phone","").replaceAll("\D","");if(p.isEmpty())return;try{SmsManager.getDefault().sendTextMessage(p,null,"नमस्ते "+x.optString("name","Customer")+" जी 🙏\nबकाया: ₹"+String.format(Locale.US,"%.0f",due),null,null);x.put("lastReminderAt",new Date().toString());}catch(Exception ignored){}}
    private boolean eligible(JSONObject c,int d){String s=c.optString("lastReminderAt","");if(s.isEmpty())return true;try{return (System.currentTimeMillis()-new Date(s).getTime())/(24L*3600*1000)>=Math.max(1,d);}catch(Exception e){return true;}}
    private void notifyUser(Context c,int n,double total){NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Recovery reminders",NotificationManager.IMPORTANCE_DEFAULT));Intent i=new Intent(c,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(c,1,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);b.setSmallIcon(R.drawable.logo).setContentTitle("Guru Shree Recovery").setContentText(n+" reminder(s) due • ₹"+String.format(Locale.US,"%.0f",total)).setAutoCancel(true).setContentIntent(pi);if(Build.VERSION.SDK_INT<33||c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")==0)nm.notify(7001,b.build());}
}
