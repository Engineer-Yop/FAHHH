package com.example.sanivend;

import android.app.DatePickerDialog;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ServerValue;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Connects one product card (photo, name, price, expiry) to its tray in Firebase.
 *
 * The vending machine (ESP32 #1) reads the same paths, under its own ID:
 *   /devices/{id}/trays/tray_N/price    whole pesos in steps of 5, e.g. 15
 *   /devices/{id}/trays/tray_N/expiry   "yyyy-MM-dd", e.g. "2026-12-26"
 *   /devices/{id}/trays/tray_N/product  product name (shown in the app and the order records)
 *   /devices/{id}/trays/tray_N/stock    written by the ESP32 after every sale
 *   /devices/{id}/product_images/tray_N product photo, small Base64 JPEG (only after a change).
 *       Kept outside /trays on purpose: the ESP32 downloads /trays and has no room for photos.
 *
 * "Change product" replaces what a tray sells: the owner must give a photo, a name and a
 * price, all three, before it saves. The old expiry date is cleared because it belonged to
 * the old product.
 *
 * Create it in onCreate() (it registers the photo picker, which Android only allows there).
 */
public class TrayEditor {

    private final ComponentActivity activity;
    private final DatabaseReference trays;          // /devices/{open machine}/trays
    private final DatabaseReference images;         // /devices/{open machine}/product_images
    private final DatabaseReference machine;        // /devices/{open machine}
    private final List<DatabaseReference> refs = new ArrayList<>();
    private final List<ValueEventListener> listeners = new ArrayList<>();

    private final SimpleDateFormat dbDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat showDate = new SimpleDateFormat("dd/MM/yyyy", Locale.US);

    private final ActivityResultLauncher<PickVisualMediaRequest> photoPicker;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    /** Where the next picked photo goes (the open "Change product" dialog). */
    private PhotoTarget pendingPhoto;

    private interface PhotoTarget {
        void onPhoto(Uri uri);
    }

    public TrayEditor(ComponentActivity activity) {
        this.activity = activity;
        this.trays = DeviceSession.ref(activity, "trays");
        this.images = DeviceSession.ref(activity, "product_images");
        this.machine = DeviceSession.deviceRef(DeviceSession.id(activity));
        dbDate.setLenient(false);
        photoPicker = activity.registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(), uri -> {
                    PhotoTarget target = pendingPhoto;
                    pendingPhoto = null;
                    if (uri != null && target != null) target.onPhoto(uri);
                });
    }

    /**
     * Shows the tray's live photo, name, price and expiry and wires the edit buttons.
     * @param defaultImage the photo bundled with the app, used until the product is changed
     */
    public void bind(final int tray, @DrawableRes final int defaultImage,
                     final ImageView imageView, final TextView nameView,
                     final TextView priceView, final TextView expiryView,
                     View editPriceButton, View editExpiryButton, View replaceButton) {

        final DatabaseReference ref = trays.child("tray_" + tray);
        final int[] currentPrice = {0};
        final String[] currentExpiry = {""};
        final String[] currentName = {ProductCatalog.defaultName(tray)};
        final String[] shownImage = {null};      // avoids decoding the same photo again

        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                currentPrice[0] = toInt(snapshot.child("price").getValue());
                Object e = snapshot.child("expiry").getValue();
                currentExpiry[0] = (e == null) ? "" : String.valueOf(e);
                currentName[0] = ProductCatalog.name(tray, snapshot.child("product").getValue());

                if (nameView != null) nameView.setText(currentName[0]);
                priceView.setText(currentPrice[0] > 0
                        ? "Price: ₱" + currentPrice[0]
                        : "Price: not set");
                expiryView.setText(currentExpiry[0].isEmpty()
                        ? "Expiration Date: not set"
                        : "Expiration Date: " + toShowDate(currentExpiry[0]));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                toast("Can't read tray " + tray + ": " + error.getMessage());
            }
        };
        ref.addValueEventListener(listener);
        refs.add(ref);
        listeners.add(listener);

        // The photo lives in its own node (see the note at the top)
        final DatabaseReference imageRef = images.child("tray_" + tray);
        ValueEventListener imageListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Object img = snapshot.getValue();
                String imgText = img == null ? "" : String.valueOf(img);
                if (imageView == null || imgText.equals(shownImage[0])) return;
                shownImage[0] = imgText;
                Bitmap b = ProductImages.decode(imgText);
                if (b != null) imageView.setImageBitmap(b);
                else imageView.setImageResource(defaultImage);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        imageRef.addValueEventListener(imageListener);
        refs.add(imageRef);
        listeners.add(imageListener);

        editPriceButton.setOnClickListener(v -> showPriceDialog(tray, currentName[0], ref, currentPrice[0]));
        editExpiryButton.setOnClickListener(v -> showExpiryPicker(tray, currentName[0], ref, currentExpiry[0]));
        if (replaceButton != null) {
            replaceButton.setOnClickListener(v -> showReplaceDialog(tray, currentName[0]));
        }
    }

    /** Call from onDestroy() so the listeners stop when the screen closes. */
    public void release() {
        for (int i = 0; i < refs.size(); i++) {
            refs.get(i).removeEventListener(listeners.get(i));
        }
        refs.clear();
        listeners.clear();
        worker.shutdownNow();
    }

    // ── Change product (photo + name + price, all required) ──

    private void showReplaceDialog(final int tray, final String oldName) {
        final View form = activity.getLayoutInflater().inflate(R.layout.dialog_replace_product, null);
        final ImageView preview = form.findViewById(R.id.replace_image);
        final TextView hint = form.findViewById(R.id.replace_image_hint);
        final TextView imageError = form.findViewById(R.id.replace_image_error);
        final EditText nameInput = form.findViewById(R.id.replace_name);
        final EditText priceInput = form.findViewById(R.id.replace_price);
        final String[] photo = {null};            // Base64 JPEG once picked and shrunk

        form.findViewById(R.id.replace_image_box).setOnClickListener(v -> {
            pendingPhoto = uri -> {
                hint.setText("Loading…");
                hint.setVisibility(View.VISIBLE);
                worker.execute(() -> {
                    try {
                        final String b64 = ProductImages.encode(activity.getContentResolver(), uri);
                        final Bitmap shown = ProductImages.decode(b64);
                        main.post(() -> {
                            photo[0] = b64;
                            preview.setImageBitmap(shown);
                            hint.setVisibility(View.GONE);
                            imageError.setVisibility(View.GONE);
                        });
                    } catch (Exception ex) {
                        main.post(() -> {
                            hint.setText("Tap to choose\na photo");
                            imageError.setText("That photo can't be used. Pick another one.");
                            imageError.setVisibility(View.VISIBLE);
                        });
                    }
                });
            };
            photoPicker.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Change product — Tray " + tray)
                .setMessage("Replacing " + oldName + ". A photo, name and price are all required.")
                .setView(form)
                .setPositiveButton("Save", null)          // set below so a missing field keeps it open
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(v -> {
                boolean ok = true;
                if (photo[0] == null) {
                    imageError.setText("A photo is required");
                    imageError.setVisibility(View.VISIBLE);
                    ok = false;
                }
                String name = nameInput.getText().toString().trim();
                String nameProblem = ProductCatalog.nameProblem(name);
                if (nameProblem != null) {
                    nameInput.setError(nameProblem);
                    ok = false;
                }
                int price = -1;
                try {
                    price = Integer.parseInt(priceInput.getText().toString().trim());
                } catch (NumberFormatException ignored) {
                }
                String priceProblem = price < 0 ? "Enter the price in pesos." : ProductCatalog.priceProblem(price);
                if (priceProblem != null) {
                    priceInput.setError(priceProblem);
                    ok = false;
                }
                if (!ok) return;

                // One update for both nodes, so name, price and photo change together
                String t = "trays/tray_" + tray + "/";
                Map<String, Object> update = new HashMap<>();
                update.put(t + "product", name);
                update.put(t + "price", price);
                update.put(t + "expiry", null);                   // belonged to the old product
                update.put(t + "replaced_at", ServerValue.TIMESTAMP);
                update.put("product_images/tray_" + tray, photo[0]);
                update.put("notifications/expiry_tray_" + tray, null);
                final int newPrice = price;
                save.setEnabled(false);
                machine.updateChildren(update)
                        .addOnSuccessListener(x -> {
                            toast("Tray " + tray + " now sells " + name + " at ₱" + newPrice
                                    + ". Set its expiry date next.");
                            dialog.dismiss();
                        })
                        .addOnFailureListener(e -> {
                            save.setEnabled(true);
                            toast("Not saved: " + e.getMessage());
                        });
            });
        });
        dialog.show();
    }

    // ── Price ─────────────────────────────────────────

    private void showPriceDialog(final int tray, final String product,
                                 final DatabaseReference ref, int currentPrice) {
        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);   // whole pesos only
        input.setHint("₱ (steps of 5)");
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
                    String problem = ProductCatalog.priceProblem(price);
                    if (problem != null) {
                        toast(problem);
                        return;
                    }
                    ref.child("price").setValue(price)
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

            ref.child("expiry").setValue(expiry)
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
