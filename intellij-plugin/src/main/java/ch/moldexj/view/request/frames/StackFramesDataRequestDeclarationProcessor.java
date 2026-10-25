package ch.moldexj.view.request.frames;

import ch.moldexj.view.request.AnnotationData;
import ch.moldexj.view.request.DataRequestDeclarationProcessor;
import ch.moldexj.view.request.ParameterSource;
import org.jetbrains.annotations.NotNull;

/** {@code @StackFrames(depth = n)}: the stack of the throwing thread, see {@link StackFramesDataRequestProcessor}. */
public final class StackFramesDataRequestDeclarationProcessor implements DataRequestDeclarationProcessor {

    private static final StackFramesDataRequestProcessor PROCESSOR = new StackFramesDataRequestProcessor();

    @Override
    public boolean accept(@NotNull AnnotationData annotation) {
        return annotation.simpleName().equals("StackFrames");
    }

    @Override
    public @NotNull ParameterSource createSource(@NotNull AnnotationData annotation) {
        if (!(annotation.attribute("depth") instanceof Integer depth)) {
            return new ParameterSource.Unsupported("@StackFrames needs a constant integer depth.");
        }
        if (depth <= 0) {
            return new ParameterSource.Unsupported("@StackFrames needs a depth of at least 1, not " + depth + ".");
        }
        return new ParameterSource.Provided(new StackFramesDataRequestArguments(depth), PROCESSOR);
    }
}
