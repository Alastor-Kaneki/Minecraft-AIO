package dev.alastorkaneki.minecrafthub;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Runs inside a Shizuku UserService process as ADB shell (UID 2000) or root (UID 0). */
public class PrivilegedFileService extends IPrivilegedFileService.Stub {
    private volatile String error = "";

    public PrivilegedFileService() {}
    public PrivilegedFileService(Context context) {}

    @Override
    public boolean canRead(String path) {
        try {
            File f = new File(path);
            boolean ok = f.exists() && f.canRead();
            if (!ok) ok = commandOk("/system/bin/ls", "-ld", path);
            error = ok ? "" : "ADB-shell/root identity cannot read: " + path;
            return ok;
        } catch (Throwable t) {
            error = t.toString();
            return false;
        }
    }

    @Override
    public String[] list(String path) {
        try {
            File dir = new File(path);
            File[] files = dir.listFiles();
            if (files != null) return encodeFiles(files);

            // OEM/FUSE fallback: enumerate with Android's shell utility under the UserService UID.
            List<String> names = commandLines("/system/bin/ls", "-A1", path);
            if (names == null) return new String[0];
            List<String> out = new ArrayList<>();
            for (String name : names) {
                if (name.isEmpty()) continue;
                File f = new File(dir, name);
                String full = f.getAbsolutePath();
                boolean isDir = f.isDirectory() || commandOk("/system/bin/toybox", "test", "-d", full);
                long size = isDir ? 0 : f.length();
                out.add((isDir ? "D" : "F") + "|" + esc(name) + "|" + esc(full) + "|" + size + "|" + f.lastModified());
            }
            out.sort(String.CASE_INSENSITIVE_ORDER);
            error = "";
            return out.toArray(new String[0]);
        } catch (Throwable t) {
            error = t.toString();
            return new String[0];
        }
    }

    private String[] encodeFiles(File[] files) {
        Arrays.sort(files, Comparator.comparing((File f) -> !f.isDirectory())
                .thenComparing(f -> f.getName().toLowerCase(Locale.ROOT)));
        List<String> out = new ArrayList<>();
        for (File f : files) {
            out.add((f.isDirectory() ? "D" : "F") + "|" + esc(f.getName()) + "|" +
                    esc(f.getAbsolutePath()) + "|" + f.length() + "|" + f.lastModified());
        }
        error = "";
        return out.toArray(new String[0]);
    }

    @Override
    public boolean copyFile(String sourcePath, String destinationPath) {
        try {
            File src = new File(sourcePath);
            File dst = new File(destinationPath);
            File parent = dst.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                commandOk("/system/bin/mkdir", "-p", parent.getAbsolutePath());
            }

            if (src.isFile()) {
                try (FileInputStream in = new FileInputStream(src);
                     FileOutputStream out = new FileOutputStream(dst)) {
                    byte[] buffer = new byte[256 * 1024];
                    int n;
                    while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                    out.flush();
                }
            } else if (!commandOk("/system/bin/cp", "-f", sourcePath, destinationPath)) {
                throw new IOException("Source is not readable as a file: " + sourcePath);
            }
            dst.setReadable(true, false);
            error = "";
            return true;
        } catch (Throwable t) {
            try {
                boolean ok = commandOk("/system/bin/cp", "-f", sourcePath, destinationPath);
                if (ok) error = "";
                else error = t.toString();
                return ok;
            } catch (Throwable ignored) {
                error = t.toString();
                return false;
            }
        }
    }

    @Override
    public boolean deletePath(String path) {
        try {
            boolean ok = deleteRecursive(new File(path));
            if (!ok) ok = commandOk("/system/bin/rm", "-rf", path);
            if (!ok) error = "Could not delete: " + path;
            else error = "";
            return ok;
        } catch (Throwable t) {
            error = t.toString();
            return false;
        }
    }

    private boolean deleteRecursive(File f) {
        if (!f.exists()) return true;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File child : children) if (!deleteRecursive(child)) return false;
        }
        return f.delete();
    }

    @Override
    public boolean makeDirectories(String path) {
        try {
            File f = new File(path);
            boolean ok = f.exists() || f.mkdirs() || commandOk("/system/bin/mkdir", "-p", path);
            if (!ok) error = "Could not create: " + path;
            else error = "";
            return ok;
        } catch (Throwable t) {
            error = t.toString();
            return false;
        }
    }

    @Override
    public String lastError() { return error; }

    private static boolean commandOk(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            while (reader.readLine() != null) { /* drain */ }
        }
        return process.waitFor() == 0;
    }

    private List<String> commandLines(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) lines.add(line);
        }
        int code = process.waitFor();
        if (code != 0) {
            error = lines.isEmpty() ? "Command failed with exit " + code : String.join("; ", lines);
            return null;
        }
        return lines;
    }

    private static String esc(String s) {
        return s.replace("%", "%25").replace("|", "%7C").replace("\n", "%0A");
    }

    public void destroy() {
        System.exit(0);
    }
}
