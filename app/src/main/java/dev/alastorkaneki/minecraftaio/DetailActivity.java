package dev.alastorkaneki.minecraftaio;

import android.app.DownloadManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.net.URLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DetailActivity extends AppCompatActivity {
    private static final String EXTRA_SOURCE = "source";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_DESCRIPTION = "description";
    private static final String EXTRA_URL = "url";
    private static final String EXTRA_TYPE = "type";
    private static final String EXTRA_IMAGE = "image";
    private static final String EXTRA_ID = "id";
    private static final String EXTRA_DOWNLOAD = "download";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ContentItem item;
    private LinearLayout body;
    private ImageView hero;
    private TextView description;
    private LinearProgressIndicator progress;
    private LinearLayout downloads;
    private LinearLayout related;

    static void open(Context context, ContentItem item) {
        android.content.Intent intent = new android.content.Intent(context, DetailActivity.class);
        intent.putExtra(EXTRA_SOURCE, item.source);
        intent.putExtra(EXTRA_TITLE, item.title);
        intent.putExtra(EXTRA_DESCRIPTION, item.description);
        intent.putExtra(EXTRA_URL, item.url);
        intent.putExtra(EXTRA_TYPE, item.type);
        intent.putExtra(EXTRA_IMAGE, item.imageUrl);
        intent.putExtra(EXTRA_ID, item.backendId);
        intent.putExtra(EXTRA_DOWNLOAD, item.directDownloadUrl);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        if (Prefs.isAmoled(this)) setTheme(R.style.Theme_MinecraftAIO_Amoled);
        super.onCreate(savedInstanceState);
        enterImmersive();

        item = new ContentItem(
                getIntent().getStringExtra(EXTRA_SOURCE),
                getIntent().getStringExtra(EXTRA_TITLE),
                getIntent().getStringExtra(EXTRA_DESCRIPTION),
                getIntent().getStringExtra(EXTRA_URL),
                getIntent().getStringExtra(EXTRA_TYPE),
                getIntent().getStringExtra(EXTRA_IMAGE),
                getIntent().getStringExtra(EXTRA_ID),
                getIntent().getStringExtra(EXTRA_DOWNLOAD)
        );

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        if (Prefs.isAmoled(this)) root.setBackgroundColor(Color.BLACK);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(item.source);
        toolbar.setSubtitle("Native details");
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar);

        progress = new LinearProgressIndicator(this);
        progress.setIndeterminate(true);
        root.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(12), dp(16), dp(28));
        if (Prefs.isAmoled(this)) body.setBackgroundColor(Color.BLACK);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));
        setContentView(root);

        buildInitialUi();
        loadDetails();
    }

    private void buildInitialUi() {
        LinearLayout sourceHeader = new LinearLayout(this);
        sourceHeader.setOrientation(LinearLayout.HORIZONTAL);
        sourceHeader.setGravity(Gravity.CENTER_VERTICAL);

        ImageView sourceLogo = new ImageView(this);
        sourceLogo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        sourceHeader.addView(sourceLogo, new LinearLayout.LayoutParams(dp(46), dp(46)));
        Backends.Backend backend = Backends.find(item.source);
        RemoteImage.load(sourceLogo, backend == null ? item.imageUrl : backend.logoUrl());

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, 0, 0);
        TextView source = text(item.source, 15, Typeface.BOLD);
        labels.addView(source);
        Chip type = new Chip(this);
        type.setText(item.type);
        type.setCheckable(false);
        labels.addView(type, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        sourceHeader.addView(labels, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));
        body.addView(sourceHeader);

        hero = new ImageView(this);
        hero.setScaleType(ImageView.ScaleType.CENTER_CROP);
        hero.setAdjustViewBounds(true);
        hero.setVisibility(item.imageUrl.isBlank() ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(210));
        heroParams.setMargins(0, dp(14), 0, dp(14));
        body.addView(hero, heroParams);
        if (!item.imageUrl.isBlank()) RemoteImage.load(hero, item.imageUrl);

        TextView title = text(item.title, 27, Typeface.BOLD);
        title.setPadding(0, dp(4), 0, dp(8));
        body.addView(title);

        description = text(item.description, 15, Typeface.NORMAL);
        description.setLineSpacing(0, 1.16f);
        body.addView(description);

        body.addView(sectionTitle("Downloads"));
        downloads = new LinearLayout(this);
        downloads.setOrientation(LinearLayout.VERTICAL);
        body.addView(downloads);
        downloads.addView(note("Looking for direct files and versions…"));

        body.addView(sectionTitle("Related content"));
        related = new LinearLayout(this);
        related.setOrientation(LinearLayout.VERTICAL);
        body.addView(related);
        related.addView(note("Loading links from this source…"));
    }

    private void loadDetails() {
        executor.execute(() -> {
            try {
                NativeDetails.Result result = NativeDetails.load(this, item);
                runOnUiThread(() -> render(result));
            } catch (Exception error) {
                runOnUiThread(() -> renderError(error));
            }
        });
    }

    private void render(NativeDetails.Result result) {
        progress.setVisibility(View.GONE);
        if (result.description != null && !result.description.isBlank()) {
            description.setText(result.description);
        }
        if (result.imageUrl != null && !result.imageUrl.isBlank()) {
            hero.setVisibility(View.VISIBLE);
            RemoteImage.load(hero, result.imageUrl);
        }

        downloads.removeAllViews();
        if (result.downloads.isEmpty()) {
            downloads.addView(note(
                    "This page did not expose a direct file. Minecraft AIO will not throw you into a browser."));
        } else {
            for (NativeDetails.Download download : result.downloads) {
                MaterialButton button = new MaterialButton(this);
                button.setText(download.label);
                button.setAllCaps(false);
                button.setIconResource(android.R.drawable.stat_sys_download);
                button.setOnClickListener(v -> downloadFile(download));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                params.setMargins(0, 0, 0, dp(8));
                downloads.addView(button, params);
            }
        }

        related.removeAllViews();
        if (result.related.isEmpty()) {
            related.addView(note("No additional native links were found on this item."));
        } else {
            for (NativeDetails.Related link : result.related) {
                MaterialButton button = new MaterialButton(
                        this,
                        null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle);
                button.setText(link.title);
                button.setAllCaps(false);
                button.setOnClickListener(v -> open(
                        this,
                        new ContentItem(
                                item.source,
                                link.title,
                                "Related content from " + item.source,
                                link.url,
                                "related",
                                link.imageUrl,
                                "",
                                ""
                        )));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                params.setMargins(0, 0, 0, dp(8));
                related.addView(button, params);
            }
        }
    }

    private void renderError(Exception error) {
        progress.setVisibility(View.GONE);
        downloads.removeAllViews();
        related.removeAllViews();
        String message = error.getMessage() == null ? "The source request failed." : error.getMessage();
        downloads.addView(note(message));
        related.addView(note("Normal browsing remains inside Minecraft AIO; no browser fallback was opened."));
    }

    private void downloadFile(NativeDetails.Download download) {
        try {
            Uri uri = Uri.parse(download.url);
            String fileName = uri.getLastPathSegment();
            if (fileName == null || fileName.isBlank() || !fileName.contains(".")) {
                fileName = sanitize(download.label) + ".download";
            }

            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle(download.label);
            request.setDescription(item.title + " • " + item.source);
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            String mime = URLConnection.guessContentTypeFromName(fileName);
            if (mime != null) request.setMimeType(mime);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            String cookie = CookieManager.getInstance().getCookie(download.url);
            if (cookie != null && !cookie.isBlank()) request.addRequestHeader("Cookie", cookie);
            request.addRequestHeader("User-Agent", Backends.USER_AGENT);

            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            manager.enqueue(request);
            Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(
                    this,
                    error.getMessage() == null ? "Download failed" : error.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 20, Typeface.BOLD);
        view.setPadding(0, dp(22), 0, dp(10));
        return view;
    }

    private TextView note(String value) {
        TextView view = text(value, 14, Typeface.NORMAL);
        view.setPadding(dp(2), dp(4), dp(2), dp(10));
        return view;
    }

    private TextView text(String value, int size, int style) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextSize(size);
        view.setTypeface(Typeface.SANS_SERIF, style);
        return view;
    }

    private String sanitize(String value) {
        String clean = value == null ? "minecraft-aio-download" : value;
        clean = clean.replaceAll("[^A-Za-z0-9._-]+", "-");
        return clean.isBlank() ? "minecraft-aio-download" : clean;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
        executor.shutdownNow();
        super.onDestroy();
    }
}
