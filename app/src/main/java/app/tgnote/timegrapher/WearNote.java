package app.tgnote.timegrapher;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import java.util.Calendar;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Lunchtime question "which watch are you wearing today?". The page owns the watch data and hands over a plan
 * {on, title, body, done ('yyyy-MM-dd' of the last day already recorded), watches:[{id, name}]} (at most 3 buttons).
 * A button press is only noted here (the watch data lives in the page); the page takes the notes the next time it opens.
 */
final class WearNote {
    private static final String PREF = "wearnote";
    private static final String KEY_PLAN = "plan", KEY_PENDING = "pending", KEY_DONE = "done";
    static final String CHANNEL = "wear";
    static final String ACT_ALARM = "app.tgnote.timegrapher.WEAR_ALARM", ACT_PICK = "app.tgnote.timegrapher.WEAR_PICK";
    private static final int ALARM_ID = 9001, NOTE_ID = 9002, PICK_BASE = 9100;
    private static final int HOUR = 12, MINUTE = 30;   // "around noon to one": inexact alarm, may slip a little

    private WearNote() {}

    static String today() {
        Calendar c = Calendar.getInstance();
        return String.format(java.util.Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    static void save(Context c, String json) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_PLAN, json == null ? "" : json).apply();
        schedule(c);
    }

    /** Returns the notes taken from notification buttons ([{d, wid}]) and clears them. */
    static synchronized String take(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String s = sp.getString(KEY_PENDING, "[]");
        sp.edit().putString(KEY_PENDING, "[]").apply();
        return s;
    }

    private static boolean on(SharedPreferences sp) {
        try { return new JSONObject(sp.getString(KEY_PLAN, "{}")).optBoolean("on", false); } catch (Exception e) { return false; }
    }

    private static String doneDate(SharedPreferences sp) {
        String a = sp.getString(KEY_DONE, "");
        String b = "";
        try { b = new JSONObject(sp.getString(KEY_PLAN, "{}")).optString("done", ""); } catch (Exception e) { /* none */ }
        return a.compareTo(b) >= 0 ? a : b;
    }

    private static PendingIntent alarmIntent(Context c, int flags) {
        Intent in = new Intent(c, WearReceiver.class).setAction(ACT_ALARM);
        return PendingIntent.getBroadcast(c, ALARM_ID, in, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Sets (or clears) the one alarm for the next 12:30; a day already recorded skips to the next day. */
    static void schedule(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent old = alarmIntent(c, PendingIntent.FLAG_NO_CREATE);
        if (old != null) { am.cancel(old); old.cancel(); }
        if (!on(sp)) return;
        Calendar t = Calendar.getInstance();
        t.set(Calendar.HOUR_OF_DAY, HOUR); t.set(Calendar.MINUTE, MINUTE); t.set(Calendar.SECOND, 0); t.set(Calendar.MILLISECOND, 0);
        // past 12:30, or today already recorded: tomorrow
        if (t.getTimeInMillis() <= System.currentTimeMillis() || today().compareTo(doneDate(sp)) <= 0) t.add(Calendar.DAY_OF_MONTH, 1);
        am.setAndAllowWhileIdle(AlarmManager.RTC, t.getTimeInMillis(), alarmIntent(c, PendingIntent.FLAG_UPDATE_CURRENT));
    }

    /** The alarm went off: ask (unless today is already recorded), then set the next day's alarm. */
    static void onAlarm(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        try {
            if (on(sp) && !today().equals(doneDate(sp))) show(c, new JSONObject(sp.getString(KEY_PLAN, "{}")));
        } catch (Exception e) { /* bad plan: nothing is shown */ }
        schedule(c);
    }

    private static void show(Context c, JSONObject plan) throws Exception {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, c.getString(R.string.notif_channel_wear), NotificationManager.IMPORTANCE_DEFAULT));
        }
        Intent open = new Intent(c, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String body = plan.optString("body");
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_stat_watch)
                .setContentTitle(plan.optString("title"))
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(tap)
                .setAutoCancel(true);
        JSONArray ws = plan.optJSONArray("watches");
        String day = today();
        for (int i = 0; ws != null && i < ws.length() && i < 3; i++) {
            JSONObject w = ws.getJSONObject(i);
            Intent pick = new Intent(c, WearReceiver.class).setAction(ACT_PICK);
            pick.putExtra("wid", w.getString("id"));
            pick.putExtra("d", day);
            PendingIntent pi = PendingIntent.getBroadcast(c, PICK_BASE + i, pick, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.addAction(0, w.optString("name"), pi);
        }
        nm.notify(NOTE_ID, b.build());   // without the notification permission Android drops it quietly
    }

    /** A watch button was pressed: note it for the page and stop asking for today. */
    static synchronized void picked(Context c, String wid, String day) {
        if (wid == null || day == null) return;
        SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        try {
            JSONArray a = new JSONArray(sp.getString(KEY_PENDING, "[]"));
            JSONObject o = new JSONObject(); o.put("d", day); o.put("wid", wid); a.put(o);
            sp.edit().putString(KEY_PENDING, a.toString()).putString(KEY_DONE, day).apply();
        } catch (Exception e) { return; }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTE_ID);
        schedule(c);
    }
}
