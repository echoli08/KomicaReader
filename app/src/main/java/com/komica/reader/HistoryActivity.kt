package com.komica.reader

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.komica.reader.adapter.HistoryAdapter
import com.komica.reader.viewmodel.HistoryViewModel

class HistoryActivity : AppCompatActivity() {
    private lateinit var viewModel: HistoryViewModel
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_thread_list) // Reuse layout

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        findViewById<TextView>(R.id.toolbarTitle).text = "瀏覽紀錄"
        
        // Hide unnecessary UI elements from reused layout
        findViewById<View>(R.id.searchEditText).visibility = View.GONE
        findViewById<View>(R.id.filterButton).visibility = View.GONE
        findViewById<View>(R.id.favoriteButton).visibility = View.GONE

        viewModel = ViewModelProvider(this)[HistoryViewModel::class.java]
        
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        adapter = HistoryAdapter { entry ->
            val intent = Intent(this, ThreadDetailActivity::class.java).apply {
                putExtra("thread_url", entry.url)
                putExtra("thread_title", entry.title)
            }
            startActivity(intent)
        }
        
        recyclerView.adapter = adapter
        
        viewModel.historyList.observe(this) { list ->
            adapter.submitList(list)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "清除所有紀錄")
            .setIcon(android.R.drawable.ic_menu_delete)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            1 -> { // Clear all
                AlertDialog.Builder(this)
                    .setTitle("清除紀錄")
                    .setMessage("確定要清除所有瀏覽紀錄嗎？")
                    .setPositiveButton("清除") { _, _ ->
                        viewModel.clearAllHistory()
                    }
                    .setNegativeButton("取消", null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}