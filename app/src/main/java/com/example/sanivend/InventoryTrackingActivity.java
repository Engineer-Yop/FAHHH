package com.example.sanivend;

import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class InventoryTrackingActivity extends BaseActivity {

    private DatabaseReference db;
    private ValueEventListener inventoryListener;
    private Map<Integer, TextView> trayViews = new HashMap<>();
    private final Map<Integer, Integer> capacities = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!DeviceSession.requireDevice(this)) return;
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_inventory_tracking);

        db = DeviceSession.ref(this, "trays");          // /devices/{open machine}/trays

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        setupTrayMappings();
        setupRefill();
        observeInventory();
    }

    /** Tap a tray's count to refill it (or correct it) after restocking the machine. */
    private void setupRefill() {
        for (Map.Entry<Integer, TextView> e : trayViews.entrySet()) {
            final int tray = e.getKey();
            if (e.getValue() == null) continue;
            e.getValue().setOnClickListener(v -> showRefillDialog(tray));
        }
    }

    private void showRefillDialog(final int tray) {
        final int capacity = capacities.containsKey(tray) ? capacities.get(tray) : TrayAlerts.DEFAULT_CAPACITY;
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("0 to " + capacity);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, input.getPaddingTop(), pad, input.getPaddingBottom());

        new AlertDialog.Builder(this)
                .setTitle("Tray " + tray + " stock")
                .setMessage("After restocking, set how many items are in the tray.")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    int n;
                    try {
                        n = Integer.parseInt(input.getText().toString().trim());
                    } catch (NumberFormatException ex) {
                        Toast.makeText(this, "Enter a number", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveStock(tray, n, capacity);
                })
                .setNeutralButton("Fill to " + capacity, (d, w) -> saveStock(tray, capacity, capacity))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveStock(int tray, int count, int capacity) {
        if (count < 0 || count > capacity) {
            Toast.makeText(this, "Stock must be 0 to " + capacity, Toast.LENGTH_LONG).show();
            return;
        }
        db.child("tray_" + tray).child("stock").setValue(count)
                .addOnSuccessListener(x -> Toast.makeText(this,
                        "Tray " + tray + " set to " + count + ". The machine picks it up within a minute.",
                        Toast.LENGTH_LONG).show())
                .addOnFailureListener(ex -> Toast.makeText(this,
                        "Not saved: " + ex.getMessage(), Toast.LENGTH_LONG).show());
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
        // The ESP32 writes the count after every sale to /devices/{id}/trays/tray_N/stock
        inventoryListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    try {
                        String key = child.getKey();
                        if (key != null && key.startsWith("tray_")) {
                            int trayNum = Integer.parseInt(key.replace("tray_", ""));
                            Object cap = child.child("capacity").getValue();
                            if (cap != null) capacities.put(trayNum, TrayEditor.toInt(cap));
                            Object value = child.child("stock").getValue();
                            String quantityStr = (value != null)
                                    ? String.valueOf(TrayEditor.toInt(value)) : "0";

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
        db.addValueEventListener(inventoryListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (db != null && inventoryListener != null) {
            db.removeEventListener(inventoryListener);
        }
    }
}
