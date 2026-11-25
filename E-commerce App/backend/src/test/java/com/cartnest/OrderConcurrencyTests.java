package com.cartnest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.cartnest.dto.AddressRequest;
import com.cartnest.dto.CartAddRequest;
import com.cartnest.dto.PlaceOrderRequest;
import com.cartnest.dto.RegisterRequest;
import com.cartnest.entity.AddressType;
import com.cartnest.entity.PaymentMethod;
import com.cartnest.entity.ProductVariant;
import com.cartnest.exception.ConflictException;
import com.cartnest.repository.ProductVariantRepository;
import com.cartnest.repository.UserRepository;
import com.cartnest.security.SecurityUser;
import com.cartnest.service.AddressService;
import com.cartnest.service.AuthService;
import com.cartnest.service.CartService;
import com.cartnest.service.OrderService;

/** Several customers press "Place order" for the last unit at the same moment: exactly one may get it. */
class OrderConcurrencyTests extends ApiTestSupport {

    private static final int BUYERS = 4;

    @Autowired
    private AuthService authService;
    @Autowired
    private AddressService addressService;
    @Autowired
    private CartService cartService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProductVariantRepository variantRepository;

    @Test
    void theLastUnitIsSoldExactlyOnce() throws Exception {
        ProductVariant lastOne = variantRepository.findAll().stream()
                .filter(v -> v.getSku().startsWith("KIT-") && v.getStock() > 1).findFirst().orElseThrow();
        int original = lastOne.getStock();
        lastOne.setStock(1);
        variantRepository.save(lastOne);
        try {
            List<Callable<String>> buyers = new ArrayList<>();
            CountDownLatch start = new CountDownLatch(1);
            for (int i = 0; i < BUYERS; i++) {
                SecurityUser buyer = newBuyer();
                Long address = addressService.create(buyer.getId(), new AddressRequest("Buyer", "9876543210",
                        "1 Main Road", null, null, "Pune", "Maharashtra", "411001", AddressType.HOME, null)).id();
                cartService.add(buyer.getId(), new CartAddRequest(lastOne.getId(), 1));
                PlaceOrderRequest request = new PlaceOrderRequest(address, PaymentMethod.CARD, null, null);
                buyers.add(() -> {
                    start.await();
                    try {
                        return orderService.placeOrder(buyer, request).orderNumber();
                    } catch (ConflictException ex) {
                        return "REFUSED: " + ex.getMessage();
                    }
                });
            }

            ExecutorService pool = Executors.newFixedThreadPool(BUYERS);
            List<Future<String>> results = new ArrayList<>();
            buyers.forEach(b -> results.add(pool.submit(b)));
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> f : results) {
                outcomes.add(f.get());
            }
            pool.shutdown();

            assertThat(outcomes.stream().filter(o -> o.startsWith("CN")).count()).isEqualTo(1);
            assertThat(outcomes.stream().filter(o -> o.startsWith("REFUSED")).count()).isEqualTo(BUYERS - 1);
            assertThat(outcomes).filteredOn(o -> o.startsWith("REFUSED")).allMatch(o -> o.endsWith("is out of stock"));
            assertThat(variantRepository.findById(lastOne.getId()).orElseThrow().getStock()).isZero();
        } finally {
            ProductVariant v = variantRepository.findById(lastOne.getId()).orElseThrow();
            v.setStock(original);
            variantRepository.save(v);
        }
    }

    private SecurityUser newBuyer() {
        String email = "buyer-" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest("Buyer", email, PASSWORD, null));
        return new SecurityUser(userRepository.findByEmail(email).orElseThrow());
    }
}
