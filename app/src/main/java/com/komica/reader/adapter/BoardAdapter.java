package com.komica.reader.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.komica.reader.R;
import com.komica.reader.model.Board;
import com.komica.reader.util.FavoritesManager;
import java.util.List;

public class BoardAdapter extends RecyclerView.Adapter<BoardAdapter.BoardViewHolder> {

    private List<Board> boards;
    private BoardCategoryAdapter.OnBoardClickListener onBoardClickListener;
    private OnFavoriteClickListener onFavoriteClickListener;

    public interface OnFavoriteClickListener {
        void onFavoriteClick(Board board);
    }

    public BoardAdapter(List<Board> boards, BoardCategoryAdapter.OnBoardClickListener listener, OnFavoriteClickListener favoriteListener) {
        this.boards = boards;
        this.onBoardClickListener = listener;
        this.onFavoriteClickListener = favoriteListener;
    }

    public void updateData(List<Board> newBoards) {
        this.boards = newBoards;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BoardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_board, parent, false);
        return new BoardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BoardViewHolder holder, int position) {
        Board board = boards.get(position);
        holder.bind(board);
    }

    @Override
    public int getItemCount() {
        return boards.size();
    }

    class BoardViewHolder extends RecyclerView.ViewHolder {
        private TextView boardName;
        private ImageView favoriteIcon;

        public BoardViewHolder(@NonNull View itemView) {
            super(itemView);
            boardName = itemView.findViewById(R.id.boardName);
            favoriteIcon = itemView.findViewById(R.id.favoriteIcon);
        }

        public void bind(Board board) {
            boardName.setText(board.getName());
            
            FavoritesManager favoritesManager = FavoritesManager.getInstance(itemView.getContext());
            boolean isFavorite = favoritesManager.isFavorite(board.getUrl());
            updateFavoriteIcon(isFavorite);
            
            itemView.setOnClickListener(v -> {
                if (onBoardClickListener != null) {
                    onBoardClickListener.onBoardClick(board);
                }
            });

            favoriteIcon.setOnClickListener(v -> {
                if (onFavoriteClickListener != null) {
                    onFavoriteClickListener.onFavoriteClick(board);
                    // Update UI immediately for better feedback
                    updateFavoriteIcon(favoritesManager.isFavorite(board.getUrl()));
                }
            });
        }
        
        private void updateFavoriteIcon(boolean isFavorite) {
            if (isFavorite) {
                favoriteIcon.setImageResource(R.drawable.ic_star);
            } else {
                favoriteIcon.setImageResource(R.drawable.ic_star_border);
            }
        }
    }
}
