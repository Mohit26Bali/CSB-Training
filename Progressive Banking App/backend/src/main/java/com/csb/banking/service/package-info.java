/**
 * The business layer: every banking rule lives here and nowhere else.
 *
 * <pre>
 * controller --(dto)--> service --(entity)--> repository
 *                          |
 *                          +-- throws exception/* on rule violations
 *                          +-- uses mapper/ to build dto responses
 * </pre>
 * <ul>
 *   <li>{@code AuthService}, {@code LoginAttemptService}, {@code CustomUserDetailsService} - identity and login.</li>
 *   <li>{@code AccountService} (customer account data) and {@code AdminAccountService} (administrator operations).</li>
 *   <li>{@code TransactionService} (money entry point) -> {@code LedgerService} (the transactional core) and
 *       {@code TransactionAuditService} (FAILED rows); {@code AccountAccessPolicy} decides who may touch which account;
 *       {@code IdempotencyService} makes repeated money requests execute only once.</li>
 *   <li>{@code AccountNumberGenerator} - helper that produces unique account numbers.</li>
 *   <li>Methods are {@code @Transactional}, so a failed operation rolls back and cannot leave a half-changed
 *       balance; {@code @PreAuthorize} restricts them by role.</li>
 * </ul>
 */
package com.csb.banking.service;
