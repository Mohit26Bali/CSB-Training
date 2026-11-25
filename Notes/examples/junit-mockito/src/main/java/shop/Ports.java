package shop;

/** The outside world that OrderService depends on. Each one is replaced by a Mockito mock in the tests. */
public final class Ports {
    private Ports() { }

    public record PaymentResult(boolean approved, String reference) { }

    public interface PaymentGateway {
        PaymentResult charge(String customerId, int amountPaise);
    }

    public interface InventoryClient {
        boolean reserve(String sku, int quantity);
        void release(String sku, int quantity);
    }

    public interface EmailService {
        void send(String to, String subject, String body);
    }
}
