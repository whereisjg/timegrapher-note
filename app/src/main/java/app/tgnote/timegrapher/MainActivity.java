package app.tgnote.timegrapher;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private WebView web;
    private FrameLayout root;
    private PermissionRequest pending;
    private ValueCallback<Uri[]> fileCb;
    private NativeRec rec;
    private Billing billing;
    private byte[] pendingFile;   // waiting for the user to pick where to save it
    private Uri cameraUri;        // where the camera is saving the photo being taken
    private boolean sheetOpen;    // the measuring sheet is up: Back closes it instead of leaving the app
    private Object backCb;        // OnBackInvokedCallback on Android 13+ (Object so older phones never load the class)

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new FrameLayout(this);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Android 15+ always draws edge to edge; keep the page clear of the system bars.
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(i.left, i.top, i.right, i.bottom);
                return WindowInsets.CONSUMED;
            });
        }
        applyColors();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (!HOST.equals(u.getHost())) return null;
                String path = u.getPath();
                if (path == null || path.equals("/")) path = "/index.html";
                try {
                    InputStream in = getAssets().open(path.substring(1));
                    String mime = path.endsWith(".html") ? "text/html" : path.endsWith(".png") ? "image/png" : "application/octet-stream";
                    return new WebResourceResponse(mime, "utf-8", in);
                } catch (Exception e) {
                    return null;
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return !HOST.equals(r.getUrl().getHost()); // the app never navigates elsewhere
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest req) {
                runOnUiThread(() -> {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        req.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                    } else {
                        pending = req;
                        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                // recordings for analysis; a backup file (json) shows every file, since phones label json differently
                boolean json = false, image = false;
                String[] acc = p.getAcceptTypes();
                if (acc != null) for (String t : acc) { if (t != null && t.contains("json")) json = true; if (t != null && t.startsWith("image")) image = true; }
                if (image && p.isCaptureEnabled() && Build.VERSION.SDK_INT >= 29) {   // 'take a photo': open the camera directly
                    try {
                        ContentValues cv = new ContentValues();
                        cv.put(MediaStore.Images.Media.DISPLAY_NAME, "watchnote_" + System.currentTimeMillis() + ".jpg");
                        cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                        cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WatchNote");
                        cameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                        Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        cam.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                        startActivityForResult(cam, 2);
                        return true;
                    } catch (Exception e) {
                        cameraUri = null;   // no camera app: fall back to picking from the gallery
                    }
                }
                if (image) i.setType("image/*");   // a watch photo
                else if (!json) i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/*", "video/*"});
                try {
                    startActivityForResult(json || image ? i : Intent.createChooser(i, getString(R.string.pick_audio)), 2);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });

        rec = new NativeRec(this, web);
        web.addJavascriptInterface(rec, "AndroidRec");
        billing = new Billing(this, web);
        web.addJavascriptInterface(billing, "AndroidBilling");

        setContentView(root);
        if (b != null) web.restoreState(b);
        else web.loadUrl("https://" + HOST + "/index.html");
    }

    private void applyColors() {
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int bg = night ? 0xFF151310 : 0xFFF1EADB;
        root.setBackgroundColor(bg);
        web.setBackgroundColor(bg);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(night ? 0 : mask, mask);
            }
        } else {
            getWindow().setStatusBarColor(bg);
            View d = getWindow().getDecorView();
            d.setSystemUiVisibility(night ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        applyColors();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code == 5) {
            web.evaluateJavascript("window.__notifPerm&&window.__notifPerm(" + (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) + ")", null);
            return;
        }
        if (code == 1 && pending != null) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED)
                pending.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            else pending.deny();
            pending = null;
        }
    }

    boolean notificationsAllowed() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        return nm == null || nm.areNotificationsEnabled();
    }

    void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 5);
        } else {
            web.evaluateJavascript("window.__notifPerm&&window.__notifPerm(" + notificationsAllowed() + ")", null);
        }
    }

    /** Lets the user choose where to save a file (no storage permission needed). */
    void createFile(byte[] bytes, String name, String mime) {
        pendingFile = bytes;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_TITLE, name);
        try {
            startActivityForResult(i, 4);
        } catch (Exception e) {
            pendingFile = null;
            fileSaved("fail");
        }
    }

    void fileSaved(String state) {
        web.evaluateJavascript("window.__fileSaved&&window.__fileSaved('" + state + "')", null);
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        if (code == 4) {
            String state = "cancel";
            if (result == RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                if (pendingFile == null) {
                    // the app was restarted while the picker was open: nothing to write, so remove the empty file
                    try { DocumentsContract.deleteDocument(getContentResolver(), uri); } catch (Exception e) { }
                    state = "fail";
                } else {
                    try (OutputStream o = getContentResolver().openOutputStream(uri)) {
                        o.write(pendingFile);
                        state = "ok";
                    } catch (Exception e) {
                        state = "fail";
                    }
                }
            }
            pendingFile = null;
            fileSaved(state);
            return;
        }
        if (code == 2 && fileCb != null) {
            Uri[] r = null;
            if (cameraUri != null) {   // the camera wrote straight to cameraUri
                if (result == RESULT_OK) r = new Uri[]{cameraUri};
                else { try { getContentResolver().delete(cameraUri, null, null); } catch (Exception e) { } }
                cameraUri = null;
            } else if (result == RESULT_OK && data != null && data.getData() != null) r = new Uri[]{data.getData()};
            fileCb.onReceiveValue(r);
            fileCb = null;
        }
    }

    /** Called by the page when the measuring sheet opens or closes. */
    void setSheetOpen(boolean open) {
        sheetOpen = open;
        if (Build.VERSION.SDK_INT < 33) return;
        OnBackInvokedDispatcher d = getOnBackInvokedDispatcher();
        if (open && backCb == null) {
            OnBackInvokedCallback cb = this::sheetBack;
            d.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
            backCb = cb;
        } else if (!open && backCb != null) {
            d.unregisterOnBackInvokedCallback((OnBackInvokedCallback) backCb);
            backCb = null;
        }
    }

    private void sheetBack() { web.evaluateJavascript("window.__back&&window.__back()", null); }

    @Override
    public void onBackPressed() {   // Android 12 and older
        if (sheetOpen) sheetBack(); else super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (billing != null) billing.refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (rec != null) rec.stop();
    }

    @Override
    protected void onDestroy() {
        if (billing != null) billing.close();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle o) {
        super.onSaveInstanceState(o);
        web.saveState(o);
    }
}
