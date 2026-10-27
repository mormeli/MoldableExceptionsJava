package ch.moldexj.debuggee;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ArrayType;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StringReference;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates strings and arrays in the debugged JVM, protected from its garbage collector until {@link #close()}: nothing
 * in the program references a new mirror, so the GC could take it before the invocation that uses it.
 */
public final class DebuggeeMirrors implements AutoCloseable {

    private final VirtualMachine vm;
    private final List<ObjectReference> mirrors = new ArrayList<>();

    public DebuggeeMirrors(@NotNull VirtualMachine vm) {
        this.vm = vm;
    }

    StringReference string(String value) {
        return protect(vm.mirrorOf(value));
    }

    /** A byte[] with the content: one JDI command for the values, whatever the size. */
    ArrayReference bytes(byte[] content) throws Exception {
        List<Value> values = new ArrayList<>(content.length);
        for (byte b : content) {
            values.add(vm.mirrorOf(b));
        }
        return array("byte[]", values);
    }

    ArrayReference objects(List<? extends ObjectReference> elements) throws Exception {
        return array("java.lang.Object[]", elements);
    }

    private ArrayReference array(String typeName, List<? extends Value> values) throws Exception {
        List<ReferenceType> types = vm.classesByName(typeName);
        if (types.isEmpty()) {
            throw new EvaluateException(typeName + " is not loaded in the debugged program");
        }
        ArrayReference array = protect(((ArrayType) types.get(0)).newInstance(values.size()));
        array.setValues(values);
        return array;
    }

    private <T extends ObjectReference> T protect(T mirror) {
        mirror.disableCollection();
        mirrors.add(mirror);
        return mirror;
    }

    @Override
    public void close() {
        for (ObjectReference mirror : mirrors) {
            try {
                mirror.enableCollection();
            } catch (RuntimeException ignored) {
                // the VM is gone; nothing to release
            }
        }
    }
}
