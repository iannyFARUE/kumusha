package com.kumusha.exception;

/**
 * Exception thrown when a write endpoint is called while the deployment is in read-only mode.
 *
 * This exception results in a 403 Forbidden response with a WRITE_OPERATIONS_DISABLED code.
 * Read-only mode is the default, so this is what a public deployment returns for every create,
 * update, delete and embedding-backfill request unless writes have been explicitly enabled.
 */
public class WriteOperationsDisabledException extends RuntimeException {

    public WriteOperationsDisabledException(String message) {
        super(message);
    }
}
