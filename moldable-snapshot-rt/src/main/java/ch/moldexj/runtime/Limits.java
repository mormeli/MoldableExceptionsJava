package ch.moldexj.runtime;

/** Bounds that keep a snapshot small, whatever the exception or its views contain. */
final class Limits {

    static final int NODES = 32;
    static final int DEPTH = 12;
    static final int TEXT = 100_000;
    static final int ROWS = 1_000;
    static final int FRAMES = 200;

    private Limits() {
    }
}
