package com.epochmaking.biologycentre;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * জীববিজ্ঞান স্টাডি সেন্টার: নিজস্ব Android অ্যাপ।
 * ভেতরে লাইভ ওয়েব অ্যাপ চলে (GitHub Pages), তাই অ্যাপ আপডেট হয় শুধু GitHub-এ push করলেই।
 * এই খোলসটা ওয়েব অ্যাপকে "AndroidApp" নামে কিছু সুবিধা দেয়:
 * অভিভাবকের WhatsApp চ্যাটে সরাসরি রশিদের ছবি, শেয়ার, আর ফাইল (ব্যাকআপ) সেভ।
 */
public class MainActivity extends Activity {

    static final String HOME = "https://epochmachin.github.io/biology-2026/";
    static final String HOST = "epochmachin.github.io";
    static final int FILE_REQ = 41;

    WebView web;
    ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setUserAgentString(s.getUserAgentString() + " BSCApp/1");

        web.addJavascriptInterface(new Bridge(), "AndroidApp");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return openOutside(r.getUrl());
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            // window.open(): wa.me / sms: ইত্যাদি বাইরের অ্যাপে খোলা
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                WebView temp = new WebView(MainActivity.this);
                temp.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                        Uri u = r.getUrl();
                        if (!openOutside(u)) web.loadUrl(u.toString());
                        v.destroy();
                        return true;
                    }
                });
                WebView.WebViewTransport t = (WebView.WebViewTransport) resultMsg.obj;
                t.setWebView(temp);
                resultMsg.sendToTarget();
                return true;
            }

            // ব্যাকআপ রিস্টোরের জন্য ফাইল বাছাই
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "ফাইল বাছুন"), FILE_REQ);
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        String go = getIntent() == null ? null : getIntent().getStringExtra("go");
        if (go != null) web.loadUrl(HOME + "?go=" + Uri.encode(go));
        else if (saved != null) web.restoreState(saved);
        else web.loadUrl(HOME);
    }

    /** নোটিফিকেশনে চাপলে: অ্যাপ খোলা থাকলেও নোটিশের পাতায় যাবে */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String go = intent == null ? null : intent.getStringExtra("go");
        if (go != null && web != null) web.loadUrl(HOME + "?go=" + Uri.encode(go));
    }

    /** নিজের সাইটের লিঙ্ক অ্যাপের ভেতরে, বাকি সব (WhatsApp, SMS, ফোন) বাইরের অ্যাপে */
    boolean openOutside(Uri u) {
        String scheme = u.getScheme() == null ? "" : u.getScheme();
        if ((scheme.equals("https") || scheme.equals("http")) && HOST.equals(u.getHost())) return false;
        try {
            Intent i;
            if (scheme.equals("intent")) i = Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME);
            else i = new Intent(Intent.ACTION_VIEW, u);
            startActivity(i);
        } catch (Exception e) {
            toast("খোলার মতো অ্যাপ পাওয়া যায়নি");
        }
        return true;
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == FILE_REQ && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            fileCallback = null;
            return;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    void toast(String m) {
        runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_SHORT).show());
    }

    boolean installed(String pkg) {
        try { getPackageManager().getPackageInfo(pkg, 0); return true; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }

    Uri cacheFile(String b64, String name) throws Exception {
        File dir = new File(getCacheDir(), "share");
        if (!dir.exists()) dir.mkdirs();
        File f = new File(dir, name.replaceAll("[^A-Za-z0-9._-]", "_"));
        try (FileOutputStream o = new FileOutputStream(f)) { o.write(Base64.decode(b64, Base64.DEFAULT)); }
        return FileProvider.getUriForFile(this, getPackageName() + ".files", f);
    }

    /** ওয়েব অ্যাপ থেকে window.AndroidApp হিসেবে ডাকা যায় */
    class Bridge {
        private final ArrayList<Uri> batch = new ArrayList<>();

        @JavascriptInterface
        public boolean isApp() { return true; }

        /** অনেকগুলো কার্ড একসাথে WhatsApp-এ (গ্রুপ বেছে নিলেই সব যাবে) */
        @JavascriptInterface
        public synchronized void batchStart() { batch.clear(); }

        @JavascriptInterface
        public synchronized String batchAdd(String b64, String name) {
            try { batch.add(cacheFile(b64, name)); return "ok"; }
            catch (Exception e) { return "error: " + e.getMessage(); }
        }

        @JavascriptInterface
        public synchronized String batchShare(String title) {
            try {
                if (batch.isEmpty()) return "error: কোনো কার্ড নেই";
                ArrayList<Uri> list = new ArrayList<>(batch);
                Intent i = new Intent(list.size() == 1 ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE);
                i.setType("image/png");
                if (list.size() == 1) i.putExtra(Intent.EXTRA_STREAM, list.get(0));
                else i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, list);
                ClipData cd = ClipData.newRawUri("cards", list.get(0));
                for (int k = 1; k < list.size(); k++) cd.addItem(new ClipData.Item(list.get(k)));
                i.setClipData(cd);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                String pkg = installed("com.whatsapp") ? "com.whatsapp"
                        : installed("com.whatsapp.w4b") ? "com.whatsapp.w4b" : null;
                if (pkg == null) {
                    runOnUiThread(() -> startActivity(Intent.createChooser(i, title)));
                    return "chooser";
                }
                i.setPackage(pkg);
                runOnUiThread(() -> startActivity(i));
                return "ok";
            } catch (Exception e) {
                return "error: " + e.getMessage();
            }
        }

        /** অভিভাবকের WhatsApp চ্যাট সরাসরি খুলবে, রশিদের ছবি সংযুক্ত অবস্থায়; শুধু "পাঠান" চাপলেই হবে */
        @JavascriptInterface
        public String sendImageToWhatsApp(String b64, String phone, String name) {
            try {
                Uri uri = cacheFile(b64, name);
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("image/png");
                i.putExtra(Intent.EXTRA_STREAM, uri);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                String pkg = installed("com.whatsapp") ? "com.whatsapp"
                        : installed("com.whatsapp.w4b") ? "com.whatsapp.w4b" : null;
                if (pkg == null) {
                    runOnUiThread(() -> startActivity(Intent.createChooser(i, "রশিদ পাঠান")));
                    return "chooser";
                }
                i.setPackage(pkg);
                String digits = phone == null ? "" : phone.replaceAll("\\D", "");
                if (!digits.isEmpty()) i.putExtra("jid", digits + "@s.whatsapp.net");
                runOnUiThread(() -> startActivity(i));
                return "ok";
            } catch (Exception e) {
                return "error: " + e.getMessage();
            }
        }

        /** নোটিশের রিমাইন্ডার: নির্দিষ্ট সময়ে ফোনে নোটিফিকেশন */
        @JavascriptInterface
        public void scheduleReminder(String id, double when, String title, String body) {
            ReminderReceiver.schedule(MainActivity.this, id, (long) when, title, body);
        }

        @JavascriptInterface
        public void cancelReminder(String id) {
            ReminderReceiver.cancel(MainActivity.this, id);
        }

        /** Android 13+ এ প্রথমবার নোটিফিকেশনের অনুমতি চাওয়া */
        @JavascriptInterface
        public void askNotify() {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 42));
            }
        }

        /** সাধারণ শেয়ার (যেকোনো অ্যাপে) */
        @JavascriptInterface
        public void shareImage(String b64, String name) {
            try {
                Uri uri = cacheFile(b64, name);
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("image/png");
                i.putExtra(Intent.EXTRA_STREAM, uri);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(() -> startActivity(Intent.createChooser(i, "শেয়ার করুন")));
            } catch (Exception e) {
                toast("শেয়ার করা গেল না");
            }
        }

        /** ফোনের Download ফোল্ডারে ফাইল সেভ (ব্যাকআপ, ছবি) */
        @JavascriptInterface
        public String saveFile(String b64, String name, String mime) {
            try {
                byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    cv.put(MediaStore.Downloads.MIME_TYPE, mime);
                    cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BiologyApp");
                    Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    try (OutputStream o = getContentResolver().openOutputStream(u)) { o.write(bytes); }
                    return "✅ সেভ হয়েছে: Download/BiologyApp/" + name;
                } else {
                    Uri uri = cacheFile(b64, name);
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType(mime);
                    i.putExtra(Intent.EXTRA_STREAM, uri);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    runOnUiThread(() -> startActivity(Intent.createChooser(i, "ফাইল রাখুন (Drive বাছুন)")));
                    return "";
                }
            } catch (Exception e) {
                return "সেভ করা গেল না: " + e.getMessage();
            }
        }
    }
}
