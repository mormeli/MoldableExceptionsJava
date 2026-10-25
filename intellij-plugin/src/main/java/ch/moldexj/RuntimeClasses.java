package ch.moldexj;

import ch.moldexj.runtime.MoldableSnapshot;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import org.jetbrains.org.objectweb.asm.ClassReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The class files of moldable-snapshot-rt, as listed in its {@value #LIST}, in an order the debugged JVM can define
 * them: a superclass or interface of the module before the classes that extend it. Other references between the
 * classes are resolved lazily, when the code runs, and need no order.
 */
final class RuntimeClasses {

    static final String LIST = "META-INF/moldexj/runtime-classes";

    private static Map<String, byte[]> ordered;

    private RuntimeClasses() {
    }

    /** Binary class name to class file, in definition order. Read once from the plugin jar. */
    static synchronized Map<String, byte[]> inDefinitionOrder() throws EvaluateException {
        if (ordered == null) {
            Map<String, byte[]> classes = new LinkedHashMap<>();
            for (String name : names()) {
                classes.put(name, bytes(name));
            }
            Map<String, byte[]> result = new LinkedHashMap<>();
            for (String name : classes.keySet()) {
                addWithSupertypes(name, classes, result);
            }
            ordered = result;
        }
        return ordered;
    }

    private static void addWithSupertypes(String name, Map<String, byte[]> classes, Map<String, byte[]> result) {
        if (result.containsKey(name) || !classes.containsKey(name)) {
            return; // done already, or a JDK class
        }
        ClassReader reader = new ClassReader(classes.get(name));
        if (reader.getSuperName() != null) {
            addWithSupertypes(reader.getSuperName().replace('/', '.'), classes, result);
        }
        for (String type : reader.getInterfaces()) {
            addWithSupertypes(type.replace('/', '.'), classes, result);
        }
        result.put(name, classes.get(name));
    }

    private static List<String> names() throws EvaluateException {
        try (InputStream in = MoldableSnapshot.class.getClassLoader().getResourceAsStream(LIST)) {
            if (in == null) {
                throw new EvaluateException(LIST + " is missing from the plugin");
            }
            List<String> names = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (!line.isBlank()) {
                    names.add(line.trim());
                }
            }
            return names;
        } catch (IOException e) {
            throw new EvaluateException("Could not read " + LIST + ": " + e, e);
        }
    }

    private static byte[] bytes(String name) throws EvaluateException {
        String resource = name.replace('.', '/') + ".class";
        try (InputStream in = MoldableSnapshot.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new EvaluateException("Class file " + resource + " is missing from the plugin");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new EvaluateException("Could not read " + resource + ": " + e, e);
        }
    }
}
