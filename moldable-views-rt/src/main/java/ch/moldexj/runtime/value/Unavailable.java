package ch.moldexj.runtime.value;

/** An injected value the debugger could not provide; the view shows the reason instead of running. */
public final class Unavailable {

    public final String reason;

    public Unavailable(String reason) {
        this.reason = reason;
    }
}
