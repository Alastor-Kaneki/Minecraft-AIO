package dev.alastorkaneki.minecraftaio;

import android.content.Context;
import android.webkit.CookieManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Connection;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class Backends {
    static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/138.0 Mobile Safari/537.36 "
                    + "Minecraft-AIO/0.1.2-alpha";

    interface Backend {
        String name();
        String edition();
        String homeUrl();
        String logoUrl();
        String tagline();
        List<ContentItem> search(Context context, String query) throws Exception;

        default List<ContentItem> featured(Context context) throws Exception {
            return search(context, "");
        }
    }

    private Backends() {}

    static List<Backend> all() {
        List<Backend> list = new ArrayList<>();
        list.add(new ModrinthBackend());
        list.add(new CurseForgeBackend());
        list.add(new HtmlBackend(
                "Planet Minecraft",
                "Java + Bedrock",
                "https://www.planetminecraft.com/",
                favicon("planetminecraft.com"),
                "Community maps, skins, texture packs, data packs, mods and builds.",
                "https://www.planetminecraft.com/search/?keywords=%s",
                "a.r-info__title, a[href*='/project/'], a[href*='/texture-pack/'], a[href*='/mod/'], a[href*='/skin/'], a[href*='/data-pack/'], a[href*='/maps/']"
        ));
        list.add(new McpedlBackend());
        list.add(new StaticBackend(
                "Vanilla Tweaks",
                "Java",
                "https://vanillatweaks.net/",
                favicon("vanillatweaks.net"),
                "Build custom Java resource-pack, data-pack and crafting-tweak bundles.",
                new String[][]{
                        {"Resource Pack Picker", "Choose visual and quality-of-life resource packs.", "https://vanillatweaks.net/picker/resource-packs/", "resource pack"},
                        {"Data Pack Picker", "Choose gameplay and utility data packs.", "https://vanillatweaks.net/picker/datapacks/", "data pack"},
                        {"Crafting Tweaks", "Choose custom crafting recipes.", "https://vanillatweaks.net/picker/crafting-tweaks/", "crafting"}
                }
        ));
        list.add(new StaticBackend(
                "Bedrock Tweaks",
                "Bedrock",
                "https://bedrocktweaks.net/",
                favicon("bedrocktweaks.net"),
                "Native catalog access to Bedrock visual, gameplay and crafting tweaks.",
                new String[][]{
                        {"Resource Packs", "Browse Bedrock visual and quality-of-life tweaks.", "https://bedrocktweaks.net/resource-packs", "resource pack"},
                        {"Addons", "Browse Bedrock gameplay addon ports.", "https://bedrocktweaks.net/addons", "addon"},
                        {"Crafting Tweaks", "Browse Bedrock crafting tweaks.", "https://bedrocktweaks.net/crafting-tweaks", "crafting"}
                }
        ));
        list.add(new StaticBackend(
                "BEComTweaks",
                "Bedrock",
                "https://becomtweaks.github.io/resource-packs/",
                favicon("becomtweaks.github.io"),
                "Community Bedrock ports and tweak packs.",
                new String[][]{
                        {"Resource Packs", "Browse community Bedrock resource-pack ports.", "https://becomtweaks.github.io/resource-packs/", "resource pack"},
                        {"Project Catalog", "Browse the BEComTweaks open-source catalog.", "https://github.com/BEComTweaks/resource-packs", "catalog"},
                        {"Documentation", "Browse setup and pack documentation.", "https://becomtweaks.gitbook.io/docs/", "documentation"}
                }
        ));
        return list;
    }

    static Backend find(String name) {
        if (name == null) return null;
        for (Backend backend : all()) {
            if (backend.name().equalsIgnoreCase(name)) return backend;
        }
        return null;
    }

    static String favicon(String domain) {
        return "https://www.google.com/s2/favicons?domain=" + domain + "&sz=128";
    }

    static String getJson(String urlText, String apiKey) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(25_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/json");
        if (apiKey != null && !apiKey.isBlank()) connection.setRequestProperty("x-api-key", apiKey);
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        if (stream == null) throw new IllegalStateException("HTTP " + code);
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        } finally {
            connection.disconnect();
        }
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code + ": " + trim(result.toString(), 300));
        }
        return result.toString();
    }

    static Document getDocument(String target) throws Exception {
        Connection request = Jsoup.connect(target)
                .userAgent(USER_AGENT)
                .timeout(25_000)
                .followRedirects(true)
                .maxBodySize(4 * 1024 * 1024);
        String cookie = CookieManager.getInstance().getCookie(target);
        if (cookie != null && !cookie.isBlank()) request.header("Cookie", cookie);
        return request.get();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        String clean = value.replaceAll("\\s+", " ").trim();
        return clean.length() > max ? clean.substring(0, max) + "…" : clean;
    }

    private static final class ModrinthBackend implements Backend {
        public String name() { return "Modrinth"; }
        public String edition() { return "Java"; }
        public String homeUrl() { return "https://modrinth.com/"; }
        public String logoUrl() { return favicon("modrinth.com"); }
        public String tagline() { return "Mods, modpacks, resource packs, shaders and plugins from Modrinth."; }

        public List<ContentItem> search(Context context, String query) throws Exception {
            String endpoint = "https://api.modrinth.com/v2/search?limit=30";
            if (query != null && !query.isBlank()) endpoint += "&query=" + encode(query);
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
                        projectType,
                        item.optString("icon_url", logoUrl()),
                        item.optString("project_id", slug),
                        ""
                ));
            }
            return results;
        }
    }

    private static final class CurseForgeBackend implements Backend {
        public String name() { return "CurseForge"; }
        public String edition() { return "Java + Bedrock"; }
        public String homeUrl() { return "https://www.curseforge.com/minecraft"; }
        public String logoUrl() { return favicon("curseforge.com"); }
        public String tagline() { return "Minecraft mods, modpacks, resource packs, worlds and addons."; }

        public List<ContentItem> search(Context context, String query) throws Exception {
            String key = Prefs.getCurseForgeKey(context);
            List<ContentItem> results = new ArrayList<>();
            if (key.isBlank()) {
                results.add(new ContentItem(
                        name(),
                        "CurseForge API key required",
                        "Add your CurseForge API key in Settings to browse its catalog natively.",
                        homeUrl(),
                        "setup",
                        logoUrl(),
                        "",
                        ""
                ));
                return results;
            }
            String endpoint = "https://api.curseforge.com/v1/mods/search?gameId=432&pageSize=30";
            if (query != null && !query.isBlank()) endpoint += "&searchFilter=" + encode(query);
            JSONObject root = new JSONObject(getJson(endpoint, key));
            JSONArray data = root.optJSONArray("data");
            if (data == null) return results;
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.getJSONObject(i);
                JSONObject links = item.optJSONObject("links");
                JSONObject logo = item.optJSONObject("logo");
                String page = links == null ? homeUrl() : links.optString("websiteUrl", homeUrl());
                String image = logo == null ? logoUrl() : logo.optString("thumbnailUrl", logoUrl());
                results.add(new ContentItem(
                        name(),
                        item.optString("name", "CurseForge project"),
                        item.optString("summary", "CurseForge project"),
                        page,
                        "project",
                        image,
                        String.valueOf(item.optLong("id")),
                        ""
                ));
            }
            return results;
        }
    }


    private static final class McpedlBackend implements Backend {
        private static final String[] CATEGORIES = new String[]{
                "https://mcpedl.com/category/mods/addons/page/1/",
                "https://mcpedl.com/category/maps/page/1/",
                "https://mcpedl.com/category/texture-packs/page/1/",
                "https://mcpedl.com/category/skin-packs/page/1/",
                "https://mcpedl.com/skins/page/1/"
        };
        private static final Set<String> RESERVED = new HashSet<>(Arrays.asList(
                "", "login", "register", "contact", "about", "vip", "privacy", "terms",
                "category", "tag", "author", "user", "wp-admin", "wp-json", "feed"
        ));

        public String name() { return "MCPEDL"; }
        public String edition() { return "Bedrock"; }
        public String homeUrl() { return "https://mcpedl.com/"; }
        public String logoUrl() { return favicon("mcpedl.com"); }
        public String tagline() { return "Bedrock addons, maps, texture packs, shaders and skins."; }

        @Override
        public List<ContentItem> featured(Context context) throws Exception {
            return collect("");
        }

        @Override
        public List<ContentItem> search(Context context, String query) throws Exception {
            return collect(query == null ? "" : query.trim());
        }

        private List<ContentItem> collect(String query) throws Exception {
            String needle = query.toLowerCase(Locale.US);
            List<ContentItem> results = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            Exception lastError = null;

            for (String category : CATEGORIES) {
                if (results.size() >= 30) break;
                try {
                    Document document = getDocument(category);
                    for (Element link : document.select("a[href]")) {
                        if (results.size() >= 30) break;
                        String href = link.absUrl("href");
                        String title = trim(link.text(), 140);
                        if (title.length() < 4 || !isItemUrl(href) || !seen.add(href)) continue;

                        Element container = link.closest(
                                "article, .post, .post-item, .entry, .listing-item, .card, li");
                        if (container == null) container = link.parent();
                        String description = container == null
                                ? "MCPEDL Bedrock content"
                                : trim(container.text(), 260);
                        String haystack = (title + " " + description).toLowerCase(Locale.US);
                        if (!needle.isBlank() && !haystack.contains(needle)) continue;

                        String image = logoUrl();
                        Element imageNode = container == null ? null : container.selectFirst(
                                "img[src], img[data-src], img[data-lazy-src]");
                        if (imageNode != null) {
                            image = firstNonBlank(
                                    imageNode.absUrl("data-src"),
                                    imageNode.absUrl("data-lazy-src"),
                                    imageNode.absUrl("src"),
                                    logoUrl());
                        }
                        results.add(new ContentItem(
                                name(), title, description, href, "Bedrock content",
                                image, "", ""));
                    }
                } catch (Exception error) {
                    lastError = error;
                }
            }

            if (results.isEmpty() && lastError != null) throw lastError;
            return results;
        }

        private boolean isItemUrl(String href) {
            try {
                URL url = new URL(href);
                String host = url.getHost().toLowerCase(Locale.US);
                if (!(host.equals("mcpedl.com") || host.equals("www.mcpedl.com"))) return false;
                String path = url.getPath();
                if (path == null) return false;
                String[] raw = path.split("/");
                List<String> parts = new ArrayList<>();
                for (String part : raw) if (!part.isBlank()) parts.add(part.toLowerCase(Locale.US));
                return parts.size() == 1 && !RESERVED.contains(parts.get(0));
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    private static final class HtmlBackend implements Backend {
        private final String name;
        private final String edition;
        private final String home;
        private final String logo;
        private final String tagline;
        private final String searchTemplate;
        private final String selector;

        HtmlBackend(
                String name,
                String edition,
                String home,
                String logo,
                String tagline,
                String searchTemplate,
                String selector
        ) {
            this.name = name;
            this.edition = edition;
            this.home = home;
            this.logo = logo;
            this.tagline = tagline;
            this.searchTemplate = searchTemplate;
            this.selector = selector;
        }

        public String name() { return name; }
        public String edition() { return edition; }
        public String homeUrl() { return home; }
        public String logoUrl() { return logo; }
        public String tagline() { return tagline; }

        public List<ContentItem> search(Context context, String query) throws Exception {
            String target = query == null || query.isBlank()
                    ? home
                    : String.format(Locale.US, searchTemplate, encode(query));
            Document document = getDocument(target);
            Elements links = document.select(selector);
            List<ContentItem> results = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (Element link : links) {
                if (results.size() >= 30) break;
                String title = link.text().trim();
                String href = link.absUrl("href");
                if (title.length() < 3 || href.isBlank() || !seen.add(href)) continue;
                Element container = link.closest("article, .post, .r-project, .resource, li, div");
                if (container == null) container = link.parent();
                String description = container == null ? edition : trim(container.text(), 230);
                Element imageNode = container == null ? null : container.selectFirst("img");
                String image = imageNode == null ? logo : imageNode.absUrl("src");
                if (image.isBlank() && imageNode != null) image = imageNode.absUrl("data-src");
                results.add(new ContentItem(
                        name,
                        title,
                        description,
                        href,
                        "catalog item",
                        image.isBlank() ? logo : image,
                        "",
                        ""
                ));
            }
            if (results.isEmpty()) {
                results.add(new ContentItem(
                        name,
                        "No catalog items were parsed",
                        "The source returned a page, but its current markup did not expose any items.",
                        target,
                        "source status",
                        logo,
                        "",
                        ""
                ));
            }
            return results;
        }
    }

    private static final class StaticBackend implements Backend {
        private final String name;
        private final String edition;
        private final String home;
        private final String logo;
        private final String tagline;
        private final String[][] entries;

        StaticBackend(
                String name,
                String edition,
                String home,
                String logo,
                String tagline,
                String[][] entries
        ) {
            this.name = name;
            this.edition = edition;
            this.home = home;
            this.logo = logo;
            this.tagline = tagline;
            this.entries = entries;
        }

        public String name() { return name; }
        public String edition() { return edition; }
        public String homeUrl() { return home; }
        public String logoUrl() { return logo; }
        public String tagline() { return tagline; }

        public List<ContentItem> search(Context context, String query) {
            String needle = query == null ? "" : query.trim().toLowerCase(Locale.US);
            List<ContentItem> results = new ArrayList<>();
            for (String[] entry : entries) {
                String haystack = (entry[0] + " " + entry[1] + " " + entry[3]).toLowerCase(Locale.US);
                if (needle.isBlank() || haystack.contains(needle) || name.toLowerCase(Locale.US).contains(needle)) {
                    results.add(new ContentItem(
                            name,
                            entry[0],
                            entry[1],
                            entry[2],
                            entry[3],
                            logo,
                            "",
                            ""
                    ));
                }
            }
            return results;
        }
    }
}
