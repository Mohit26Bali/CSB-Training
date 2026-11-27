package com.springlab.lab.micro.contract;

import com.springlab.lab.micro.core.Operation;
import com.springlab.lab.micro.core.ServiceName;

import java.math.BigDecimal;

/** The public contract of the Product service. The business refusal code is NOT_FOUND. */
public final class ProductContract {

    private ProductContract() {
    }

    /** Lab fixture, not part of a real service API: creates a product and returns its reference. */
    public static final Operation<SeedProduct, ProductRef> SEED_PRODUCT = new Operation<>(ServiceName.PRODUCT, "seedProduct");
    public static final Operation<GetProduct, ProductView> GET_PRODUCT = new Operation<>(ServiceName.PRODUCT, "getProduct");

    /** Lab fixture: deletes a product. Nothing stops it even when orders refer to it: there is no foreign key across services. */
    public static final Operation<DeleteProduct, Ack> DELETE_PRODUCT = new Operation<>(ServiceName.PRODUCT, "deleteProduct");
    public static final Operation<ChangePrice, Ack> CHANGE_PRICE = new Operation<>(ServiceName.PRODUCT, "changePrice");

    public record DeleteProduct(long productRef) {
    }

    public record ChangePrice(long productRef, BigDecimal price) {
    }

    public record Ack(boolean done) {
    }

    public record SeedProduct(String sku, String name, BigDecimal price) {
    }

    public record ProductRef(long productRef) {
    }

    public record GetProduct(long productRef) {
    }

    public record ProductView(long productRef, String sku, String name, BigDecimal price) {
    }
}
