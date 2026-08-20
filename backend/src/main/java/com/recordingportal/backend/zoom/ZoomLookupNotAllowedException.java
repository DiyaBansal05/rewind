package com.recordingportal.backend.zoom;

/** Thrown when a caller asks for a date outside the configured safety window. */
public class ZoomLookupNotAllowedException extends RuntimeException {
    public ZoomLookupNotAllowedException(String message) {
        super(message);
    }
}
