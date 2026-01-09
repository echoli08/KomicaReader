package com.komica.reader.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.komica.reader.R
import com.komica.reader.model.Board
import com.komica.reader.model.BoardCategory

class BoardCategoryAdapter(
    private var categories: List<BoardCategory>,
    private val onBoardClickListener: OnBoardClickListener?,
    private val onFavoriteClickListener: BoardAdapter.OnFavoriteClickListener?
) : RecyclerView.Adapter<BoardCategoryAdapter.CategoryViewHolder>() {

    fun interface OnBoardClickListener {
        fun onBoardClick(board: Board)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_board_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position], position)
    }

    override fun getItemCount(): Int = categories.size

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val categoryHeader: View = itemView.findViewById(R.id.categoryHeader)
        private val categoryName: TextView = itemView.findViewById(R.id.categoryName)
        private val expandIcon: ImageView = itemView.findViewById(R.id.expandIcon)
        private val boardsRecyclerView: RecyclerView = itemView.findViewById(R.id.boardsRecyclerView)

        init {
            boardsRecyclerView.layoutManager = LinearLayoutManager(itemView.context)
        }

        fun bind(category: BoardCategory, position: Int) {
            categoryName.text = category.name

            categoryHeader.setOnClickListener {
                category.isExpanded = !category.isExpanded
                notifyItemChanged(position)
            }

            if (category.isExpanded) {
                expandIcon.setImageResource(R.drawable.ic_expand_less)
                boardsRecyclerView.visibility = View.VISIBLE
            } else {
                expandIcon.setImageResource(R.drawable.ic_expand_more)
                boardsRecyclerView.visibility = View.GONE
            }

            val boardAdapter = boardsRecyclerView.adapter as? BoardAdapter
            if (boardAdapter == null) {
                boardsRecyclerView.adapter = BoardAdapter(
                    category.boards,
                    onBoardClickListener,
                    onFavoriteClickListener
                )
            } else {
                boardAdapter.updateData(category.boards)
            }
        }
    }
}
