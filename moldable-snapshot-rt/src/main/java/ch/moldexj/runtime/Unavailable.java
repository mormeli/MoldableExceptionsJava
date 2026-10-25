package ch.moldexj.runtime;

/** An injected value the debugger could not provide; the view shows the reason instead of running. */
final class Unavailable {

    final String reason;

    Unavailable(String reason) {
        this.reason = reason;
    }
}
