package ch.moldexj.runtime.value;

import ch.moldexj.runtime.util.Reflection;
import ch.moldexj.runtime.util.Safe;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Answers the methods of an interface ({@code DebugStack}, ...) from a map the debugger sent: {@code frames()} returns
 * the entry "frames", adapted to the return type. Providers can add computed methods with two special entries:
 * <ul>
 *   <li>{@code "$lookup:local": ["locals", "name"]}: {@code local(x)} returns the first element of the list "locals"
 *       whose "name" equals x (more arguments compare more fields);</li>
 *   <li>{@code "$text:text": "value"}: {@code text()} returns a safe, shortened toString() of "value".</li>
 * </ul>
 * Unknown methods return null, 0 or false.
 */
final class DataProxy implements InvocationHandler {

    private final Map<String, Object> data;

    DataProxy(Map<String, Object> data) {
        this.data = data;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        String name = method.getName();
        int count = args == null ? 0 : args.length;
        if (name.equals("equals") && count == 1) {
            return proxy == args[0];
        }
        if (name.equals("hashCode") && count == 0) {
            return System.identityHashCode(proxy);
        }
        if (name.equals("toString") && count == 0) {
            return describe();
        }
        try {
            return ValueAdapter.adapt(lookup(name, args), method.getReturnType(), method.getGenericReturnType());
        } catch (IllegalArgumentException e) {
            return ValueAdapter.defaultValue(method.getReturnType());
        }
    }

    @SuppressWarnings("unchecked")
    private Object lookup(String name, Object[] args) {
        if (args == null || args.length == 0) {
            if (data.containsKey(name)) {
                return data.get(name);
            }
            Object textOf = data.get("$text:" + name);
            if (textOf instanceof String) {
                Object source = data.get(textOf);
                return source == null ? "null" : Safe.truncate(Safe.toString(source));
            }
            return null;
        }
        Object lookup = data.get("$lookup:" + name);
        if (!(lookup instanceof List) || ((List<?>) lookup).isEmpty()) {
            return null;
        }
        List<Object> spec = (List<Object>) lookup;
        for (Object item : Reflection.items(data.get(String.valueOf(spec.get(0))))) {
            if (item instanceof Map && matches((Map<String, Object>) item, spec, args)) {
                return item;
            }
        }
        return null;
    }

    /** spec = [list name, field 1, field 2, ...]; field i must equal argument i. */
    private static boolean matches(Map<String, Object> item, List<Object> spec, Object[] args) {
        for (int i = 0; i < args.length && i + 1 < spec.size(); i++) {
            Object field = item.get(String.valueOf(spec.get(i + 1)));
            if (field == null ? args[i] != null : !field.equals(args[i])) {
                return false;
            }
        }
        return true;
    }

    private String describe() {
        return data.entrySet().stream()
                .filter(entry -> !entry.getKey().startsWith("$"))
                .map(entry -> entry.getKey() + "=" + (entry.getValue() instanceof Collection
                        ? "[" + ((Collection<?>) entry.getValue()).size() + " items]"
                        : Safe.toString(entry.getValue())))
                .collect(Collectors.joining(", ", "{", "}"));
    }
}
