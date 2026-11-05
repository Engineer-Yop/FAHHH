package com.example.sanivend;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Wet Wipes: price and expiry are saved to /trays/tray_N in Firebase,
 * so the vending machine picks up the change without a new upload.
 */
public class ProductWipesActivity extends BaseActivity {

    private TrayEditor trayEditor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_product_wipes);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        trayEditor = new TrayEditor(this);

        // Kleenfant = Tray 3
        trayEditor.bind(3, "KLEENFANT",
                findViewById(R.id.tv_price_kleenfant), findViewById(R.id.tv_expiry_kleenfant),
                findViewById(R.id.btn_edit_price_kleenfant), findViewById(R.id.btn_edit_expiry_kleenfant));

        // Giggles = Tray 4
        trayEditor.bind(4, "GIGGLES",
                findViewById(R.id.tv_price_giggles), findViewById(R.id.tv_expiry_giggles),
                findViewById(R.id.btn_edit_price_giggles), findViewById(R.id.btn_edit_expiry_giggles));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (trayEditor != null) trayEditor.release();
    }
}
