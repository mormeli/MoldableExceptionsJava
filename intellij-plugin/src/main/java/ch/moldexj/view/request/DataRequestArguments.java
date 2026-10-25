package ch.moldexj.view.request;

/**
 * The configuration of one data request, read from a parameter annotation, e.g. the depth of {@code @StackFrames}.
 * Implementations must define {@code equals} and {@code hashCode} (records do): equal requests are resolved once per
 * snapshot, however many views ask for them.
 */
public interface DataRequestArguments {
}
