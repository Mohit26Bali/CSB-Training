/**
 * Application-wide Spring configuration.
 *
 * <ul>
 *   <li>{@link com.csb.banking.config.SecurityConfig} - the security filter chain, password encoder and CORS; wires
 *       together the classes of {@code security/}.</li>
 *   <li>{@link com.csb.banking.config.DataInitializer} - fills an empty database with demo data through the
 *       {@code repository/} interfaces.</li>
 * </ul>
 */
package com.csb.banking.config;
