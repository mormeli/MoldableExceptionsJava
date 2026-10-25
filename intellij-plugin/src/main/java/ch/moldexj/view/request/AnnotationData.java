package ch.moldexj.view.request;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * An annotation of a view parameter, independent of where it was read: from PSI (project sources) or from a view
 * manifest (library jars). Attribute values are Boolean, Integer, Long, Double or String; enum constants and class
 * literals are given by name. Defaults are included.
 */
public record AnnotationData(@NotNull String qualifiedName, @NotNull Map<String, Object> attributes) {

    public AnnotationData {
        attributes = Map.copyOf(attributes);
    }

    public @NotNull String simpleName() {
        int dot = Math.max(qualifiedName.lastIndexOf('.'), qualifiedName.lastIndexOf('$'));
        return qualifiedName.substring(dot + 1);
    }

    public @Nullable Object attribute(@NotNull String name) {
        return attributes.get(name);
    }
}
