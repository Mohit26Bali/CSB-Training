package com.cartnest.repository;

/** One row of a "group by id, count(*)" query. */
public record IdCount(Long id, Long total) {
}
