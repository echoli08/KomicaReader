package com.komica.reader;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.komica.reader.adapter.ThreadAdapter;
import com.komica.reader.model.Board;
import com.komica.reader.model.Thread;
import com.komica.reader.service.KomicaService;
import com.komica.reader.util.FavoritesManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.appcompat.widget.Toolbar;
import android.widget.TextView;

public class ThreadListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private ThreadAdapter adapter;
    private List<Thread> threads = new ArrayList<>();
    private List<Thread> allThreads = new ArrayList<>();
    private Set<String> existingThreadUrls = new HashSet<>();
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Board currentBoard;
    private int currentPage = 0;
    private boolean isLoading = false;
    private boolean hasMore = true;
    private EditText searchEditText;
    private Button sortButton;
    private Button filterButton;
    private ImageButton favoriteButton;
    private String currentSearchQuery = "";
    private FavoritesManager favoritesManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thread_list);

        favoritesManager = FavoritesManager.getInstance(this);

        currentBoard = (Board) getIntent().getSerializableExtra("board");
        
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        TextView toolbarTitle = findViewById(R.id.toolbarTitle);
        if (currentBoard != null) {
            toolbarTitle.setText(currentBoard.getName());
        }

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        searchEditText = findViewById(R.id.searchEditText);
        sortButton = findViewById(R.id.sortButton);
        filterButton = findViewById(R.id.filterButton);
        favoriteButton = findViewById(R.id.favoriteButton);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ThreadAdapter(threads, new ThreadAdapter.OnThreadClickListener() {
            @Override
            public void onThreadClick(Thread thread) {
                Intent intent = new Intent(ThreadListActivity.this, ThreadDetailActivity.class);
                intent.putExtra("thread", thread);
                startActivity(intent);
            }

            @Override
            public void onShareClick(Thread thread) {
                shareThread(thread);
            }
        });
        recyclerView.setAdapter(adapter);

        setupScrollListener();
        setupSearchListener();
        setupSortListener();
        setupFilterButton();
        setupFavoriteListener();

        if (currentBoard != null) {
            updateFavoriteIcon();
            loadThreads(0);
        }
    }

    private void shareThread(Thread thread) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        
        String shareText = thread.getTitle() + "\n" + thread.getUrl();
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        
        startActivity(Intent.createChooser(shareIntent, "分享討論串"));
    }

    private void setupFavoriteListener() {
        favoriteButton.setOnClickListener(v -> {
            if (currentBoard != null) {
                favoritesManager.toggleFavorite(currentBoard.getUrl());
                updateFavoriteIcon();
            }
        });
    }

    private void updateFavoriteIcon() {
        if (currentBoard != null) {
            boolean isFavorite = favoritesManager.isFavorite(currentBoard.getUrl());
            if (isFavorite) {
                favoriteButton.setImageResource(R.drawable.ic_star);
            } else {
                favoriteButton.setImageResource(R.drawable.ic_star_border);
            }
        }
    }

    private void setupFilterButton() {
        filterButton.setOnClickListener(v -> {
            filterThreads();
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        });
    }

    private void setupScrollListener() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                    if (!isLoading && hasMore && currentSearchQuery.isEmpty()) { // Only load more if not filtering
                        if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5) {
                            loadMoreThreads();
                        }
                    }
                }
            }
        });
    }

    private void setupSearchListener() {
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                filterThreads();
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                return true;
            }
            return false;
        });

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                currentSearchQuery = s.toString().trim().toLowerCase();
                filterThreads();
            }
        });
    }

    private void setupSortListener() {
        sortButton.setOnClickListener(v -> showSortDialog());
    }

    private void showSortDialog() {
        String[] sortOptions = {"最新發表", "最新回覆時間"};
        new AlertDialog.Builder(this)
                .setTitle("選擇排序方式")
                .setItems(sortOptions, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            sortThreadsByLatest();
                            break;
                        case 1:
                            sortThreadsByLastReply();
                            break;
                    }
                    filterThreads();
                })
                .show();
    }

    private void sortThreadsByLastReply() {
        Collections.sort(allThreads, (t1, t2) -> {
            String time1 = t1.getLastReplyTime();
            String time2 = t2.getLastReplyTime();
            if (time1 == null || time1.isEmpty()) return 1;
            if (time2 == null || time2.isEmpty()) return -1;
            return time2.compareTo(time1);
        });
    }

    private void sortThreadsByLatest() {
        Collections.sort(allThreads, Comparator.comparing(Thread::getPostNumber).reversed());
    }

    private void filterThreads() {
        threads.clear();
        if (currentSearchQuery.isEmpty()) {
            threads.addAll(allThreads);
        } else {
            for (Thread thread : allThreads) {
                if (thread.getTitle().toLowerCase().contains(currentSearchQuery) || 
                    thread.getContentPreview().toLowerCase().contains(currentSearchQuery)) {
                    threads.add(thread);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void loadThreads(int page) {
        if (isLoading) return;

        isLoading = true;
        progressBar.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            try {
                KomicaService.FetchThreadsTask task = new KomicaService.FetchThreadsTask(currentBoard.getUrl(), page);
                List<Thread> newThreads = task.call();

                runOnUiThread(() -> {
                    if (newThreads == null || newThreads.isEmpty()) {
                        hasMore = false;
                        progressBar.setVisibility(View.GONE);
                        isLoading = false;
                    } else {
                        List<Thread> uniqueThreads = new ArrayList<>();
                        for (Thread thread : newThreads) {
                            if (!existingThreadUrls.contains(thread.getUrl())) {
                                existingThreadUrls.add(thread.getUrl());
                                uniqueThreads.add(thread);
                            }
                        }

                        if (uniqueThreads.isEmpty()) {
                            currentPage = page;
                            progressBar.setVisibility(View.GONE);
                            isLoading = false;
                            // Only retry loading next page if we haven't failed too many times consecutively
                            // For simplicity, just stop or try one more time
                            if (page < 10) { // Limit recursion
                                recyclerView.postDelayed(() -> loadMoreThreads(), 500);
                            }
                        } else {
                            currentPage = page;
                            allThreads.addAll(uniqueThreads);
                            sortThreadsByLatest();
                            filterThreads();
                            progressBar.setVisibility(View.GONE);
                            isLoading = false;
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    isLoading = false;
                });
            }
        });
    }

    private void loadMoreThreads() {
        currentPage++;
        loadThreads(currentPage);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
