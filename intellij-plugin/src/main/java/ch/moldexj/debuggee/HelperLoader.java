package ch.moldexj.debuggee;

import ch.moldexj.runtime.ExceptionMolder;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.ClassLoadingUtils;
import com.sun.jdi.ClassLoaderReference;
import com.sun.jdi.ClassType;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Finds the runtime (moldable-views-rt) in the debugged program. A program that depends on it has it on its classpath,
 * and that copy is used. Otherwise the runtime is injected: a fresh class loader in the program (a child of
 * the application's, so the runtime sees the application's classes), and every class of the module defined in it from
 * the plugin's class files. So the application needs no dependency.
 *
 * <p>Only plain JDI and long-stable IntelliJ APIs are used: IntelliJ's own helper loader
 * ({@code DebuggerUtilsImpl.invokeHelperMethod}) only reads helper classes from {@code idea_rt.jar} in 2025.3.
 */
public final class HelperLoader {

    private static final String DEFINE_CLASS_SIGNATURE = "(Ljava/lang/String;[BII)Ljava/lang/Class;";

    private static final String ENTRY = ExceptionMolder.class.getName();

    /** The entry class per debugged VM and application class loader, so the module is defined once per session. */
    private static final Map<VirtualMachine, Map<Long, ClassType>> LOADED = new WeakHashMap<>();

    /** The class loaders this class created per debugged VM, to tell an injected runtime from the program's own. */
    private static final Map<VirtualMachine, Set<ClassLoaderReference>> INJECTED = new WeakHashMap<>();

    private HelperLoader() {
    }

    /** {@link ExceptionMolder} in the debugged program; defines the module first if needed. */
    public static ClassType entryClass(EvaluationContextImpl context, VirtualMachine vm) throws EvaluateException {
        // Try to fetch a cached class type of our entry class.
        // We do so using the uniqueID of the class loaded of the class whose method is running in the current frame.
        ClassLoaderReference classLoader = context.getClassLoader();
        long key = classLoader == null ? 0 : classLoader.uniqueID();
        synchronized (LOADED) {
            ClassType cached = LOADED.computeIfAbsent(vm, k -> new HashMap<>()).get(key);
            if (cached != null && isUsable(cached)) {
                return cached;
            }
        }

        // A program that depends on moldable-views-rt has the runtime itself: use that one. Otherwise inject ours.
        ClassType classType = findInProgram(context, vm, classLoader);
        if (classType == null) {
            classType = inject(context, vm);
        }
        synchronized (LOADED) {
            LOADED.computeIfAbsent(vm, k -> new HashMap<>()).put(key, classType);
        }
        return classType;
    }

    /**
     * The program's own {@link ExceptionMolder}, or null if it has none: a copy that is already loaded (found without
     * running code in the program), or one the stopped frame's class loader can load from the classpath.
     *
     * <p>A stop inside the JDK has the bootstrap loader, which cannot see the library. If the program has not used the
     * runtime by then, this returns null and the runtime is injected next to the program's. That is harmless: the
     * runtime keeps no state, and both copies are the same code.
     */
    private static ClassType findInProgram(EvaluationContextImpl context, VirtualMachine vm, ClassLoaderReference frameLoader) {
        Set<ClassLoaderReference> injected;
        synchronized (LOADED) {
            injected = new HashSet<>(INJECTED.getOrDefault(vm, Collections.emptySet()));
        }
        for (ReferenceType type : vm.classesByName(ENTRY)) {
            if (type instanceof ClassType loaded && !injected.contains(type.classLoader()) && isUsable(loaded)) {
                return loaded;
            }
        }
        if (frameLoader == null) {
            return null;
        }
        try {
            // Loads the class if it is on the program's classpath; throws if it is not.
            ReferenceType type = context.getDebugProcess().findClass(context, ENTRY, frameLoader);
            return type instanceof ClassType loaded && !injected.contains(type.classLoader()) ? loaded : null;
        } catch (EvaluateException e) {
            return null;
        }
    }

    /**
     * Defines the runtime in the debugged program: a fresh class loader there (a child of the application's), and
     * every class of the module defined in it from the plugin's class files.
     */
    private static ClassType inject(EvaluationContextImpl context, VirtualMachine vm) throws EvaluateException {
        DebugProcess process = context.getDebugProcess();
        ClassLoaderReference loader = ClassLoadingUtils.getClassLoader(context, process);
        loader.disableCollection(); // keep it, and the classes defined in it, for the rest of the debug session
        synchronized (LOADED) {
            INJECTED.computeIfAbsent(vm, k -> new HashSet<>()).add(loader);
        }
        // The runtime-classes manifest, generated by Gradle, lists the classes to define.
        for (Map.Entry<String, byte[]> runtimeClass : RuntimeClasses.inDefinitionOrder().entrySet()) {
            defineClass(context, loader, runtimeClass.getKey(), runtimeClass.getValue());
        }

        ReferenceType type = process.findClass(context, ENTRY, loader);
        if (!(type instanceof ClassType classType)) {
            throw new EvaluateException("Could not load " + ENTRY + " into the debugged program");
        }
        return classType;
    }

    /** Calls loader.defineClass(name, bytes, 0, bytes.length) in the debugged program. */
    private static void defineClass(
            EvaluationContextImpl context,
            ClassLoaderReference loader,
            String name,
            byte[] bytes)
            throws EvaluateException {
        Method define = DebuggerUtils.findMethod(loader.referenceType(), "defineClass", DEFINE_CLASS_SIGNATURE);
        if (define == null) {
            throw new EvaluateException("ClassLoader.defineClass not found");
        }
        VirtualMachine vm = loader.virtualMachine();
        try (DebuggeeMirrors mirrors = new DebuggeeMirrors(vm)) {
            context.getDebugProcess().invokeMethod(context, loader, define,
                    Arrays.asList(mirrors.string(name), mirrors.bytes(bytes), vm.mirrorOf(0), vm.mirrorOf(bytes.length)));
        } catch (EvaluateException e) {
            throw e;
        } catch (Exception e) {
            throw new EvaluateException("Could not define " + name + " in the debugged program: " + e, e);
        }
    }

    private static boolean isUsable(ClassType type) {
        try {
            return type.isPrepared();
        } catch (Exception e) {
            return false; // the class was unloaded or the VM is gone
        }
    }
}
