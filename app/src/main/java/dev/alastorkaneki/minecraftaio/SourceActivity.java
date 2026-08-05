package dev.alastorkaneki.minecraftaio;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SourceActivity extends AppCompatActivity {
    static final String EXTRA_SOURCE = "source";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Backends.Backend backend;
    private ContentAdapter adapter;
    private LinearProgressIndicator progress;
    private MaterialButton searchButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        if (Prefs.isAmoled(this)) setTheme(R.style.Theme_MinecraftAIO_Amoled);
        super.onCreate(savedInstanceState);
        enterImmersive();

        backend = Backends.find(getIntent().getStringExtra(EXTRA_SOURCE));
        if (backend == null) {
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        if (Prefs.isAmoled(this)) root.setBackgroundColor(Color.BLACK);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(backend.name());
        toolbar.setSubtitle(backend.edition() + " catalog");
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(dp(16), dp(12), dp(16), dp(8));

        ImageView logo = new ImageView(this);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        header.addView(logo, new LinearLayout.LayoutParams(dp(62), dp(62)));
        RemoteImage.load(logo, backend.logoUrl());

        LinearLayout intro = new LinearLayout(this);
        intro.setOrientation(LinearLayout.VERTICAL);
        intro.setPadding(dp(14), 0, 0, 0);
        TextView title = new TextView(this);
        title.setText(backend.name());
        title.setTextSize(22);
        title.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        intro.addView(title);
        TextView tagline = new TextView(this);
        tagline.setText(backend.tagline());
        tagline.setTextSize(14);
        tagline.setTypeface(Typeface.SANS_SERIF);
        tagline.setMaxLines(3);
        intro.addView(tagline);
        header.addView(intro, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(header);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setPadding(dp(12), 0, dp(12), dp(8));

        TextInputLayout inputLayout = new TextInputLayout(this);
        inputLayout.setHint("Search " + backend.name());
        TextInputEditText query = new TextInputEditText(this);
        query.setSingleLine(true);
        inputLayout.addView(query);
        searchRow.addView(inputLayout, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        searchButton = new MaterialButton(this);
        searchButton.setText("Search");
        searchButton.setAllCaps(false);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.setMargins(dp(8), 0, 0, 0);
        searchRow.addView(searchButton, searchParams);
        root.addView(searchRow);

        progress = new LinearProgressIndicator(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ContentAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));

        setContentView(root);

        searchButton.setOnClickListener(v -> {
            String text = query.getText() == null ? "" : query.getText().toString().trim();
            load(text);
        });
        load("");
    }

    private void load(String query) {
        progress.setVisibility(View.VISIBLE);
        searchButton.setEnabled(false);
        executor.execute(() -> {
            List<ContentItem> items = new ArrayList<>();
            try {
                items.addAll(query.isBlank() ? backend.featured(this) : backend.search(this, query));
            } catch (Exception error) {
                items.add(new ContentItem(
                        backend.name(),
                        "Could not load this catalog",
                        error.getMessage() == null ? "The source request failed." : error.getMessage(),
                        backend.homeUrl(),
                        "error",
                        backend.logoUrl(),
                        "",
                        ""
                ));
            }
            if (items.isEmpty()) {
                items.add(new ContentItem(
                        backend.name(),
                        query.isBlank() ? "No catalog items found" : "No matching results",
                        query.isBlank()
                                ? "This source did not return any usable items right now."
                                : "No items on the currently indexed catalog pages matched “" + query + "”.",
                        "",
                        "empty",
                        backend.logoUrl(),
                        "",
                        ""
                ));
            }
            runOnUiThread(() -> {
                adapter.replace(items);
                progress.setVisibility(View.GONE);
                searchButton.setEnabled(true);
            });
        });
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
