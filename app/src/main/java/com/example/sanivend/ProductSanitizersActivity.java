package com.example.sanivend;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ProductSanitizersActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_product_sanitizers);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Hygienix Edit Listeners
        findViewById(R.id.btn_edit_expiry_hygienix).setOnClickListener(v -> 
            showEditDialog("Edit Expiry Date", "Expiry: ", findViewById(R.id.tv_expiry_hygienix), "Expiration Date: ")
        );
        findViewById(R.id.btn_edit_price_hygienix).setOnClickListener(v -> 
            showEditDialog("Edit Price", "₱", findViewById(R.id.tv_price_hygienix), "Price: ₱")
        );

        // Alcosafe Edit Listeners
        findViewById(R.id.btn_edit_expiry_alcosafe).setOnClickListener(v -> 
            showEditDialog("Edit Expiry Date", "Expiry: ", findViewById(R.id.tv_expiry_alcosafe), "Expiration Date: ")
        );
        findViewById(R.id.btn_edit_price_alcosafe).setOnClickListener(v -> 
            showEditDialog("Edit Price", "₱", findViewById(R.id.tv_price_alcosafe), "Price: ₱")
        );
    }

    private void showEditDialog(String title, String hint, TextView targetView, String prefix) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);

        final EditText input = new EditText(this);
        input.setHint(hint);
        if (title.contains("Price")) {
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        } else {
            input.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
        }
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newValue = input.getText().toString();
            if (!newValue.isEmpty()) {
                targetView.setText(prefix + newValue);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, id) -> dialog.cancel());

        builder.show();
    }
}
