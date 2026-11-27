package com.hackathon.leave.repo;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.leave.entity.LeaveRequest;
import com.hackathon.leave.entity.LeaveStatus;
import com.hackathon.leave.entity.User;

import jakarta.persistence.LockModeType;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    @EntityGraph(attributePaths = "employee")
    List<LeaveRequest> findByEmployeeOrderByStartDateDesc(User employee);

    @EntityGraph(attributePaths = "employee")
    List<LeaveRequest> findAllByOrderBySubmittedAtDesc();

    /** This employee's requests in the given statuses that touch the date range [from, to]. */
    @Query("select r from LeaveRequest r where r.employee = :employee and r.status in :statuses "
            + "and r.startDate <= :to and r.endDate >= :from")
    List<LeaveRequest> findOverlapping(@Param("employee") User employee,
                                       @Param("statuses") Collection<LeaveStatus> statuses,
                                       @Param("from") LocalDate from, @Param("to") LocalDate to);

    @EntityGraph(attributePaths = "employee")
    @Query("select r from LeaveRequest r where r.status = com.hackathon.leave.entity.LeaveStatus.APPROVED "
            + "and r.startDate <= :to and r.endDate >= :from order by r.startDate")
    List<LeaveRequest> findApprovedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select r from LeaveRequest r where r.employee = :employee and r.status = "
            + "com.hackathon.leave.entity.LeaveStatus.PENDING and r.startDate >= :yearStart and r.startDate <= :yearEnd")
    List<LeaveRequest> findPendingInYear(@Param("employee") User employee, @Param("yearStart") LocalDate yearStart,
                                         @Param("yearEnd") LocalDate yearEnd);

    /**
     * Reads only plain values, so the request is NOT loaded into the persistence context. Loading it before
     * taking the row lock would leave a stale cached copy that the locked query then hands back unchanged.
     */
    @Query("select r.employee.id as employeeId, r.employee.username as employeeUsername, r.startDate as startDate "
            + "from LeaveRequest r where r.id = :id")
    Optional<Peek> peek(@Param("id") Long id);

    interface Peek {
        Long getEmployeeId();

        String getEmployeeUsername();

        LocalDate getStartDate();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LeaveRequest r where r.id = :id")
    Optional<LeaveRequest> findByIdForUpdate(@Param("id") Long id);
}
