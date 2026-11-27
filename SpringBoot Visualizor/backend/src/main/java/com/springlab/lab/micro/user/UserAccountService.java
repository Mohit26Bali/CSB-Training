package com.springlab.lab.micro.user;

import com.springlab.lab.micro.contract.UserContract;
import com.springlab.lab.micro.contract.UserContract.GetUser;
import com.springlab.lab.micro.contract.UserContract.SeedUser;
import com.springlab.lab.micro.contract.UserContract.UserRef;
import com.springlab.lab.micro.contract.UserContract.UserView;
import com.springlab.lab.micro.core.CallContext;
import com.springlab.lab.micro.core.HandlerRegistry;
import com.springlab.lab.micro.core.ServiceModule;
import com.springlab.lab.micro.core.ServiceName;
import com.springlab.lab.micro.core.ServiceResult;
import com.springlab.lab.micro.run.MicroDb;
import org.springframework.stereotype.Service;

/** The User service: customers and their credit limit. It owns user data; other services ask it through {@link UserContract}. */
@Service
public class UserAccountService implements ServiceModule {

    private final UserAccountRepository repository;
    private final MicroDb db;

    UserAccountService(UserAccountRepository repository, MicroDb db) {
        this.repository = repository;
        this.db = db;
    }

    @Override
    public ServiceName service() {
        return ServiceName.USER;
    }

    @Override
    public void register(HandlerRegistry.Registrar registrar) {
        registrar.on(UserContract.SEED_USER, this::seedUser);
        registrar.on(UserContract.GET_USER, this::getUser);
    }

    ServiceResult<UserRef> seedUser(CallContext context, SeedUser request) {
        return ServiceResult.ok(new UserRef(db.local(ServiceName.USER, () -> repository.insert(request.name(), request.creditLimit()))));
    }

    ServiceResult<UserView> getUser(CallContext context, GetUser request) {
        return repository.find(request.userRef()).map(ServiceResult::ok).orElseGet(() -> ServiceResult.failure("NOT_FOUND"));
    }
}
