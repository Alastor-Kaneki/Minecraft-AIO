package dev.alastorkaneki.minecraftaio;

final class ContentItem {
    final String source;
    final String title;
    final String description;
    final String url;
    final String type;
    final String imageUrl;
    final String backendId;
    final String directDownloadUrl;

    ContentItem(String source, String title, String description, String url, String type) {
        this(source, title, description, url, type, "", "", "");
    }

    ContentItem(
            String source,
            String title,
            String description,
            String url,
            String type,
            String imageUrl,
            String backendId,
            String directDownloadUrl
    ) {
        this.source = safe(source);
        this.title = safe(title);
        this.description = safe(description);
        this.url = safe(url);
        this.type = safe(type);
        this.imageUrl = safe(imageUrl);
        this.backendId = safe(backendId);
        this.directDownloadUrl = safe(directDownloadUrl);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
