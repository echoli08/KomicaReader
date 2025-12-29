package com.komica.reader;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import com.komica.reader.adapter.ImagePagerAdapter;
import java.util.ArrayList;
import java.util.List;

public class ImagePreviewActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private TextView imageCounter;
    private ImagePagerAdapter adapter;
    private List<String> imageUrls;
    private int currentPosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_preview);

        viewPager = findViewById(R.id.viewPager);
        imageCounter = findViewById(R.id.imageCounter);

        imageUrls = getIntent().getStringArrayListExtra("imageUrls");
        currentPosition = getIntent().getIntExtra("position", 0);

        if (imageUrls != null && !imageUrls.isEmpty()) {
            adapter = new ImagePagerAdapter(imageUrls);
            viewPager.setAdapter(adapter);
            viewPager.setCurrentItem(currentPosition, false);
            updateCounter();

            viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    super.onPageSelected(position);
                    updateCounter();
                }
            });
        } else {
            finish();
        }
    }

    private void updateCounter() {
        int current = viewPager.getCurrentItem() + 1;
        int total = imageUrls.size();
        imageCounter.setText(current + " / " + total);
    }
}
