package com.springlab.security;

import com.springlab.entity.Role;

/** The only user columns needed to authorize a request (the password hash is deliberately not loaded). */
public record UserAuthInfo(String username, Role role, boolean enabled) {
}
