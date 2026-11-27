package com.hackathon.asset.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.asset.entity.Asset;

import jakarta.persistence.LockModeType;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    Optional<Asset> findByAssetTag(String assetTag);

    Optional<Asset> findBySerialNumber(String serialNumber);

    List<Asset> findAllByOrderByAssetTagAsc();

    /** Row lock: assigning, returning, editing and status changes for one asset are handled one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Asset a where a.id = :id")
    Optional<Asset> findByIdForUpdate(@Param("id") Long id);
}
