package com.springlab.lab.micro.product;

import com.springlab.lab.micro.contract.ProductContract;
import com.springlab.lab.micro.contract.ProductContract.GetProduct;
import com.springlab.lab.micro.contract.ProductContract.ProductRef;
import com.springlab.lab.micro.contract.ProductContract.ProductView;
import com.springlab.lab.micro.contract.ProductContract.SeedProduct;
import com.springlab.lab.micro.core.CallContext;
import com.springlab.lab.micro.core.HandlerRegistry;
import com.springlab.lab.micro.core.ServiceModule;
import com.springlab.lab.micro.core.ServiceName;
import com.springlab.lab.micro.core.ServiceResult;
import com.springlab.lab.micro.run.MicroDb;
import org.springframework.stereotype.Service;

/** The Product service: the catalogue. It owns product data; other services ask it through {@link ProductContract}. */
@Service
public class ProductService implements ServiceModule {

    private final ProductRepository repository;
    private final MicroDb db;

    ProductService(ProductRepository repository, MicroDb db) {
        this.repository = repository;
        this.db = db;
    }

    @Override
    public ServiceName service() {
        return ServiceName.PRODUCT;
    }

    @Override
    public void register(HandlerRegistry.Registrar registrar) {
        registrar.on(ProductContract.SEED_PRODUCT, this::seedProduct);
        registrar.on(ProductContract.GET_PRODUCT, this::getProduct);
        registrar.on(ProductContract.DELETE_PRODUCT, this::deleteProduct);
        registrar.on(ProductContract.CHANGE_PRICE, this::changePrice);
    }

    ServiceResult<ProductContract.Ack> deleteProduct(CallContext context, ProductContract.DeleteProduct request) {
        int removed = db.local(ServiceName.PRODUCT, () -> repository.delete(request.productRef()));
        return removed == 0 ? ServiceResult.failure("NOT_FOUND") : ServiceResult.ok(new ProductContract.Ack(true));
    }

    ServiceResult<ProductContract.Ack> changePrice(CallContext context, ProductContract.ChangePrice request) {
        int changed = db.local(ServiceName.PRODUCT, () -> repository.updatePrice(request.productRef(), request.price()));
        return changed == 0 ? ServiceResult.failure("NOT_FOUND") : ServiceResult.ok(new ProductContract.Ack(true));
    }

    ServiceResult<ProductRef> seedProduct(CallContext context, SeedProduct request) {
        long ref = db.local(ServiceName.PRODUCT, () -> repository.insert(request.sku(), request.name(), request.price()));
        return ServiceResult.ok(new ProductRef(ref));
    }

    ServiceResult<ProductView> getProduct(CallContext context, GetProduct request) {
        return repository.find(request.productRef()).map(ServiceResult::ok).orElseGet(() -> ServiceResult.failure("NOT_FOUND"));
    }
}
