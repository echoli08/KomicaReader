package com.komica.reader.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.komica.reader.R;
import com.komica.reader.model.Board;
import com.komica.reader.model.BoardCategory;
import java.util.List;

public class BoardCategoryAdapter extends RecyclerView.Adapter<BoardCategoryAdapter.CategoryViewHolder> {

    private List<BoardCategory> categories;
    private OnBoardClickListener onBoardClickListener;
    private BoardAdapter.OnFavoriteClickListener onFavoriteClickListener;

    public interface OnBoardClickListener {
        void onBoardClick(Board board);
    }

    public BoardCategoryAdapter(List<BoardCategory> categories, OnBoardClickListener listener, BoardAdapter.OnFavoriteClickListener favoriteListener) {
        this.categories = categories;
        this.onBoardClickListener = listener;
        this.onFavoriteClickListener = favoriteListener;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_board_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        BoardCategory category = categories.get(position);
        holder.bind(category, position);
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    class CategoryViewHolder extends RecyclerView.ViewHolder {
        private View categoryHeader;
        private TextView categoryName;
        private ImageView expandIcon;
        private RecyclerView boardsRecyclerView;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryHeader = itemView.findViewById(R.id.categoryHeader);
            categoryName = itemView.findViewById(R.id.categoryName);
            expandIcon = itemView.findViewById(R.id.expandIcon);
            boardsRecyclerView = itemView.findViewById(R.id.boardsRecyclerView);
            boardsRecyclerView.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
        }

        public void bind(BoardCategory category, int position) {
            categoryName.setText(category.getName());
            
            categoryHeader.setOnClickListener(v -> {
                category.setExpanded(!category.isExpanded());
                notifyItemChanged(position);
            });

            if (category.isExpanded()) {
                expandIcon.setImageResource(R.drawable.ic_expand_less);
                boardsRecyclerView.setVisibility(View.VISIBLE);
            } else {
                expandIcon.setImageResource(R.drawable.ic_expand_more);
                boardsRecyclerView.setVisibility(View.GONE);
            }

            BoardAdapter boardAdapter = (BoardAdapter) boardsRecyclerView.getAdapter();
            if (boardAdapter == null) {
                boardAdapter = new BoardAdapter(category.getBoards(), onBoardClickListener, onFavoriteClickListener);
                boardsRecyclerView.setAdapter(boardAdapter);
            } else {
                boardAdapter.updateData(category.getBoards());
            }
        }
    }
}
