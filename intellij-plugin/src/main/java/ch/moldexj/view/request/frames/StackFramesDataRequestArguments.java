package ch.moldexj.view.request.frames;

import ch.moldexj.view.request.DataRequestArguments;

/** {@code @StackFrames(depth = n)}. */
public record StackFramesDataRequestArguments(int depth) implements DataRequestArguments {
}
