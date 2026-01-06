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

import androidx.appcompat.widget.Toolbar;

import com.komica.reader.viewmodel.ThreadDetailViewModel;
import com.komica.reader.viewmodel.ThreadDetailViewModelFactory;
import com.komica.reader.util.KLog;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class ThreadDetailActivity extends AppCompatActivity {

    private TextView threadTitle;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private FloatingActionButton fabScrollDown;
    private FloatingActionButton fabShare;
    private FloatingActionButton fabReply;
    
    private View previewCard;
    private TextView previewTitle;
    private TextView previewContent;
    
    private PostAdapter adapter;
    private ThreadDetailViewModel viewModel;
    private Thread currentThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thread_detail);

        if (getIntent().hasExtra("thread")) {
            currentThread = (Thread) getIntent().getSerializableExtra("thread");
        } else if (getIntent().hasExtra("thread_url")) {
            String url = getIntent().getStringExtra("thread_url");
            String title = getIntent().getStringExtra("thread_title");
            // Create a lightweight thread object
            currentThread = new Thread(
                String.valueOf(System.currentTimeMillis()), // temp id
                title != null ? title : "Loading...",
                "",
                0,
                url
            );
        }

        if (currentThread == null) {
            finish();
            return;
        }

        ThreadDetailViewModelFactory factory = new ThreadDetailViewModelFactory(getApplication(), currentThread);
        viewModel = new ViewModelProvider(this, factory).get(ThreadDetailViewModel.class);
        
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        threadTitle = findViewById(R.id.threadTitle);
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        fabScrollDown = findViewById(R.id.fabScrollDown);
        fabShare = findViewById(R.id.fabShare);
        fabReply = findViewById(R.id.fabReply);
        
        previewCard = findViewById(R.id.previewCard);
        previewTitle = findViewById(R.id.previewTitle);
        previewContent = findViewById(R.id.previewContent);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        fabScrollDown.setOnClickListener(v -> scrollToBottom());
        fabShare.setOnClickListener(v -> shareThread());
        fabReply.setOnClickListener(v -> showReplyDialog());

        threadTitle.setText(currentThread.getTitle());
        
        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.getThreadDetail().observe(this, thread -> {
            if (thread != null) {
                updateUI(thread);
            }
        });

        viewModel.getIsLoading().observe(this, isLoading -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                android.widget.Toast.makeText(this, error, android.widget.Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getReplyStatus().observe(this, success -> {
            if (success != null) {
                if (success) {
                    android.widget.Toast.makeText(this, "回覆成功", android.widget.Toast.LENGTH_SHORT).show();
                    scrollToBottom();
                } else {
                    android.widget.Toast.makeText(this, "回覆失敗", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showReplyDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_reply, null);
        
        com.google.android.material.textfield.TextInputEditText editContent = view.findViewById(R.id.editContent);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnSend = view.findViewById(R.id.btnSend);
        
        builder.setView(view);
        android.app.AlertDialog dialog = builder.create();
        
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        btnSend.setOnClickListener(v -> {
            String content = editContent.getText() != null ? editContent.getText().toString() : "";
            if (content.trim().isEmpty()) {
                editContent.setError("內容不能為空");
                return;
            }
            
            viewModel.sendReply(content);
            dialog.dismiss();
        });
        
        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void updateUI(Thread thread) {
        List<Post> posts = thread.getPosts();
        adapter = new PostAdapter(posts, new PostAdapter.OnQuoteInteractionListener() {
            @Override
            public void onImageClick(int imageIndex, List<String> imageUrls) {
                Intent intent = new Intent(ThreadDetailActivity.this, ImagePreviewActivity.class);
                intent.putStringArrayListExtra("imageUrls", new ArrayList<>(imageUrls));
                intent.putExtra("position", imageIndex);
                startActivity(intent);
            }

            @Override
            public void onImageLongClick(String imageUrl) {
                shareImage(imageUrl);
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
            
            String shareText = currentThread.getTitle() + "\n\n" + KomicaService.resolveUrl(currentThread.getUrl(), "");
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            
            Intent chooserIntent = Intent.createChooser(shareIntent, "分享討論串");
            startActivity(chooserIntent);
        }
    }

    private void shareImage(String imageUrl) {
        com.komica.reader.repository.KomicaRepository.getInstance(this).execute(() -> {
            try {
                java.io.File file = com.bumptech.glide.Glide.with(this)
                    .asFile()
                    .load(imageUrl)
                    .submit()
                    .get();
                    
                android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(this, 
                    getApplicationContext().getPackageName() + ".fileprovider", file);
                    
                runOnUiThread(() -> {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("image/*");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(shareIntent, "分享圖片"));
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> android.widget.Toast.makeText(this, "分享圖片失敗", android.widget.Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.thread_detail_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_gallery) {
            if (currentThread != null) {
                Intent intent = new Intent(this, GalleryActivity.class);
                intent.putExtra("thread_url", currentThread.getUrl());
                intent.putExtra("thread_title", currentThread.getTitle());
                startActivity(intent);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}