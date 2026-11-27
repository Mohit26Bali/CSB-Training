package com.cartnest.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cartnest.entity.Address;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByUserIdOrderByDefaultAddressDescCreatedAtDescIdDesc(Long userId);

    Optional<Address> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
