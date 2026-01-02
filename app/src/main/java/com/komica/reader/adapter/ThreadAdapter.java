package com.komica.reader.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.komica.reader.R;
import com.komica.reader.model.Thread;
import androidx.recyclerview.widget.DiffUtil;
import java.util.ArrayList;
import java.util.List;

public class ThreadAdapter extends RecyclerView.Adapter<ThreadAdapter.ThreadViewHolder> {

    private List<Thread> threads;
    private OnThreadClickListener onThreadClickListener;

    public interface OnThreadClickListener {
        void onThreadClick(Thread thread);
        void onShareClick(Thread thread);
    }

    public ThreadAdapter(List<Thread> threads, OnThreadClickListener listener) {
        this.threads = new ArrayList<>(threads);
        this.onThreadClickListener = listener;
    }

    public void updateThreads(List<Thread> newThreads) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return threads.size();
            }

            @Override
            public int getNewListSize() {
                return newThreads.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return threads.get(oldItemPosition).getUrl().equals(newThreads.get(newItemPosition).getUrl());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Thread oldItem = threads.get(oldItemPosition);
                Thread newItem = newThreads.get(newItemPosition);
                return oldItem.getPostNumber() == newItem.getPostNumber() &&
                       oldItem.getTitle().equals(newItem.getTitle()) &&
                       oldItem.getReplyCount() == newItem.getReplyCount() &&
                       (oldItem.getLastReplyTime() == null ? "" : oldItem.getLastReplyTime())
                           .equals(newItem.getLastReplyTime() == null ? "" : newItem.getLastReplyTime());
            }
        });

        this.threads.clear();
        this.threads.addAll(newThreads);
        diffResult.dispatchUpdatesTo(this);
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
        private TextView replyCount;
        private View shareButton;

        public ThreadViewHolder(@NonNull View itemView) {
            super(itemView);
            threadPostNumber = itemView.findViewById(R.id.threadPostNumber);
            threadTitle = itemView.findViewById(R.id.threadTitle);
            threadAuthor = itemView.findViewById(R.id.threadAuthor);
            contentPreview = itemView.findViewById(R.id.contentPreview);
            lastReplyTime = itemView.findViewById(R.id.lastReplyTime);
            replyCount = itemView.findViewById(R.id.replyCount);
            shareButton = itemView.findViewById(R.id.shareButton);
        }

        public void bind(Thread thread) {
            threadPostNumber.setText("No. " + thread.getPostNumber());
            threadTitle.setText(thread.getTitle());
            threadAuthor.setText(thread.getAuthor());
            replyCount.setText("回覆: " + thread.getReplyCount());

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
            
            shareButton.setOnClickListener(v -> {
                if (onThreadClickListener != null) {
                    onThreadClickListener.onShareClick(thread);
                }
            });
        }
    }
}
