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
import androidx.appcompat.widget.Toolbar;

import com.komica.reader.viewmodel.MainViewModel;
import androidx.lifecycle.ViewModelProvider;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private BoardCategoryAdapter categoryAdapter;
    private List<BoardCategory> categories = new ArrayList<>();
    private MainViewModel viewModel;
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
        
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_settings);
        }
        
        favoritesManager = FavoritesManager.getInstance(this);
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        swipeRefreshLayout.setOnRefreshListener(() -> {
            viewModel.loadBoards(true);
        });

        categoryAdapter = new BoardCategoryAdapter(categories, 
            board -> {
                Intent intent = new Intent(MainActivity.this, ThreadListActivity.class);
                intent.putExtra("board", board);
                startActivity(intent);
            },
            board -> {
                viewModel.toggleFavorite(board);
            }
        );

        recyclerView.setAdapter(categoryAdapter);
        
        observeViewModel();
        
        if (savedInstanceState == null) {
            viewModel.loadBoards();
        }
    }

    private void observeViewModel() {
        viewModel.getCategories().observe(this, newCategories -> {
            categories.clear();
            categories.addAll(newCategories);
            categoryAdapter.notifyDataSetChanged();
            swipeRefreshLayout.setRefreshing(false);
        });

        viewModel.getIsLoading().observe(this, isLoading -> {
            if (!swipeRefreshLayout.isRefreshing()) {
                progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.refreshFavorites();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        MenuItem item = menu.findItem(R.id.action_theme_toggle);
        boolean isNightMode = prefs.getBoolean("night_mode", false);
        item.setIcon(isNightMode ? R.drawable.ic_mode_day : R.drawable.ic_mode_night);
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
        } else if (item.getItemId() == android.R.id.home) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}