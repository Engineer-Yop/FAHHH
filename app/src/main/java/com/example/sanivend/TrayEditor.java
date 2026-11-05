package com.example.sanivend;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Connects one product card (price + expiry) to its tray in Firebase.
 *
 * The vending machine (ESP32 #1) reads the same paths, under its own ID:
 *   /devices/{id}/trays/tray_N/price    whole pesos, e.g. 30
 *   /devices/{id}/trays/tray_N/expiry   "yyyy-MM-dd", e.g. "2026-12-26"
 *   /devices/{id}/trays/tray_N/product  product name (for people reading the database)
 *   /devices/{id}/trays/tray_N/stock    written by the ESP32 after every sale
 *
 * Saving a price here changes it on the machine without uploading a new program:
 * the ESP32 reads the tray's price every time a customer presses that tray's button,
 * and also re-syncs all trays every minute while idle.
 */
public class TrayEditor {

    /** The coin acceptor counts whole pesos, so prices are whole numbers. */
    public static final int MIN_PRICE = 1;
    public static final int MAX_PRICE = 500;

    private final Activity activity;
    private final DatabaseReference trays;          // /devices/{open machine}/trays
    private final List<DatabaseReference> refs = new ArrayList<>();
    private final List<ValueEventListener> listeners = new ArrayList<>();

    private final SimpleDateFormat dbDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat showDate = new SimpleDateFormat("dd/MM/yyyy", Locale.US);

    public TrayEditor(Activity activity) {
        this.activity = activity;
        this.trays = DeviceSession.ref(activity, "trays");
        dbDate.setLenient(false);
    }

    /**
     * Shows the tray's live price and expiry in the two TextViews and makes the
     * two edit buttons save straight to Firebase.
     */
    public void bind(final int tray, final String product,
                     final TextView priceView, final TextView expiryView,
                     View editPriceButton, View editExpiryButton) {

        final DatabaseReference ref = trays.child("tray_" + tray);
        final int[] currentPrice = {0};
        final String[] currentExpiry = {""};

        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                currentPrice[0] = toInt(snapshot.child("price").getValue());
                Object e = snapshot.child("expiry").getValue();
                currentExpiry[0] = (e == null) ? "" : String.valueOf(e);

                priceView.setText(currentPrice[0] > 0
                        ? "Price: ₱" + currentPrice[0]
                        : "Price: not set");
                expiryView.setText(currentExpiry[0].isEmpty()
                        ? "Expiration Date: not set"
                        : "Expiration Date: " + toShowDate(currentExpiry[0]));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(activity, "Can't read tray " + tray + ": " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        };
        ref.addValueEventListener(listener);
        refs.add(ref);
        listeners.add(listener);

        editPriceButton.setOnClickListener(v -> showPriceDialog(tray, product, ref, currentPrice[0]));
        editExpiryButton.setOnClickListener(v -> showExpiryPicker(tray, product, ref, currentExpiry[0]));
    }

    /** Call from onDestroy() so the listeners stop when the screen closes. */
    public void release() {
        for (int i = 0; i < refs.size(); i++) {
            refs.get(i).removeEventListener(listeners.get(i));
        }
        refs.clear();
        listeners.clear();
    }

    // ── Price ─────────────────────────────────────────

    private void showPriceDialog(final int tray, final String product,
                                 final DatabaseReference ref, int currentPrice) {
        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);   // whole pesos only
        input.setHint("₱");
        if (currentPrice > 0) {
            input.setText(String.valueOf(currentPrice));
            input.setSelection(input.getText().length());
        }

        new AlertDialog.Builder(activity)
                .setTitle("Edit Price — " + product + " (Tray " + tray + ")")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    int price;
                    try {
                        price = Integer.parseInt(input.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        toast("Enter a whole number of pesos.");
                        return;
                    }
                    if (price < MIN_PRICE || price > MAX_PRICE) {
                        toast("Price must be ₱" + MIN_PRICE + " to ₱" + MAX_PRICE + ".");
                        return;
                    }
                    Map<String, Object> update = new HashMap<>();
                    update.put("price", price);
                    update.put("product", product);
                    ref.updateChildren(update)
                            .addOnSuccessListener(x -> toast(product + " is now ₱" + price
                                    + ". The machine uses it on the next selection."))
                            .addOnFailureListener(e -> toast("Price not saved: " + e.getMessage()));
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.cancel())
                .show();
    }

    // ── Expiry ────────────────────────────────────────

    private void showExpiryPicker(final int tray, final String product,
                                  final DatabaseReference ref, String currentExpiry) {
        Calendar start = Calendar.getInstance();
        Date parsed = parseDbDate(currentExpiry);
        if (parsed != null) start.setTime(parsed);

        new DatePickerDialog(activity, (view, year, month, day) -> {
            Calendar picked = Calendar.getInstance();
            picked.clear();
            picked.set(year, month, day);
            final String expiry = dbDate.format(picked.getTime());   // "yyyy-MM-dd" for the ESP32

            Map<String, Object> update = new HashMap<>();
            update.put("expiry", expiry);
            update.put("product", product);
            ref.updateChildren(update)
                    .addOnSuccessListener(x -> {
                        // Old alert no longer applies; the ESP32 writes a new one if still near expiry.
                        DeviceSession.ref(activity, "notifications")
                                .child("expiry_tray_" + tray)
                                .removeValue();
                        toast(product + " expiry set to " + toShowDate(expiry) + ".");
                    })
                    .addOnFailureListener(e -> toast("Expiry not saved: " + e.getMessage()));
        }, start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DAY_OF_MONTH))
                .show();
    }

    // ── Helpers ───────────────────────────────────────

    /** Firebase may hand back Long, Double or String depending on who wrote the value. */
    public static int toInt(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return (int) Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private Date parseDbDate(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return dbDate.parse(s);
        } catch (ParseException e) {
            return null;
        }
    }

    private String toShowDate(String dbValue) {
        Date d = parseDbDate(dbValue);
        return (d == null) ? dbValue : showDate.format(d);
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }
}
