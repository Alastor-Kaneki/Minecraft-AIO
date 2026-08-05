package dev.alastorkaneki.minecraftaio;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class RemoteImage {
    private static final String USER_AGENT =
            "Minecraft-AIO/0.1.1-alpha (Android; github.com/Alastor-Kaneki/Minecraft-AIO)";
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Bitmap> CACHE = new ConcurrentHashMap<>();

    private RemoteImage() {}

    static void load(ImageView view, String url) {
        if (url == null || url.isBlank()) {
            view.setImageResource(android.R.drawable.sym_def_app_icon);
            return;
        }

        view.setTag(url);
        Bitmap cached = CACHE.get(url);
        if (cached != null && !cached.isRecycled()) {
            view.setImageBitmap(cached);
            return;
        }

        view.setImageResource(android.R.drawable.sym_def_app_icon);
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(12_000);
                connection.setReadTimeout(18_000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", USER_AGENT);
                connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
                try (InputStream input = connection.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    if (bitmap == null) return;
                    CACHE.put(url, bitmap);
                    MAIN.post(() -> {
                        Object tag = view.getTag();
                        if (url.equals(tag)) view.setImageBitmap(bitmap);
                    });
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }
}
