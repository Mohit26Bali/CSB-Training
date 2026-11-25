/**
 * Spring Data JPA repositories: the only classes that talk to the database.
 *
 * <ul>
 *   <li>Each interface manages one {@code entity/} class; Spring generates the implementation at startup.</li>
 *   <li>Injected into {@code service/} classes (and {@code config/DataInitializer}); controllers never use them.</li>
 *   <li>Contain queries only, no business rules.</li>
 * </ul>
 */
package com.csb.banking.repository;
