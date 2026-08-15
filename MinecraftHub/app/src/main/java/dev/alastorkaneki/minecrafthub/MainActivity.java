package dev.alastorkaneki.minecrafthub;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(10, 13, 12);
    private static final int PANEL = Color.rgb(20, 27, 24);
    private static final int PANEL2 = Color.rgb(28, 39, 34);
    private static final int GREEN = Color.rgb(110, 219, 131);
    private static final int TEXT = Color.rgb(239, 247, 241);
    private static final int MUTED = Color.rgb(158, 177, 165);
    private static final int SHIZUKU_REQ = 5287;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LinearLayout content;
    private TextView subtitle;
    private IPrivilegedFileService privileged;
    private boolean bound;

    private final Shizuku.OnRequestPermissionResultListener permissionListener = (requestCode, grantResult) -> {
        if (requestCode == SHIZUKU_REQ && grantResult == PackageManager.PERMISSION_GRANTED) bindShizuku();
        runOnUiThread(this::showAccess);
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            privileged = IPrivilegedFileService.Stub.asInterface(binder);
            bound = true;
            runOnUiThread(MainActivity.this::showData);
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            privileged = null;
            bound = false;
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        buildUi();
        showApps();
    }

    @Override protected void onResume() {
        super.onResume();
        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED && !bound) bindShizuku();
    }

    @Override protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        io.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(12));
        root.setBackgroundColor(BG);
        root.addView(label("MINECRAFT HUB", 28, TEXT, true));
        subtitle = label("Launcher • packs • Minecraft data", 13, MUTED, false);
        root.addView(subtitle);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(this);
        tabs.addView(tab("APPS", v -> showApps()));
        tabs.addView(tab("PACKS", v -> showPacks()));
        tabs.addView(tab("DATA", v -> showData()));
        tabs.addView(tab("ACCESS", v -> showAccess()));
        hsv.addView(tabs);
        root.addView(hsv);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(8), 0, dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    private void clear(String s) { content.removeAllViews(); subtitle.setText(s); }

    private void showApps() {
        clear("Scanning installed Minecraft-related apps…");
        content.addView(info("Detection", "Minecraft, Flarial, MB Loader, LeviLauncher, mod/mods names, Bedrock/Minecraft tools, and manually included apps."));
        io.execute(() -> {
            List<MinecraftAppDetector.Entry> entries = MinecraftAppDetector.scan(this);
            runOnUiThread(() -> renderApps(entries));
        });
    }

    private void renderApps(List<MinecraftAppDetector.Entry> entries) {
        clear(entries.size() + " Minecraft launcher entries");
        String category = "";
        for (MinecraftAppDetector.Entry e : entries) {
            if (!category.equals(e.category)) { category = e.category; content.addView(section(category)); }
            LinearLayout row = card();
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            ImageView icon = new ImageView(this);
            if (e.icon != null) icon.setImageDrawable(e.icon);
            row.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
            LinearLayout text = new LinearLayout(this); text.setOrientation(LinearLayout.VERTICAL); text.setPadding(dp(12),0,dp(6),0);
            text.addView(label(e.label, 16, TEXT, true));
            text.addView(label(e.packageName, 10, MUTED, false));
            text.addView(label(e.reason, 10, GREEN, false));
            row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
            Button open = small("OPEN"); open.setOnClickListener(v -> launch(e.packageName)); row.addView(open);
            row.setOnClickListener(v -> launch(e.packageName));
            row.setOnLongClickListener(v -> { exclude(e.packageName); showApps(); return true; });
            content.addView(row);
        }
        if (entries.isEmpty()) content.addView(info("Nothing found", "Use Access → Add app manually if a Minecraft tool is missed."));
    }

    private void launch(String pkg) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) startActivity(i); else startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
    }

    private void exclude(String pkg) {
        Set<String> set = new HashSet<>(getSharedPreferences("rules", MODE_PRIVATE).getStringSet("exclude_packages", Collections.emptySet()));
        set.add(pkg);
        getSharedPreferences("rules", MODE_PRIVATE).edit().putStringSet("exclude_packages", set).apply();
        toast("Hidden from Hub");
    }

    private void showPacks() {
        clear("Minecraft pack scanner");
        if (!hasAllFiles()) {
            content.addView(info("Permission needed", "All files access lets the Hub recursively find .mcpack and .mcaddon files in shared storage."));
            Button b = big("GRANT ALL FILES ACCESS"); b.setOnClickListener(v -> requestAllFiles()); content.addView(b); return;
        }
        TextView progress = label("Scanning…", 13, MUTED, false); content.addView(progress);
        io.execute(() -> {
            List<StorageScanner.PackFile> packs = StorageScanner.scan((dirs, found) -> runOnUiThread(() -> progress.setText("Scanned " + dirs + " folders • " + found + " found")));
            runOnUiThread(() -> renderPacks(packs));
        });
    }

    private void renderPacks(List<StorageScanner.PackFile> packs) {
        clear(packs.size() + " Minecraft content files");
        Button rescan = big("RESCAN"); rescan.setOnClickListener(v -> showPacks()); content.addView(rescan);
        for (StorageScanner.PackFile p : packs) {
            LinearLayout c = card(); c.setOrientation(LinearLayout.VERTICAL);
            c.addView(label(p.file.getName(), 15, TEXT, true));
            c.addView(label(p.type + " • " + Formatter.formatShortFileSize(this, p.file.length()), 11, GREEN, false));
            c.addView(label(DateFormat.getDateTimeInstance().format(new Date(p.file.lastModified())), 10, MUTED, false));
            c.addView(label(p.file.getAbsolutePath(), 10, MUTED, false));
            c.setOnClickListener(v -> importPack(p.file));
            content.addView(c);
        }
        if (packs.isEmpty()) content.addView(info("No packs", "No .mcpack or .mcaddon files were found outside protected Android/data."));
    }

    private void importPack(File f) {
        Uri uri = HubFileProvider.uriFor(f);
        Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/zip").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { i.setPackage("com.mojang.minecraftpe"); startActivity(i); }
        catch (Exception e) { try { i.setPackage(null); startActivity(i); } catch (Exception x) { toast("No app can open this pack"); } }
    }

    private void showData() {
        clear("Minecraft external data");
        content.addView(info("Minecraft Data", "The Hub tests normal access first, then Shizuku/ADB/root for the protected Android/data location."));
        dataPath("LEGACY EXTERNAL", legacyPath());
        dataPath("MODERN ANDROID/DATA", modernPath());
        Button s = big(bound ? "SHIZUKU CONNECTED" : "CONNECT SHIZUKU"); s.setOnClickListener(v -> ensureShizuku()); content.addView(s);
    }

    private void dataPath(String title, String path) {
        LinearLayout c = card(); c.setOrientation(LinearLayout.VERTICAL);
        File f = new File(path);
        c.addView(label(title, 13, GREEN, true));
        c.addView(label(path, 10, MUTED, false));
        boolean direct = f.exists() && f.canRead();
        c.addView(label(direct ? "● Direct access" : "○ Direct access blocked", 11, direct ? GREEN : MUTED, false));
        c.setOnClickListener(v -> {
            if (direct) browseDirect(f);
            else if (privileged != null) browsePrivileged(path);
            else { toast("Connect Shizuku for protected access"); ensureShizuku(); }
        });
        content.addView(c);
    }

    private void browseDirect(File dir) {
        clear(dir.getAbsolutePath());
        File parent = dir.getParentFile(); if (parent != null) { Button up = big("← UP"); up.setOnClickListener(v -> browseDirect(parent)); content.addView(up); }
        File[] files = dir.listFiles();
        if (files == null) { content.addView(info("Unreadable", dir.getAbsolutePath())); return; }
        java.util.Arrays.sort(files, Comparator.comparing((File x) -> !x.isDirectory()).thenComparing(x -> x.getName().toLowerCase(Locale.ROOT)));
        for (File f : files) {
            LinearLayout c = card(); c.setOrientation(LinearLayout.VERTICAL);
            c.addView(label((f.isDirectory() ? "📁 " : "📄 ") + f.getName(), 14, TEXT, true));
            if (!f.isDirectory()) c.addView(label(Formatter.formatShortFileSize(this, f.length()), 10, MUTED, false));
            c.setOnClickListener(v -> { if (f.isDirectory()) browseDirect(f); else if (isPack(f.getName())) importPack(f); });
            content.addView(c);
        }
    }

    private void browsePrivileged(String path) {
        clear("Shizuku • " + path);
        io.execute(() -> {
            try {
                if (privileged == null || !privileged.canRead(path)) { runOnUiThread(() -> content.addView(info("Access denied", "Shizuku identity cannot read this path on this device."))); return; }
                String[] list = privileged.list(path);
                runOnUiThread(() -> renderPrivileged(path, list));
            } catch (Exception e) { runOnUiThread(() -> toast("Shizuku error: " + e.getMessage())); }
        });
    }

    private void renderPrivileged(String path, String[] list) {
        clear("Shizuku • " + path);
        File parent = new File(path).getParentFile(); if (parent != null) { Button up = big("← UP"); up.setOnClickListener(v -> browsePrivileged(parent.getAbsolutePath())); content.addView(up); }
        for (String raw : list) {
            String[] p = raw.split("\\|", -1); if (p.length < 5) continue;
            boolean dir = "D".equals(p[0]); String name = unesc(p[1]); String full = unesc(p[2]); long size = parseLong(p[3]);
            LinearLayout c = card(); c.setOrientation(LinearLayout.VERTICAL);
            c.addView(label((dir ? "📁 " : "📄 ") + name, 14, TEXT, true));
            if (!dir) c.addView(label(Formatter.formatShortFileSize(this, size) + " • tap to export", 10, MUTED, false));
            c.setOnClickListener(v -> { if (dir) browsePrivileged(full); else exportPrivileged(full, name); });
            content.addView(c);
        }
    }

    private void exportPrivileged(String source, String name) {
        File out = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MinecraftHub/Exports/" + name);
        io.execute(() -> {
            try {
                boolean ok = privileged != null && privileged.copyFile(source, out.getAbsolutePath());
                runOnUiThread(() -> { toast(ok ? "Exported to " + out.getAbsolutePath() : "Export failed"); if (ok && isPack(name)) importPack(out); });
            } catch (Exception e) { runOnUiThread(() -> toast("Export error: " + e.getMessage())); }
        });
    }

    private void showAccess() {
        clear("Permissions & detection");
        content.addView(info("All files access", hasAllFiles() ? "Granted" : "Not granted"));
        Button files = big("OPEN ALL FILES ACCESS"); files.setOnClickListener(v -> requestAllFiles()); content.addView(files);
        content.addView(info("Shizuku", shizukuStatus()));
        Button shizuku = big("CONNECT / REQUEST SHIZUKU"); shizuku.setOnClickListener(v -> ensureShizuku()); content.addView(shizuku);
        Button add = big("ADD APP MANUALLY"); add.setOnClickListener(v -> manualPicker()); content.addView(add);
        Button reset = big("RESET HIDDEN APPS"); reset.setOnClickListener(v -> { getSharedPreferences("rules", MODE_PRIVATE).edit().remove("exclude_packages").apply(); toast("Hidden apps reset"); }); content.addView(reset);
    }

    private void manualPicker() {
        clear("Choose apps to force into Hub");
        io.execute(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> all = Build.VERSION.SDK_INT >= 33 ? pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0)) : pm.getInstalledApplications(0);
            List<ApplicationInfo> apps = new ArrayList<>();
            for (ApplicationInfo a : all) if (!a.packageName.equals(getPackageName()) && pm.getLaunchIntentForPackage(a.packageName) != null) apps.add(a);
            apps.sort(Comparator.comparing(a -> String.valueOf(pm.getApplicationLabel(a)).toLowerCase(Locale.ROOT)));
            runOnUiThread(() -> renderPicker(apps));
        });
    }

    private void renderPicker(List<ApplicationInfo> apps) {
        clear("Manual include • " + apps.size() + " launchable apps");
        Set<String> forced = new HashSet<>(getSharedPreferences("rules", MODE_PRIVATE).getStringSet("include_packages", Collections.emptySet()));
        PackageManager pm = getPackageManager();
        for (ApplicationInfo a : apps) {
            boolean on = forced.contains(a.packageName);
            Button b = big((on ? "✓ " : "+ ") + pm.getApplicationLabel(a) + "\n" + a.packageName);
            b.setOnClickListener(v -> { Set<String> s = new HashSet<>(getSharedPreferences("rules", MODE_PRIVATE).getStringSet("include_packages", Collections.emptySet())); if (!s.add(a.packageName)) s.remove(a.packageName); getSharedPreferences("rules", MODE_PRIVATE).edit().putStringSet("include_packages", s).apply(); manualPicker(); });
            content.addView(b);
        }
    }

    private void ensureShizuku() {
        if (!Shizuku.pingBinder()) { toast("Start Shizuku first"); Intent i = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api"); if (i != null) startActivity(i); return; }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bindShizuku();
        else Shizuku.requestPermission(SHIZUKU_REQ);
    }

    private void bindShizuku() {
        if (bound || !Shizuku.pingBinder()) return;
        try {
            Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(new ComponentName(this, PrivilegedFileService.class))
                    .daemon(false).processNameSuffix("mcdata").debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE);
            Shizuku.bindUserService(args, connection);
        } catch (Throwable t) { toast("Shizuku bind failed: " + t.getMessage()); }
    }

    private String shizukuStatus() {
        try {
            if (!Shizuku.pingBinder()) return "Not running";
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return "Running • permission needed";
            int uid = Shizuku.getUid();
            return (bound ? "Connected" : "Permission granted") + " • UID " + uid + (uid == 0 ? " (root)" : uid == 2000 ? " (ADB shell)" : "");
        } catch (Throwable t) { return "Unavailable"; }
    }

    private boolean hasAllFiles() {
        return Build.VERSION.SDK_INT >= 30 ? Environment.isExternalStorageManager() : checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAllFiles() {
        if (Build.VERSION.SDK_INT >= 30) {
            try { startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()))); }
            catch (Exception e) { startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
        } else requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
    }

    private static String modernPath() { return Environment.getExternalStorageDirectory() + "/Android/data/com.mojang.minecraftpe/files/games/com.mojang"; }
    private static String legacyPath() { return Environment.getExternalStorageDirectory() + "/games/com.mojang"; }
    private static boolean isPack(String n) { String s = n.toLowerCase(Locale.ROOT); return s.endsWith(".mcpack") || s.endsWith(".mcaddon"); }

    private LinearLayout card() { LinearLayout c = new LinearLayout(this); c.setPadding(dp(14),dp(12),dp(14),dp(12)); c.setBackground(round(PANEL, 16)); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,0,0,dp(8)); c.setLayoutParams(p); return c; }
    private View info(String title, String body) { LinearLayout c = card(); c.setOrientation(LinearLayout.VERTICAL); c.addView(label(title,15,TEXT,true)); c.addView(label(body,12,MUTED,false)); return c; }
    private TextView section(String s) { TextView t = label(s.toUpperCase(Locale.ROOT),12,GREEN,true); t.setPadding(dp(4),dp(12),0,dp(6)); return t; }
    private Button tab(String s, View.OnClickListener l) { Button b = small(s); b.setOnClickListener(l); return b; }
    private Button small(String s) { Button b = new Button(this); b.setText(s); b.setTextColor(TEXT); b.setTextSize(11); b.setAllCaps(false); b.setTypeface(Typeface.DEFAULT_BOLD); b.setBackground(round(PANEL2,12)); b.setPadding(dp(12),dp(7),dp(12),dp(7)); return b; }
    private Button big(String s) { Button b = new Button(this); b.setText(s); b.setTextColor(TEXT); b.setTextSize(12); b.setAllCaps(false); b.setTypeface(Typeface.DEFAULT_BOLD); b.setGravity(Gravity.CENTER_VERTICAL); b.setBackground(round(PANEL2,14)); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,0,0,dp(8)); b.setLayoutParams(p); return b; }
    private TextView label(String s, float sp, int color, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL); return t; }
    private GradientDrawable round(int color, int radius) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); return g; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private static long parseLong(String s) { try { return Long.parseLong(s); } catch (Exception e) { return 0; } }
    private static String unesc(String s) { return s.replace("%0A", "\n").replace("%7C", "|").replace("%25", "%"); }
}
