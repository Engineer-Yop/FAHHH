package com.example.sanivend;

import android.os.Bundle;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class InventoryTrackingActivity extends BaseActivity {

    private DatabaseReference db;
    private ValueEventListener inventoryListener;
    private Map<Integer, TextView> trayViews = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_inventory_tracking);

        db = FirebaseDatabase.getInstance().getReference();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        setupTrayMappings();
        observeInventory();
    }

    private void setupTrayMappings() {
        trayViews.put(1, findViewById(R.id.tv_count_tray_1));
        trayViews.put(2, findViewById(R.id.tv_count_tray_2));
        trayViews.put(3, findViewById(R.id.tv_count_tray_3));
        trayViews.put(4, findViewById(R.id.tv_count_tray_4));
        trayViews.put(5, findViewById(R.id.tv_count_tray_5));
        trayViews.put(6, findViewById(R.id.tv_count_tray_6));
        trayViews.put(7, findViewById(R.id.tv_count_tray_7));
        trayViews.put(8, findViewById(R.id.tv_count_tray_8));
    }

    private void observeInventory() {
        // Listening to "inventory" node. 
        // ESP32 should update this node: inventory/tray_1, inventory/tray_2, etc.
        inventoryListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    try {
                        String key = child.getKey(); 
                        if (key != null && key.startsWith("tray_")) {
                            int trayNum = Integer.parseInt(key.replace("tray_", ""));
                            Object value = child.getValue();
                            String quantityStr = (value != null) ? String.valueOf(value) : "0";
                            
                            TextView tv = trayViews.get(trayNum);
                            if (tv != null) {
                                tv.setText(quantityStr);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        db.child("inventory").addValueEventListener(inventoryListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (inventoryListener != null) {
            db.child("inventory").removeEventListener(inventoryListener);
        }
    }
}
