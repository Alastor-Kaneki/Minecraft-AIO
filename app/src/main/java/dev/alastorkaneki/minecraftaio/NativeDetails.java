package dev.alastorkaneki.minecraftaio;

import android.content.Context;
import android.net.Uri;
import android.webkit.CookieManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class NativeDetails {
    static final class Download {
        final String label;
        final String url;

        Download(String label, String url) {
            this.label = label == null || label.isBlank() ? "Download file" : label;
            this.url = url == null ? "" : url;
        }
    }

    static final class Related {
        final String title;
        final String url;
        final String imageUrl;

        Related(String title, String url, String imageUrl) {
            this.title = title == null || title.isBlank() ? "Related item" : title;
            this.url = url == null ? "" : url;
            this.imageUrl = imageUrl == null ? "" : imageUrl;
        }
    }

    static final class Result {
        String description = "";
        String imageUrl = "";
        String author = "";
        String status = "";
        final List<Download> downloads = new ArrayList<>();
        final List<Related> related = new ArrayList<>();
    }

    private NativeDetails() {}

    static Result load(Context context, ContentItem item) throws Exception {
        if ("Modrinth".equalsIgnoreCase(item.source) && !item.backendId.isBlank()) {
            return loadModrinth(item);
        }
        if ("CurseForge".equalsIgnoreCase(item.source) && !item.backendId.isBlank()) {
            return loadCurseForge(context, item);
        }
        return loadHtml(item);
    }

    private static Result loadModrinth(ContentItem item) throws Exception {
        Result result = new Result();
        JSONObject project = new JSONObject(
                Backends.getJson("https://api.modrinth.com/v2/project/" + item.backendId, null));
        result.description = firstNonBlank(
                project.optString("body"),
                project.optString("description"),
                item.description);
        result.imageUrl = firstNonBlank(project.optString("icon_url"), item.imageUrl);
        result.author = project.optString("team", "");
        result.status = project.optString("status", "");

        JSONArray gallery = project.optJSONArray("gallery");
        if (gallery != null && gallery.length() > 0) {
            result.imageUrl = firstNonBlank(
                    gallery.optJSONObject(0) == null ? "" : gallery.optJSONObject(0).optString("url"),
                    result.imageUrl);
        }

        JSONArray versions = new JSONArray(
                Backends.getJson("https://api.modrinth.com/v2/project/" + item.backendId + "/version", null));
        for (int i = 0; i < versions.length() && result.downloads.size() < 18; i++) {
            JSONObject version = versions.getJSONObject(i);
            JSONArray files = version.optJSONArray("files");
            if (files == null || files.length() == 0) continue;
            JSONObject selected = null;
            for (int j = 0; j < files.length(); j++) {
                JSONObject candidate = files.getJSONObject(j);
                if (selected == null || candidate.optBoolean("primary", false)) selected = candidate;
                if (candidate.optBoolean("primary", false)) break;
            }
            if (selected == null) continue;
            String url = selected.optString("url");
            if (url.isBlank()) continue;
            String versionNumber = version.optString("version_number", "Latest");
            String gameVersions = join(version.optJSONArray("game_versions"));
            String loaders = join(version.optJSONArray("loaders"));
            String label = versionNumber;
            if (!gameVersions.isBlank()) label += "  •  " + gameVersions;
            if (!loaders.isBlank()) label += "  •  " + loaders;
            result.downloads.add(new Download(label, url));
        }
        return result;
    }

    private static Result loadCurseForge(Context context, ContentItem item) throws Exception {
        String key = Prefs.getCurseForgeKey(context);
        if (key.isBlank()) throw new IllegalStateException(
                "A CurseForge API key is required. Add it in Minecraft AIO Settings.");

        Result result = new Result();
        JSONObject root = new JSONObject(
                Backends.getJson("https://api.curseforge.com/v1/mods/" + item.backendId, key));
        JSONObject project = root.optJSONObject("data");
        if (project == null) throw new IllegalStateException("CurseForge returned no project data.");

        result.description = firstNonBlank(project.optString("summary"), item.description);
        JSONObject logo = project.optJSONObject("logo");
        result.imageUrl = logo == null
                ? item.imageUrl
                : firstNonBlank(logo.optString("thumbnailUrl"), item.imageUrl);
        JSONArray authors = project.optJSONArray("authors");
        if (authors != null && authors.length() > 0 && authors.optJSONObject(0) != null) {
            result.author = authors.optJSONObject(0).optString("name", "");
        }
        result.status = project.optBoolean("isAvailable", true) ? "Available" : "Unavailable";

        JSONObject filesRoot = new JSONObject(Backends.getJson(
                "https://api.curseforge.com/v1/mods/" + item.backendId + "/files?pageSize=20",
                key));
        JSONArray files = filesRoot.optJSONArray("data");
        if (files != null) {
            for (int i = 0; i < files.length() && result.downloads.size() < 18; i++) {
                JSONObject file = files.getJSONObject(i);
                String url = file.optString("downloadUrl");
                if (url.isBlank()) continue;
                String label = firstNonBlank(
                        file.optString("displayName"),
                        file.optString("fileName"),
                        "Download");
                result.downloads.add(new Download(label, url));
            }
        }
        return result;
    }

    private static Result loadHtml(ContentItem item) throws Exception {
        Result result = new Result();
        result.description = item.description;
        result.imageUrl = item.imageUrl;
        if (!item.directDownloadUrl.isBlank()) {
            result.downloads.add(new Download("Download", item.directDownloadUrl));
        }
        if (item.url.isBlank()) return result;

        String normalizedUrl = item.url.replace("https://www.bedrocktweaks.net", "https://bedrocktweaks.net");
        Document document = Backends.getDocument(normalizedUrl);
        Element description = document.selectFirst(
                "meta[property=og:description], meta[name=description], meta[name=twitter:description]");
        if (description != null) {
            result.description = firstNonBlank(description.attr("content"), result.description);
        }
        Element image = document.selectFirst(
                "meta[property=og:image], meta[name=twitter:image]");
        if (image != null) result.imageUrl = firstNonBlank(image.attr("content"), result.imageUrl);

        Element author = document.selectFirst(
                "[rel=author], .author, .post-author, [itemprop=author]");
        if (author != null) result.author = clean(author.text(), 120);

        Element main = document.selectFirst(
                "article, main, .entry-content, .post-content, .project-description, .description");
        if ((result.description == null || result.description.length() < 80) && main != null) {
            result.description = clean(main.text(), 1400);
        }

        Set<String> seenDownloads = new LinkedHashSet<>();
        Set<String> seenRelated = new LinkedHashSet<>();
        String sourceHost = host(normalizedUrl);
        Elements links = document.select("a[href]");
        for (Element link : links) {
            String href = link.absUrl("href");
            if (href.isBlank()) continue;
            String label = clean(link.text(), 120);
            if (isDirectDownload(link, href)) {
                if (seenDownloads.add(href) && result.downloads.size() < 20) {
                    result.downloads.add(new Download(
                            label.isBlank() ? fileName(href) : label,
                            href));
                }
                continue;
            }

            String linkHost = host(href);
            boolean sameHost = !sourceHost.isBlank() && sourceHost.equalsIgnoreCase(linkHost);
            boolean useful = sameHost && label.length() >= 4
                    && !label.equalsIgnoreCase("home")
                    && !label.equalsIgnoreCase("login")
                    && !label.toLowerCase(Locale.US).contains("privacy")
                    && !label.toLowerCase(Locale.US).contains("terms");
            if (useful && seenRelated.add(href) && result.related.size() < 10) {
                Element container = link.closest("article, li, .card, .post, div");
                Element relatedImage = container == null ? null : container.selectFirst("img");
                String imageUrl = relatedImage == null ? "" : firstNonBlank(
                        relatedImage.absUrl("src"),
                        relatedImage.absUrl("data-src"));
                result.related.add(new Related(label, href, imageUrl));
            }
        }
        return result;
    }

    private static boolean isDirectDownload(Element link, String url) {
        String lower = url.toLowerCase(Locale.US);
        if (link.hasAttr("download")) return true;
        if (lower.contains("cdn.modrinth.com/")
                || lower.contains("forgecdn.net/")
                || lower.contains("mediafilez.com/")
                || lower.contains("githubusercontent.com/")) return true;
        return lower.matches(".*\\.(zip|jar|mcpack|mcaddon|mcworld|mctemplate|mcstructure)(\\?.*)?$");
    }

    private static String fileName(String url) {
        try {
            String path = Uri.parse(url).getLastPathSegment();
            return path == null || path.isBlank() ? "Download file" : path;
        } catch (Exception ignored) {
            return "Download file";
        }
    }

    private static String host(String url) {
        try {
            return new URL(url).getHost();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String clean(String value, int max) {
        if (value == null) return "";
        String clean = value.replaceAll("\\s+", " ").trim();
        return clean.length() > max ? clean.substring(0, max) + "…" : clean;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private static String join(JSONArray array) {
        if (array == null) return "";
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < array.length() && i < 4; i++) {
            String value = array.optString(i);
            if (!value.isBlank()) parts.add(value);
        }
        return String.join(", ", parts);
    }
}
