package com.komica.reader;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("設定");
        }
        
        findViewById(R.id.btn_about).setOnClickListener(v -> showAboutDialog());
    }
    
    private void showAboutDialog() {
        String versionName = "Unknown";
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            e.printStackTrace();
        }
        
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