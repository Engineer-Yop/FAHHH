package com.example.sanivend;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Wet Wipes: price and expiry are saved to /devices/{id}/trays/tray_N in Firebase
 * of the open machine, so it picks up the change without a new upload.
 */
public class ProductWipesActivity extends BaseActivity {

    private TrayEditor trayEditor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!DeviceSession.requireDevice(this)) return;
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_product_wipes);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        trayEditor = new TrayEditor(this);

        // Tray 3
        trayEditor.bind(3, R.drawable.kleenfant,
                findViewById(R.id.img_kleenfant), findViewById(R.id.tv_name_kleenfant),
                findViewById(R.id.tv_price_kleenfant), findViewById(R.id.tv_expiry_kleenfant),
                findViewById(R.id.btn_edit_price_kleenfant), findViewById(R.id.btn_edit_expiry_kleenfant),
                findViewById(R.id.btn_replace_kleenfant));

        // Tray 4
        trayEditor.bind(4, R.drawable.giggles,
                findViewById(R.id.img_giggles), findViewById(R.id.tv_name_giggles),
                findViewById(R.id.tv_price_giggles), findViewById(R.id.tv_expiry_giggles),
                findViewById(R.id.btn_edit_price_giggles), findViewById(R.id.btn_edit_expiry_giggles),
                findViewById(R.id.btn_replace_giggles));

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (trayEditor != null) trayEditor.release();
    }
}
