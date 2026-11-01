package ch.moldexj.runtime.view;

import java.util.ArrayList;
import java.util.List;

/** A view method by name, with what the manifest says about its parameters. */
final class ViewDeclaration {

    public final String className;
    public final String methodName;
    public final boolean isStatic;
    public final List<ParameterDeclaration> parameters = new ArrayList<>();

    ViewDeclaration(String className, String methodName, boolean isStatic) {
        this.className = className;
        this.methodName = methodName;
        this.isStatic = isStatic;
    }

    /** Sets the declaration of a parameter; parameters skipped on the way cannot be filled. */
    void declare(int index, ParameterDeclaration parameter) {
        while (parameters.size() <= index) {
            parameters.add(ParameterDeclaration.missing("Parameter " + parameters.size() + " is not in the view manifest, so it cannot be filled."));
        }
        parameters.set(index, parameter);
    }
}
