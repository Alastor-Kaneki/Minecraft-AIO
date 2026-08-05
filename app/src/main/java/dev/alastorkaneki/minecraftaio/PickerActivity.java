package dev.alastorkaneki.minecraftaio;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PickerActivity extends AppCompatActivity {
    private static final String EXTRA_SOURCE = "source";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_URL = "url";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private WebView webView;
    private LinearProgressIndicator progress;
    private ValueCallback<Uri[]> pendingUpload;
    private String source;
    private String title;

    private final ActivityResultLauncher<Intent> filePicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (pendingUpload == null) return;
                Uri[] selected = null;
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selected = WebChromeClient.FileChooserParams.parseResult(
                            result.getResultCode(), result.getData());
                }
                pendingUpload.onReceiveValue(selected);
                pendingUpload = null;
            });

    static boolean handles(ContentItem item) {
        if (item == null || item.url == null) return false;
        String source = item.source == null ? "" : item.source.toLowerCase(Locale.US);
        String url = item.url.toLowerCase(Locale.US);
        if (url.contains("vanillatweaks.net/picker/")) return true;
        if (source.equals("bedrock tweaks")) {
            return url.contains("bedrocktweaks.net/resource-packs")
                    || url.contains("bedrocktweaks.net/addons")
                    || url.contains("bedrocktweaks.net/crafting-tweaks");
        }
        if (source.equals("becomtweaks")) {
            return url.contains("becomtweaks.github.io/resource-packs")
                    || url.contains("becomtweaks.github.io/crafting-tweaks")
                    || url.contains("becomtweaks.github.io/behaviour-packs");
        }
        return false;
    }

    static void open(Context context, ContentItem item) {
        Intent intent = new Intent(context, PickerActivity.class);
        intent.putExtra(EXTRA_SOURCE, item.source);
        intent.putExtra(EXTRA_TITLE, item.title);
        intent.putExtra(EXTRA_URL, item.url);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        if (Prefs.isAmoled(this)) setTheme(R.style.Theme_MinecraftAIO_Amoled);
        super.onCreate(savedInstanceState);
        enterImmersive();

        source = safe(getIntent().getStringExtra(EXTRA_SOURCE), "Minecraft AIO");
        title = safe(getIntent().getStringExtra(EXTRA_TITLE), "Picker");
        String url = safe(getIntent().getStringExtra(EXTRA_URL), "");
        if (url.isBlank()) {
            finish();
            return;
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    5301);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        if (Prefs.isAmoled(this)) root.setBackgroundColor(Color.BLACK);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(title);
        toolbar.setSubtitle(source + " • interactive picker");
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert);
        toolbar.setNavigationOnClickListener(v -> navigateBack());
        root.addView(toolbar);

        progress = new LinearProgressIndicator(this);
        progress.setIndeterminate(true);
        root.addView(progress, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        webView = new WebView(this);
        configureWebView(webView);
        root.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));
        setContentView(root);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navigateBack();
            }
        });

        webView.loadUrl(url);
    }

    private void configureWebView(WebView view) {
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUserAgentString(settings.getUserAgentString() + " MinecraftAIO/0.1.3");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(view, true);

        view.addJavascriptInterface(new BlobBridge(), "MinecraftAioDownloads");
        view.setDownloadListener(new PickerDownloadListener());
        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams
            ) {
                if (pendingUpload != null) pendingUpload.onReceiveValue(null);
                pendingUpload = filePathCallback;
                try {
                    filePicker.launch(fileChooserParams.createIntent());
                    return true;
                } catch (Exception error) {
                    pendingUpload = null;
                    Toast.makeText(
                            PickerActivity.this,
                            "No file picker is available",
                            Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });
        view.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progress.setVisibility(android.view.View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(android.view.View.GONE);
                installBlobDownloadHook();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.US);
                if (scheme.equals("http") || scheme.equals("https")) {
                    view.loadUrl(uri.toString());
                    return true;
                }
                if (scheme.equals("mailto")) {
                    Toast.makeText(PickerActivity.this, "Mail links are not opened by the picker", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return true;
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                handler.cancel();
                Toast.makeText(
                        PickerActivity.this,
                        "The source's secure connection could not be verified",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void installBlobDownloadHook() {
        String script = "(function(){"
                + "if(window.__minecraftAioBlobHook)return;"
                + "window.__minecraftAioBlobHook=true;"
                + "window.__minecraftAioSaveBlob=function(url,name){"
                + "fetch(url).then(function(r){return r.blob();}).then(function(b){"
                + "var reader=new FileReader();"
                + "reader.onloadend=function(){MinecraftAioDownloads.save(reader.result,name||'minecraft-aio-pack',b.type||'application/octet-stream');};"
                + "reader.readAsDataURL(b);"
                + "}).catch(function(e){MinecraftAioDownloads.failed(String(e));});"
                + "};"
                + "var original=HTMLAnchorElement.prototype.click;"
                + "HTMLAnchorElement.prototype.click=function(){"
                + "var href=this.href||'';"
                + "if(href.indexOf('blob:')===0){window.__minecraftAioSaveBlob(href,this.download);return;}"
                + "return original.call(this);"
                + "};"
                + "document.addEventListener('click',function(e){"
                + "var a=e.target&&e.target.closest?e.target.closest('a'):null;"
                + "if(a&&a.href&&a.href.indexOf('blob:')===0){e.preventDefault();window.__minecraftAioSaveBlob(a.href,a.download);}" 
                + "},true);"
                + "})();";
        webView.evaluateJavascript(script, null);
    }

    private final class PickerDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(
                String url,
                String userAgent,
                String contentDisposition,
                String mimeType,
                long contentLength
        ) {
            if (url == null || url.isBlank()) return;
            if (url.startsWith("blob:")) {
                String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                webView.evaluateJavascript(
                        "window.__minecraftAioSaveBlob(" + quote(url) + "," + quote(fileName) + ");",
                        null);
                return;
            }
            if (url.startsWith("data:")) {
                executor.execute(() -> saveDataUrl(url, "minecraft-aio-pack", mimeType));
                return;
            }
            enqueueDownload(url, userAgent, contentDisposition, mimeType);
        }
    }

    private final class BlobBridge {
        @JavascriptInterface
        public void save(String dataUrl, String fileName, String mimeType) {
            executor.execute(() -> saveDataUrl(dataUrl, fileName, mimeType));
        }

        @JavascriptInterface
        public void failed(String message) {
            runOnUiThread(() -> Toast.makeText(
                    PickerActivity.this,
                    "The generated pack could not be captured",
                    Toast.LENGTH_LONG).show());
        }
    }

    private void enqueueDownload(
            String url,
            String userAgent,
            String contentDisposition,
            String mimeType
    ) {
        try {
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(fileName);
            request.setDescription(title + " • " + source);
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            if (mimeType != null && !mimeType.isBlank()) request.setMimeType(mimeType);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null && !cookie.isBlank()) request.addRequestHeader("Cookie", cookie);
            request.addRequestHeader("User-Agent", safe(userAgent, Backends.USER_AGENT));
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            manager.enqueue(request);
            Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(this, "Download could not be started", Toast.LENGTH_LONG).show();
        }
    }

    private void saveDataUrl(String dataUrl, String requestedName, String suppliedMime) {
        try {
            int comma = dataUrl.indexOf(',');
            if (comma < 0) throw new IllegalArgumentException("Invalid generated file");
            String header = dataUrl.substring(0, comma);
            String encoded = dataUrl.substring(comma + 1);
            String mime = suppliedMime;
            if ((mime == null || mime.isBlank()) && header.startsWith("data:")) {
                int semicolon = header.indexOf(';');
                mime = semicolon > 5 ? header.substring(5, semicolon) : "application/octet-stream";
            }
            if (mime == null || mime.isBlank()) mime = "application/octet-stream";
            byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
            String fileName = ensureFileName(requestedName, mime);
            writeDownload(fileName, mime, bytes);
            String finalFileName = fileName;
            runOnUiThread(() -> Toast.makeText(
                    this,
                    finalFileName + " saved to Downloads",
                    Toast.LENGTH_LONG).show());
        } catch (Exception error) {
            runOnUiThread(() -> Toast.makeText(
                    this,
                    "Generated pack could not be saved",
                    Toast.LENGTH_LONG).show());
        }
    }

    private void writeDownload(String fileName, String mime, byte[] bytes) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + File.separator + "Minecraft AIO");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri output = getContentResolver().insert(collection, values);
            if (output == null) throw new IllegalStateException("Could not create download");
            try (OutputStream stream = getContentResolver().openOutputStream(output)) {
                if (stream == null) throw new IllegalStateException("Could not open download");
                stream.write(bytes);
            }
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            getContentResolver().update(output, values, null, null);
            return;
        }

        File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create Downloads folder");
        }
        File output = uniqueFile(directory, fileName);
        try (FileOutputStream stream = new FileOutputStream(output)) {
            stream.write(bytes);
        }
    }

    private File uniqueFile(File directory, String requested) {
        File file = new File(directory, requested);
        if (!file.exists()) return file;
        int dot = requested.lastIndexOf('.');
        String base = dot > 0 ? requested.substring(0, dot) : requested;
        String extension = dot > 0 ? requested.substring(dot) : "";
        for (int i = 2; i < 10_000; i++) {
            file = new File(directory, base + " (" + i + ")" + extension);
            if (!file.exists()) return file;
        }
        return new File(directory, System.currentTimeMillis() + "-" + requested);
    }

    private String ensureFileName(String requested, String mime) {
        String fileName = safe(requested, "minecraft-aio-pack");
        fileName = fileName.replaceAll("[\\\\/:*?\"<>|]+", "-").trim();
        if (fileName.isBlank()) fileName = "minecraft-aio-pack";
        if (!fileName.contains(".")) {
            String lowerSource = source.toLowerCase(Locale.US);
            if (lowerSource.contains("vanilla tweaks")) fileName += ".zip";
            else if (mime.toLowerCase(Locale.US).contains("zip")) fileName += ".zip";
            else fileName += ".mcpack";
        }
        return fileName;
    }

    private void navigateBack() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else finish();
    }

    private String quote(String value) {
        if (value == null) return "''";
        return "'" + value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "") + "'";
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersive();
    }

    @Override
    protected void onDestroy() {
        if (pendingUpload != null) {
            pendingUpload.onReceiveValue(null);
            pendingUpload = null;
        }
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        executor.shutdownNow();
        super.onDestroy();
    }
}
