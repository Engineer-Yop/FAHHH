/*
package com.example.sanivend;

import android.content.Intent;
import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SalesMonitoringActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sales_monitoring);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.btn_sales_napkins).setOnClickListener(v -> {
            startActivity(new Intent(SalesMonitoringActivity.this, SalesNapkinsActivity.class));
        });

        findViewById(R.id.btn_sales_wipes).setOnClickListener(v -> {
            startActivity(new Intent(SalesMonitoringActivity.this, SalesWipesActivity.class));
        });

        findViewById(R.id.btn_sales_tissues).setOnClickListener(v -> {
            startActivity(new Intent(SalesMonitoringActivity.this, SalesTissuesActivity.class));
        });

        findViewById(R.id.btn_sales_sanitizers).setOnClickListener(v -> {
            startActivity(new Intent(SalesMonitoringActivity.this, SalesSanitizersActivity.class));
        });
    }
}
*/
