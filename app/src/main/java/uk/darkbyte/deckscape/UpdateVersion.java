package uk.darkbyte.deckscape;

import java.util.Locale;

/** Parses and compares the stable {@code major.minor.patch} versions used by releases. */
final class UpdateVersion {
    private UpdateVersion() {}

    /** Returns a normalized version without a leading {@code v}. */
    static String normalize(String value) {
        int[] parts = parse(value);
        return String.format(Locale.ROOT, "%d.%d.%d", parts[0], parts[1], parts[2]);
    }

    /** Compares two stable versions component by component. */
    static int compare(String left, String right) {
        int[] a = parse(left);
        int[] b = parse(right);
        for (int index = 0; index < a.length; index++) {
            int comparison = Integer.compare(a[index], b[index]);
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    private static int[] parse(String value) {
        if (value == null) throw new IllegalArgumentException("Release version is missing");
        String candidate = value.trim();
        if (candidate.startsWith("v") || candidate.startsWith("V")) {
            candidate = candidate.substring(1);
        }
        String[] values = candidate.split("\\.", -1);
        if (values.length != 3) throw invalid(value);
        int[] result = new int[3];
        for (int index = 0; index < values.length; index++) {
            if (values[index].isEmpty() || values[index].length() > 7) throw invalid(value);
            for (int offset = 0; offset < values[index].length(); offset++) {
                if (!Character.isDigit(values[index].charAt(offset))) throw invalid(value);
            }
            try {
                result[index] = Integer.parseInt(values[index]);
            } catch (NumberFormatException exception) {
                throw invalid(value);
            }
        }
        return result;
    }

    private static IllegalArgumentException invalid(String value) {
        return new IllegalArgumentException("Unsupported release version: " + value);
    }
}
