package com.byankarail.trafficcity2;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URLConnection;

public class MainActivity extends Activity {
    private static final String BASE_URL = "https://byanka-rail.github.io/traffic-city-2/";
    private static final String ALLOWED_HOST = "byanka-rail.github.io";
    private static final String ALLOWED_PREFIX = "/traffic-city-2/";
    private static final int FILE_CHOOSER_REQUEST = 4012;

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private boolean showingOfflinePage = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.rgb(23, 19, 15));
        getWindow().setNavigationBarColor(Color.rgb(23, 19, 15));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(23, 19, 15));
        setContentView(webView);
        configureWebView();

        if (savedInstanceState != null && webView.restoreState(savedInstanceState) != null) {
            return;
        }
        loadLatest();
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " TRAFFIC_CITY_2_ANDROID/1.0");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            s.setSafeBrowsingEnabled(true);
        }

        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidBridge");
        webView.setWebViewClient(new TrafficClient());
        webView.setWebChromeClient(new TrafficChromeClient());
        webView.setDownloadListener(new TrafficDownloadListener());
    }

    private void loadLatest() {
        showingOfflinePage = false;
        // Cache-bust the document only. Query parameters do not change the HTTPS origin,
        // so localStorage saves remain on the same GitHub Pages origin.
        webView.loadUrl(BASE_URL + "?app=android&t=" + System.currentTimeMillis());
    }

    private boolean isAllowedAppUri(Uri uri) {
        if (uri == null) return false;
        if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
        if (!ALLOWED_HOST.equalsIgnoreCase(uri.getHost())) return false;
        String path = uri.getPath();
        return path != null && (path.equals("/traffic-city-2") || path.startsWith(ALLOWED_PREFIX));
    }

    private void openExternally(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "이 링크를 열 앱이 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showOffline() {
        if (showingOfflinePage) return;
        showingOfflinePage = true;
        webView.loadUrl("file:///android_asset/offline.html");
    }

    private boolean hasNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network n = cm.getActiveNetwork();
        if (n == null) return false;
        NetworkCapabilities cap = cm.getNetworkCapabilities(n);
        return cap != null && cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private final class TrafficClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (isAllowedAppUri(uri) || "file".equalsIgnoreCase(uri.getScheme())) return false;
            openExternally(uri);
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            if (url != null && url.startsWith(BASE_URL)) {
                showingOfflinePage = false;
                injectBlobDownloadBridge();
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request.isForMainFrame() && !hasNetwork()) showOffline();
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
            // Never bypass certificate failures.
            handler.cancel();
            if (!showingOfflinePage) showOffline();
        }
    }

    private final class TrafficChromeClient extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                         FileChooserParams fileChooserParams) {
            if (MainActivity.this.filePathCallback != null) {
                MainActivity.this.filePathCallback.onReceiveValue(null);
            }
            MainActivity.this.filePathCallback = filePathCallback;
            Intent intent;
            try {
                intent = fileChooserParams.createIntent();
            } catch (Exception e) {
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("application/json");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
            }
            try {
                startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                return true;
            } catch (ActivityNotFoundException e) {
                MainActivity.this.filePathCallback = null;
                Toast.makeText(MainActivity.this, "파일 선택기를 열 수 없습니다.", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
    }

    private final class TrafficDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                    String mimetype, long contentLength) {
            if (url == null) return;
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                // blob/data downloads are handled by the injected JS bridge.
                return;
            }
            Uri uri = Uri.parse(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                Toast.makeText(MainActivity.this, "지원하지 않는 다운로드 주소입니다.", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                String filename = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype);
                DownloadManager.Request req = new DownloadManager.Request(uri);
                req.setMimeType(mimetype);
                req.addRequestHeader("User-Agent", userAgent);
                req.setTitle(filename);
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(req);
                Toast.makeText(MainActivity.this, "다운로드를 시작했습니다.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                openExternally(uri);
            }
        }
    }

    private void injectBlobDownloadBridge() {
        String js = "(function(){" +
                "if(window.__tc2AndroidBlobBridge)return;window.__tc2AndroidBlobBridge=1;" +
                "document.addEventListener('click',async function(ev){" +
                "var a=ev.target&&ev.target.closest?ev.target.closest('a[download]'):null;" +
                "if(!a||!a.href||!a.href.startsWith('blob:'))return;" +
                "ev.preventDefault();try{" +
                "var b=await fetch(a.href).then(r=>r.blob());var fr=new FileReader();" +
                "fr.onloadend=function(){var s=String(fr.result||'');var p=s.indexOf(',');" +
                "AndroidBridge.saveBase64File(a.download||'traffic-city-2-save.json',b.type||'application/octet-stream',p>=0?s.slice(p+1):s);};" +
                "fr.readAsDataURL(b);" +
                "}catch(e){console.error('Android blob save failed',e);}" +
                "},true);})();";
        webView.evaluateJavascript(js, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || filePathCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                result = new Uri[n];
                for (int i = 0; i < n; i++) result[i] = data.getClipData().getItemAt(i).getUri();
            } else if (data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
        }
        filePathCallback.onReceiveValue(result);
        filePathCallback = null;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack() && !showingOfflinePage) {
            webView.goBack();
        } else if (showingOfflinePage) {
            loadLatest();
        } else {
            super.onBackPressed();
        }
    }

    private static final class AndroidBridge {
        private final Activity activity;

        AndroidBridge(Activity activity) {
            this.activity = activity;
        }

        @JavascriptInterface
        public void saveBase64File(String requestedName, String mime, String base64) {
            activity.runOnUiThread(() -> {
                try {
                    String name = sanitizeFilename(requestedName);
                    byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                    Uri saved = saveToDownloads(activity, name, mime, bytes);
                    if (saved != null) {
                        Toast.makeText(activity, "저장 완료: " + name, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(activity, "파일 저장에 실패했습니다.", Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(activity, "파일 저장에 실패했습니다.", Toast.LENGTH_LONG).show();
                }
            });
        }

        private static String sanitizeFilename(String name) {
            if (name == null || name.trim().isEmpty()) return "traffic-city-2-save.json";
            String clean = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
            return clean.isEmpty() ? "traffic-city-2-save.json" : clean;
        }

        private static Uri saveToDownloads(Context ctx, String name, String mime, byte[] bytes) throws Exception {
            if (mime == null || mime.isEmpty()) {
                mime = URLConnection.guessContentTypeFromName(name);
                if (mime == null) mime = "application/octet-stream";
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, mime);
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                v.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) return null;
                try (OutputStream os = ctx.getContentResolver().openOutputStream(uri)) {
                    if (os == null) return null;
                    os.write(bytes);
                }
                v.clear();
                v.put(MediaStore.Downloads.IS_PENDING, 0);
                ctx.getContentResolver().update(uri, v, null, null);
                return uri;
            } else {
                File dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir == null) return null;
                if (!dir.exists()) dir.mkdirs();
                File f = uniqueFile(dir, name);
                try (FileOutputStream fos = new FileOutputStream(f)) {
                    fos.write(bytes);
                }
                return Uri.fromFile(f);
            }
        }

        private static File uniqueFile(File dir, String name) {
            File f = new File(dir, name);
            if (!f.exists()) return f;
            int dot = name.lastIndexOf('.');
            String stem = dot > 0 ? name.substring(0, dot) : name;
            String ext = dot > 0 ? name.substring(dot) : "";
            int i = 2;
            while (f.exists()) f = new File(dir, stem + " (" + (i++) + ")" + ext);
            return f;
        }
    }
}
