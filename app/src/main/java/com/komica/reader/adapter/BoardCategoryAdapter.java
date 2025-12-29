package com.komica.reader.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

    public interface OnBoardClickListener {
        void onBoardClick(Board board);
    }

    public BoardCategoryAdapter(List<BoardCategory> categories, OnBoardClickListener listener) {
        this.categories = categories;
        this.onBoardClickListener = listener;
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
        holder.bind(category);
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    class CategoryViewHolder extends RecyclerView.ViewHolder {
        private TextView categoryName;
        private RecyclerView boardsRecyclerView;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryName = itemView.findViewById(R.id.categoryName);
            boardsRecyclerView = itemView.findViewById(R.id.boardsRecyclerView);
            boardsRecyclerView.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
        }

        public void bind(BoardCategory category) {
            categoryName.setText(category.getName());
            
            BoardAdapter boardAdapter = new BoardAdapter(category.getBoards(), onBoardClickListener);
            boardsRecyclerView.setAdapter(boardAdapter);
        }
    }
}
