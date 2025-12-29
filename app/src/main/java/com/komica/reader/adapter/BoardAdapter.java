package com.komica.reader.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.komica.reader.R;
import com.komica.reader.model.Board;
import com.komica.reader.model.BoardCategory;
import java.util.List;

public class BoardAdapter extends RecyclerView.Adapter<BoardAdapter.BoardViewHolder> {

    private List<Board> boards;
    private BoardCategoryAdapter.OnBoardClickListener onBoardClickListener;

    public BoardAdapter(List<Board> boards, BoardCategoryAdapter.OnBoardClickListener listener) {
        this.boards = boards;
        this.onBoardClickListener = listener;
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

        public BoardViewHolder(@NonNull View itemView) {
            super(itemView);
            boardName = itemView.findViewById(R.id.boardName);
        }

        public void bind(Board board) {
            boardName.setText(board.getName());
            itemView.setOnClickListener(v -> {
                if (onBoardClickListener != null) {
                    onBoardClickListener.onBoardClick(board);
                }
            });
        }
    }
}
