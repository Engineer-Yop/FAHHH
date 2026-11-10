package com.example.sanivend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Factory products, changed products, categories and the price/name rules. */
public class ProductCatalogTest {

    @Test
    public void defaultNames() {
        assertEquals("Sisters Sanitary Napkin", ProductCatalog.defaultName(1));
        assertEquals("Alcosafe Sanitizer", ProductCatalog.defaultName(8));
        assertEquals("Tray 9", ProductCatalog.defaultName(9));
    }

    @Test
    public void savedNameWinsButOldCapitalBrandIsExpanded() {
        assertEquals("Modess Napkin", ProductCatalog.name(1, "Modess Napkin"));      // changed product
        assertEquals("Sisters Sanitary Napkin", ProductCatalog.name(1, "SISTERS"));   // old app wrote brands
        assertEquals("Hygienix Sanitizer", ProductCatalog.name(7, null));
        assertEquals("Hygienix Sanitizer", ProductCatalog.name(7, "  "));
    }

    @Test
    public void categories() {
        assertEquals("Sanitary Napkins", ProductCatalog.category(2));
        assertEquals("Wet Wipes", ProductCatalog.category(3));
        assertEquals("Facial Tissues", ProductCatalog.category(6));
        assertEquals("Sanitizers", ProductCatalog.category(7));
        assertTrue(ProductCatalog.inCategory(8, "Sanitizers"));
        assertFalse(ProductCatalog.inCategory(1, "Sanitizers"));
        assertTrue(ProductCatalog.inCategory(1, null));          // all products
    }

    @Test
    public void pricesInStepsOfFive() {
        assertNull(ProductCatalog.priceProblem(5));
        assertNull(ProductCatalog.priceProblem(35));
        assertNull(ProductCatalog.priceProblem(500));
        assertNotNull(ProductCatalog.priceProblem(4));        // change is given in ₱5 coins
        assertNotNull(ProductCatalog.priceProblem(31));
        assertNotNull(ProductCatalog.priceProblem(0));
        assertNotNull(ProductCatalog.priceProblem(505));
    }

    @Test
    public void names() {
        assertNull(ProductCatalog.nameProblem("Modess Napkin"));
        assertNotNull(ProductCatalog.nameProblem(""));
        assertNotNull(ProductCatalog.nameProblem("A"));
        assertNotNull(ProductCatalog.nameProblem("Say \"hi\""));     // would break the machine's JSON
        assertNotNull(ProductCatalog.nameProblem("12345678901234567890123456789012345678901"));
    }
}
