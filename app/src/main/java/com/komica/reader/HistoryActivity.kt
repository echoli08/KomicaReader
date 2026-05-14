package com.komica.reader

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.komica.reader.adapter.HistoryAdapter
import com.komica.reader.data.HistoryStore
import com.komica.reader.databinding.ActivityHistoryBinding
import com.komica.reader.util.WindowInsetsUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHistoryBinding
    private lateinit var store: HistoryStore
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsUtil.ApplyBrandStatusBar(window)
        WindowInsetsUtil.ApplyToolbarInsets(binding.toolbar)

        store = HistoryStore(this)
        adapter = HistoryAdapter(
            onClick = { thread ->
                startActivity(Intent(this, ThreadDetailActivity::class.java).putExtra(ThreadDetailActivity.ExtraThread, thread))
            },
            onLongClick = { thread -> DeleteHistoryItem(thread.url) }
        )
        binding.historyRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.historyRecyclerView.adapter = adapter
        RefreshHistory()
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) RefreshHistory()
    }

    private fun RefreshHistory() {
        lifecycleScope.launch {
            val history = withContext(Dispatchers.IO) { store.GetAll() }
            adapter.SubmitItems(history)
        }
    }

    private fun DeleteHistoryItem(url: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { store.Remove(url) }
            RefreshHistory()
            Toast.makeText(this@HistoryActivity, "已刪除此筆歷史", Toast.LENGTH_SHORT).show()
        }
    }
}
