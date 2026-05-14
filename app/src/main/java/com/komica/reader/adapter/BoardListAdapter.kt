package com.komica.reader.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.komica.reader.databinding.ItemBoardBinding
import com.komica.reader.databinding.ItemBoardCategoryBinding
import com.komica.reader.model.Board
import com.komica.reader.model.BoardCategory

class BoardListAdapter(
    private val onBoardClick: (Board) -> Unit,
    private val onCategoryClick: (BoardCategory) -> Unit,
    private val onFavoriteClick: (Board) -> Unit,
    private val isCategoryCollapsed: (BoardCategory) -> Boolean,
    private val isFavorite: (Board) -> Boolean
) : ListAdapter<BoardListAdapter.BoardListItem, RecyclerView.ViewHolder>(DiffCallback) {
    private var categories: List<BoardCategory> = emptyList()

    fun SubmitCategories(categories: List<BoardCategory>) {
        this.categories = categories
        RebuildItems()
    }

    fun RefreshCategoryState() {
        RebuildItems()
    }

    private fun RebuildItems() {
        val newItems = mutableListOf<BoardListItem>()
        categories.forEach { category ->
            val isCollapsed = isCategoryCollapsed(category)
            newItems.add(BoardListItem.Category(category, isCollapsed))
            if (!isCategoryCollapsed(category)) {
                category.boards.forEach { board -> newItems.add(BoardListItem.BoardRow(board, isFavorite(board))) }
            }
        }
        submitList(newItems)
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is BoardListItem.Category -> ViewTypeCategory
            is BoardListItem.BoardRow -> ViewTypeBoard
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == ViewTypeCategory) {
            CategoryViewHolder(ItemBoardCategoryBinding.inflate(inflater, parent, false))
        } else {
            BoardViewHolder(ItemBoardBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is BoardListItem.Category -> (holder as CategoryViewHolder).Bind(item)
            is BoardListItem.BoardRow -> (holder as BoardViewHolder).Bind(item)
        }
    }

    private inner class CategoryViewHolder(
        private val binding: ItemBoardCategoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun Bind(item: BoardListItem.Category) {
            val category = item.category
            binding.categoryTitle.text = category.name
            binding.categoryMeta.text = if (item.isCollapsed) "${category.boards.size} 個看板，已收合" else "${category.boards.size} 個看板"
            binding.root.setOnClickListener { onCategoryClick(category) }
        }
    }

    private inner class BoardViewHolder(
        private val binding: ItemBoardBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun Bind(item: BoardListItem.BoardRow) {
            val board = item.board
            binding.boardName.text = board.name
            binding.boardDescription.text = board.description.ifBlank { "${board.categoryName} 看板" }
            binding.boardPath.text = board.url
            binding.favoriteButton.text = if (item.isFavorite) "★" else "☆"
            binding.root.setOnClickListener { onBoardClick(board) }
            binding.favoriteButton.setOnClickListener { onFavoriteClick(board) }
        }
    }

    sealed interface BoardListItem {
        data class Category(val category: BoardCategory, val isCollapsed: Boolean) : BoardListItem
        data class BoardRow(val board: Board, val isFavorite: Boolean) : BoardListItem
    }

    companion object {
        private const val ViewTypeCategory = 1
        private const val ViewTypeBoard = 2

        private val DiffCallback = object : DiffUtil.ItemCallback<BoardListItem>() {
            override fun areItemsTheSame(oldItem: BoardListItem, newItem: BoardListItem): Boolean {
                return when {
                    oldItem is BoardListItem.Category && newItem is BoardListItem.Category -> oldItem.category.name == newItem.category.name
                    oldItem is BoardListItem.BoardRow && newItem is BoardListItem.BoardRow -> oldItem.board.url == newItem.board.url
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: BoardListItem, newItem: BoardListItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
