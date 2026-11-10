package com.example.sanivend;

/**
 * The factory products of the 8 trays, used until the owner changes a tray's product
 * on the Products page. After a change, the new name, price and photo live in Firebase
 * under /devices/{id}/trays/tray_N/{product, price, image} and every screen shows those.
 *
 * Plain Java (no Android code), so it is covered by ProductCatalogTest.
 */
public final class ProductCatalog {

    public static final int TRAY_COUNT = 8;

    private static final String[] NAMES = {
            "",
            "Sisters Sanitary Napkin",   // 1
            "Charmee Sanitary Napkin",   // 2
            "Kleenfant Wet Wipes",       // 3
            "Giggles Wet Wipes",         // 4
            "Femme Facial Tissue",       // 5
            "Blest Facial Tissue",       // 6
            "Hygienix Sanitizer",        // 7
            "Alcosafe Sanitizer"         // 8
    };

    public static final String NAPKINS = "Sanitary Napkins";
    public static final String WIPES = "Wet Wipes";
    public static final String TISSUES = "Facial Tissues";
    public static final String SANITIZERS = "Sanitizers";

    private ProductCatalog() {}

    /** Factory product name of a tray, e.g. tray 7 → "Hygienix Sanitizer". */
    public static String defaultName(int tray) {
        return (tray >= 1 && tray <= TRAY_COUNT) ? NAMES[tray] : "Tray " + tray;
    }

    /**
     * The name to show: the one saved in Firebase if the owner changed the product,
     * otherwise the factory name.
     */
    public static String name(int tray, Object savedName) {
        String s = savedName == null ? "" : String.valueOf(savedName).trim();
        // Older app versions saved the brand in capitals ("SISTERS"); show the full name instead
        if (s.isEmpty() || s.equalsIgnoreCase(brand(defaultName(tray)))) return defaultName(tray);
        return s;
    }

    /** Category of a tray: 1–2 napkins, 3–4 wipes, 5–6 tissues, 7–8 sanitizers. */
    public static String category(int tray) {
        if (tray <= 2) return NAPKINS;
        if (tray <= 4) return WIPES;
        if (tray <= 6) return TISSUES;
        return SANITIZERS;
    }

    /** True when the tray belongs to the category (null category = all products). */
    public static boolean inCategory(int tray, String category) {
        return category == null || category.equals(category(tray));
    }

    /** Prices: whole pesos in steps of ₱5, because the machine gives change in ₱5 coins. */
    public static final int PRICE_STEP = 5;
    public static final int MIN_PRICE = 5;
    public static final int MAX_PRICE = 500;

    /** Why a price can't be used, or null when it's fine. */
    public static String priceProblem(int price) {
        if (price < MIN_PRICE || price > MAX_PRICE) return "Price must be ₱" + MIN_PRICE + " to ₱" + MAX_PRICE + ".";
        if (price % PRICE_STEP != 0) return "Use steps of ₱5 (₱5, ₱10, ₱15…): the machine gives change in ₱5 coins.";
        return null;
    }

    /** Why a product name can't be used, or null when it's fine. */
    public static String nameProblem(String name) {
        String n = name == null ? "" : name.trim();
        if (n.length() < 2) return "Enter the product name.";
        if (n.length() > 40) return "Keep the name under 40 characters.";
        if (n.contains("\"") || n.contains("\\")) return "Leave out quotes (\") and backslashes (\\).";
        return null;
    }

    /** First word of a name: "Kleenfant Wet Wipes" → "Kleenfant". */
    static String brand(String name) {
        int sp = name.indexOf(' ');
        return sp < 0 ? name : name.substring(0, sp);
    }
}
