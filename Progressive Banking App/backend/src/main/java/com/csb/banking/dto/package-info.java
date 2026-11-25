/**
 * Data Transfer Objects: the JSON shapes of the REST API, written as immutable records.
 *
 * <ul>
 *   <li><b>Requests</b> (LoginRequest, RegisterRequest, CreateAccountRequest, UpdateAccountStatusRequest) carry Bean
 *       Validation annotations; {@code controller/} triggers them with {@code @Valid} and
 *       {@code exception/GlobalExceptionHandler} reports violations as 400 with per-field messages.</li>
 *   <li><b>Responses</b> (AuthResponse, UserResponse, AccountResponse, BalanceResponse, ErrorResponse) are what the
 *       client sees. Entities are never exposed; {@code mapper/} (and the services) build the responses.</li>
 *   <li>Only the controllers and services know about DTOs; repositories and entities do not.</li>
 * </ul>
 */
package com.csb.banking.dto;
