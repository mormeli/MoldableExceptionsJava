package ch.moldexj.view.request.frames;

import ch.moldexj.view.request.DataRequestArguments;

/** {@code @StackFrames(depth = n, fields = b)}. */
public record StackFramesDataRequestArguments(int depth, boolean fields) implements DataRequestArguments {
}
