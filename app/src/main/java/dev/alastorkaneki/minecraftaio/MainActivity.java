package dev.alastorkaneki.minecraftaio;

import android.accounts.AccountManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;
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
    private MaterialToolbar toolbar;
    private String pendingGoogleTitle;
    private String pendingGoogleUrl;

    private final ActivityResultLauncher<Intent> googleAccountPicker =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                        String accountName = result.getData().getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                        openGoogleAuth(pendingGoogleTitle, pendingGoogleUrl, accountName);
                    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        if (Prefs.isAmoled(this)) setTheme(R.style.Theme_MinecraftAIO_Amoled);
        super.onCreate(savedInstanceState);
        enterImmersive();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        if (Prefs.isAmoled(this)) root.setBackgroundColor(Color.BLACK);

        toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Minecraft AIO");
        toolbar.setSubtitle("Native Minecraft content hub");
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));

        BottomNavigationView navigation = new BottomNavigationView(this);
        Menu menu = navigation.getMenu();
        menu.add(Menu.NONE, NAV_BROWSE, 0, "Browse")
                .setIcon(android.R.drawable.ic_menu_compass);
        menu.add(Menu.NONE, NAV_SEARCH, 1, "Search")
                .setIcon(android.R.drawable.ic_menu_search);
        menu.add(Menu.NONE, NAV_ACCOUNTS, 2, "Accounts")
                .setIcon(android.R.drawable.ic_menu_myplaces);
        menu.add(Menu.NONE, NAV_SETTINGS, 3, "Settings")
                .setIcon(android.R.drawable.ic_menu_preferences);
        navigation.setOnItemSelectedListener(item -> {
            if (item.getItemId() == NAV_BROWSE) showBrowse();
            else if (item.getItemId() == NAV_SEARCH) showSearch();
            else if (item.getItemId() == NAV_ACCOUNTS) showAccounts();
            else if (item.getItemId() == NAV_SETTINGS) showSettings();
            return true;
        });
        root.addView(navigation, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        navigation.setSelectedItemId(NAV_BROWSE);
    }

    private void showBrowse() {
        setToolbar("Minecraft AIO", "Browse every source without leaving the app");
        LinearLayout body = verticalBody();
        body.addView(heading("Choose a source"));
        body.addView(paragraph(
                "Catalogs, search results, item details, screenshots, versions and downloads are rendered natively. WebView is reserved for sign-in only."));

        for (Backends.Backend backend : Backends.all()) {
            body.addView(sourceCard(backend));
        }
        setScrollContent(body);
    }

    private MaterialCardView sourceCard(Backends.Backend backend) {
        MaterialCardView card = card();
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout cardBody = verticalBodyNoScroll();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        header.addView(logo, new LinearLayout.LayoutParams(dp(58), dp(58)));
        RemoteImage.load(logo, backend.logoUrl());

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(14), 0, 0, 0);

        TextView title = heading(backend.name());
        title.setTextSize(20);
        title.setPadding(0, 0, 0, dp(2));
        labels.addView(title);

        Chip edition = new Chip(this);
        edition.setText(backend.edition());
        edition.setCheckable(false);
        labels.addView(edition, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        header.addView(labels, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));
        cardBody.addView(header);
        cardBody.addView(paragraph(backend.tagline()));

        MaterialButton browse = new MaterialButton(this);
        browse.setText("Browse " + backend.name());
        browse.setAllCaps(false);
        browse.setIconResource(android.R.drawable.ic_menu_view);
        browse.setOnClickListener(v -> openSource(backend));
        cardBody.addView(browse, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        card.setOnClickListener(v -> openSource(backend));
        card.addView(cardBody);
        return card;
    }

    private void openSource(Backends.Backend backend) {
        Intent intent = new Intent(this, SourceActivity.class);
        intent.putExtra(SourceActivity.EXTRA_SOURCE, backend.name());
        startActivity(intent);
    }

    private void showSearch() {
        setToolbar("Search", "Search all seven native catalogs");
        LinearLayout body = verticalBodyNoScroll();
        body.setPadding(dp(12), dp(10), dp(12), 0);

        TextInputLayout inputLayout = new TextInputLayout(this);
        inputLayout.setHint("Search mods, packs, maps, skins and tweaks");
        TextInputEditText query = new TextInputEditText(this);
        query.setSingleLine(true);
        inputLayout.addView(query);
        body.addView(inputLayout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

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
        search.setAllCaps(false);
        search.setIconResource(android.R.drawable.ic_menu_search);
        body.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearProgressIndicator progress = new LinearProgressIndicator(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        body.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        RecyclerView results = new RecyclerView(this);
        results.setLayoutManager(new LinearLayoutManager(this));
        ContentAdapter adapter = new ContentAdapter();
        results.setAdapter(adapter);
        body.addView(results, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));
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
                        merged.add(new ContentItem(
                                backend.name(),
                                "Source unavailable",
                                error.getMessage() == null
                                        ? "Search failed."
                                        : error.getMessage(),
                                backend.homeUrl(),
                                "error",
                                backend.logoUrl(),
                                "",
                                ""
                        ));
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

    private static final String CURSEFORGE_SSO =
            "https://curseforge.overwolf.com/auth/v3/login-start.html?key=5604bf10017987e856b58c35497a816d0dda4233e03790f11cae00cef5367842";
    private static final String MODRINTH_RETURN =
            "https%3A%2F%2Fmodrinth.com%2Fauth%2Fsign-in%3Fredirect%3D";

    private void showAccounts() {
        setToolbar("Accounts", "Real provider routes, without fake login buttons");
        LinearLayout body = verticalBody();
        body.addView(heading("Connected services"));
        body.addView(paragraph(
                "Google authentication opens in a secure Chrome Custom Tab because Google blocks embedded WebView sign-in. Discord and standard site sign-in stay in isolated authentication WebViews."));

        addAccountCard(body, "Planet Minecraft",
                "Planet Minecraft currently offers email and password sign-in.",
                Backends.favicon("planetminecraft.com"),
                "https://www.planetminecraft.com/account/sign_in/",
                false, false, false, false, false, false, false);
        addAccountCard(body, "MCPEDL",
                "MCPEDL currently offers email or username and password sign-in.",
                Backends.favicon("mcpedl.com"),
                "https://mcpedl.com/login/",
                false, false, false, false, false, false, false);
        addAccountCard(body, "CurseForge",
                "CurseForge SSO supports Google, Discord, GitHub and Twitch.",
                Backends.favicon("curseforge.com"),
                CURSEFORGE_SSO,
                true, true, true, false, false, false, true);
        addAccountCard(body, "Modrinth",
                "Modrinth supports Google, Discord, GitHub, Microsoft, Steam and GitLab.",
                Backends.favicon("modrinth.com"),
                "https://modrinth.com/auth/sign-in",
                true, true, true, true, true, true, false);

        setScrollContent(body);
    }

    private void addAccountCard(
            LinearLayout body,
            String title,
            String subtitle,
            String logoUrl,
            String loginUrl,
            boolean google,
            boolean discord,
            boolean github,
            boolean microsoft,
            boolean steam,
            boolean gitlab,
            boolean twitch
    ) {
        MaterialCardView card = card();
        LinearLayout cardBody = verticalBodyNoScroll();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        header.addView(logo, new LinearLayout.LayoutParams(dp(58), dp(58)));
        RemoteImage.load(logo, logoUrl);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(14), 0, 0, 0);
        TextView titleView = heading(title);
        titleView.setTextSize(20);
        titleView.setPadding(0, 0, 0, dp(2));
        labels.addView(titleView);
        TextView subtitleView = paragraph(subtitle);
        subtitleView.setPadding(0, 0, 0, dp(4));
        labels.addView(subtitleView);
        header.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        cardBody.addView(header);

        if (google) cardBody.addView(providerButton(
                "Continue with Google",
                "Secure account chooser outside WebView",
                Backends.favicon("accounts.google.com"),
                () -> chooseGoogleAccount(title, providerUrl(title, "google", loginUrl))));
        if (discord) cardBody.addView(providerButton(
                "Continue with Discord",
                "Open the real Discord provider route",
                Backends.favicon("discord.com"),
                () -> launchProviderLogin(title, loginUrl, "discord")));
        if (github) cardBody.addView(providerButton(
                "Continue with GitHub", "Open the real GitHub provider route",
                Backends.favicon("github.com"),
                () -> launchProviderLogin(title, loginUrl, "github")));
        if (microsoft) cardBody.addView(providerButton(
                "Continue with Microsoft", "Open the Microsoft provider route",
                Backends.favicon("microsoft.com"),
                () -> launchProviderLogin(title, loginUrl, "microsoft")));
        if (steam) cardBody.addView(providerButton(
                "Continue with Steam", "Open the Steam provider route",
                Backends.favicon("steampowered.com"),
                () -> launchProviderLogin(title, loginUrl, "steam")));
        if (gitlab) cardBody.addView(providerButton(
                "Continue with GitLab", "Open the GitLab provider route",
                Backends.favicon("gitlab.com"),
                () -> launchProviderLogin(title, loginUrl, "gitlab")));
        if (twitch) cardBody.addView(providerButton(
                "Continue with Twitch", "Open the Twitch provider route",
                Backends.favicon("twitch.tv"),
                () -> launchProviderLogin(title, loginUrl, "twitch")));

        cardBody.addView(providerButton(
                google || discord || github || microsoft || steam || gitlab || twitch
                        ? "All sign-in methods" : "Sign in",
                "Use every authentication method offered by this service",
                logoUrl,
                () -> launchLogin(title, loginUrl, null, null)));

        card.addView(cardBody);
        body.addView(card);
    }

    private String providerUrl(String service, String provider, String fallback) {
        if ("Modrinth".equalsIgnoreCase(service)) {
            return "https://api.modrinth.com/v2/auth/init?provider=" + provider
                    + "&url=" + MODRINTH_RETURN;
        }
        return fallback;
    }

    private void chooseGoogleAccount(String title, String url) {
        pendingGoogleTitle = title;
        pendingGoogleUrl = url;
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

    private void openGoogleAuth(String title, String url, String accountName) {
        android.net.Uri target = android.net.Uri.parse(url);
        if (accountName != null && !accountName.isBlank()) {
            target = target.buildUpon().appendQueryParameter("login_hint", accountName).build();
        }
        CustomTabColorSchemeParams dark = new CustomTabColorSchemeParams.Builder()
                .setToolbarColor(Color.BLACK)
                .setNavigationBarColor(Color.BLACK)
                .build();
        CustomTabsIntent intent = new CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setUrlBarHidingEnabled(true)
                .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
                .setDefaultColorSchemeParams(dark)
                .setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_DARK, dark)
                .build();
        try {
            intent.launchUrl(this, target);
        } catch (Exception error) {
            Toast.makeText(this, "No secure browser is available for Google sign-in", Toast.LENGTH_LONG).show();
        }
    }

    private void launchProviderLogin(String title, String loginUrl, String provider) {
        String target = providerUrl(title, provider, loginUrl);
        // Modrinth exposes direct provider endpoints. CurseForge exposes one SSO
        // entry page, so its authentication-only WebView selects the requested provider.
        String webViewSelector = "Modrinth".equalsIgnoreCase(title) ? null : provider;
        launchLogin(title, target, webViewSelector, null);
    }

    private MaterialCardView providerButton(
            String title,
            String subtitle,
            String iconUrl,
            Runnable action
    ) {
        MaterialCardView button = new MaterialCardView(this);
        button.setRadius(dp(16));
        button.setStrokeWidth(dp(1));
        button.setClickable(true);
        button.setFocusable(true);
        button.setCardElevation(0);
        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        outer.setMargins(0, dp(10), 0, 0);
        button.setLayoutParams(outer);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        row.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));
        RemoteImage.load(icon, iconUrl);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(14), 0, dp(8), 0);
        labels.addView(text(title, 16, Typeface.BOLD));
        TextView subtitleView = text(subtitle, 13, Typeface.NORMAL);
        subtitleView.setMaxLines(2);
        labels.addView(subtitleView);
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(text("›", 30, Typeface.NORMAL));
        button.addView(row);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private void showSettings() {
        setToolbar("Settings", "Appearance and source configuration");
        LinearLayout body = verticalBody();
        body.addView(heading("Appearance"));

        SwitchMaterial amoled = new SwitchMaterial(this);
        amoled.setText("AMOLED true-black mode");
        amoled.setChecked(Prefs.isAmoled(this));
        amoled.setPadding(0, dp(12), 0, dp(12));
        body.addView(amoled);

        body.addView(heading("CurseForge"));
        TextInputLayout apiLayout = new TextInputLayout(this);
        apiLayout.setHint("CurseForge API key");
        apiLayout.setHelperText(
                "Required by CurseForge's official catalog API. Stored only in Minecraft AIO.");
        TextInputEditText apiKey = new TextInputEditText(this);
        apiKey.setSingleLine(true);
        apiKey.setText(Prefs.getCurseForgeKey(this));
        apiLayout.addView(apiKey);
        body.addView(apiLayout);

        MaterialButton save = new MaterialButton(this);
        save.setText("Save settings");
        save.setAllCaps(false);
        save.setOnClickListener(v -> {
            Prefs.setCurseForgeKey(
                    this,
                    apiKey.getText() == null ? "" : apiKey.getText().toString());
            boolean changed = Prefs.isAmoled(this) != amoled.isChecked();
            Prefs.setAmoled(this, amoled.isChecked());
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
            if (changed) recreate();
        });
        body.addView(save, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        MaterialButton clear = outlinedButton("Clear all website sessions");
        clear.setOnClickListener(v -> CookieManager.getInstance().removeAllCookies(
                value -> Toast.makeText(
                        this,
                        "Website sessions cleared",
                        Toast.LENGTH_SHORT).show()));
        body.addView(clear, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        body.addView(paragraph(
                "Immersive mode is always enabled. Swipe from a screen edge to temporarily reveal Android's system bars."));
        setScrollContent(body);
    }

    private void setToolbar(String title, String subtitle) {
        toolbar.setTitle(title);
        toolbar.setSubtitle(subtitle);
    }

    private boolean matchesEdition(String filter, String edition) {
        if ("All".equals(filter)) return true;
        return edition.toLowerCase().contains(filter.toLowerCase());
    }


    private void launchLogin(
            String title,
            String loginUrl,
            String provider,
            String loginHint
    ) {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra(LoginActivity.EXTRA_TITLE, title + " sign in");
        intent.putExtra(LoginActivity.EXTRA_URL, loginUrl);
        intent.putExtra(LoginActivity.EXTRA_PROVIDER, provider);
        intent.putExtra(LoginActivity.EXTRA_LOGIN_HINT, loginHint);
        startActivity(intent);
    }

    private void setScrollContent(LinearLayout body) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContent(scroll);
    }

    private void setContent(View view) {
        content.removeAllViews();
        content.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
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

    private TextView heading(String value) {
        TextView view = text(value, 24, Typeface.BOLD);
        view.setPadding(0, dp(4), 0, dp(8));
        return view;
    }

    private TextView paragraph(String value) {
        TextView view = text(value, 15, Typeface.NORMAL);
        view.setLineSpacing(0, 1.12f);
        view.setPadding(0, 0, 0, dp(12));
        return view;
    }

    private TextView text(String value, int size, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTypeface(Typeface.SANS_SERIF, style);
        return view;
    }

    private MaterialCardView card() {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(7), 0, dp(7));
        card.setLayoutParams(params);
        card.setRadius(dp(22));
        card.setCardElevation(dp(1));
        card.setContentPadding(dp(16), dp(16), dp(16), dp(16));
        if (Prefs.isAmoled(this)) card.setCardBackgroundColor(0xFF090909);
        return card;
    }

    private MaterialButton outlinedButton(String value) {
        MaterialButton button = new MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(value);
        button.setAllCaps(false);
        return button;
    }

    private Chip chip(String value, boolean checked) {
        Chip chip = new Chip(this);
        chip.setText(value);
        chip.setCheckable(true);
        chip.setChecked(checked);
        return chip;
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
