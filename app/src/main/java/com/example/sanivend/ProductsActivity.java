package com.example.sanivend;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ProductsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_products);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Category names: renamed by the owner (pencil button, or long-press the name)
        final TextView[] labels = {
                findViewById(R.id.label_sanitary_napkins), findViewById(R.id.label_wet_wipes),
                findViewById(R.id.label_facial_tissues), findViewById(R.id.label_hand_sanitizers)};
        final int[] pencils = {R.id.btn_rename_cat_1, R.id.btn_rename_cat_2,
                R.id.btn_rename_cat_3, R.id.btn_rename_cat_4};
        CategoryNames.follow(this, names -> CategoryNames.setTexts(names, labels));
        for (int i = 0; i < 4; i++) {
            final int index = i + 1;
            findViewById(pencils[i]).setOnClickListener(v -> CategoryNames.showRenameDialog(this, index));
            labels[i].setOnLongClickListener(v -> {
                CategoryNames.showRenameDialog(this, index);
                return true;
            });
        }

        findViewById(R.id.label_sanitary_napkins).setOnClickListener(v -> {
            Intent intent = new Intent(ProductsActivity.this, ProductNapkinsActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.label_wet_wipes).setOnClickListener(v -> {
            Intent intent = new Intent(ProductsActivity.this, ProductWipesActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.label_facial_tissues).setOnClickListener(v -> {
            Intent intent = new Intent(ProductsActivity.this, ProductTissuesActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.label_hand_sanitizers).setOnClickListener(v -> {
            Intent intent = new Intent(ProductsActivity.this, ProductSanitizersActivity.class);
            startActivity(intent);
        });
    }
}
