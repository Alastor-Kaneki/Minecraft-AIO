# Minecraft AIO

A native Android hub for Minecraft content across:

- Planet Minecraft
- Vanilla Tweaks
- CurseForge
- Modrinth
- MCPEDL
- Bedrock Tweaks
- BEComTweaks

## Native browsing

Normal browsing does not launch a browser or WebView. Minecraft AIO renders source catalogs, search results, item details, related content, version lists and direct downloads using native Android views.

WebView is reserved for authentication because the supported sites control their own login and OAuth pages. Google login begins with Android's account chooser; Discord and other identity providers continue in the isolated sign-in WebView.

## Current alpha features

- Material You-compatible interface
- Optional AMOLED true-black mode
- Always-immersive system UI
- Branded source cards and provider buttons
- Native per-source catalog screens
- Cross-source Java/Bedrock search
- Native detail pages with screenshots and descriptions
- Native Modrinth version downloads
- Native CurseForge catalog/details/downloads when an API key is configured
- Native HTML catalog parsing for Planet Minecraft and MCPEDL
- Native catalog navigation for Vanilla Tweaks, Bedrock Tweaks and BEComTweaks
- Cookie-aware native requests after sign-in
- Android DownloadManager integration

Package: `dev.alastorkaneki.minecraftaio`
