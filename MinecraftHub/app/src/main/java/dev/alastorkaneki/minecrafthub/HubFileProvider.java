package dev.alastorkaneki.minecrafthub;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Minimal read-only provider so discovered packs can be handed to Minecraft without AndroidX. */
public class HubFileProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    public static Uri uriFor(File file) {
        return new Uri.Builder()
                .scheme("content")
                .authority("dev.alastorkaneki.minecrafthub.files")
                .appendPath("open")
                .appendQueryParameter("path", file.getAbsolutePath())
                .build();
    }

    @Override
    public String getType(Uri uri) {
        String path = uri.getQueryParameter("path");
        if (path == null) return "application/octet-stream";
        String p = path.toLowerCase();
        if (p.endsWith(".mcpack")) return "application/zip";
        if (p.endsWith(".mcaddon")) return "application/zip";
        return "application/octet-stream";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only provider");
        String path = uri.getQueryParameter("path");
        if (path == null) throw new FileNotFoundException("Missing path");
        File file = new File(path);
        if (!file.isFile()) throw new FileNotFoundException(path);
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        String path = uri.getQueryParameter("path");
        File f = path == null ? null : new File(path);
        String[] cols = projection != null ? projection : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(cols, 1);
        MatrixCursor.RowBuilder row = c.newRow();
        for (String col : cols) {
            if (OpenableColumns.DISPLAY_NAME.equals(col)) row.add(f == null ? "file" : f.getName());
            else if (OpenableColumns.SIZE.equals(col)) row.add(f == null ? 0 : f.length());
            else row.add(null);
        }
        return c;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
