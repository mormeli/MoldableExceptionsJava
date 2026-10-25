package ch.moldexj.view.request;

import org.jetbrains.annotations.NotNull;

/**
 * Reads a parameter annotation (the declaration of a data request) and says how the parameter is filled. Works on
 * {@link AnnotationData}, so the same processor serves views found in sources and in library manifests.
 */
public interface DataRequestDeclarationProcessor {

    boolean accept(@NotNull AnnotationData annotation);

    /** Called only for accepted annotations. Problems with the declaration become {@link ParameterSource.Unsupported}. */
    @NotNull ParameterSource createSource(@NotNull AnnotationData annotation);
}
