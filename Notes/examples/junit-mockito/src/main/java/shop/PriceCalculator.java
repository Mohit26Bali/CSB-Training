package shop;

public final class PriceCalculator {
    private PriceCalculator() { }

    /**
     * SAVE10: 10% off.  SAVE20: 20% off, only when the total is at least 100000 paise (1000 rupees).
     * FLAT500: 500 paise off, never below zero.  No code: no change.
     */
    public static int apply(int total, String code) {
        if (total < 0) throw new IllegalArgumentException("total cannot be negative");
        if (code == null || code.isBlank()) return total;
        return switch (code.trim().toUpperCase()) {
            case "SAVE10" -> total - total / 10;
            case "SAVE20" -> {
                if (total < 100_000) throw new IllegalArgumentException("SAVE20 needs a total of at least 100000");
                yield total - total / 5;
            }
            case "FLAT500" -> Math.max(0, total - 500);
            default -> throw new IllegalArgumentException("unknown code: " + code);
        };
    }
}
