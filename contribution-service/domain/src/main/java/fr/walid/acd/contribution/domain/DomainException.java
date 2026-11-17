package fr.walid.acd.contribution.domain;

import java.util.Objects;

public final class DomainException extends RuntimeException {
    private final String code;

    public DomainException(String code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "Domain error code is required");
    }

    public String code() {
        return code;
    }
}
