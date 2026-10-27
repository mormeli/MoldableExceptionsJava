package ch.moldexj.data.frames;

import ch.moldexj.data.AnnotationData;
import ch.moldexj.data.DebuggerDataProvider;
import ch.moldexj.data.ProviderContext;
import ch.moldexj.data.ProvidedValue;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.jdi.LocalVariableProxyImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Field;
import com.sun.jdi.Location;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the stack of the throwing thread with plain JDI reads (no invocation in the debugged JVM): per frame the
 * location, {@code this}, the arguments and the visible local variables, and on request the fields of {@code this}.
 * Objects stay live references; the view receives them as the objects themselves. The shape matches
 * {@code DebugStack}, {@code DebugFrame} and {@code DebugLocal} of moldable-views.
 */
public final class StackFramesProvider implements DebuggerDataProvider<StackFramesArguments> {

    static final int MAX_FRAMES = 200;
    static final int MAX_LOCALS = 64;

    /** {@code @StackFrames(depth = n)}. */
    @Override
    public @Nullable StackFramesArguments parse(@NotNull AnnotationData annotation) {
        if (!annotation.simpleName().equals("StackFrames")) {
            return null;
        }
        if (!(annotation.attribute("depth") instanceof Integer depth)) {
            throw new IllegalArgumentException("@StackFrames needs a constant integer depth.");
        }
        if (depth <= 0) {
            throw new IllegalArgumentException("@StackFrames needs a depth of at least 1, not " + depth + ".");
        }
        boolean fields = Boolean.TRUE.equals(annotation.attribute("fields")); // default false, also for older manifests
        return new StackFramesArguments(depth, fields);
    }

    @Override
    public @NotNull ProvidedValue provide(
            @NotNull ProviderContext context, @NotNull StackFramesArguments arguments) {
        ThreadReferenceProxyImpl thread = context.getThrowThread();
        if (thread == null) {
            return ProvidedValue.unavailable("This view needs the stack at the throw of the exception. It is only available"
                    + " while the debugger is stopped at that throw (an exception breakpoint or the uncaught-exception stop),"
                    + " not when the views are opened from a variable elsewhere.");
        }
        List<StackFrameProxyImpl> frames;
        try {
            frames = thread.frames(); // [0] = top of the stack, the throw site
        } catch (EvaluateException e) {
            return ProvidedValue.unavailable("Could not read the stack of " + thread.name() + ": " + e.getMessage());
        }
        int count = Math.min(Math.min(arguments.depth(), MAX_FRAMES), frames.size());
        List<Object> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(frame(frames.get(i), i, arguments.fields()));
        }
        Map<String, Object> stack = new LinkedHashMap<>();
        stack.put("frames", result);
        stack.put("truncated", frames.size() > count);
        stack.put("$lookup:frameOf", List.of("frames", "className", "methodName"));
        return ProvidedValue.of(stack);
    }

    private static Map<String, Object> frame(
            StackFrameProxyImpl frame,
            int index,
            boolean withFields) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("index", index);
        try {
            Location location = frame.location();
            map.put("className", location.declaringType().name());
            map.put("methodName", location.method().name());
            map.put("fileName", sourceName(location));
            map.put("line", location.lineNumber());
        } catch (EvaluateException e) {
            map.put("className", "?");
            map.put("methodName", "?");
            map.put("line", -1);
        }
        ObjectReference self;
        try {
            self = frame.thisObject(); // null in static methods
        } catch (EvaluateException ignored) {
            self = null;
        }
        map.put("thisObject", self);
        map.put("fields", withFields && self != null ? fields(self) : List.of());
        map.put("$lookup:field", List.of("fields", "name"));
        List<Object> locals = new ArrayList<>();
        boolean hasDebugInfo = true;
        try {
            for (LocalVariableProxyImpl variable : frame.visibleVariables()) {
                if (locals.size() >= MAX_LOCALS) {
                    break;
                }
                locals.add(local(variable.name(), variable.typeName(), variable.getVariable().isArgument(), frame.getValue(variable)));
            }
        } catch (EvaluateException e) {
            // Compiled without -g: no names, but the argument values are still there.
            hasDebugInfo = false;
            locals.clear();
            if (e.getCause() instanceof AbsentInformationException) {
                try {
                    List<Value> arguments = frame.getArgumentValues();
                    for (int i = 0; i < arguments.size() && i < MAX_LOCALS; i++) {
                        Value value = arguments.get(i);
                        locals.add(local("arg" + i, value == null ? "?" : value.type().name(), true, value));
                    }
                } catch (EvaluateException ignored) {
                    // a native frame, or the frame is gone
                }
            }
        }
        map.put("locals", locals);
        map.put("hasDebugInfo", hasDebugInfo);
        map.put("$lookup:local", List.of("locals", "name"));
        return map;
    }

    /** The instance fields of {@code this}, inherited ones included: one JDI read for all values, no access checks. */
    private static List<Object> fields(ObjectReference self) {
        List<Field> fields = self.referenceType().allFields().stream()
                .filter(field -> !field.isStatic())
                .limit(MAX_LOCALS)
                .toList();
        Map<Field, Value> values = self.getValues(fields);
        List<Object> result = new ArrayList<>(fields.size());
        for (Field field : fields) {
            result.add(local(field.name(), field.typeName(), false, values.get(field)));
        }
        return result;
    }

    private static Map<String, Object> local(String name, String typeName, boolean argument, Value value) {
        Map<String, Object> local = new LinkedHashMap<>();
        local.put("name", name);
        local.put("typeName", typeName);
        local.put("isArgument", argument);
        local.put("value", value);
        local.put("$text:text", "value");
        return local;
    }

    private static String sourceName(Location location) {
        try {
            return location.sourceName();
        } catch (AbsentInformationException e) {
            return null;
        }
    }
}
