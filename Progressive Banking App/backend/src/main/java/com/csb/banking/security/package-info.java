/**
 * JWT plumbing for stateless authentication. Contains no business logic.
 *
 * <pre>
 * login:    AuthService --asks--> JwtService.generateToken --> token returned to the client
 * request:  Authorization: Bearer token
 *           -> JwtAuthenticationFilter (validates token via JwtService, loads user via CustomUserDetailsService)
 *           -> SecurityContext filled -> controller -> service (@PreAuthorize)
 * failure:  RestAuthenticationEntryPoint (401) / RestAccessDeniedHandler (403) -> JsonErrorWriter
 * </pre>
 * The filter chain that wires these classes together is defined in {@code config/SecurityConfig}.
 */
package com.csb.banking.security;
