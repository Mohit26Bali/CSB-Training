package shop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A shopping cart. Prices are whole paise (1 rupee = 100 paise) so there is no floating-point rounding. */
public class Cart {

    public record Item(String sku, int unitPrice, int quantity) {
        public Item {
            if (sku == null || sku.isBlank()) throw new IllegalArgumentException("sku is required");
            if (unitPrice < 0) throw new IllegalArgumentException("price cannot be negative");
            if (quantity < 1) throw new IllegalArgumentException("quantity must be at least 1");
        }
        public int lineTotal() { return unitPrice * quantity; }
    }

    private final List<Item> items = new ArrayList<>();

    public void add(String sku, int unitPrice, int quantity) {
        items.add(new Item(sku, unitPrice, quantity));
    }

    public boolean remove(String sku) {
        return items.removeIf(i -> i.sku().equals(sku));
    }

    public List<Item> items() { return Collections.unmodifiableList(items); }

    public boolean isEmpty() { return items.isEmpty(); }

    public int total() { return items.stream().mapToInt(Item::lineTotal).sum(); }

    /** Total after a discount code. Unknown codes are an error, not silently ignored. */
    public int totalWithDiscount(String code) {
        return PriceCalculator.apply(total(), code);
    }
}
