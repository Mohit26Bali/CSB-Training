/**
 * JWT plumbing for stateless login. No business logic here.
 *
 * <pre>
 * login:    AuthService --asks--> JwtService.generateToken --> token returned to the client
 * request:  Authorization: Bearer token
 *           -> JwtAuthenticationFilter (validates the token via JwtService, loads the user via CustomUserDetailsService)
 *           -> SecurityContext filled -> controller -> service
 * failure:  RestAuthenticationEntryPoint (401) / RestAccessDeniedHandler (403) -> JsonErrorWriter
 * </pre>
 * The filter chain that wires these classes together, and the list of public endpoints, is {@code config/SecurityConfig}.
 */
package com.csb.cinescope.security;
