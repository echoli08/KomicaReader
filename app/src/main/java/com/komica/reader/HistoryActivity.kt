package com.komica.reader

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.komica.reader.adapter.HistoryAdapter
import com.komica.reader.data.HistoryStore
import com.komica.reader.databinding.ActivityHistoryBinding
import com.komica.reader.util.WindowInsetsUtil

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
        adapter.SubmitItems(store.GetAll())
    }

    private fun DeleteHistoryItem(url: String) {
        store.Remove(url)
        RefreshHistory()
        Toast.makeText(this, "已刪除此筆歷史", Toast.LENGTH_SHORT).show()
    }
}
