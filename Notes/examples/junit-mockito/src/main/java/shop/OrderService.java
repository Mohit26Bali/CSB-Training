package shop;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import shop.Ports.EmailService;
import shop.Ports.InventoryClient;
import shop.Ports.PaymentGateway;
import shop.Ports.PaymentResult;

public class OrderService {

    public record Receipt(String orderId, int amount, String paymentReference, Instant placedAt) { }

    public static class OutOfStockException extends RuntimeException {
        public OutOfStockException(String sku) { super("out of stock: " + sku); }
    }

    public static class PaymentDeclinedException extends RuntimeException {
        public PaymentDeclinedException(String customerId) { super("payment declined for " + customerId); }
    }

    private final PaymentGateway payments;
    private final InventoryClient inventory;
    private final EmailService email;
    private final Clock clock;

    public OrderService(PaymentGateway payments, InventoryClient inventory, EmailService email, Clock clock) {
        this.payments = payments;
        this.inventory = inventory;
        this.email = email;
        this.clock = clock;
    }

    public Receipt placeOrder(String customerId, String customerEmail, Cart cart, String discountCode) {
        if (cart.isEmpty()) throw new IllegalArgumentException("cart is empty");

        // 1. reserve stock, undoing the reservations already made if one item is not available
        List<Cart.Item> reserved = new ArrayList<>();
        for (Cart.Item item : cart.items()) {
            if (!inventory.reserve(item.sku(), item.quantity())) {
                reserved.forEach(r -> inventory.release(r.sku(), r.quantity()));
                throw new OutOfStockException(item.sku());
            }
            reserved.add(item);
        }

        // 2. take the payment, giving the stock back if it is declined
        int amount = cart.totalWithDiscount(discountCode);
        PaymentResult result = payments.charge(customerId, amount);
        if (!result.approved()) {
            reserved.forEach(r -> inventory.release(r.sku(), r.quantity()));
            throw new PaymentDeclinedException(customerId);
        }

        // 3. tell the customer
        Receipt receipt = new Receipt(UUID.randomUUID().toString(), amount, result.reference(), clock.instant());
        email.send(customerEmail, "Order " + receipt.orderId(), "Thank you. You paid " + amount + " paise.");
        return receipt;
    }
}
