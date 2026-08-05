package dev.alastorkaneki.minecraftaio;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    private static final String FILE = "minecraft_aio_settings";
    private static final String AMOLED = "amoled";
    private static final String CURSEFORGE_KEY = "curseforge_key";

    private Prefs() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean isAmoled(Context context) {
        return prefs(context).getBoolean(AMOLED, false);
    }

    static void setAmoled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(AMOLED, enabled).apply();
    }

    static String getCurseForgeKey(Context context) {
        return prefs(context).getString(CURSEFORGE_KEY, "");
    }

    static void setCurseForgeKey(Context context, String key) {
        prefs(context).edit().putString(CURSEFORGE_KEY, key == null ? "" : key.trim()).apply();
    }
}
