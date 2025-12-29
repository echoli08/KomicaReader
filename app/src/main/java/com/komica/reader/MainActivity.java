package com.komica.reader;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.komica.reader.adapter.ThreadAdapter;
import com.komica.reader.adapter.BoardCategoryAdapter;
import com.komica.reader.model.Board;
import com.komica.reader.model.BoardCategory;
import com.komica.reader.model.Thread;
import com.komica.reader.service.KomicaService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private BoardCategoryAdapter categoryAdapter;
    private ThreadAdapter searchAdapter;
    private List<BoardCategory> categories = new ArrayList<>();
    private List<Thread> searchResults = new ArrayList<>();
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private EditText searchEditText;
    private Button searchButton;
    private boolean isSearchMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        searchEditText = findViewById(R.id.searchEditText);
        searchButton = findViewById(R.id.searchButton);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        categoryAdapter = new BoardCategoryAdapter(categories, board -> {
            Intent intent = new Intent(MainActivity.this, ThreadListActivity.class);
            intent.putExtra("board", board);
            startActivity(intent);
        });

        searchAdapter = new ThreadAdapter(searchResults, thread -> {
            Intent intent = new Intent(MainActivity.this, ThreadDetailActivity.class);
            intent.putExtra("thread", thread);
            startActivity(intent);
        });

        recyclerView.setAdapter(categoryAdapter);

        setupSearchListeners();
        loadBoards();
    }

    private void setupSearchListeners() {
        searchButton.setOnClickListener(v -> performSearch());

        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || 
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                performSearch();
                return true;
            }
            return false;
        });
    }

    private void performSearch() {
        String query = searchEditText.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, R.string.enter_keyword, Toast.LENGTH_SHORT).show();
            return;
        }

        showBoardSelectionDialog(query);
    }

    private void showBoardSelectionDialog(String query) {
        List<String> boardNames = new ArrayList<>();
        for (BoardCategory category : categories) {
            for (Board board : category.getBoards()) {
                boardNames.add(board.getName());
            }
        }

        String[] boardArray = boardNames.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle(R.string.select_board_hint)
                .setItems(boardArray, (dialog, which) -> {
                    Board selectedBoard = null;
                    int index = 0;
                    for (BoardCategory category : categories) {
                        for (Board board : category.getBoards()) {
                            if (index == which) {
                                selectedBoard = board;
                                break;
                            }
                            index++;
                        }
                        if (selectedBoard != null) break;
                    }

                    if (selectedBoard != null) {
                        searchOnBoard(selectedBoard, query);
                    }
                })
                .show();
    }

    private void searchOnBoard(Board board, String query) {
        progressBar.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            try {
                KomicaService.SearchTask task = new KomicaService.SearchTask(query);
                List<Thread> results = task.call();

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);

                    if (results.isEmpty()) {
                        Toast.makeText(MainActivity.this, R.string.no_results, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    searchResults.clear();
                    searchResults.addAll(results);

                    isSearchMode = true;
                    recyclerView.setAdapter(searchAdapter);
                    searchAdapter.notifyDataSetChanged();

                    Toast.makeText(MainActivity.this, String.format(getString(R.string.found_results), results.size()), Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, String.format(getString(R.string.search_failed), e.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    public void clearSearch() {
        isSearchMode = false;
        recyclerView.setAdapter(categoryAdapter);
        adapter.notifyDataSetChanged();
        searchEditText.setText("");
    }

    @Override
    public void onBackPressed() {
        if (isSearchMode) {
            clearSearch();
        } else {
            super.onBackPressed();
        }
    }

    private void loadBoards() {
        progressBar.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            try {
                KomicaService.FetchBoardsTask task = new KomicaService.FetchBoardsTask();
                List<BoardCategory> result = task.call();

                runOnUiThread(() -> {
                    categories.clear();
                    categories.addAll(result);
                    adapter.notifyDataSetChanged();
                    progressBar.setVisibility(View.GONE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
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
