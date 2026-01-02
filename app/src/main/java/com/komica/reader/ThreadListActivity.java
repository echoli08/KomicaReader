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

import com.komica.reader.viewmodel.ThreadListViewModel;
import com.komica.reader.viewmodel.ThreadListViewModelFactory;
import androidx.lifecycle.ViewModelProvider;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class ThreadListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ThreadAdapter adapter;
    private List<Thread> threads = new ArrayList<>();
    private ThreadListViewModel viewModel;
    private Board currentBoard;
    private EditText searchEditText;
    private Button sortButton;
    private Button filterButton;
    private ImageButton favoriteButton;
    private FavoritesManager favoritesManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thread_list);

        favoritesManager = FavoritesManager.getInstance(this);
        currentBoard = (Board) getIntent().getSerializableExtra("board");
        
        if (currentBoard == null) {
            finish();
            return;
        }

        ThreadListViewModelFactory factory = new ThreadListViewModelFactory(getApplication(), currentBoard);
        viewModel = new ViewModelProvider(this, factory).get(ThreadListViewModel.class);
        
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        TextView toolbarTitle = findViewById(R.id.toolbarTitle);
        toolbarTitle.setText(currentBoard.getName());

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        searchEditText = findViewById(R.id.searchEditText);
        sortButton = findViewById(R.id.sortButton);
        filterButton = findViewById(R.id.filterButton);
        favoriteButton = findViewById(R.id.favoriteButton);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        swipeRefreshLayout.setOnRefreshListener(() -> {
            viewModel.refresh();
        });

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

        updateFavoriteIcon();
        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.getThreads().observe(this, newThreads -> {
            adapter.updateThreads(newThreads);
            swipeRefreshLayout.setRefreshing(false);
        });

        viewModel.getIsLoading().observe(this, isLoading -> {
            if (!swipeRefreshLayout.isRefreshing()) {
                progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            }
        });
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
            favoritesManager.toggleFavorite(currentBoard.getUrl());
            updateFavoriteIcon();
        });
    }

    private void updateFavoriteIcon() {
        boolean isFavorite = favoritesManager.isFavorite(currentBoard.getUrl());
        favoriteButton.setImageResource(isFavorite ? R.drawable.ic_star : R.drawable.ic_star_border);
    }

    private void setupFilterButton() {
        filterButton.setOnClickListener(v -> {
            String query = searchEditText.getText().toString().trim();
            if (!query.isEmpty()) {
                viewModel.setSearchQuery(query);
                viewModel.performRemoteSearch();
            } else {
                viewModel.clearSearch();
            }
            hideKeyboard(v);
        });
    }

    private void hideKeyboard(View v) {
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }

    private void setupScrollListener() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0 && layoutManager != null) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5) {
                        viewModel.loadMore();
                    }
                }
            }
        });
    }

    private void setupSearchListener() {
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || 
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                String query = searchEditText.getText().toString().trim();
                if (!query.isEmpty()) {
                    viewModel.setSearchQuery(query);
                    viewModel.performRemoteSearch();
                } else {
                    viewModel.clearSearch();
                }
                hideKeyboard(v);
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
                String query = s.toString().trim();
                viewModel.setSearchQuery(query);
                if (query.isEmpty()) {
                    viewModel.clearSearch();
                }
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
                    viewModel.setSortMode(which);
                })
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
