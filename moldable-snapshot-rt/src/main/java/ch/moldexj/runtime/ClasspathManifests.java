package ch.moldexj.runtime;

import ch.moldexj.runtime.manifest.ViewManifest;
import ch.moldexj.runtime.manifest.ViewManifest.AnnotationEntry;
import ch.moldexj.runtime.manifest.ViewManifest.ParamEntry;
import ch.moldexj.runtime.manifest.ViewManifest.ViewEntry;

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
 * The views declared in every manifest on the debugged program's classpath, for views the IDE does not know (a jar
 * that is not a dependency of the IntelliJ project). No one resolved debugger data for them, so only {@code @Exception}
 * parameters can be filled.
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
            view.bind(param.index, binding(param));
        }
        return view;
    }

    private static Binding binding(ParamEntry param) {
        if (param.annotations.isEmpty()) {
            return Binding.missing("Parameter " + param.index + " has no annotation, so it cannot be filled.");
        }
        for (AnnotationEntry annotation : param.annotations) {
            if (simpleName(annotation.name).equals("Exception")) {
                return Binding.exception(); // @Exception decides, whatever other annotations the parameter has
            }
        }
        return Binding.missing("@" + simpleName(param.annotations.get(0).name) + " needs data from the debugger, but the"
                + " IDE did not provide it: the view was only found in the debugged program, for example because its"
                + " jar is not a dependency of the IntelliJ project.");
    }

    private static String simpleName(String className) {
        return className.substring(Math.max(className.lastIndexOf('.'), className.lastIndexOf('$')) + 1);
    }
}
