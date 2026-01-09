package com.komica.reader;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.widget.SwitchCompat;

import android.content.SharedPreferences;
import android.widget.TextView;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private TextView tvListSize;
    private TextView tvPostSize;
    private SwitchCompat switchReplyWebViewSlim;

    private static final String PREFS_NAME = "KomicaReader";
    private static final String KEY_REPLY_WEBVIEW_SLIM = "reply_webview_slim";

    private static final String[] SIZE_LABELS = {"小", "正常", "大", "特大", "超大"};
    private static final float[] SIZE_VALUES = {14f, 16f, 18f, 20f, 24f};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        tvListSize = findViewById(R.id.tv_font_size_list_value);
        tvPostSize = findViewById(R.id.tv_font_size_post_value);
        switchReplyWebViewSlim = findViewById(R.id.switch_reply_webview_slim);

        updateLabels();

        findViewById(R.id.btn_font_size_list).setOnClickListener(v -> showSizeDialog("theme_font_size", tvListSize));
        findViewById(R.id.btn_font_size_post).setOnClickListener(v -> showSizeDialog("post_font_size", tvPostSize));
        findViewById(R.id.item_reply_webview_slim).setOnClickListener(v -> switchReplyWebViewSlim.toggle());

        boolean isSlimEnabled = prefs.getBoolean(KEY_REPLY_WEBVIEW_SLIM, true);
        switchReplyWebViewSlim.setChecked(isSlimEnabled);
        switchReplyWebViewSlim.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 繁體中文註解：儲存回覆頁資源精簡開關
            prefs.edit().putBoolean(KEY_REPLY_WEBVIEW_SLIM, isChecked).apply();
        });
        
        findViewById(R.id.btn_about).setOnClickListener(v -> showAboutDialog());
    }

    private void updateLabels() {
        float listSize = prefs.getFloat("theme_font_size", 16f);
        float postSize = prefs.getFloat("post_font_size", 16f);
        
        tvListSize.setText(getLabelForSize(listSize));
        tvPostSize.setText(getLabelForSize(postSize));
    }

    private String getLabelForSize(float size) {
        for (int i = 0; i < SIZE_VALUES.length; i++) {
            if (Math.abs(size - SIZE_VALUES[i]) < 0.1) {
                return SIZE_LABELS[i] + " (" + (int)size + "sp)";
            }
        }
        return "自訂 (" + size + "sp)";
    }

    private void showSizeDialog(String key, TextView labelView) {
        new AlertDialog.Builder(this)
            .setTitle("選擇字體大小")
            .setItems(SIZE_LABELS, (dialog, which) -> {
                float size = SIZE_VALUES[which];
                prefs.edit().putFloat(key, size).apply();
                updateLabels();
            })
            .show();
    }
    
    private void showAboutDialog() {
        String versionName = BuildConfig.VERSION_NAME;
        
        String message = "Komica Reader\n\n" +
                "專為瀏覽 Komica 匿名討論板設計的閱讀器。\n" +
                "提供流暢的閱讀體驗、圖片預覽與引文追蹤功能。\n\n" +
                "版本: " + versionName + "\n" +
                "開發者: echoli08\n\n" +
                "本軟體僅供學術研究與交流使用。";

        new AlertDialog.Builder(this)
                .setTitle("關於軟體")
                .setMessage(message)
                .setPositiveButton("關閉", (dialog, which) -> dialog.dismiss())
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
