package dev.alastorkaneki.minecraftaio;

import android.app.Application;
import com.google.android.material.color.DynamicColors;

public final class MinecraftAioApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
