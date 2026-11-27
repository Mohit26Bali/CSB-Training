import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ProductService {

    private final Map<Integer, String> names = new TreeMap<>();
    private final Map<Integer, Double> prices = new HashMap<>();

    private void check(int id, String name, double price) {
        if (id <= 0 || name == null || name.trim().isEmpty() || price <= 0) {
            throw new IllegalArgumentException("invalid product");
        }
    }

    private void need(int id) {
        if (!names.containsKey(id)) {
            throw new ProductNotFoundException("Product " + id + " not found");
        }
    }

    public void create(int id, String name, double price) {
        check(id, name, price);
        if (names.containsKey(id)) {
            throw new DuplicateProductException("Product " + id + " already exists");
        }
        names.put(id, name);
        prices.put(id, price);
    }

    public String get(int id) {
        need(id);
        return names.get(id);
    }

    public double priceOf(int id) {
        need(id);
        return prices.get(id);
    }

    public void update(int id, String name, double price) {
        check(id, name, price);
        names.put(id, name);
        prices.put(id, price);
    }

    public void delete(int id) {
        need(id);
        names.remove(id);
        prices.remove(id);
    }

    public int count() {
        return names.size();
    }

    public List<String> names() {
        return new ArrayList<>(names.values());
    }
}
