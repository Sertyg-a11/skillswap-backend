package nl.ak.skillswap.userservice.support;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) { super(message); }
}

