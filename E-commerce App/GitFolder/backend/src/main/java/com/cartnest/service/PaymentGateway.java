package com.cartnest.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.cartnest.entity.PaymentMethod;
import com.cartnest.exception.PaymentFailedException;

@Service
public class PaymentGateway {
    private static final Logger log = LoggerFactory.getLogger(PaymentGateway.class);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public String charge(PaymentMethod method, BigDecimal amount, String upiId) {
        if (method == PaymentMethod.UPI && upiId != null && upiId.toLowerCase(Locale.ROOT).endsWith("@fail")) {
            throw new PaymentFailedException("Payment was declined by your bank. You have not been charged");
        }
        String reference = "PAY" + randomCode(12);
        log.info("Simulated {} payment of ₹{} accepted, reference {}", method, amount, reference);
        return reference;
    }

    public String refund(String paymentReference, BigDecimal amount) {
        String reference = "RFD" + randomCode(12);
        log.info("Simulated refund of ₹{} for {}, reference {}", amount, paymentReference, reference);
        return reference;
    }

    static String randomCode(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
