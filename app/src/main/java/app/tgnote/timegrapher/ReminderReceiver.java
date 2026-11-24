package app.tgnote.timegrapher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Shows one overhaul reminder when its alarm goes off; tapping it opens the app. */
public class ReminderReceiver extends BroadcastReceiver {
    static final String CHANNEL = "service";

    @Override
    public void onReceive(Context c, Intent in) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, c.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT));
        }
        Intent open = new Intent(c, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_stat_watch)
                .setContentTitle(in.getStringExtra("title"))
                .setContentText(in.getStringExtra("body"))
                .setStyle(new Notification.BigTextStyle().bigText(in.getStringExtra("body")))
                .setContentIntent(tap)
                .setAutoCancel(true);
        nm.notify(in.getIntExtra("id", 0), b.build());   // without the notification permission Android drops it quietly
    }
}
