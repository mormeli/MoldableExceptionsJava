package ch.moldexj;

import ch.moldexj.runtime.MoldableSnapshot;
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
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Loads moldable-snapshot-rt into the debugged program: a fresh class loader there (a child of the application's, so
 * the helper sees the application's classes but never clashes with them), and every class of the module defined in it
 * from the plugin's class files. The application needs no dependency.
 *
 * <p>Only plain JDI and long-stable IntelliJ APIs are used: IntelliJ's own helper loader
 * ({@code DebuggerUtilsImpl.invokeHelperMethod}) only reads helper classes from {@code idea_rt.jar} in 2025.3.
 */
final class HelperLoader {

    private static final String DEFINE_CLASS_SIGNATURE = "(Ljava/lang/String;[BII)Ljava/lang/Class;";

    /** The entry class per debugged VM and application class loader, so the module is defined once per session. */
    private static final Map<VirtualMachine, Map<Long, ClassType>> LOADED = new WeakHashMap<>();

    private HelperLoader() {
    }

    /** {@link MoldableSnapshot} in the debugged program; defines the module first if needed. */
    static ClassType entryClass(EvaluationContextImpl context, VirtualMachine vm) throws EvaluateException {
        DebugProcess process = context.getDebugProcess();
        ClassLoaderReference appLoader = context.getClassLoader();
        long key = appLoader == null ? 0 : appLoader.uniqueID();
        synchronized (LOADED) {
            ClassType cached = LOADED.computeIfAbsent(vm, k -> new HashMap<>()).get(key);
            if (cached != null && isUsable(cached)) {
                return cached;
            }
        }

        ClassLoaderReference loader = ClassLoadingUtils.getClassLoader(context, process);
        loader.disableCollection(); // keep it, and the classes defined in it, for the rest of the debug session
        for (Map.Entry<String, byte[]> runtimeClass : RuntimeClasses.inDefinitionOrder().entrySet()) {
            defineClass(context, loader, runtimeClass.getKey(), runtimeClass.getValue());
        }

        String entry = MoldableSnapshot.class.getName();
        ReferenceType type = process.findClass(context, entry, loader);
        if (!(type instanceof ClassType classType)) {
            throw new EvaluateException("Could not load " + entry + " into the debugged program");
        }
        synchronized (LOADED) {
            LOADED.computeIfAbsent(vm, k -> new HashMap<>()).put(key, classType);
        }
        return classType;
    }

    /** Calls loader.defineClass(name, bytes, 0, bytes.length) in the debugged program. */
    private static void defineClass(EvaluationContextImpl context, ClassLoaderReference loader, String name, byte[] bytes)
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
