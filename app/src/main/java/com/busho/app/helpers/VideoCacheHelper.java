package com.busho.app.helpers;

import android.content.Context;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import java.io.File;

public class VideoCacheHelper {
    private static Cache cache;

    public static Cache getCache(Context context) {
        if (cache == null) {
            // 100MB cache size
            long maxCacheSize = 100 * 1024 * 1024; // 100 MB
            File cacheDir = new File(context.getCacheDir(), "exoplayer_cache");
            LeastRecentlyUsedCacheEvictor evictor = new LeastRecentlyUsedCacheEvictor(maxCacheSize);
            cache = new SimpleCache(cacheDir, evictor);
        }
        return cache;
    }
}