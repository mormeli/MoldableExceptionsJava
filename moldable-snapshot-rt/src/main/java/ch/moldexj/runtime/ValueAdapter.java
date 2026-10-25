package ch.moldexj.runtime;

import java.lang.invoke.MethodType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fits a value from the debugger to a parameter or return type: maps become proxies of interfaces ({@code DebugStack}),
 * lists are adapted element by element, numbers are converted, everything else must already fit.
 */
final class ValueAdapter {

    private ValueAdapter() {
    }

    /** @throws IllegalArgumentException if the value does not fit the type */
    @SuppressWarnings("unchecked")
    static Object adapt(Object value, Class<?> type, Type genericType) {
        if (value == null) {
            return defaultValue(type);
        }
        Class<?> target = type.isPrimitive() ? boxed(type) : type;
        if (value instanceof Number && Number.class.isAssignableFrom(target) && !target.isInstance(value)) {
            return convert((Number) value, target);
        }
        if (value instanceof List && target.isAssignableFrom(List.class)) {
            Type element = genericType instanceof ParameterizedType ? ((ParameterizedType) genericType).getActualTypeArguments()[0] : null;
            if (!(element instanceof Class) || element == Object.class) {
                return value;
            }
            return ((List<Object>) value).stream()
                    .map(item -> adapt(item, (Class<?>) element, element))
                    .collect(Collectors.toList());
        }
        if (value instanceof Map && target.isInterface() && !target.isInstance(value)) {
            return Proxy.newProxyInstance(target.getClassLoader(), new Class<?>[]{target}, new DataProxy((Map<String, Object>) value));
        }
        if (target.isInstance(value)) {
            return value;
        }
        throw new IllegalArgumentException("a " + value.getClass().getName() + " from the debugger does not fit " + type.getName());
    }

    /** null for objects, 0 / false / '\0' for primitives. */
    static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == char.class) {
            return '\0';
        }
        return convert(0, boxed(type));
    }

    private static Class<?> boxed(Class<?> primitive) {
        return MethodType.methodType(primitive).wrap().returnType();
    }

    private static Number convert(Number n, Class<?> target) {
        if (target == Integer.class) return n.intValue();
        if (target == Long.class) return n.longValue();
        if (target == Double.class) return n.doubleValue();
        if (target == Float.class) return n.floatValue();
        if (target == Short.class) return n.shortValue();
        if (target == Byte.class) return n.byteValue();
        return n;
    }
}
