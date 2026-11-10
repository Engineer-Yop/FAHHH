package com.example.sanivend;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Hand Sanitizers: price and expiry are saved to /devices/{id}/trays/tray_N in Firebase
 * of the open machine, so it picks up the change without a new upload.
 */
public class ProductSanitizersActivity extends BaseActivity {

    private TrayEditor trayEditor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!DeviceSession.requireDevice(this)) return;
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_product_sanitizers);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        trayEditor = new TrayEditor(this);

        // Tray 7
        trayEditor.bind(7, R.drawable.greencross,
                findViewById(R.id.img_hygienix), findViewById(R.id.tv_name_hygienix),
                findViewById(R.id.tv_price_hygienix), findViewById(R.id.tv_expiry_hygienix),
                findViewById(R.id.btn_edit_price_hygienix), findViewById(R.id.btn_edit_expiry_hygienix),
                findViewById(R.id.btn_replace_hygienix));

        // Tray 8
        trayEditor.bind(8, R.drawable.alcosafe,
                findViewById(R.id.img_alcosafe), findViewById(R.id.tv_name_alcosafe),
                findViewById(R.id.tv_price_alcosafe), findViewById(R.id.tv_expiry_alcosafe),
                findViewById(R.id.btn_edit_price_alcosafe), findViewById(R.id.btn_edit_expiry_alcosafe),
                findViewById(R.id.btn_replace_alcosafe));

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (trayEditor != null) trayEditor.release();
    }
}
