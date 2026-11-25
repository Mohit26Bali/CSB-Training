/**
 * Converts {@code entity/} objects into {@code dto/} responses.
 *
 * <p>Kept out of both the entities (which should not know about the API) and the controllers (which should not know
 * about JPA relations). Called by {@code service/} classes inside their transaction, because the conversion reads
 * lazily loaded relations such as account -> customer -> user.
 */
package com.csb.banking.mapper;
