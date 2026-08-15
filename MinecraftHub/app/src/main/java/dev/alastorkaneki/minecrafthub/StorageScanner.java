package dev.alastorkaneki.minecrafthub;

import android.os.Environment;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class StorageScanner {
    private StorageScanner() {}

    public static final class PackFile {
        public final File file;
        public final String type;
        PackFile(File file, String type) { this.file = file; this.type = type; }
    }

    public interface Progress { void onProgress(int directories, int found); }

    public static List<PackFile> scan(Progress progress) {
        File root = Environment.getExternalStorageDirectory();
        ArrayDeque<File> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        List<PackFile> found = new ArrayList<>();
        queue.add(root);
        int dirs = 0;

        while (!queue.isEmpty()) {
            File dir = queue.removeFirst();
            String path;
            try { path = dir.getCanonicalPath(); } catch (Exception e) { continue; }
            if (!visited.add(path) || shouldSkip(dir, root)) continue;
            dirs++;
            File[] children;
            try { children = dir.listFiles(); } catch (SecurityException e) { continue; }
            if (children == null) continue;
            for (File child : children) {
                if (child.isDirectory()) {
                    queue.addLast(child);
                } else {
                    String n = child.getName().toLowerCase(Locale.ROOT);
                    if (n.endsWith(".mcpack")) found.add(new PackFile(child, "MCPACK"));
                    else if (n.endsWith(".mcaddon")) found.add(new PackFile(child, "MCADDON"));
                }
            }
            if (progress != null && dirs % 40 == 0) progress.onProgress(dirs, found.size());
        }

        found.sort(Comparator.comparingLong((PackFile p) -> p.file.lastModified()).reversed());
        if (progress != null) progress.onProgress(dirs, found.size());
        return found;
    }

    private static boolean shouldSkip(File dir, File root) {
        if (dir.equals(root)) return false;
        String p = dir.getAbsolutePath().replace('\\', '/');
        String n = dir.getName().toLowerCase(Locale.ROOT);
        if (n.equals(".thumbnails") || n.equals("cache") || n.equals("code_cache")) return true;
        String android = new File(root, "Android").getAbsolutePath().replace('\\', '/');
        if (p.startsWith(android + "/data") || p.startsWith(android + "/obb")) return true;
        return false;
    }
}
