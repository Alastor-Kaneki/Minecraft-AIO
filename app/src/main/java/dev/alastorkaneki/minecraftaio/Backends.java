package dev.alastorkaneki.minecraftaio;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class Backends {
    private static final String USER_AGENT = "Minecraft-AIO/0.1.0-alpha (Android; github.com/Alastor-Kaneki/Minecraft-AIO)";

    interface Backend {
        String name();
        String edition();
        String homeUrl();
        List<ContentItem> search(Context context, String query) throws Exception;
    }

    private Backends() {}

    static List<Backend> all() {
        List<Backend> list = new ArrayList<>();
        list.add(new ModrinthBackend());
        list.add(new CurseForgeBackend());
        list.add(new HtmlBackend(
                "Planet Minecraft", "Java + Bedrock", "https://www.planetminecraft.com/",
                "https://www.planetminecraft.com/search/?keywords=%s",
                "a.r-info__title, a[href*='/project/'], a[href*='/texture-pack/'], a[href*='/mod/'], a[href*='/skin/']"));
        list.add(new HtmlBackend(
                "MCPEDL", "Bedrock", "https://mcpedl.com/",
                "https://mcpedl.com/?s=%s",
                "article h2 a, h2.entry-title a, .post-title a, a[href*='/category/']"));
        list.add(new StaticBackend("Vanilla Tweaks", "Java", "https://vanillatweaks.net/", new String[][]{
                {"Resource Pack Picker", "Build a custom Java resource-pack bundle.", "https://vanillatweaks.net/picker/resource-packs/"},
                {"Data Pack Picker", "Choose Java data packs and download a generated bundle.", "https://vanillatweaks.net/picker/datapacks/"},
                {"Crafting Tweaks", "Choose custom Java crafting recipes.", "https://vanillatweaks.net/picker/crafting-tweaks/"}
        }));
        list.add(new StaticBackend("Bedrock Tweaks", "Bedrock", "https://www.bedrocktweaks.net/", new String[][]{
                {"Resource Packs", "Select Bedrock visual and quality-of-life tweaks.", "https://www.bedrocktweaks.net/resource-packs"},
                {"Addons", "Browse Bedrock gameplay addon ports.", "https://www.bedrocktweaks.net/addons"},
                {"Crafting Tweaks", "Create a Bedrock crafting-tweak bundle.", "https://www.bedrocktweaks.net/crafting-tweaks"}
        }));
        list.add(new StaticBackend("BEComTweaks", "Bedrock", "https://becomtweaks.github.io/resource-packs/", new String[][]{
                {"BEComTweaks Resource Packs", "Community Bedrock ports of Vanilla Tweaks resource packs.", "https://becomtweaks.github.io/resource-packs/"},
                {"Source Repository", "Browse the open-source BEComTweaks pack catalog.", "https://github.com/BEComTweaks/resource-packs"},
                {"Documentation", "Read BEComTweaks setup and contribution documentation.", "https://becomtweaks.gitbook.io/docs/"}
        }));
        return list;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String getJson(String urlText, String apiKey) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/json");
        if (apiKey != null && !apiKey.isBlank()) {
            connection.setRequestProperty("x-api-key", apiKey);
        }
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        if (stream == null) throw new IllegalStateException("HTTP " + code);
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        } finally {
            connection.disconnect();
        }
        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code + ": " + result);
        return result.toString();
    }

    private static final class ModrinthBackend implements Backend {
        public String name() { return "Modrinth"; }
        public String edition() { return "Java"; }
        public String homeUrl() { return "https://modrinth.com/"; }

        public List<ContentItem> search(Context context, String query) throws Exception {
            String endpoint = "https://api.modrinth.com/v2/search?limit=20&query=" + encode(query);
            JSONObject root = new JSONObject(getJson(endpoint, null));
            JSONArray hits = root.optJSONArray("hits");
            List<ContentItem> results = new ArrayList<>();
            if (hits == null) return results;
            for (int i = 0; i < hits.length(); i++) {
                JSONObject item = hits.getJSONObject(i);
                String projectType = item.optString("project_type", "project");
                String slug = item.optString("slug", item.optString("project_id"));
                results.add(new ContentItem(
                        name(),
                        item.optString("title", slug),
                        item.optString("description", "Modrinth project"),
                        "https://modrinth.com/" + projectType + "/" + slug,
                        projectType));
            }
            return results;
        }
    }

    private static final class CurseForgeBackend implements Backend {
        public String name() { return "CurseForge"; }
        public String edition() { return "Java + Bedrock"; }
        public String homeUrl() { return "https://www.curseforge.com/minecraft"; }

        public List<ContentItem> search(Context context, String query) throws Exception {
            String key = Prefs.getCurseForgeKey(context);
            List<ContentItem> results = new ArrayList<>();
            if (key.isBlank()) {
                results.add(new ContentItem(
                        name(),
                        "CurseForge API key required",
                        "Open Settings and enter your CurseForge API key to enable native CurseForge results.",
                        "https://console.curseforge.com/",
                        "setup"));
                return results;
            }
            String endpoint = "https://api.curseforge.com/v1/mods/search?gameId=432&pageSize=20&searchFilter=" + encode(query);
            JSONObject root = new JSONObject(getJson(endpoint, key));
            JSONArray data = root.optJSONArray("data");
            if (data == null) return results;
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.getJSONObject(i);
                JSONObject links = item.optJSONObject("links");
                String page = links == null ? homeUrl() : links.optString("websiteUrl", homeUrl());
                results.add(new ContentItem(
                        name(),
                        item.optString("name", "CurseForge project"),
                        item.optString("summary", "CurseForge project"),
                        page,
                        "project"));
            }
            return results;
        }
    }

    private static final class HtmlBackend implements Backend {
        private final String name;
        private final String edition;
        private final String home;
        private final String searchTemplate;
        private final String selector;

        HtmlBackend(String name, String edition, String home, String searchTemplate, String selector) {
            this.name = name;
            this.edition = edition;
            this.home = home;
            this.searchTemplate = searchTemplate;
            this.selector = selector;
        }

        public String name() { return name; }
        public String edition() { return edition; }
        public String homeUrl() { return home; }

        public List<ContentItem> search(Context context, String query) throws Exception {
            String target = String.format(Locale.US, searchTemplate, encode(query));
            Document document = Jsoup.connect(target).userAgent(USER_AGENT).timeout(20000).followRedirects(true).get();
            Elements links = document.select(selector);
            List<ContentItem> results = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (Element link : links) {
                if (results.size() >= 20) break;
                String title = link.text().trim();
                String href = link.absUrl("href");
                if (title.length() < 3 || href.isBlank() || !seen.add(href)) continue;
                Element parent = link.parent();
                String description = parent == null ? edition : parent.text().trim();
                if (description.length() > 220) description = description.substring(0, 220) + "…";
                results.add(new ContentItem(name, title, description, href, "web catalog"));
            }
            if (results.isEmpty()) {
                results.add(new ContentItem(name, "Search " + name, "Open the source search for “" + query + "”.", target, "search"));
            }
            return results;
        }
    }

    private static final class StaticBackend implements Backend {
        private final String name;
        private final String edition;
        private final String home;
        private final String[][] entries;

        StaticBackend(String name, String edition, String home, String[][] entries) {
            this.name = name;
            this.edition = edition;
            this.home = home;
            this.entries = entries;
        }

        public String name() { return name; }
        public String edition() { return edition; }
        public String homeUrl() { return home; }

        public List<ContentItem> search(Context context, String query) {
            String needle = query == null ? "" : query.trim().toLowerCase(Locale.US);
            List<ContentItem> results = new ArrayList<>();
            for (String[] entry : entries) {
                String haystack = (entry[0] + " " + entry[1]).toLowerCase(Locale.US);
                if (needle.isBlank() || haystack.contains(needle) || name.toLowerCase(Locale.US).contains(needle)) {
                    results.add(new ContentItem(name, entry[0], entry[1], entry[2], "picker"));
                }
            }
            if (results.isEmpty()) {
                results.add(new ContentItem(name, "Open " + name, "Browse this source's native picker and catalog.", home, "catalog"));
            }
            return results;
        }
    }
}
