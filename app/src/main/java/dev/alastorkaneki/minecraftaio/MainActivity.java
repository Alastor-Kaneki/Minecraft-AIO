package dev.alastorkaneki.minecraftaio;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {
    private static final int NAV_BROWSE = 1;
    private static final int NAV_SEARCH = 2;
    private static final int NAV_ACCOUNTS = 3;
    private static final int NAV_SETTINGS = 4;

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private FrameLayout content;
    private String pendingTitle;
    private String pendingLoginUrl;

    private final ActivityResultLauncher<Intent> googleAccountPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                String accountName = result.getData().getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                launchLogin(pendingTitle, pendingLoginUrl, "google", accountName);
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        if (Prefs.isAmoled(this)) setTheme(R.style.Theme_MinecraftAIO_Amoled);
        super.onCreate(savedInstanceState);
        enterImmersive();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        if (Prefs.isAmoled(this)) root.setBackgroundColor(Color.BLACK);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Minecraft AIO");
        toolbar.setSubtitle("Native Minecraft content hub");
        root.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        BottomNavigationView navigation = new BottomNavigationView(this);
        Menu menu = navigation.getMenu();
        menu.add(Menu.NONE, NAV_BROWSE, 0, "Browse").setIcon(android.R.drawable.ic_menu_compass);
        menu.add(Menu.NONE, NAV_SEARCH, 1, "Search").setIcon(android.R.drawable.ic_menu_search);
        menu.add(Menu.NONE, NAV_ACCOUNTS, 2, "Accounts").setIcon(android.R.drawable.ic_menu_myplaces);
        menu.add(Menu.NONE, NAV_SETTINGS, 3, "Settings").setIcon(android.R.drawable.ic_menu_preferences);
        navigation.setOnItemSelectedListener(item -> {
            if (item.getItemId() == NAV_BROWSE) showBrowse();
            else if (item.getItemId() == NAV_SEARCH) showSearch();
            else if (item.getItemId() == NAV_ACCOUNTS) showAccounts();
            else if (item.getItemId() == NAV_SETTINGS) showSettings();
            return true;
        });
        root.addView(navigation, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
        navigation.setSelectedItemId(NAV_BROWSE);
    }

    private void showBrowse() {
        LinearLayout body = verticalBody();
        body.addView(heading("Seven sources. One native app."));
        body.addView(paragraph("Browse Java and Bedrock content without turning the entire app into a website wrapper. Search results and source controls are rendered with native Android views."));
        for (Backends.Backend backend : Backends.all()) {
            MaterialCardView card = card();
            LinearLayout cardBody = verticalBodyNoScroll();
            TextView title = heading(backend.name());
            title.setTextSize(19);
            cardBody.addView(title);
            cardBody.addView(paragraph(backend.edition()));
            MaterialButton open = outlinedButton("Open source");
            open.setOnClickListener(v -> openUrl(backend.homeUrl()));
            cardBody.addView(open);
            card.addView(cardBody);
            body.addView(card);
        }
        setScrollContent(body);
    }

    private void showSearch() {
        LinearLayout body = verticalBodyNoScroll();
        body.setPadding(dp(12), dp(8), dp(12), 0);

        TextInputLayout inputLayout = new TextInputLayout(this);
        inputLayout.setHint("Search mods, packs, maps, skins and tweaks");
        TextInputEditText query = new TextInputEditText(this);
        query.setSingleLine(true);
        inputLayout.addView(query);
        body.addView(inputLayout, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ChipGroup editions = new ChipGroup(this);
        editions.setSingleSelection(true);
        Chip all = chip("All", true);
        Chip java = chip("Java", false);
        Chip bedrock = chip("Bedrock", false);
        editions.addView(all);
        editions.addView(java);
        editions.addView(bedrock);
        body.addView(editions);

        MaterialButton search = new MaterialButton(this);
        search.setText("Search all sources");
        body.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearProgressIndicator progress = new LinearProgressIndicator(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        body.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        RecyclerView results = new RecyclerView(this);
        results.setLayoutManager(new LinearLayoutManager(this));
        ContentAdapter adapter = new ContentAdapter();
        results.setAdapter(adapter);
        body.addView(results, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContent(body);

        search.setOnClickListener(v -> {
            String text = query.getText() == null ? "" : query.getText().toString().trim();
            if (text.isBlank()) text = "minecraft";
            String filter = bedrock.isChecked() ? "Bedrock" : java.isChecked() ? "Java" : "All";
            progress.setVisibility(View.VISIBLE);
            search.setEnabled(false);
            String finalText = text;
            executor.execute(() -> {
                List<ContentItem> merged = new ArrayList<>();
                for (Backends.Backend backend : Backends.all()) {
                    if (!matchesEdition(filter, backend.edition())) continue;
                    try {
                        merged.addAll(backend.search(this, finalText));
                    } catch (Exception error) {
                        merged.add(new ContentItem(backend.name(), "Source unavailable", error.getMessage() == null ? "Search failed." : error.getMessage(), backend.homeUrl(), "error"));
                    }
                }
                merged.sort(Comparator.comparing(item -> item.source));
                runOnUiThread(() -> {
                    adapter.replace(merged);
                    progress.setVisibility(View.GONE);
                    search.setEnabled(true);
                });
            });
        });
    }

    private void showAccounts() {
        LinearLayout body = verticalBody();
        body.addView(heading("Accounts"));
        body.addView(paragraph("Authentication WebViews are used only for sign-in. Cookies stay scoped to each provider's own domain. Google first uses Android's account chooser; Discord and every other method remain available inside the provider login page."));
        addAccountCard(body, "Planet Minecraft", "https://www.planetminecraft.com/account/sign_in/");
        addAccountCard(body, "MCPEDL", "https://mcpedl.com/login/");
        addAccountCard(body, "CurseForge", "https://www.curseforge.com/login");
        addAccountCard(body, "Modrinth", "https://modrinth.com/auth/sign-in");
        setScrollContent(body);
    }

    private void addAccountCard(LinearLayout body, String title, String loginUrl) {
        MaterialCardView card = card();
        LinearLayout cardBody = verticalBodyNoScroll();
        TextView heading = heading(title);
        heading.setTextSize(19);
        cardBody.addView(heading);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.START);

        MaterialButton login = outlinedButton("Web login");
        login.setOnClickListener(v -> launchLogin(title, loginUrl, null, null));
        buttons.addView(login);

        MaterialButton discord = outlinedButton("Discord / other");
        discord.setOnClickListener(v -> launchLogin(title, loginUrl, "discord", null));
        buttons.addView(discord);

        MaterialButton google = outlinedButton("Google");
        google.setOnClickListener(v -> chooseGoogleAccount(title, loginUrl));
        buttons.addView(google);

        cardBody.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(cardBody);
        body.addView(card);
    }

    private void showSettings() {
        LinearLayout body = verticalBody();
        body.addView(heading("Settings"));

        SwitchMaterial amoled = new SwitchMaterial(this);
        amoled.setText("AMOLED true-black mode");
        amoled.setChecked(Prefs.isAmoled(this));
        amoled.setPadding(0, dp(12), 0, dp(12));
        body.addView(amoled);

        TextInputLayout apiLayout = new TextInputLayout(this);
        apiLayout.setHint("CurseForge API key");
        apiLayout.setHelperText("Required by CurseForge's official API; stored only in this app's private preferences.");
        TextInputEditText apiKey = new TextInputEditText(this);
        apiKey.setSingleLine(true);
        apiKey.setText(Prefs.getCurseForgeKey(this));
        apiLayout.addView(apiKey);
        body.addView(apiLayout);

        MaterialButton save = new MaterialButton(this);
        save.setText("Save settings");
        save.setOnClickListener(v -> {
            Prefs.setCurseForgeKey(this, apiKey.getText() == null ? "" : apiKey.getText().toString());
            boolean changed = Prefs.isAmoled(this) != amoled.isChecked();
            Prefs.setAmoled(this, amoled.isChecked());
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
            if (changed) recreate();
        });
        body.addView(save);

        MaterialButton clear = outlinedButton("Clear all website sessions");
        clear.setOnClickListener(v -> CookieManager.getInstance().removeAllCookies(value -> Toast.makeText(this, "Website sessions cleared", Toast.LENGTH_SHORT).show()));
        body.addView(clear);

        body.addView(paragraph("Immersive mode is always enabled. Swipe from a screen edge to temporarily reveal Android's system bars."));
        setScrollContent(body);
    }

    private boolean matchesEdition(String filter, String edition) {
        if ("All".equals(filter)) return true;
        return edition.toLowerCase().contains(filter.toLowerCase());
    }

    private void chooseGoogleAccount(String title, String loginUrl) {
        pendingTitle = title;
        pendingLoginUrl = loginUrl;
        Intent picker = AccountManager.newChooseAccountIntent(
                null,
                null,
                new String[]{"com.google"},
                "Choose a Google account",
                null,
                null,
                null);
        googleAccountPicker.launch(picker);
    }

    private void launchLogin(String title, String loginUrl, String provider, String loginHint) {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra(LoginActivity.EXTRA_TITLE, title + " sign in");
        intent.putExtra(LoginActivity.EXTRA_URL, loginUrl);
        intent.putExtra(LoginActivity.EXTRA_PROVIDER, provider);
        intent.putExtra(LoginActivity.EXTRA_LOGIN_HINT, loginHint);
        startActivity(intent);
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception error) {
            Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show();
        }
    }

    private void setScrollContent(LinearLayout body) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(body, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContent(scroll);
    }

    private void setContent(View view) {
        content.removeAllViews();
        content.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private LinearLayout verticalBody() {
        LinearLayout body = verticalBodyNoScroll();
        body.setPadding(dp(16), dp(16), dp(16), dp(24));
        return body;
    }

    private LinearLayout verticalBodyNoScroll() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        if (Prefs.isAmoled(this)) body.setBackgroundColor(Color.BLACK);
        return body;
    }

    private TextView heading(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(24);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(4), 0, dp(6));
        return view;
    }

    private TextView paragraph(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setLineSpacing(0, 1.12f);
        view.setPadding(0, 0, 0, dp(12));
        return view;
    }

    private MaterialCardView card() {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, dp(6));
        card.setLayoutParams(params);
        card.setRadius(dp(18));
        card.setCardElevation(dp(1));
        card.setContentPadding(dp(16), dp(12), dp(16), dp(12));
        if (Prefs.isAmoled(this)) card.setCardBackgroundColor(0xFF090909);
        return card;
    }

    private MaterialButton outlinedButton(String text) {
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(text);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, dp(8), 0);
        button.setLayoutParams(params);
        return button;
    }

    private Chip chip(String text, boolean checked) {
        Chip chip = new Chip(this);
        chip.setText(text);
        chip.setCheckable(true);
        chip.setChecked(checked);
        return chip;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
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
