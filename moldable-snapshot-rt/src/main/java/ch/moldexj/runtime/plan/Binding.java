package ch.moldexj.runtime.plan;

/** How one parameter of a view is filled. */
public final class Binding {

    public enum Kind {
        /** The exception the view is shown for ({@code @Exception}). */
        EXCEPTION,
        /** A value the debugger provided: {@link #valueIndex} is the request index of the plan. */
        VALUE,
        /** Cannot be filled: the view shows {@link #reason} instead of running. */
        MISSING
    }

    public final Kind kind;
    public final int valueIndex;
    public final String reason;

    private Binding(Kind kind, int valueIndex, String reason) {
        this.kind = kind;
        this.valueIndex = valueIndex;
        this.reason = reason;
    }

    public static Binding exception() {
        return new Binding(Kind.EXCEPTION, -1, null);
    }

    public static Binding value(int index) {
        return new Binding(Kind.VALUE, index, null);
    }

    public static Binding missing(String reason) {
        return new Binding(Kind.MISSING, -1, reason);
    }
}
