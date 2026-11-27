package com.hackathon.leave.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.leave.entity.LeaveBalance;
import com.hackathon.leave.entity.User;

import jakarta.persistence.LockModeType;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

    List<LeaveBalance> findByUserAndLeaveYearOrderByLeaveType(User user, int leaveYear);

    /**
     * Locks all of one employee's balance rows for the year (always in the same order). Every operation that
     * changes or depends on that employee's leave takes this lock first, so their requests are handled one at a time.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from LeaveBalance b where b.user = :user and b.leaveYear = :year order by b.leaveType")
    List<LeaveBalance> lockForEmployee(@Param("user") User user, @Param("year") int year);
}
