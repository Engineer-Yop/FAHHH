package com.example.sanivend;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Facial Tissues: price and expiry are saved to /devices/{id}/trays/tray_N in Firebase
 * of the open machine, so it picks up the change without a new upload.
 */
public class ProductTissuesActivity extends BaseActivity {

    private TrayEditor trayEditor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!DeviceSession.requireDevice(this)) return;
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_product_tissues);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        trayEditor = new TrayEditor(this);

        // Femme = Tray 5
        trayEditor.bind(5, "FEMME",
                findViewById(R.id.tv_price_femme), findViewById(R.id.tv_expiry_femme),
                findViewById(R.id.btn_edit_price_femme), findViewById(R.id.btn_edit_expiry_femme));

        // Blest = Tray 6
        trayEditor.bind(6, "BLEST",
                findViewById(R.id.tv_price_blest), findViewById(R.id.tv_expiry_blest),
                findViewById(R.id.btn_edit_price_blest), findViewById(R.id.btn_edit_expiry_blest));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (trayEditor != null) trayEditor.release();
    }
}
