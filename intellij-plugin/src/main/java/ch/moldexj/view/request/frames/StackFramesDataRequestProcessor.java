package ch.moldexj.view.request.frames;

import ch.moldexj.view.request.DataRequestContext;
import ch.moldexj.view.request.DataRequestProcessor;
import ch.moldexj.view.request.ProvidedValue;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.jdi.LocalVariableProxyImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the stack of the throwing thread with plain JDI reads (no invocation in the debugged JVM): per frame the
 * location, {@code this}, the arguments and the visible local variables. Objects stay live references; the view
 * receives them as the objects themselves. The shape matches {@code DebugStack}, {@code DebugFrame} and
 * {@code DebugLocal} of moldable-views.
 */
public final class StackFramesDataRequestProcessor implements DataRequestProcessor<StackFramesDataRequestArguments> {

    static final int MAX_FRAMES = 200;
    static final int MAX_LOCALS = 64;

    @Override
    public @NotNull ProvidedValue process(@NotNull StackFramesDataRequestArguments arguments, @NotNull DataRequestContext context) {
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
            result.add(frame(frames.get(i), i));
        }
        Map<String, Object> stack = new LinkedHashMap<>();
        stack.put("frames", result);
        stack.put("truncated", frames.size() > count);
        stack.put("$lookup:frameOf", List.of("frames", "className", "methodName"));
        return ProvidedValue.of(stack);
    }

    private static Map<String, Object> frame(StackFrameProxyImpl frame, int index) {
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
        try {
            map.put("thisObject", frame.thisObject()); // null in static methods
        } catch (EvaluateException ignored) {
            map.put("thisObject", null);
        }
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
