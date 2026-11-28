package app.tgnote.timegrapher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The lunchtime alarm and the watch buttons of its notification. */
public class WearReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        String a = in.getAction();
        if (WearNote.ACT_ALARM.equals(a)) WearNote.onAlarm(c);
        else if (WearNote.ACT_PICK.equals(a)) WearNote.picked(c, in.getStringExtra("wid"), in.getStringExtra("d"));
    }
}
