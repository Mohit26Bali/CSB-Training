/**
 * Business exceptions and their translation to HTTP.
 *
 * <ul>
 *   <li>{@code service/} classes throw the exceptions below when a business rule is violated.</li>
 *   <li>Every one extends {@link com.csb.banking.exception.BankingException}, which carries the HTTP status.</li>
 *   <li>{@link com.csb.banking.exception.GlobalExceptionHandler} catches them (plus validation and security errors)
 *       and answers with a {@code dto/ErrorResponse}, so controllers need no try/catch.</li>
 * </ul>
 */
package com.csb.banking.exception;
