package dev.alastorkaneki.minecraftaio;

final class ContentItem {
    final String source;
    final String title;
    final String description;
    final String url;
    final String type;

    ContentItem(String source, String title, String description, String url, String type) {
        this.source = source;
        this.title = title;
        this.description = description;
        this.url = url;
        this.type = type;
    }
}
