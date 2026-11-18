package com.rrvgreenbasket.delivery.util;

import java.util.Locale;

/**
 * Utility for normalizing geographic address fields (City, State, PIN code)
 * to ensure deterministic, case-insensitive, whitespace-tolerant matching.
 */
public final class AddressNormalizer {

    private AddressNormalizer() {}

    /**
     * Normalizes a text string by trimming, removing excessive whitespace,
     * and converting to canonical lowercase format for comparison.
     */
    public static String normalizeCanonical(String input) {
        if (input == null) {
            return "";
        }
        return input.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * Formats a text string in Title Case for display purposes.
     */
    public static String toTitleCase(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String clean = input.trim().replaceAll("\\s+", " ");
        String[] words = clean.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (!words[i].isEmpty()) {
                sb.append(Character.toUpperCase(words[i].charAt(0)));
                if (words[i].length() > 1) {
                    sb.append(words[i].substring(1).toLowerCase(Locale.ROOT));
                }
                if (i < words.length - 1) {
                    sb.append(" ");
                }
            }
        }
        return sb.toString();
    }

    /**
     * Normalizes an Indian PIN code by stripping spaces, hyphens, and non-digit characters.
     */
    public static String normalizePincode(String pincode) {
        if (pincode == null) {
            return null;
        }
        String clean = pincode.replaceAll("[^0-9]", "");
        return clean.isBlank() ? null : clean;
    }

    /**
     * Checks whether two city or state names match after normalization.
     */
    public static boolean matches(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return normalizeCanonical(a).equals(normalizeCanonical(b));
    }
}
