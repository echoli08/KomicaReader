package com.komica.reader.adapter;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.MotionEvent;
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
    private OnQuoteInteractionListener interactionListener;

    public interface OnQuoteInteractionListener {
        void onImageClick(int imageIndex, List<String> imageUrls);
        void onImageLongClick(String imageUrl);
        void onQuoteClick(int position);
        void onQuoteLongClick(Post post);
        void onQuoteReleased();
    }

    public PostAdapter(List<Post> posts, OnQuoteInteractionListener listener) {
        this.posts = posts;
        this.interactionListener = listener;
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

    private SpannableString createSpannableContent(android.content.Context context, String content) {
        SpannableString spannable = new SpannableString(content);
        
        int quoteColor = androidx.core.content.ContextCompat.getColor(context, R.color.quote_text);
        int linkColor = androidx.core.content.ContextCompat.getColor(context, R.color.text_link);
        
        // 1. Handle Green Text (lines starting with >)
        String[] lines = content.split("\n");
        int currentPos = 0;
        for (String line : lines) {
            // If it starts with > but is not followed by a number link
            if (line.startsWith(">") && !line.matches("^>>?\\d+.*")) {
                int end = Math.min(currentPos + line.length(), spannable.length());
                spannable.setSpan(new ForegroundColorSpan(quoteColor), 
                    currentPos, end, 
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            currentPos += line.length() + 1; // +1 for the newline
        }

        // 2. Handle Quotes (>>No. or >No.) - High Priority
        Pattern pattern = Pattern.compile(">>?(\\d+)");
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            final String postId = matcher.group(1);
            final Integer targetPosition = postIdToPositionMap.get(postId);
            
            if (targetPosition != null) {
                ClickableSpan clickableSpan = new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) { }

                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        super.updateDrawState(ds);
                        ds.setColor(linkColor);
                        ds.setUnderlineText(true);
                    }
                };
                
                spannable.setSpan(clickableSpan, matcher.start(), matcher.end(), 
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        // 3. Handle Web URLs - Do not overlap with existing spans
        Matcher urlMatcher = android.util.Patterns.WEB_URL.matcher(content);
        while (urlMatcher.find()) {
            int start = urlMatcher.start();
            int end = urlMatcher.end();
            
            // Check if this area is already covered by a Quote span
            ClickableSpan[] existingSpans = spannable.getSpans(start, end, ClickableSpan.class);
            if (existingSpans.length > 0) continue;

            String url = urlMatcher.group();
            if (!url.startsWith("http")) {
                url = "https://" + url;
            }
            final String finalUrl = url;
            
            ClickableSpan urlSpan = new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        widget.getContext().startActivity(intent);
                    } catch (Exception e) {
                        android.widget.Toast.makeText(widget.getContext(), "無法開啟網址: " + finalUrl, android.widget.Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setColor(linkColor);
                    ds.setUnderlineText(true);
                }
            };
            spannable.setSpan(urlSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        
        return spannable;
    }

    class PostViewHolder extends RecyclerView.ViewHolder {
        private TextView postNumber;
        private TextView postAuthor;
        private TextView postContent;
        private TextView postTime;
        private ImageView postImage;
        private Handler handler = new Handler(Looper.getMainLooper());
        private Runnable longPressRunnable;
        private boolean isLongPressed = false;

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
                SpannableString spannable = createSpannableContent(itemView.getContext(), content);
                postContent.setText(spannable);
                postContent.setMovementMethod(null);
                postContent.setVisibility(View.VISIBLE);
                
                setupTouchListener(spannable);
            }

            String displayImageUrl = post.getImageUrl();
            if (displayImageUrl == null || displayImageUrl.isEmpty()) {
                displayImageUrl = post.getThumbnailUrl();
            }

            if (displayImageUrl != null && !displayImageUrl.isEmpty()) {
                postImage.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext())
                        .load(displayImageUrl)
                        .error(android.R.drawable.ic_menu_gallery)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .into(postImage);

                Integer imageIndex = postToImageIndexMap.get(position);
                if (imageIndex != null) {
                    final int index = imageIndex;
                    postImage.setOnClickListener(v -> {
                        if (interactionListener != null) {
                            interactionListener.onImageClick(index, allImageUrls);
                        }
                    });
                    
                    final String finalImageUrl = displayImageUrl;
                    postImage.setOnLongClickListener(v -> {
                        if (interactionListener != null) {
                            interactionListener.onImageLongClick(finalImageUrl);
                            return true;
                        }
                        return false;
                    });
                }
            } else {
                postImage.setVisibility(View.GONE);
            }
        }

        private ClickableSpan pressedSpan = null;

        private void setupTouchListener(SpannableString spannable) {
            postContent.setOnTouchListener((v, event) -> {
                int action = event.getAction();
                TextView widget = (TextView) v;

                if (action == MotionEvent.ACTION_DOWN) {
                    pressedSpan = null;
                    int x = (int) event.getX();
                    int y = (int) event.getY();

                    x -= widget.getTotalPaddingLeft();
                    y -= widget.getTotalPaddingTop();
                    x += widget.getScrollX();
                    y += widget.getScrollY();

                    Layout layout = widget.getLayout();
                    int line = layout.getLineForVertical(y);
                    int off = layout.getOffsetForHorizontal(line, x);

                    ClickableSpan[] links = spannable.getSpans(off, off, ClickableSpan.class);

                    if (links.length != 0) {
                        // Priority: Find a quote link first, otherwise take the first found
                        ClickableSpan target = links[0];
                        for (ClickableSpan link : links) {
                            int start = spannable.getSpanStart(link);
                            int end = spannable.getSpanEnd(link);
                            String text = spannable.subSequence(start, end).toString();
                            if (text.matches(">>?\\d+")) {
                                target = link;
                                break;
                            }
                        }
                        
                        pressedSpan = target;
                        int start = spannable.getSpanStart(pressedSpan);
                        int end = spannable.getSpanEnd(pressedSpan);
                        String linkText = spannable.subSequence(start, end).toString();
                        
                        Matcher matcher = Pattern.compile(">>?(\\d+)").matcher(linkText);
                        if (matcher.find()) {
                            String postId = matcher.group(1);
                            Integer targetPos = postIdToPositionMap.get(postId);
                            
                            if (targetPos != null) {
                                isLongPressed = false;
                                longPressRunnable = () -> {
                                    isLongPressed = true;
                                    v.getParent().requestDisallowInterceptTouchEvent(true);
                                    if (interactionListener != null) {
                                        interactionListener.onQuoteLongClick(posts.get(targetPos));
                                    }
                                };
                                handler.postDelayed(longPressRunnable, 500);
                                return true;
                            }
                        } else {
                            // URL ClickableSpan found
                            return true;
                        }
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    handler.removeCallbacks(longPressRunnable);
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    
                    if (isLongPressed) {
                        isLongPressed = false;
                        if (interactionListener != null) {
                            interactionListener.onQuoteReleased();
                        }
                        pressedSpan = null;
                        return true;
                    } else if (action == MotionEvent.ACTION_UP && pressedSpan != null) {
                        int start = spannable.getSpanStart(pressedSpan);
                        int end = spannable.getSpanEnd(pressedSpan);
                        String linkText = spannable.subSequence(start, end).toString();
                        Matcher upMatcher = Pattern.compile(">>?(\\d+)").matcher(linkText);
                        
                        if (upMatcher.find()) {
                            String postId = upMatcher.group(1);
                            Integer targetPos = postIdToPositionMap.get(postId);
                            if (targetPos != null && interactionListener != null) {
                                interactionListener.onQuoteClick(targetPos);
                                
                                if (recyclerView != null) {
                                    RecyclerView.ViewHolder targetHolder = recyclerView.findViewHolderForAdapterPosition(targetPos);
                                    if (targetHolder != null) {
                                        int highlightColor = androidx.core.content.ContextCompat.getColor(targetHolder.itemView.getContext(), R.color.highlight_background);
                                        targetHolder.itemView.setBackgroundColor(highlightColor);
                                        targetHolder.itemView.postDelayed(() -> {
                                            targetHolder.itemView.setBackgroundColor(0x00000000);
                                        }, 1000);
                                    }
                                }
                            }
                        } else {
                            // Trigger the original onClick for URLs
                            pressedSpan.onClick(widget);
                        }
                        pressedSpan = null;
                        return true;
                    }
                    pressedSpan = null;
                }
                return false;
            });
        }
    }
}