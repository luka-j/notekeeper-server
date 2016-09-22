package controllers;

/**
 * Created by luka on 27.1.16..
 * Lightweight (marker) exception, no stack trace
 */
public abstract class TokenException extends Exception {
    private TokenException() {
        super();
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }

    public static class Expired extends TokenException {
        public Expired() {super();}
    }
    public static class Invalid extends TokenException {
        public Invalid() {super();}
    }
}
