package app.tgnote.timegrapher;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Overhaul reminders. The page decides what to remind about and when (it owns the watch data) and hands over a list
 * [{id, at (ms since epoch), title, body}]. It is kept here so the alarms can be set again after a restart or an update.
 */
final class Reminders {
    private static final String PREF = "reminders";
    private static final String KEY_LIST = "list";
    private static final String KEY_IDS = "ids";

    private Reminders() {}

    /** Stores the list and (re)sets the alarms. An empty list switches every reminder off. */
    static void save(Context c, String json) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_LIST, json == null ? "[]" : json).apply();
        schedule(c);
    }

    static void schedule(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        // drop the alarms set last time
        String old = sp.getString(KEY_IDS, "");
        if (!old.isEmpty()) {
            for (String s : old.split(",")) {
                try { cancel(c, am, Integer.parseInt(s)); } catch (NumberFormatException e) { /* skip */ }
            }
        }
        StringBuilder ids = new StringBuilder();
        long now = System.currentTimeMillis();
        try {
            JSONArray a = new JSONArray(sp.getString(KEY_LIST, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                long at = o.getLong("at");
                if (at <= now) continue;   // already past: the page shows it in the watch library instead
                int id = o.getInt("id");
                Intent in = new Intent(c, ReminderReceiver.class);
                in.putExtra("id", id);
                in.putExtra("title", o.optString("title"));
                in.putExtra("body", o.optString("body"));
                PendingIntent pi = PendingIntent.getBroadcast(c, id, in, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                am.setAndAllowWhileIdle(AlarmManager.RTC, at, pi);   // inexact: no special permission, may slip a little
                if (ids.length() > 0) ids.append(',');
                ids.append(id);
            }
        } catch (Exception e) { /* bad list: nothing is scheduled */ }
        sp.edit().putString(KEY_IDS, ids.toString()).apply();
    }

    private static void cancel(Context c, AlarmManager am, int id) {
        Intent in = new Intent(c, ReminderReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, id, in, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) { am.cancel(pi); pi.cancel(); }
    }
}
