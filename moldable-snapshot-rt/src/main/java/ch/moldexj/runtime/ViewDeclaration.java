package ch.moldexj.runtime;

import java.util.ArrayList;
import java.util.List;

/** A view method by name, with how to fill its parameters: from the plugin's spec or from a manifest. */
final class ViewDeclaration {

    final String className;
    final String methodName;
    final boolean isStatic;
    final List<Binding> bindings = new ArrayList<>();

    ViewDeclaration(String className, String methodName, boolean isStatic) {
        this.className = className;
        this.methodName = methodName;
        this.isStatic = isStatic;
    }

    /** Sets the binding of a parameter; parameters skipped on the way cannot be filled. */
    void bind(int index, Binding binding) {
        while (bindings.size() <= index) {
            bindings.add(Binding.missing("Parameter " + bindings.size() + " has no annotation the plugin knows, so it cannot be filled."));
        }
        bindings.set(index, binding);
    }

    /** Identifies the method like the plugin and the manifest do: class, name and parameter count. */
    String key() {
        return className + "#" + methodName + "/" + bindings.size();
    }
}
