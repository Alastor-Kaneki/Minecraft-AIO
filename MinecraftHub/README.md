# Minecraft Hub

Native Android launcher/manager for Minecraft-related apps and content.

## Included in v0.1.0

- Scans installed apps and groups Minecraft/Mojang, Flarial, MB Loader, LeviLauncher, apps with `mod`/`mods` in their name/package, Minecraft tools, and Alastor project namespaces.
- Opens detected apps directly from the hub.
- Long-press app entries to hide false positives; reset hidden rules from **Access**.
- Scans shared storage recursively for `.mcpack` and `.mcaddon` files.
- Shows pack type, size, modified time, and full path.
- Attempts direct Minecraft import, with a normal Android opener fallback.
- Dedicated Minecraft Data tab for:
  - legacy `/storage/emulated/0/games/com.mojang`
  - modern `/storage/emulated/0/Android/data/com.mojang.minecraftpe/files/games/com.mojang`
- `MANAGE_EXTERNAL_STORAGE` special-access flow for normal shared storage.
- Shizuku UserService backend that runs as ADB-shell/root identity and tests whether the device actually allows Minecraft `Android/data` access.
- Privileged browsing + export of files to `Downloads/MinecraftHub/Exports` when Shizuku/root access works.
- SAF folder grant fallback.

## Android storage reality

On Android 11+, ordinary apps cannot access another app's app-specific external directory under `Android/data`, even with broad shared-storage access. The app therefore treats Minecraft Data as a separate capability. It tries direct/legacy access first, then Shizuku. ADB-shell access itself varies by Android/OEM and can still be denied; root remains the strongest backend.

## Build

Open this folder in Android Studio with JDK 17+ and build `app`.

Command line, once Android SDK + Gradle are installed:

```bash
gradle :app:assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Notes

`QUERY_ALL_PACKAGES` and `MANAGE_EXTERNAL_STORAGE` are intentionally used because this is a local launcher/file-manager style utility. These permissions are heavily restricted for Google Play distribution, but work for sideloaded/private builds when the user grants the special access.
