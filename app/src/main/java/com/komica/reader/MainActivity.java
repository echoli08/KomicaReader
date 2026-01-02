package com.komica.reader;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.komica.reader.adapter.BoardCategoryAdapter;
import com.komica.reader.model.Board;
import com.komica.reader.model.BoardCategory;
import com.komica.reader.service.KomicaService;
import com.komica.reader.util.FavoritesManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.view.Menu;
import android.view.MenuItem;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private BoardCategoryAdapter categoryAdapter;
    private List<BoardCategory> categories = new ArrayList<>();
    private List<BoardCategory> originalCategories = new ArrayList<>();
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private FavoritesManager favoritesManager;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        prefs = getSharedPreferences("KomicaReader", MODE_PRIVATE);
        boolean isNightMode = prefs.getBoolean("night_mode", false);
        AppCompatDelegate.setDefaultNightMode(isNightMode ? 
            AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            
        setContentView(R.layout.activity_main);
        
        favoritesManager = FavoritesManager.getInstance(this);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        categoryAdapter = new BoardCategoryAdapter(categories, 
            board -> {
                Intent intent = new Intent(MainActivity.this, ThreadListActivity.class);
                intent.putExtra("board", board);
                startActivity(intent);
            },
            board -> {
                favoritesManager.toggleFavorite(board.getUrl());
                updateCategoriesWithFavorites();
            }
        );

        recyclerView.setAdapter(categoryAdapter);
        loadBoards();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        MenuItem item = menu.findItem(R.id.action_theme_toggle);
        boolean isNightMode = prefs.getBoolean("night_mode", false);
        item.setTitle(isNightMode ? "切換日間模式" : "切換夜間模式");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_theme_toggle) {
            boolean isNightMode = prefs.getBoolean("night_mode", false);
            prefs.edit().putBoolean("night_mode", !isNightMode).apply();
            recreate(); // Recreate activity to apply theme
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadBoards() {
        progressBar.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            try {
                KomicaService.FetchBoardsTask task = new KomicaService.FetchBoardsTask();
                List<BoardCategory> result = task.call();
                
                runOnUiThread(() -> {
                    originalCategories.clear();
                    originalCategories.addAll(result);
                    updateCategoriesWithFavorites();
                    progressBar.setVisibility(View.GONE);
                });
            } catch (Exception e) {
                android.util.Log.e("Komica", "Error loading boards: " + e.getMessage());
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, 
                        "載入板塊失敗: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void updateCategoriesWithFavorites() {
        categories.clear();
        
        List<Board> favoriteBoards = new ArrayList<>();
        // Iterate through originalCategories to find favorite boards
        // This ensures we have the Board objects with correct names and URLs
        for (BoardCategory cat : originalCategories) {
            for (Board board : cat.getBoards()) {
                if (favoritesManager.isFavorite(board.getUrl())) {
                    // Create a copy or add reference? Adding reference is fine for now
                    // check if not already added (some boards might appear in multiple categories if API changes, but unlikely)
                    boolean alreadyAdded = false;
                    for (Board fb : favoriteBoards) {
                        if (fb.getUrl().equals(board.getUrl())) {
                            alreadyAdded = true;
                            break;
                        }
                    }
                    if (!alreadyAdded) {
                        favoriteBoards.add(board);
                    }
                }
            }
        }
        
        if (!favoriteBoards.isEmpty()) {
            BoardCategory favCategory = new BoardCategory("我的最愛", favoriteBoards);
            favCategory.setExpanded(true); 
            categories.add(favCategory);
        }
        
        categories.addAll(originalCategories);
        categoryAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}