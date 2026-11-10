package com.example.sanivend;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Sanitary Napkins: photo, name, price and expiry are saved to /devices/{id}/trays/tray_N in Firebase
 * of the open machine, so it picks up the change without a new upload.
 */
public class ProductNapkinsActivity extends BaseActivity {

    private TrayEditor trayEditor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!DeviceSession.requireDevice(this)) return;
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_product_napkins);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        trayEditor = new TrayEditor(this);

        // Tray 1
        trayEditor.bind(1, R.drawable.sisters,
                findViewById(R.id.img_sisters), findViewById(R.id.tv_name_sisters),
                findViewById(R.id.tv_price_sisters), findViewById(R.id.tv_expiry_sisters),
                findViewById(R.id.btn_edit_price_sisters), findViewById(R.id.btn_edit_expiry_sisters),
                findViewById(R.id.btn_replace_sisters));

        // Tray 2
        trayEditor.bind(2, R.drawable.charmee,
                findViewById(R.id.img_charmee), findViewById(R.id.tv_name_charmee),
                findViewById(R.id.tv_price_charmee), findViewById(R.id.tv_expiry_charmee),
                findViewById(R.id.btn_edit_price_charmee), findViewById(R.id.btn_edit_expiry_charmee),
                findViewById(R.id.btn_replace_charmee));

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (trayEditor != null) trayEditor.release();
    }
}
