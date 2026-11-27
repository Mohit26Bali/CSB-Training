package com.springlab.lab.jpa;

import com.springlab.lab.jpa.entity.Department;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Lab-only repository. Each method is one way of loading the same departments, so the N+1 experiments can compare them. */
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    /** Plain query: employees are LAZY, so touching them later costs one extra query per department (N+1). */
    @Query("select d from Department d where d.runKey = :runKey and d.name like :prefix order by d.id")
    List<Department> findForRun(@Param("runKey") String runKey, @Param("prefix") String prefix);

    /** Fetch join in JPQL: departments and employees in one statement. */
    @Query("select distinct d from Department d join fetch d.employees where d.runKey = :runKey and d.name like :prefix order by d.id")
    List<Department> findForRunWithEmployeesJoinFetch(@Param("runKey") String runKey, @Param("prefix") String prefix);

    /** Same result through an EntityGraph attached to the query method. */
    @EntityGraph(attributePaths = "employees")
    @Query("select d from Department d where d.runKey = :runKey and d.name like :prefix order by d.id")
    List<Department> findForRunWithEmployeesGraph(@Param("runKey") String runKey, @Param("prefix") String prefix);
}
