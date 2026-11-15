package com.epochmaking.biologycentre;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONObject;

import java.util.Map;

/**
 * নোটিশের রিমাইন্ডার: নির্দিষ্ট সময়ে ফোনে নোটিফিকেশন দেখায়।
 * ফোন রিস্টার্ট বা অ্যাপ আপডেট হলেও রিমাইন্ডার আবার সেট হয়ে যায়।
 */
public class ReminderReceiver extends BroadcastReceiver {

    static final String PREF = "reminders";
    static final String CHANNEL = "bsc_reminders";
    static final String ACTION = "com.epochmaking.biologycentre.REMIND";

    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction() == null ? "" : i.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            rescheduleAll(c);
            return;
        }
        if (!a.startsWith(ACTION)) return;
        String id = i.getStringExtra("id");
        if (id == null) return;
        show(c, id, i.getStringExtra("title"), i.getStringExtra("body"));
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(id).apply();
    }

    static PendingIntent pending(Context c, String id, String title, String body) {
        Intent i = new Intent(c, ReminderReceiver.class);
        i.setAction(ACTION + "." + id);
        i.putExtra("id", id);
        i.putExtra("title", title);
        i.putExtra("body", body);
        return PendingIntent.getBroadcast(c, id.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context c, String id, long when, String title, String body) {
        try {
            JSONObject o = new JSONObject();
            o.put("w", when);
            o.put("t", title);
            o.put("b", body);
            c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(id, o.toString()).apply();
        } catch (Exception ignored) { }
        setAlarm(c, id, when, title, body);
    }

    static void setAlarm(Context c, String id, long when, String title, String body) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        /* সাধারণ (inexact) অ্যালার্ম: আলাদা অনুমতি লাগে না, কয়েক মিনিট এদিক-ওদিক হতে পারে */
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending(c, id, title, body));
    }

    static void cancel(Context c, String id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = pending(c, id, "", "");
        if (am != null) am.cancel(pi);
        pi.cancel();
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(id).apply();
    }

    static void rescheduleAll(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        SharedPreferences.Editor ed = p.edit();
        for (Map.Entry<String, ?> en : p.getAll().entrySet()) {
            try {
                JSONObject o = new JSONObject(String.valueOf(en.getValue()));
                long w = o.getLong("w");
                if (w > now) setAlarm(c, en.getKey(), w, o.optString("t"), o.optString("b"));
                else ed.remove(en.getKey());
            } catch (Exception e) {
                ed.remove(en.getKey());
            }
        }
        ed.apply();
    }

    static void show(Context c, String id, String title, String body) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "নোটিশের রিমাইন্ডার", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("পরীক্ষা, এক্সট্রা ক্লাস ইত্যাদির আগে মনে করানো");
            nm.createNotificationChannel(ch);
            b = new Notification.Builder(c, CHANNEL);
        } else {
            b = new Notification.Builder(c);
            b.setPriority(Notification.PRIORITY_HIGH);
            b.setDefaults(Notification.DEFAULT_ALL);
        }
        Intent open = new Intent(c, MainActivity.class);
        open.putExtra("go", "events");
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, id.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String t = title == null ? "জীববিজ্ঞান স্টাডি সেন্টার" : title;
        String bd = body == null ? "" : body;
        b.setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(t)
                .setContentText(bd)
                .setStyle(new Notification.BigTextStyle().bigText(bd))
                .setAutoCancel(true)
                .setContentIntent(pi);
        nm.notify(id.hashCode(), b.build());
    }
}
