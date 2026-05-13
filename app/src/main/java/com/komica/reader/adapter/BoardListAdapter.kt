package com.komica.reader.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
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
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var categories: List<BoardCategory> = emptyList()
    private val items = mutableListOf<BoardListItem>()

    fun SubmitCategories(categories: List<BoardCategory>) {
        this.categories = categories
        RebuildItems()
    }

    fun RefreshCategoryState() {
        RebuildItems()
    }

    private fun RebuildItems() {
        items.clear()
        categories.forEach { category ->
            items.add(BoardListItem.Category(category))
            if (!isCategoryCollapsed(category)) {
                category.boards.forEach { board -> items.add(BoardListItem.BoardRow(board)) }
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
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

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is BoardListItem.Category -> (holder as CategoryViewHolder).Bind(item.category)
            is BoardListItem.BoardRow -> (holder as BoardViewHolder).Bind(item.board)
        }
    }

    private inner class CategoryViewHolder(
        private val binding: ItemBoardCategoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun Bind(category: BoardCategory) {
            binding.categoryTitle.text = category.name
            binding.categoryMeta.text = if (isCategoryCollapsed(category)) "${category.boards.size} 個看板，已收合" else "${category.boards.size} 個看板"
            binding.root.setOnClickListener { onCategoryClick(category) }
        }
    }

    private inner class BoardViewHolder(
        private val binding: ItemBoardBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun Bind(board: Board) {
            binding.boardName.text = board.name
            binding.boardDescription.text = board.description.ifBlank { "${board.categoryName} 看板" }
            binding.boardPath.text = board.url
            binding.favoriteButton.text = if (isFavorite(board)) "★" else "☆"
            binding.root.setOnClickListener { onBoardClick(board) }
            binding.favoriteButton.setOnClickListener { onFavoriteClick(board) }
        }
    }

    private sealed interface BoardListItem {
        data class Category(val category: BoardCategory) : BoardListItem
        data class BoardRow(val board: Board) : BoardListItem
    }

    companion object {
        private const val ViewTypeCategory = 1
        private const val ViewTypeBoard = 2
    }
}
