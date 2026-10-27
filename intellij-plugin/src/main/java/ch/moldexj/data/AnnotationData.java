package ch.moldexj.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * An annotation of a view parameter, as the debugged program read it from the view manifest and sent it with a request. Attribute values are Boolean, Integer, Long, Double or String; enum constants and class
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
