package com.komica.reader.adapter;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.komica.reader.R;
import com.komica.reader.model.Post;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {

    private List<Post> posts;
    private List<String> allImageUrls;
    private Map<Integer, Integer> postToImageIndexMap;
    private Map<String, Integer> postIdToPositionMap;
    private RecyclerView recyclerView;
    private OnImageClickListener onImageClickListener;

    public interface OnImageClickListener {
        void onImageClick(int imageIndex, List<String> imageUrls);
    }

    public PostAdapter(List<Post> posts, OnImageClickListener listener) {
        this.posts = posts;
        this.onImageClickListener = listener;
        this.allImageUrls = new ArrayList<>();
        this.postToImageIndexMap = new HashMap<>();
        this.postIdToPositionMap = new HashMap<>();

        for (int i = 0; i < posts.size(); i++) {
            Post post = posts.get(i);
            postIdToPositionMap.put(String.valueOf(post.getNumber()), i);
            if (post.getImageUrl() != null && !post.getImageUrl().isEmpty()) {
                allImageUrls.add(post.getImageUrl());
                postToImageIndexMap.put(i, allImageUrls.size() - 1);
            }
        }
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_post, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        Post post = posts.get(position);
        holder.bind(post, position);
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    private SpannableString createClickableLinks(String content) {
        SpannableString spannable = new SpannableString(content);
        Pattern pattern = Pattern.compile(">>(\\d+)");
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            String postId = matcher.group(1);
            final Integer targetPosition = postIdToPositionMap.get(postId);
            
            if (targetPosition != null) {
                ClickableSpan clickableSpan = new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        if (recyclerView != null) {
                            recyclerView.smoothScrollToPosition(targetPosition);
                            PostViewHolder holder = (PostViewHolder) recyclerView.findViewHolderForAdapterPosition(targetPosition);
                            if (holder != null) {
                                holder.itemView.setBackgroundColor(0xFFFFE0B2);
                                holder.itemView.postDelayed(() -> {
                                    holder.itemView.setBackgroundColor(0x00000000);
                                }, 1000);
                            }
                        }
                    }

                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        super.updateDrawState(ds);
                        ds.setColor(0xFF2196F3);
                        ds.setUnderlineText(true);
                    }
                };
                
                spannable.setSpan(clickableSpan, matcher.start(), matcher.end(), 
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(new ForegroundColorSpan(0xFF2196F3), 
                    matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        
        return spannable;
    }

    class PostViewHolder extends RecyclerView.ViewHolder {
        private TextView postNumber;
        private TextView postAuthor;
        private TextView postContent;
        private TextView postTime;
        private ImageView postImage;

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);
            postNumber = itemView.findViewById(R.id.postNumber);
            postAuthor = itemView.findViewById(R.id.postAuthor);
            postContent = itemView.findViewById(R.id.postContent);
            postTime = itemView.findViewById(R.id.postTime);
            postImage = itemView.findViewById(R.id.postImage);
        }

        public void bind(Post post, int position) {
            String content = post.getContent();
            String author = post.getAuthor();
            
            postNumber.setText("No. " + post.getNumber());
            postAuthor.setText(author != null ? author : "Unknown");
            postTime.setText(post.getTime() != null ? post.getTime() : "");

            if (content == null || content.trim().isEmpty()) {
                postContent.setText("(無內容)");
                postContent.setVisibility(View.GONE);
            } else {
                postContent.setText(createClickableLinks(content));
                postContent.setMovementMethod(LinkMovementMethod.getInstance());
                postContent.setVisibility(View.VISIBLE);
            }

            if (post.getThumbnailUrl() != null && !post.getThumbnailUrl().isEmpty()) {
                postImage.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext())
                        .load(post.getThumbnailUrl())
                        .override(400, 400)
                        .error(android.R.drawable.ic_menu_gallery)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .into(postImage);

                Integer imageIndex = postToImageIndexMap.get(position);
                if (imageIndex != null) {
                    final int index = imageIndex;
                    postImage.setOnClickListener(v -> {
                        if (onImageClickListener != null) {
                            onImageClickListener.onImageClick(index, allImageUrls);
                        }
                    });
                }
            } else {
                postImage.setVisibility(View.GONE);
            }
        }
    }
}
