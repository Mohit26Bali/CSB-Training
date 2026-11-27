package com.hackathon.leave.dto;

/** One leave type's balance for the year. "available" already subtracts days reserved by pending requests. */
public record BalanceResponse(String leaveType, String label, boolean tracked, int allocated, int used, int pending,
                              int available) {
}
