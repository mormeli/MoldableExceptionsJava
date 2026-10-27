package ch.moldexj.processor;

import ch.moldexj.runtime.view.ViewManifest;
import ch.moldexj.runtime.view.ViewManifest.AnnotationEntry;
import ch.moldexj.runtime.view.ViewManifest.ParamEntry;
import ch.moldexj.runtime.view.ViewManifest.ViewEntry;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Writes the {@link ViewManifest} ({@value ViewManifest#PATH}) for all {@code @DebugView} methods of a compilation.
 * With it, the plugin and the helper in the debugged JVM find views in jars without searching sources, including views
 * a library ships for its own exceptions.
 *
 * <p>It looks for {@code ch.moldexj.view.DebugView}; a project that declares its own annotation passes its name with
 * {@code -Amoldexj.debugView=com.acme.DebugView}. Parameter annotations are written as they are, the plugin matches
 * them. Register the processor explicitly, e.g. in Gradle:
 * {@code annotationProcessor("ch.moldexj:moldable-views-processor")}. Neither Gradle nor javac from JDK 23 on run
 * processors that are only on the compile classpath.
 *
 * <p>Incremental builds: Gradle runs it as an aggregating processor and passes all annotated classes again. Other
 * builds (IntelliJ's own) may pass only the changed classes, so entries of an existing manifest are kept for classes
 * that were not compiled this time and still exist.
 */
public final class DebugViewProcessor extends AbstractProcessor {

    /** Option for projects that declare their own DebugView annotation: its qualified name. */
    public static final String ANNOTATION_OPTION = "moldexj.debugView";
    private static final String DEFAULT_ANNOTATION = "ch.moldexj.view.DebugView";

    private final Map<String, ViewEntry> views = new TreeMap<String, ViewEntry>();
    private final Set<String> compiledClasses = new HashSet<String>();

    /**
     * An explicit type, not "*": Gradle would otherwise see every annotation, find ones with source retention
     * (@Override) and fall back to full recompilation for the whole project.
     */
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        String own = processingEnv == null ? null : processingEnv.getOptions().get(ANNOTATION_OPTION);
        return Collections.singleton(own == null || own.isEmpty() ? DEFAULT_ANNOTATION : own);
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Collections.singleton(ANNOTATION_OPTION);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element root : roundEnv.getRootElements()) {
            collectClassNames(root);
        }
        for (TypeElement annotation : annotations) {
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (element.getKind() == ElementKind.METHOD && element.getEnclosingElement() instanceof TypeElement) {
                    String className = processingEnv.getElementUtils()
                            .getBinaryName((TypeElement) element.getEnclosingElement()).toString();
                    ViewEntry view = describe(className, (ExecutableElement) element);
                    views.put(view.key(), view);
                }
            }
        }
        if (roundEnv.processingOver()) {
            writeManifest();
        }
        return false; // never claim annotations: other processors may want them too
    }

    /** Remembers which classes this compilation saw, to drop their entries from an older manifest. */
    private void collectClassNames(Element element) {
        if (element instanceof TypeElement) {
            compiledClasses.add(processingEnv.getElementUtils().getBinaryName((TypeElement) element).toString());
            for (Element member : element.getEnclosedElements()) {
                collectClassNames(member);
            }
        }
    }

    private ViewEntry describe(String className, ExecutableElement method) {
        boolean isStatic = method.getModifiers().contains(Modifier.STATIC);
        ViewEntry view = new ViewEntry(className, method.getSimpleName().toString(), isStatic);
        boolean hasException = false;
        List<? extends VariableElement> parameters = method.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            VariableElement parameter = parameters.get(i);
            ParamEntry param = new ParamEntry(i, typeName(parameter.asType()));
            for (AnnotationMirror mirror : parameter.getAnnotationMirrors()) {
                String name = typeName(mirror.getAnnotationType());
                param.annotations.add(new AnnotationEntry(name, attributes(mirror)));
                hasException |= simpleName(name).equals("Exception");
            }
            view.params.add(param);
        }
        if (isStatic && !hasException) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                    "A static @DebugView method needs a parameter annotated with @Exception; this view is never shown",
                    method);
        }
        return view;
    }

    private Map<String, Object> attributes(AnnotationMirror mirror) {
        Map<String, Object> attributes = new LinkedHashMap<String, Object>();
        Map<? extends ExecutableElement, ? extends AnnotationValue> values =
                processingEnv.getElementUtils().getElementValuesWithDefaults(mirror);
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : values.entrySet()) {
            Object value = entry.getValue().getValue();
            if (value instanceof TypeMirror) {
                value = ViewManifest.classLiteral(typeName((TypeMirror) value));
            } else if (value instanceof VariableElement) {
                value = ViewManifest.enumConstant(((VariableElement) value).getSimpleName().toString());
            } else if (value instanceof List || value instanceof AnnotationMirror) {
                continue; // not supported by the manifest format
            }
            attributes.put(entry.getKey().getSimpleName().toString(), value);
        }
        return attributes;
    }

    private void writeManifest() {
        List<ViewEntry> previous = readPreviousManifest();
        if (views.isEmpty() && previous == null) {
            return; // nothing to declare, and no old manifest to overwrite
        }
        Map<String, ViewEntry> all = new TreeMap<String, ViewEntry>(views);
        if (previous != null) {
            for (ViewEntry view : previous) {
                if (!compiledClasses.contains(view.className) && exists(view.className) && !all.containsKey(view.key())) {
                    all.put(view.key(), view);
                }
            }
        }
        try {
            FileObject file = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", ViewManifest.PATH);
            Writer writer = file.openWriter();
            try {
                ViewManifest.write(writer, new ArrayList<ViewEntry>(all.values()));
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Could not write " + ViewManifest.PATH + ": " + e);
        }
    }

    /** The manifest of an earlier, incremental compilation into the same output folder, or null. */
    private List<ViewEntry> readPreviousManifest() {
        try {
            FileObject file = processingEnv.getFiler().getResource(StandardLocation.CLASS_OUTPUT, "", ViewManifest.PATH);
            Reader reader = file.openReader(true);
            try {
                return ViewManifest.read(reader);
            } finally {
                reader.close();
            }
        } catch (IOException | IllegalArgumentException e) {
            return null; // no manifest yet
        }
    }

    private boolean exists(String binaryName) {
        return processingEnv.getElementUtils().getTypeElement(binaryName.replace('$', '.')) != null;
    }

    /** The erased type as the JVM names it: binary names for classes (a$b), Java syntax for primitives and arrays. */
    private String typeName(TypeMirror type) {
        TypeMirror erased = processingEnv.getTypeUtils().erasure(type);
        if (erased.getKind() == TypeKind.DECLARED) {
            TypeElement element = (TypeElement) ((DeclaredType) erased).asElement();
            return processingEnv.getElementUtils().getBinaryName(element).toString();
        }
        return erased.toString();
    }

    private static String simpleName(String className) {
        int dot = Math.max(className.lastIndexOf('.'), className.lastIndexOf('$'));
        return className.substring(dot + 1);
    }
}
