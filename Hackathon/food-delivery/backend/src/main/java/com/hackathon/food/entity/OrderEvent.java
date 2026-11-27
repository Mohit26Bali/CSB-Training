package com.hackathon.food.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One line of an order's history: which status it entered, who did it and when. Append-only. */
@Entity
@Table(name = "order_events")
public class OrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The history goes with its order (database-level cascade, so deleting an order never leaves orphans or is blocked). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private FoodOrder order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrderStatus status;

    @Column(nullable = false, length = 100)
    private String actor;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    protected OrderEvent() {
    }

    public OrderEvent(FoodOrder order, OrderStatus status, String actor, LocalDateTime occurredAt) {
        this.order = order;
        this.status = status;
        this.actor = actor;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public FoodOrder getOrder() {
        return order;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getActor() {
        return actor;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
