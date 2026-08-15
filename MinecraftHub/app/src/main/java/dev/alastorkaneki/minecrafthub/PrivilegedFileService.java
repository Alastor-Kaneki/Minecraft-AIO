package dev.alastorkaneki.minecrafthub;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Runs inside a Shizuku UserService process as ADB shell or root identity. */
public class PrivilegedFileService extends IPrivilegedFileService.Stub {
    private volatile String error = "";

    public PrivilegedFileService() {}
    public PrivilegedFileService(Context context) {}

    @Override
    public boolean canRead(String path) {
        try {
            File f = new File(path);
            boolean ok = f.exists() && f.canRead();
            if (!ok) error = "Cannot read: " + path;
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
            if (files == null) {
                error = "Directory is unreadable or empty: " + path;
                return new String[0];
            }
            Arrays.sort(files, Comparator.comparing((File f) -> !f.isDirectory())
                    .thenComparing(f -> f.getName().toLowerCase()));
            List<String> out = new ArrayList<>();
            for (File f : files) {
                out.add((f.isDirectory() ? "D" : "F") + "|" + esc(f.getName()) + "|" +
                        esc(f.getAbsolutePath()) + "|" + f.length() + "|" + f.lastModified());
            }
            error = "";
            return out.toArray(new String[0]);
        } catch (Throwable t) {
            error = t.toString();
            return new String[0];
        }
    }

    @Override
    public boolean copyFile(String sourcePath, String destinationPath) {
        try {
            File src = new File(sourcePath);
            File dst = new File(destinationPath);
            if (!src.isFile()) throw new IOException("Source is not a file: " + sourcePath);
            File parent = dst.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Could not create: " + parent);
            }
            try (FileInputStream in = new FileInputStream(src);
                 FileOutputStream out = new FileOutputStream(dst)) {
                byte[] buffer = new byte[256 * 1024];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                out.flush();
            }
            dst.setReadable(true, false);
            error = "";
            return true;
        } catch (Throwable t) {
            error = t.toString();
            return false;
        }
    }

    @Override
    public boolean deletePath(String path) {
        try {
            boolean ok = deleteRecursive(new File(path));
            if (!ok) error = "Could not delete: " + path;
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
            boolean ok = f.exists() || f.mkdirs();
            if (!ok) error = "Could not create: " + path;
            return ok;
        } catch (Throwable t) {
            error = t.toString();
            return false;
        }
    }

    @Override
    public String lastError() { return error; }

    private static String esc(String s) {
        return s.replace("%", "%25").replace("|", "%7C").replace("\n", "%0A");
    }

    public void destroy() {
        System.exit(0);
    }
}
