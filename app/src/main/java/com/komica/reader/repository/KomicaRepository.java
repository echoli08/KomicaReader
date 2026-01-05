package com.komica.reader.repository;

import android.content.Context;
import android.util.LruCache;

import com.komica.reader.model.BoardCategory;
import com.komica.reader.model.Thread;
import com.komica.reader.service.KomicaService;
import com.komica.reader.util.KLog;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import okhttp3.Cache;
import okhttp3.CertificatePinner;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class KomicaRepository {
    private static KomicaRepository instance;
    
    /**
     * ExecutorService is kept alive for application lifetime.
     * No need to explicitly shutdown as it's bound to singleton repository
     * and follows the app process lifecycle.
     */
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    
    // Memory Caches
    private volatile List<BoardCategory> boardCategoryCache;
    private final LruCache<String, Thread> threadDetailCache = new LruCache<>(20); // Cache last 20 threads

    private KomicaRepository(Context context) {
        initOkHttp(context);
    }

    public static synchronized KomicaRepository getInstance(Context context) {
        if (instance == null) {
            instance = new KomicaRepository(context.getApplicationContext());
        }
        return instance;
    }

    public void execute(Runnable runnable) {
        executor.execute(runnable);
    }

    public void shutdown() {
        executor.shutdown();
    }

    private void initOkHttp(Context context) {
        // Disk Cache: 10MB
        File cacheDir = new File(context.getCacheDir(), "http_cache");
        Cache cache = new Cache(cacheDir, 10 * 1024 * 1024);

        OkHttpClient client = new OkHttpClient.Builder()
                .cache(cache)
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request request = original.newBuilder()
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                            .header("Accept-Language", "zh-TW,zh;q=0.8,en-US;q=0.5,en;q=0.3")
                            .build();
                    return chain.proceed(request);
                })
                .build();
        
        KomicaService.setClient(client);
    }

    public LiveData<List<BoardCategory>> fetchBoards(boolean forceRefresh) {
        MutableLiveData<List<BoardCategory>> data = new MutableLiveData<>();
        
        if (!forceRefresh) {
            synchronized (this) {
                if (boardCategoryCache != null) {
                    data.setValue(boardCategoryCache);
                    return data;
                }
            }
        }

        executor.execute(() -> {
            try {
                List<BoardCategory> result = new KomicaService.FetchBoardsTask().call();
                if (result != null) {
                    synchronized (this) {
                        boardCategoryCache = result;
                    }
                }
                data.postValue(result != null ? result : new java.util.ArrayList<>());
            } catch (Exception e) {
                data.postValue(new java.util.ArrayList<>());
            }
        });
        return data;
    }

    public LiveData<List<Thread>> fetchThreads(String boardUrl, int page) {
        MutableLiveData<List<Thread>> data = new MutableLiveData<>();
        executor.execute(() -> {
            try {
                List<Thread> result = new KomicaService.FetchThreadsTask(boardUrl, page).call();
                data.postValue(result != null ? result : new java.util.ArrayList<>());
            } catch (Exception e) {
                KLog.e("Error fetching threads: " + e.getMessage());
                data.postValue(new java.util.ArrayList<>());
            }
        });
        return data;
    }

    public LiveData<Thread> fetchThreadDetail(String threadUrl, boolean forceRefresh) {
        MutableLiveData<Thread> data = new MutableLiveData<>();
        
        if (!forceRefresh) {
            Thread cached = threadDetailCache.get(threadUrl);
            if (cached != null) {
                data.setValue(cached);
                return data;
            }
        }

        executor.execute(() -> {
            try {
                Thread result = new KomicaService.FetchThreadDetailTask(threadUrl).call();
                if (result != null) {
                    threadDetailCache.put(threadUrl, result);
                }
                data.postValue(result);
            } catch (Exception e) {
                data.postValue(null);
            }
        });
        return data;
    }

    public LiveData<List<Thread>> searchThreads(String boardUrl, String query) {
        MutableLiveData<List<Thread>> data = new MutableLiveData<>();
        executor.execute(() -> {
            try {
                List<Thread> result = new KomicaService.BoardSearchTask(boardUrl, query).call();
                data.postValue(result);
            } catch (Exception e) {
                data.postValue(null);
            }
        });
        return data;
    }
}
