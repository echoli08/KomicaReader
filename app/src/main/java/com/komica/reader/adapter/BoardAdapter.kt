package com.komica.reader.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.komica.reader.R
import com.komica.reader.model.Board
import com.komica.reader.util.FavoritesManager

class BoardAdapter(
    private var boards: List<Board>,
    private val onBoardClickListener: BoardCategoryAdapter.OnBoardClickListener?,
    private val onFavoriteClickListener: OnFavoriteClickListener?
) : RecyclerView.Adapter<BoardAdapter.BoardViewHolder>() {

    fun interface OnFavoriteClickListener {
        fun onFavoriteClick(board: Board)
    }

    fun updateData(newBoards: List<Board>) {
        boards = newBoards
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BoardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_board, parent, false)
        return BoardViewHolder(view)
    }

    override fun onBindViewHolder(holder: BoardViewHolder, position: Int) {
        holder.bind(boards[position])
    }

    override fun getItemCount(): Int = boards.size

    inner class BoardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val boardName: TextView = itemView.findViewById(R.id.boardName)
        private val favoriteIcon: ImageView = itemView.findViewById(R.id.favoriteIcon)

        fun bind(board: Board) {
            boardName.text = board.name

            val favoritesManager = FavoritesManager.getInstance(itemView.context)
            updateFavoriteIcon(favoritesManager.isFavorite(board.url))

            itemView.setOnClickListener {
                onBoardClickListener?.onBoardClick(board)
            }

            favoriteIcon.setOnClickListener {
                onFavoriteClickListener?.onFavoriteClick(board)
                // 繁體中文註解：點擊最愛後即時更新圖示
                updateFavoriteIcon(favoritesManager.isFavorite(board.url))
            }
        }

        private fun updateFavoriteIcon(isFavorite: Boolean) {
            val icon = if (isFavorite) R.drawable.ic_star else R.drawable.ic_star_border
            favoriteIcon.setImageResource(icon)
        }
    }
}
