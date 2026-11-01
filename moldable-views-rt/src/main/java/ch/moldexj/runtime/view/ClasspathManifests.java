package ch.moldexj.runtime.view;

import ch.moldexj.runtime.util.Reflection;

import ch.moldexj.runtime.view.ViewManifest.AnnotationEntry;
import ch.moldexj.runtime.view.ViewManifest.ParamEntry;
import ch.moldexj.runtime.view.ViewManifest.ViewEntry;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The views declared in every manifest on the debugged program's classpath: the only place the parameters of a view
 * are known, since their annotations are not retained at run time. A parameter with an annotation other than
 * {@code @Exception} asks the plugin for debugger data.
 */
final class ClasspathManifests {

    private ClasspathManifests() {
    }

    static List<ViewDeclaration> read(List<String> notes) {
        List<ViewDeclaration> views = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ClassLoader loader : Reflection.classLoaders()) {
            List<URL> urls;
            try {
                urls = Collections.list(loader.getResources(ViewManifest.PATH));
            } catch (Throwable e) {
                continue;
            }
            for (URL url : urls) {
                if (!seen.add(url.toString())) {
                    continue; // both loaders see the same jar
                }
                try (InputStream in = url.openStream()) {
                    for (ViewEntry entry : ViewManifest.read(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                        views.add(declaration(entry));
                    }
                } catch (Throwable e) {
                    notes.add("Could not read " + url + ": " + e);
                }
            }
        }
        return views;
    }

    private static ViewDeclaration declaration(ViewEntry entry) {
        ViewDeclaration view = new ViewDeclaration(entry.className, entry.methodName, entry.isStatic);
        for (ParamEntry param : entry.params) {
            view.declare(param.index, parameter(param));
        }
        return view;
    }

    private static ParameterDeclaration parameter(ParamEntry param) {
        if (param.annotations.isEmpty()) {
            return ParameterDeclaration.missing("Parameter " + param.index + " has no annotation, so it cannot be filled.");
        }
        for (AnnotationEntry annotation : param.annotations) {
            if (simpleName(annotation.name).equals("Exception")) {
                return ParameterDeclaration.exception(); // @Exception decides, whatever other annotations the parameter has
            }
        }
        return ParameterDeclaration.debugger(param.annotations);
    }

    private static String simpleName(String className) {
        return className.substring(Math.max(className.lastIndexOf('.'), className.lastIndexOf('$')) + 1);
    }
}
