/**
 * The REST layer: translates HTTP into service calls and back. No business rules here.
 *
 * <pre>
 * AuthController          /api/auth/**           public login / register, plus "me"
 * AccountController       /api/accounts/**       account data: open, list, details, balance
 * TransactionController   /api/accounts/{no}/deposit|withdraw|transactions, /api/transfers
 * AdminAccountController  /api/admin/accounts/** administrator operations (ADMIN only, see config/SecurityConfig)
 * </pre>
 * Each controller receives {@code dto/} request records (validated with {@code @Valid}), hands them to one
 * {@code service/} class and returns {@code dto/} responses. Errors are handled centrally by
 * {@code exception/GlobalExceptionHandler}.
 */
package com.csb.banking.controller;
