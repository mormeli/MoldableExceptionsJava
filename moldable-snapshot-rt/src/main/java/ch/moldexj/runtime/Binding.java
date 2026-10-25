package ch.moldexj.runtime;

/** How one parameter of a view is filled. */
final class Binding {

    enum Kind {
        /** The exception the view is shown for ({@code @Exception}). */
        EXCEPTION,
        /** A value the debugger provided: {@link #valueIndex} into the injected values. */
        VALUE,
        /** Cannot be filled: the view shows {@link #reason} instead of running. */
        MISSING
    }

    final Kind kind;
    final int valueIndex;
    final String reason;

    private Binding(Kind kind, int valueIndex, String reason) {
        this.kind = kind;
        this.valueIndex = valueIndex;
        this.reason = reason;
    }

    static Binding exception() {
        return new Binding(Kind.EXCEPTION, -1, null);
    }

    static Binding value(int index) {
        return new Binding(Kind.VALUE, index, null);
    }

    static Binding missing(String reason) {
        return new Binding(Kind.MISSING, -1, reason);
    }
}
