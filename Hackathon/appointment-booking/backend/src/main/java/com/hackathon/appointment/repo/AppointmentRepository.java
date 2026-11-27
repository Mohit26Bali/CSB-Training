package com.hackathon.appointment.repo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.appointment.entity.Appointment;
import com.hackathon.appointment.entity.AppointmentStatus;
import com.hackathon.appointment.entity.Provider;
import com.hackathon.appointment.entity.Slot;
import com.hackathon.appointment.entity.User;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /** Plain values, so the appointment is not loaded before the slot row is locked. */
    interface Peek {
        Long getSlotId();

        String getCustomerUsername();

        String getProviderUsername();
    }

    @Query("select a.slot.id as slotId, a.customer.username as customerUsername, a.slot.provider.user.username as providerUsername from Appointment a where a.id = :id")
    Optional<Peek> peek(@Param("id") Long id);

    @EntityGraph(attributePaths = { "slot", "slot.provider", "slot.provider.user", "customer" })
    Optional<Appointment> findDetailedById(Long id);

    boolean existsBySlotAndStatusIn(Slot slot, Collection<AppointmentStatus> statuses);

    /** The customer's own live appointments that overlap [start, end), whoever the provider is. */
    @Query("select count(a) from Appointment a where a.customer = :customer and a.status in :statuses and a.slot.startTime < :end and a.slot.endTime > :start")
    long countOwnOverlapping(@Param("customer") User customer, @Param("statuses") Collection<AppointmentStatus> statuses,
                             @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @EntityGraph(attributePaths = { "slot", "slot.provider", "slot.provider.user", "customer" })
    @Query("select a from Appointment a where a.status in :statuses and a.slot.startTime >= :from and a.slot.startTime < :to")
    List<Appointment> findLiveBetween(@Param("statuses") Collection<AppointmentStatus> statuses, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @EntityGraph(attributePaths = { "slot", "slot.provider", "slot.provider.user", "customer" })
    @Query("select a from Appointment a where a.slot.provider = :provider and a.slot.startTime >= :from and a.slot.startTime < :to")
    List<Appointment> findForProviderBetween(@Param("provider") Provider provider, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @EntityGraph(attributePaths = { "slot", "slot.provider", "slot.provider.user", "customer" })
    @Query("select a from Appointment a where a.slot.provider = :provider order by a.slot.startTime")
    List<Appointment> findAllForProvider(@Param("provider") Provider provider);

    @EntityGraph(attributePaths = { "slot", "slot.provider", "slot.provider.user", "customer" })
    List<Appointment> findByCustomerOrderBySlotStartTimeAsc(User customer);
}
