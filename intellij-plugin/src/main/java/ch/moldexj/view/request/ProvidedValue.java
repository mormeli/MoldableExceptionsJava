package ch.moldexj.view.request;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The result of a {@link DataRequestProcessor}. */
public sealed interface ProvidedValue {

    /**
     * A tree of {@code Map<String, ?>}, {@code List<?>}, String, Number, Boolean and null, plus JDI values: an
     * {@code ObjectReference} arrives in the view as the live object, a {@code PrimitiveValue} as the boxed value.
     *
     * <p>A map arrives as a {@code Map}, or as a proxy when the parameter type is an interface: its method {@code x()}
     * returns the entry "x". Two special entries add computed methods:
     * {@code "$lookup:local": ["locals", "name"]} makes {@code local(n)} return the element of "locals" whose "name" is n;
     * {@code "$text:text": "value"} makes {@code text()} return a safe toString() of "value".
     */
    record Available(@Nullable Object tree) implements ProvidedValue {
    }

    /** Shown in place of the view. */
    record Unavailable(@NotNull String reason) implements ProvidedValue {
    }

    static ProvidedValue of(@Nullable Object tree) {
        return new Available(tree);
    }

    static ProvidedValue unavailable(@NotNull String reason) {
        return new Unavailable(reason);
    }
}
