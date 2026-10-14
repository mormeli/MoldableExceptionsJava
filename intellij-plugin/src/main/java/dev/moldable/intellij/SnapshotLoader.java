package dev.moldable.intellij;

import com.google.gson.Gson;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.ClassLoadingUtils;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ArrayType;
import com.sun.jdi.ClassLoaderReference;
import com.sun.jdi.ClassType;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StringReference;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import dev.moldable.intellij.rt.MoldableSnapshot;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Takes a snapshot of an exception in the paused JVM.
 *
 * <p>The helper class {@link MoldableSnapshot} is loaded into the debugged program from this plugin's own class file:
 * a new class loader is created in the program (a child of the application's class loader), the class bytes are passed
 * to its {@code defineClass}, and {@code MoldableSnapshot.toJson(exception)} is called. The application needs no
 * dependency. Only plain JDI and long-stable IntelliJ APIs are used: IntelliJ's own helper loader
 * ({@code DebuggerUtilsImpl.invokeHelperMethod}) only reads helper classes from {@code idea_rt.jar} in 2025.3.
 */
final class SnapshotLoader {

    private static final Gson GSON = new Gson();
    private static final String HELPER = MoldableSnapshot.class.getName();
    private static final String DEFINE_CLASS_SIGNATURE = "(Ljava/lang/String;[BII)Ljava/lang/Class;";
    private static final String TO_JSON_SIGNATURE = "(Ljava/lang/Throwable;Ljava/lang/String;)Ljava/lang/String;";

    /** Helper class per debugged VM and application class loader, so it is defined only once per session. */
    private static final Map<VirtualMachine, Map<Long, ClassType>> LOADED = new WeakHashMap<>();

    private static byte[] helperBytes;

    private SnapshotLoader() {
    }

    /** Talks to the VM: call on the debugger manager thread only. */
    static boolean isThrowable(ObjectReference value) {
        return DebuggerUtils.instanceOf(value.referenceType(), "java.lang.Throwable");
    }

    /**
     * Must run on the debugger manager thread, inside a suspend context (a SuspendContextCommandImpl).
     *
     * @param externalViews "com.acme.Views#method" lines from {@link ExternalViewFinder}, or ""
     */
    static ExceptionSnapshot load(EvaluationContextImpl context, ObjectReference exception, String externalViews)
            throws EvaluateException {
        DebugProcess process = context.getDebugProcess();
        ClassType helper = helperClass(context, process, exception.virtualMachine());
        Method toJson = DebuggerUtils.findMethod(helper, "toJson", TO_JSON_SIGNATURE);
        if (toJson == null) {
            throw new EvaluateException("MoldableSnapshot.toJson not found in the loaded helper class");
        }
        StringReference spec = exception.virtualMachine().mirrorOf(externalViews);
        spec.disableCollection();
        Value json;
        try {
            json = process.invokeMethod(context, helper, toJson, Arrays.asList(exception, spec));
        } finally {
            spec.enableCollection();
        }
        if (!(json instanceof StringReference)) {
            throw new EvaluateException("The snapshot helper returned " + json);
        }
        ExceptionSnapshot snapshot = GSON.fromJson(((StringReference) json).value(), ExceptionSnapshot.class);
        return snapshot == null ? new ExceptionSnapshot() : snapshot;
    }

    private static ClassType helperClass(EvaluationContextImpl context, DebugProcess process, VirtualMachine vm)
            throws EvaluateException {
        ClassLoaderReference appLoader = context.getClassLoader();
        long key = appLoader == null ? 0 : appLoader.uniqueID();
        synchronized (LOADED) {
            ClassType cached = LOADED.computeIfAbsent(vm, k -> new java.util.HashMap<>()).get(key);
            if (cached != null && isUsable(cached)) {
                return cached;
            }
        }

        // A fresh java.security.SecureClassLoader in the debugged program, parented to the application's loader,
        // so the helper can see the application's classes but never clashes with them.
        ClassLoaderReference loader = ClassLoadingUtils.getClassLoader(context, process);
        loader.disableCollection(); // keep it (and the helper class) alive for the rest of the debug session

        defineClass(context, process, vm, loader, HELPER, helperBytes());

        ReferenceType type = process.findClass(context, HELPER, loader);
        if (!(type instanceof ClassType)) {
            throw new EvaluateException("Could not load " + HELPER + " into the debugged program");
        }
        synchronized (LOADED) {
            LOADED.computeIfAbsent(vm, k -> new java.util.HashMap<>()).put(key, (ClassType) type);
        }
        return (ClassType) type;
    }

    /** Calls loader.defineClass(name, bytes, 0, bytes.length) in the debugged program, using plain JDI mirrors. */
    private static void defineClass(EvaluationContextImpl context, DebugProcess process, VirtualMachine vm,
                                    ClassLoaderReference loader, String name, byte[] bytes) throws EvaluateException {
        Method define = DebuggerUtils.findMethod(loader.referenceType(), "defineClass", DEFINE_CLASS_SIGNATURE);
        if (define == null) {
            throw new EvaluateException("ClassLoader.defineClass not found");
        }

        StringReference nameMirror = null;
        ArrayReference bytesMirror = null;
        try {
            nameMirror = vm.mirrorOf(name);
            nameMirror.disableCollection();

            List<ReferenceType> byteArrayTypes = vm.classesByName("byte[]");
            if (byteArrayTypes.isEmpty()) {
                throw new EvaluateException("byte[] is not loaded in the debugged program");
            }
            bytesMirror = ((ArrayType) byteArrayTypes.get(0)).newInstance(bytes.length);
            bytesMirror.disableCollection();
            List<Value> values = new ArrayList<>(bytes.length);
            for (byte b : bytes) {
                values.add(vm.mirrorOf(b));
            }
            bytesMirror.setValues(values);

            process.invokeMethod(context, loader, define,
                    Arrays.asList(nameMirror, bytesMirror, vm.mirrorOf(0), vm.mirrorOf(bytes.length)));
        } catch (EvaluateException e) {
            throw e;
        } catch (Exception e) {
            throw new EvaluateException("Could not define " + name + " in the debugged program: " + e, e);
        } finally {
            if (nameMirror != null) {
                nameMirror.enableCollection();
            }
            if (bytesMirror != null) {
                bytesMirror.enableCollection();
            }
        }
    }

    private static boolean isUsable(ClassType type) {
        try {
            return type.isPrepared();
        } catch (Exception e) {
            return false; // the class was unloaded or the VM is gone
        }
    }

    private static synchronized byte[] helperBytes() throws EvaluateException {
        if (helperBytes == null) {
            String resource = "/" + HELPER.replace('.', '/') + ".class";
            try (InputStream in = MoldableSnapshot.class.getResourceAsStream(resource)) {
                if (in == null) {
                    throw new EvaluateException("Class file " + resource + " is missing from the plugin");
                }
                helperBytes = in.readAllBytes();
            } catch (IOException e) {
                throw new EvaluateException("Could not read " + resource + ": " + e, e);
            }
        }
        return helperBytes;
    }
}
