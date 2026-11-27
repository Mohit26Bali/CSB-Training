package com.hackathon.food.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hackathon.food.entity.OrderEvent;

public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {

    List<OrderEvent> findByOrderIdOrderByIdAsc(Long orderId);
}
