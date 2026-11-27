package com.hackathon.hotel.repo;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.hotel.entity.Booking;
import com.hackathon.hotel.entity.BookingStatus;
import com.hackathon.hotel.entity.Room;
import com.hackathon.hotel.entity.User;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /** Plain values, so the booking is not loaded before the room row is locked. */
    interface Peek {
        Long getRoomId();

        String getCustomerUsername();
    }

    @Query("select b.room.id as roomId, b.customer.username as customerUsername from Booking b where b.id = :id")
    Optional<Peek> peek(@Param("id") Long id);

    @Query("select b from Booking b where b.room = :room and b.status in :statuses and b.checkIn < :checkOut and b.checkOut > :checkIn")
    List<Booking> findOverlapping(@Param("room") Room room, @Param("statuses") Collection<BookingStatus> statuses,
                                  @Param("checkIn") LocalDate checkIn, @Param("checkOut") LocalDate checkOut);

    @Query("select b from Booking b join fetch b.room where b.status = :status and b.checkIn < :checkOut and b.checkOut > :checkIn")
    List<Booking> findAllOverlapping(@Param("status") BookingStatus status, @Param("checkIn") LocalDate checkIn, @Param("checkOut") LocalDate checkOut);

    @Query("select count(b) from Booking b where b.room = :room and b.customer = :customer and b.status in :statuses and b.checkIn < :checkOut and b.checkOut > :checkIn")
    long countOwnOverlapping(@Param("room") Room room, @Param("customer") User customer, @Param("statuses") Collection<BookingStatus> statuses,
                             @Param("checkIn") LocalDate checkIn, @Param("checkOut") LocalDate checkOut);

    boolean existsByRoomAndStatusInAndCheckOutAfter(Room room, Collection<BookingStatus> statuses, LocalDate date);

    @EntityGraph(attributePaths = { "room", "customer" })
    List<Booking> findByCustomerOrderByCheckInDesc(User customer);

    @EntityGraph(attributePaths = { "room", "customer" })
    List<Booking> findAllByOrderByCheckInAsc();

    @EntityGraph(attributePaths = { "room", "customer" })
    Optional<Booking> findWithRoomById(Long id);
}
