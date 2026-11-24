package app.tgnote.timegrapher;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Raw PCM capture from the phone mic, delivered to the page in 100 ms chunks. */
public class NativeRec {
    private final MainActivity act;
    private final WebView web;
    private volatile boolean running;
    private AudioRecord rec;
    private Thread th;

    NativeRec(MainActivity act, WebView web) { this.act = act; this.web = web; }

    @JavascriptInterface
    public synchronized int start() {
        if (act.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            act.runOnUiThread(new Runnable() { public void run() {
                act.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 3);
            }});
            return -1;
        }
        stop();
        final int sr = 48000;
        int min = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int size = Math.max(min * 4, sr); // >= 0.5 s of headroom
        int[] sources = {9 /* UNPROCESSED */, 6 /* VOICE_RECOGNITION */, 1 /* MIC */};
        for (int src : sources) {
            try {
                AudioRecord r = new AudioRecord(src, sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size);
                if (r.getState() == AudioRecord.STATE_INITIALIZED) { preferBuiltInMic(r); rec = r; break; }
                r.release();
            } catch (Exception e) { /* try next source */ }
        }
        if (rec == null) return -2;
        try { rec.startRecording(); } catch (Exception e) { rec.release(); rec = null; return -2; }
        running = true;
        final AudioRecord r = rec;
        th = new Thread(new Runnable() { public void run() {
            short[] buf = new short[sr / 10];
            while (running) {
                int n = r.read(buf, 0, buf.length);
                if (n < 0) break;  // mic lost (e.g. a call took it): stop instead of spinning; the page notices the silence
                if (n == 0) continue;
                ByteBuffer bb = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN);
                bb.asShortBuffer().put(buf, 0, n);
                final String js = "window.__recData&&window.__recData('" + Base64.encodeToString(bb.array(), Base64.NO_WRAP) + "')";
                web.post(new Runnable() { public void run() { web.evaluateJavascript(js, null); } });
            }
        }});
        th.setPriority(Thread.MAX_PRIORITY);
        th.start();
        return sr;
    }

    /**
     * Bluetooth earbuds or a headset would otherwise take over the input; the watch sits on the phone's own mic.
     * Only steps in when such a headset is connected, so the system's usual (bottom) mic stays the default,
     * and then prefers the built-in mic reported as "bottom" on phones that list several.
     */
    private void preferBuiltInMic(AudioRecord r) {
        try {
            AudioManager am = (AudioManager) act.getSystemService(Context.AUDIO_SERVICE);
            AudioDeviceInfo builtin = null;
            boolean headset = false;
            for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
                int t = d.getType();
                if (t == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                    String a = d.getAddress();
                    if (builtin == null || (a != null && a.toLowerCase().contains("bottom"))) builtin = d;
                } else if (t != AudioDeviceInfo.TYPE_TELEPHONY && t != AudioDeviceInfo.TYPE_FM_TUNER
                        && t != AudioDeviceInfo.TYPE_TV_TUNER && t != 25 /* REMOTE_SUBMIX */
                        && t != 28 /* ECHO_REFERENCE */) {
                    headset = true;  // any external input (Bluetooth, wired, USB) could be picked over the phone mic
                }
            }
            if (headset && builtin != null) r.setPreferredDevice(builtin);
        } catch (Exception e) { /* keep the system default */ }
    }

    /** Page hands over a finished recording (WAV, base64); the user picks where it goes. */
    @JavascriptInterface
    public void saveWav(String b64, final String name) { saveFile(b64, name, "audio/wav"); }

    /** Any file from the page (base64), e.g. a backup (application/json). */
    @JavascriptInterface
    public void saveFile(String b64, final String name, final String mime) {
        final byte[] data;
        try { data = Base64.decode(b64, Base64.DEFAULT); } catch (Exception e) {
            act.runOnUiThread(new Runnable() { public void run() { act.fileSaved("fail"); } });
            return;
        }
        act.runOnUiThread(new Runnable() { public void run() { act.createFile(data, name, mime); } });
    }

    /** The measuring sheet opened or closed (Back closes it while it is up). */
    @JavascriptInterface
    public void sheet(final boolean open) {
        act.runOnUiThread(new Runnable() { public void run() { act.setSheetOpen(open); } });
    }

    @JavascriptInterface
    public synchronized void stop() {
        running = false;
        if (th != null) { try { th.join(500); } catch (InterruptedException e) {} th = null; }
        if (rec != null) {
            try { rec.stop(); } catch (Exception e) {}
            rec.release(); rec = null;
        }
    }
}
