package com.komica.reader.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.komica.reader.R;
import com.komica.reader.model.Thread;
import java.util.List;

public class ThreadAdapter extends RecyclerView.Adapter<ThreadAdapter.ThreadViewHolder> {

    private List<Thread> threads;
    private OnThreadClickListener onThreadClickListener;

    public interface OnThreadClickListener {
        void onThreadClick(Thread thread);
    }

    public ThreadAdapter(List<Thread> threads, OnThreadClickListener listener) {
        this.threads = threads;
        this.onThreadClickListener = listener;
    }

    @NonNull
    @Override
    public ThreadViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_thread, parent, false);
        return new ThreadViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ThreadViewHolder holder, int position) {
        Thread thread = threads.get(position);
        holder.bind(thread);
    }

    @Override
    public int getItemCount() {
        return threads.size();
    }

    class ThreadViewHolder extends RecyclerView.ViewHolder {
        private TextView threadPostNumber;
        private TextView threadTitle;
        private TextView threadAuthor;
        private TextView contentPreview;
        private TextView lastReplyTime;

        public ThreadViewHolder(@NonNull View itemView) {
            super(itemView);
            threadPostNumber = itemView.findViewById(R.id.threadPostNumber);
            threadTitle = itemView.findViewById(R.id.threadTitle);
            threadAuthor = itemView.findViewById(R.id.threadAuthor);
            contentPreview = itemView.findViewById(R.id.contentPreview);
            lastReplyTime = itemView.findViewById(R.id.lastReplyTime);
        }

        public void bind(Thread thread) {
            threadPostNumber.setText("No. " + thread.getPostNumber());
            threadTitle.setText(thread.getTitle());
            threadAuthor.setText(thread.getAuthor());

            if (thread.getContentPreview() != null && !thread.getContentPreview().isEmpty()) {
                contentPreview.setText(thread.getContentPreview());
                contentPreview.setVisibility(View.VISIBLE);
            } else {
                contentPreview.setVisibility(View.GONE);
            }
 
            if (thread.getLastReplyTime() != null && !thread.getLastReplyTime().isEmpty()) {
                lastReplyTime.setText(thread.getLastReplyTime());
                lastReplyTime.setVisibility(View.VISIBLE);
            } else {
                lastReplyTime.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (onThreadClickListener != null) {
                    onThreadClickListener.onThreadClick(thread);
                }
            });
        }
    }
}
