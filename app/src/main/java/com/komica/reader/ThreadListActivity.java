package com.komica.reader;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.komica.reader.adapter.ThreadAdapter;
import com.komica.reader.model.Board;
import com.komica.reader.model.Thread;
import com.komica.reader.service.KomicaService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private ThreadAdapter adapter;
    private List<Thread> threads = new ArrayList<>();
    private List<Thread> allThreads = new ArrayList<>();
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Board currentBoard;
    private int currentPage = 0;
    private boolean isLoading = false;
    private boolean hasMore = true;
    private EditText searchEditText;
    private Button sortButton;
    private String currentSearchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thread_list);

        currentBoard = (Board) getIntent().getSerializableExtra("board");

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        searchEditText = findViewById(R.id.searchEditText);
        sortButton = findViewById(R.id.sortButton);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ThreadAdapter(threads, thread -> {
            Intent intent = new Intent(ThreadListActivity.this, ThreadDetailActivity.class);
            intent.putExtra("thread", thread);
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);

        setupScrollListener();
        setupSearchListener();
        setupSortListener();

        if (currentBoard != null) {
            loadThreads(0);
        }
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

                    if (!isLoading && hasMore) {
                        if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5) {
                            loadMoreThreads();
                        }
                    }
                }
            }
        });
    }

    private void setupSearchListener() {
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
        String[] sortOptions = {"預設順序", "最新回覆時間", "最新發表"};
        new AlertDialog.Builder(this)
                .setTitle("選擇排序方式")
                .setItems(sortOptions, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            threads.clear();
                            threads.addAll(allThreads);
                            break;
                        case 1:
                            sortThreadsByLastReply();
                            break;
                        case 2:
                            sortThreadsByLatest();
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
        Collections.sort(allThreads, Comparator.comparing(Thread::getId).reversed());
    }

    private void filterThreads() {
        threads.clear();
        if (currentSearchQuery.isEmpty()) {
            threads.addAll(allThreads);
        } else {
            for (Thread thread : allThreads) {
                if (thread.getTitle().toLowerCase().contains(currentSearchQuery)) {
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
        System.out.println("Loading page: " + page + ", current board URL: " + currentBoard.getUrl());
        executor.execute(() -> {
            try {
                KomicaService.FetchThreadsTask task = new KomicaService.FetchThreadsTask(currentBoard.getUrl(), page);
                List<Thread> newThreads = task.call();

                runOnUiThread(() -> {
                    if (newThreads == null || newThreads.isEmpty()) {
                        hasMore = false;
                        System.out.println("Page " + page + " has no more threads");
                        progressBar.setVisibility(View.GONE);
                        isLoading = false;
                    } else {
                        currentPage = page;
                        allThreads.addAll(newThreads);
                        filterThreads();
                        System.out.println("Added " + newThreads.size() + " threads from page " + page);
                        progressBar.setVisibility(View.GONE);
                        isLoading = false;
                    }
                });
            } catch (Exception e) {
                System.out.println("Error loading page " + page + ": " + e.getMessage());
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
