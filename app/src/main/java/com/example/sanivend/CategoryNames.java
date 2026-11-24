package com.example.sanivend;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

/**
 * Names of the 4 product categories, which the owner can rename on the Products page
 * (for example "Sanitary Napkins" → "Feminine Care").
 *
 * Saved per machine in Firebase: /devices/{id}/categories/cat_1 … cat_4.
 * A missing value means the factory name (ProductCatalog.CATEGORIES). The tray → category
 * grouping never changes (trays 1–2, 3–4, 5–6, 7–8); only the shown names do.
 * The last names seen are kept on the phone too, so screens open with the right names
 * even before Firebase answers.
 */
public final class CategoryNames {

    public interface Listener {
        /** names[0..3] = categories 1–4, never null or empty. */
        void onNames(String[] names);
    }

    private static final String PREFS = "category_names";

    private CategoryNames() {}

    /** Factory name of category 1–4. */
    public static String defaultName(int index) {
        return ProductCatalog.CATEGORIES[index - 1];
    }

    /** Last known names of the open machine's 4 categories. */
    public static String[] cached(Context context) {
        String id = DeviceSession.id(context);
        SharedPreferences p = prefs(context);
        String[] names = new String[4];
        for (int i = 1; i <= 4; i++) {
            String n = id == null ? null : p.getString(id + "_" + i, null);
            names[i - 1] = (n == null || n.trim().isEmpty()) ? defaultName(i) : n;
        }
        return names;
    }

    /** Shown name of a category ID (e.g. ProductCatalog.WIPES); null stays null. */
    public static String display(Context context, String categoryId) {
        int i = ProductCatalog.categoryIndex(categoryId);
        return i == 0 ? categoryId : cached(context)[i - 1];
    }

    /**
     * Calls the listener now with the cached names, then again whenever they change
     * in Firebase, until the activity is destroyed.
     */
    public static void follow(AppCompatActivity activity, Listener listener) {
        listener.onNames(cached(activity));
        final String id = DeviceSession.id(activity);
        if (id == null) return;
        final DatabaseReference ref = DeviceSession.deviceRef(id).child("categories");
        final Context app = activity.getApplicationContext();
        final ValueEventListener l = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                SharedPreferences.Editor e = prefs(app).edit();
                for (int i = 1; i <= 4; i++) {
                    Object v = snapshot.child("cat_" + i).getValue();
                    String n = v == null ? "" : String.valueOf(v).trim();
                    if (n.isEmpty() || ProductCatalog.categoryNameProblem(n) != null) e.remove(id + "_" + i);
                    else e.putString(id + "_" + i, n);
                }
                e.apply();
                listener.onNames(cached(app));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        ref.addValueEventListener(l);
        activity.getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onDestroy(@NonNull LifecycleOwner owner) {
                ref.removeEventListener(l);
            }
        });
    }

    /** Puts names[i] into views[i]; null views are skipped. */
    public static void setTexts(String[] names, TextView... views) {
        for (int i = 0; i < views.length && i < names.length; i++) {
            if (views[i] != null) views[i].setText(names[i]);
        }
    }

    /** Rename dialog for category 1–4 of the open machine. */
    public static void showRenameDialog(AppCompatActivity activity, int index) {
        String id = DeviceSession.id(activity);
        if (id == null) return;
        String current = cached(activity)[index - 1];

        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setSingleLine(true);
        input.setText(current);
        input.setSelection(current.length());
        int pad = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        input.setPadding(pad, input.getPaddingTop(), pad, input.getPaddingBottom());

        final DatabaseReference ref = DeviceSession.deviceRef(id).child("categories").child("cat_" + index);
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Rename category " + index)
                .setMessage("Trays " + (index * 2 - 1) + "–" + (index * 2) + ". The new name shows on every screen.")
                .setView(input)
                .setPositiveButton("Save", null)
                .setNeutralButton("Reset to \"" + defaultName(index) + "\"", (d, w) ->
                        ref.removeValue().addOnFailureListener(e ->
                                Toast.makeText(activity, "Couldn't reset: " + e.getMessage(), Toast.LENGTH_LONG).show()))
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim().replaceAll("\\s+", " ");
            String problem = ProductCatalog.categoryNameProblem(name);
            if (problem != null) {
                input.setError(problem);
                return;
            }
            ref.setValue(name)
                    .addOnSuccessListener(x -> Toast.makeText(activity, "Renamed to " + name, Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e ->
                            Toast.makeText(activity, "Couldn't save: " + e.getMessage(), Toast.LENGTH_LONG).show());
            dialog.dismiss();
        }));
        dialog.show();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
