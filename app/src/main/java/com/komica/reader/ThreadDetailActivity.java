package com.komica.reader;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.komica.reader.adapter.PostAdapter;
import com.komica.reader.model.Post;
import com.komica.reader.model.Thread;
import com.komica.reader.service.KomicaService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadDetailActivity extends AppCompatActivity {

    private TextView threadTitle;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private Button scrollToBottomButton;
    private Button shareButton;
    private PostAdapter adapter;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Thread currentThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thread_detail);

        Thread thread = (Thread) getIntent().getSerializableExtra("thread");
        currentThread = thread;

        threadTitle = findViewById(R.id.threadTitle);
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        scrollToBottomButton = findViewById(R.id.scrollToBottomButton);
        shareButton = findViewById(R.id.shareButton);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        scrollToBottomButton.setOnClickListener(v -> scrollToBottom());
        shareButton.setOnClickListener(v -> shareThread());

        if (thread != null) {
            threadTitle.setText(thread.getTitle());
            loadThreadDetail(thread);
        }
    }

    private void scrollToBottom() {
        if (adapter != null) {
            int itemCount = adapter.getItemCount();
            if (itemCount > 0) {
                recyclerView.smoothScrollToPosition(itemCount - 1);
            }
        }
    }

    private void shareThread() {
        if (currentThread != null) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            
            String shareText = currentThread.getTitle() + "\n\n" + resolveFullUrl(currentThread.getUrl());
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            
            Intent chooserIntent = Intent.createChooser(shareIntent, "分享討論串");
            startActivity(chooserIntent);
        }
    }

    private String resolveFullUrl(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return "https://gaia.komica1.org/79/" + url;
    }

    private void loadThreadDetail(Thread thread) {
        System.out.println("=== Loading thread detail ===");
        System.out.println("Thread URL: " + thread.getUrl());
        System.out.println("Thread title: " + thread.getTitle());
        progressBar.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            try {
                KomicaService.FetchThreadDetailTask task = new KomicaService.FetchThreadDetailTask(thread.getUrl());
                Thread result = task.call();

                runOnUiThread(() -> {
                    List<Post> posts = result.getPosts();
                    System.out.println("Posts loaded in activity: " + posts.size());
                    System.out.println("Result thread title: " + result.getTitle());
                    
                    if (posts != null && !posts.isEmpty()) {
                        System.out.println("First post: " + posts.get(0).getAuthor() + " - " + posts.get(0).getContent());
                    }
                    
                    adapter = new PostAdapter(posts, (position, imageUrls) -> {
                        Intent intent = new Intent(ThreadDetailActivity.this, ImagePreviewActivity.class);
                        intent.putStringArrayListExtra("imageUrls", new ArrayList<>(imageUrls));
                        intent.putExtra("position", position);
                        startActivity(intent);
                    });
                    recyclerView.setAdapter(adapter);
                    progressBar.setVisibility(View.GONE);
                    System.out.println("=== Thread detail loading completed successfully ===");
                });
            } catch (Exception e) {
                System.out.println("Error loading thread detail: " + e.getMessage());
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    android.widget.Toast.makeText(ThreadDetailActivity.this, 
                        "載入失敗: " + e.getMessage(), 
                        android.widget.Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
