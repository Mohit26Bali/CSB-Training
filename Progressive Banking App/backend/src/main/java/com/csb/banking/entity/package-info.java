/**
 * JPA entities: the Java shape of the tables in the MySQL "bank" database.
 *
 * <pre>
 * users 1 --- 0..1 customers 1 --- * accounts 1 --- * transactions
 * idempotency_keys (stand-alone: key + username -> stored response)
 * </pre>
 * <ul>
 *   <li>Read and written only through {@code repository/} interfaces.</li>
 *   <li>Never returned from controllers; {@code mapper/} converts them to {@code dto/} records.</li>
 *   <li>The enums here ({@link com.csb.banking.entity.Role}, {@link com.csb.banking.entity.AccountStatus}, ...)
 *       are stored as strings and reused by dto/ and service/.</li>
 * </ul>
 */
package com.csb.banking.entity;
