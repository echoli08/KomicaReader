package com.komica.reader;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.komica.reader.adapter.BoardCategoryAdapter;
import com.komica.reader.model.Board;
import com.komica.reader.model.BoardCategory;
import com.komica.reader.service.KomicaService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private BoardCategoryAdapter categoryAdapter;
    private List<BoardCategory> categories = new ArrayList<>();
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        categoryAdapter = new BoardCategoryAdapter(categories, board -> {
            Intent intent = new Intent(MainActivity.this, ThreadListActivity.class);
            intent.putExtra("board", board);
            startActivity(intent);
        });

        recyclerView.setAdapter(categoryAdapter);
        loadBoards();
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
                    categoryAdapter.notifyDataSetChanged();
                    progressBar.setVisibility(View.GONE);
                });
            } catch (Exception e) {
                android.util.Log.e("Komica", "Error loading boards: " + e.getMessage());
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    android.widget.Toast.makeText(MainActivity.this, 
                        "載入板塊失敗: " + e.getMessage(), 
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
