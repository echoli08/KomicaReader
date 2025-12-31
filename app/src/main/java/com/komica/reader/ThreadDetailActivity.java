package com.komica.reader;

import android.content.Intent;
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
    
    private View previewCard;
    private TextView previewTitle;
    private TextView previewContent;
    
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
        
        previewCard = findViewById(R.id.previewCard);
        previewTitle = findViewById(R.id.previewTitle);
        previewContent = findViewById(R.id.previewContent);

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
        progressBar.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            try {
                KomicaService.FetchThreadDetailTask task = new KomicaService.FetchThreadDetailTask(thread.getUrl());
                Thread result = task.call();

                runOnUiThread(() -> {
                    List<Post> posts = result.getPosts();
                    
                    adapter = new PostAdapter(posts, new PostAdapter.OnQuoteInteractionListener() {
                        @Override
                        public void onImageClick(int imageIndex, List<String> imageUrls) {
                            Intent intent = new Intent(ThreadDetailActivity.this, ImagePreviewActivity.class);
                            intent.putStringArrayListExtra("imageUrls", new ArrayList<>(imageUrls));
                            intent.putExtra("position", imageIndex);
                            startActivity(intent);
                        }

                        @Override
                        public void onQuoteClick(int position) {
                            recyclerView.smoothScrollToPosition(position);
                        }

                        @Override
                        public void onQuoteLongClick(Post post) {
                            if (post != null) {
                                previewTitle.setText("No. " + post.getNumber() + " " + (post.getAuthor() != null ? post.getAuthor() : ""));
                                previewContent.setText(post.getContent());
                                previewCard.setVisibility(View.VISIBLE);
                            }
                        }

                        @Override
                        public void onQuoteReleased() {
                            previewCard.setVisibility(View.GONE);
                        }
                    });
                    
                    recyclerView.setAdapter(adapter);
                    progressBar.setVisibility(View.GONE);
                });
            } catch (Exception e) {
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