package com.hackathon.vehicle.repo;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.vehicle.entity.BookingStatus;
import com.hackathon.vehicle.entity.ServiceBooking;
import com.hackathon.vehicle.entity.User;
import com.hackathon.vehicle.entity.Vehicle;

public interface ServiceBookingRepository extends JpaRepository<ServiceBooking, Long> {

    /** Plain values, so the booking is not loaded before the center row is locked. */
    interface Peek {
        String getCustomerUsername();
    }

    @Query("select b.customer.username as customerUsername from ServiceBooking b where b.id = :id")
    Optional<Peek> peek(@Param("id") Long id);

    @EntityGraph(attributePaths = { "vehicle", "customer", "serviceType" })
    @Query("select b from ServiceBooking b where b.status in :statuses and b.startDate <= :end and b.endDate >= :start")
    List<ServiceBooking> findLiveBetween(@Param("statuses") Collection<BookingStatus> statuses, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @EntityGraph(attributePaths = { "vehicle", "customer", "serviceType" })
    Optional<ServiceBooking> findDetailedById(Long id);

    @EntityGraph(attributePaths = { "vehicle", "customer", "serviceType" })
    List<ServiceBooking> findByCustomerOrderByStartDateDesc(User customer);

    @EntityGraph(attributePaths = { "vehicle", "customer", "serviceType" })
    List<ServiceBooking> findByVehicleOrderByStartDateDesc(Vehicle vehicle);

    @EntityGraph(attributePaths = { "vehicle", "customer", "serviceType" })
    List<ServiceBooking> findAllByOrderByStartDateAsc();

    boolean existsByVehicle(Vehicle vehicle);
}
