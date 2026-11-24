package app.tgnote.timegrapher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Alarms are lost on a restart and on an app update: set them again from the saved list. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        Reminders.schedule(c);
    }
}
