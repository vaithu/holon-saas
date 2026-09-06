package com.holonplatform.multitenant.whatsapp.exception;

/** Base exception for all WhatsApp integration errors. */
public class WhatsAppException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public WhatsAppException(String message) {
        super(message);
    }

    public WhatsAppException(String message, Throwable cause) {
        super(message, cause);
    }
}

