package dev.alastorkaneki.minecrafthub;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Collections;
import java.util.regex.Pattern;

public final class MinecraftAppDetector {
    private MinecraftAppDetector() {}

    public static final class Entry {
        public final String label;
        public final String packageName;
        public final Drawable icon;
        public final String category;
        public final String reason;

        Entry(String label, String packageName, Drawable icon, String category, String reason) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
            this.category = category;
            this.reason = reason;
        }
    }

    private static final Pattern MOD_WORD = Pattern.compile("(^|[^a-z0-9])mods?([^a-z0-9]|$)");

    public static List<Entry> scan(Context context) {
        PackageManager pm = context.getPackageManager();
        SharedPreferences prefs = context.getSharedPreferences("rules", Context.MODE_PRIVATE);
        Set<String> included = prefs.getStringSet("include_packages", Collections.emptySet());
        Set<String> excluded = prefs.getStringSet("exclude_packages", Collections.emptySet());
        List<Entry> result = new ArrayList<>();

        List<ApplicationInfo> installed;
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            installed = pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA));
        } else {
            installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        }
        for (ApplicationInfo ai : installed) {
            String pkg = ai.packageName;
            if (pkg.equals(context.getPackageName()) || excluded.contains(pkg)) continue;
            String label = String.valueOf(pm.getApplicationLabel(ai));
            Match m = classify(label, pkg, included.contains(pkg));
            if (m == null) continue;
            Drawable icon;
            try { icon = pm.getApplicationIcon(ai); } catch (Exception e) { icon = null; }
            result.add(new Entry(label, pkg, icon, m.category, m.reason));
        }

        result.sort(Comparator.comparing((Entry e) -> categoryRank(e.category))
                .thenComparing(e -> e.label.toLowerCase(Locale.ROOT)));
        return result;
    }

    private static int categoryRank(String s) {
        if (s.equals("Minecraft")) return 0;
        if (s.equals("Clients & Launchers")) return 1;
        if (s.equals("Mods")) return 2;
        if (s.equals("Minecraft Tools")) return 3;
        if (s.equals("Our Apps")) return 4;
        return 5;
    }

    private static final class Match {
        final String category, reason;
        Match(String c, String r) { category = c; reason = r; }
    }

    private static Match classify(String label, String pkg, boolean forced) {
        String l = label.toLowerCase(Locale.ROOT);
        String p = pkg.toLowerCase(Locale.ROOT);
        String both = l + " " + p;
        if (forced) return new Match("Custom", "Manually included");

        if (p.equals("com.mojang.minecraftpe") || l.equals("minecraft") || both.contains("minecraft"))
            return new Match("Minecraft", "Minecraft/Mojang match");

        if (both.contains("flarial"))
            return new Match("Clients & Launchers", "Flarial match");
        if (l.contains("mb loader") || both.contains("mbloader") || both.contains("mb.loader"))
            return new Match("Clients & Launchers", "MB Loader match");
        if (both.contains("levilauncher") || l.contains("levi launcher") || both.contains("levi.launcher"))
            return new Match("Clients & Launchers", "LeviLauncher match");

        if (l.contains("mods") || p.contains("mods") || MOD_WORD.matcher(l).find() || MOD_WORD.matcher(p.replace('.', ' ')).find())
            return new Match("Mods", "mod/mods in app name or package");

        String[] toolWords = {
                "bedrock", "mcpedl", "mcpack", "mcaddon", "mc tool", "mctool", "mc manager",
                "world editor", "world manager", "worldshuttle", "blockbench", "texture pack",
                "resource pack", "behavior pack", "behaviour pack", "addon", "shader", "skin pack"
        };
        for (String word : toolWords) {
            if (both.contains(word)) return new Match("Minecraft Tools", "Matched “" + word + "”");
        }

        if (p.startsWith("dev.alastorkaneki.") || p.startsWith("com.alastorkaneki.") || p.startsWith("io.alastorkaneki."))
            return new Match("Our Apps", "Alastor project namespace");

        return null;
    }
}
