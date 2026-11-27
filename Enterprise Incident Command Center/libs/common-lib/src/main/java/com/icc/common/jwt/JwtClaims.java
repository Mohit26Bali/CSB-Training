package com.icc.common.jwt;

public record JwtClaims(Long userId, String username, Role role, Long teamId) {
}
