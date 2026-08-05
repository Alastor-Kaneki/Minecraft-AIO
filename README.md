# Minecraft AIO

A native Android Minecraft content hub for Java and Bedrock Edition.

## Current alpha

Minecraft AIO uses native Android screens for discovery, aggregated search, accounts and settings. An authentication-only WebView is opened when a website needs its own sign-in flow.

### Sources

- Planet Minecraft
- Vanilla Tweaks
- CurseForge
- Modrinth
- MCPEDL
- Bedrock Tweaks
- BEComTweaks

### Features

- Material You dynamic colors
- Optional true-black AMOLED mode
- Always-on immersive mode
- Native aggregated search
- Java/Bedrock filtering
- Modrinth API search
- CurseForge API search using a user-provided key
- Native HTML catalog parsing for Planet Minecraft and MCPEDL
- Native source cards for Vanilla Tweaks, Bedrock Tweaks and BEComTweaks
- Website account sessions for Planet Minecraft, MCPEDL, CurseForge and Modrinth
- Android Google account chooser before Google-backed site login
- Authentication WebView for Discord and all other site-supported providers
- Android Download Manager handoff from authenticated pages

## Authentication limitations

The app does not impersonate the supported websites or embed their private OAuth credentials. Each site owns its available authentication methods and can change them at any time. Google account selection is native; the selected address is passed as a login hint to the site's own authorization flow.

## Build

```bash
gradle :app:assembleDebug
```

The GitHub Actions workflow builds an installable debug-signed alpha APK and publishes it to the `v0.1.0-alpha` prerelease.

## Package

`dev.alastorkaneki.minecraftaio`

Minecraft AIO is not affiliated with Mojang Studios, Microsoft, or any listed content platform.
