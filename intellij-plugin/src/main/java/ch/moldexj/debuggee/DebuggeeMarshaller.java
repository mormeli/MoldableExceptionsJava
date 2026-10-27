package ch.moldexj.debuggee;

import ch.moldexj.data.ProvidedValue;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.ByteValue;
import com.sun.jdi.CharValue;
import com.sun.jdi.DoubleValue;
import com.sun.jdi.FloatValue;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.LongValue;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ShortValue;
import com.sun.jdi.VoidValue;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns provided values into what {@code ExceptionMolder.mold(plan, data, handles)} takes: one byte[] with
 * all data, read by {@code InjectedValues} in moldable-snapshot-rt (the tags are documented there), and one list of
 * the live objects it refers to. Two mirrors in the debugged JVM, however large the data.
 */
public final class DebuggeeMarshaller {

    private final List<ObjectReference> handles = new ArrayList<>();
    private final Map<ObjectReference, Integer> handleIndex = new HashMap<>();

    @NotNull byte[] write(@NotNull List<ProvidedValue> values) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(values.size());
            for (ProvidedValue value : values) {
                if (value instanceof ProvidedValue.Unavailable(String reason)) {
                    out.writeByte('u');
                    writeString(out, reason);
                } else {
                    write(out, ((ProvidedValue.Available) value).tree());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e); // a ByteArrayOutputStream does not fail
        }
        return bytes.toByteArray();
    }

    @NotNull List<ObjectReference> handles() {
        return handles;
    }

    private void write(DataOutputStream out, Object value) throws IOException {
        switch (value) {
            case null -> out.writeByte('n');
            case VoidValue ignored -> out.writeByte('n');
            case ObjectReference reference -> {
                out.writeByte('h');
                out.writeInt(handleIndex.computeIfAbsent(reference, r -> {
                    handles.add(r);
                    return handles.size() - 1;
                }));
            }
            // JDI primitives and Java boxes alike: by their JVM descriptor letter
            case BooleanValue v -> { out.writeByte('Z'); out.writeBoolean(v.value()); }
            case ByteValue v -> { out.writeByte('B'); out.writeByte(v.value()); }
            case CharValue v -> { out.writeByte('C'); out.writeChar(v.value()); }
            case ShortValue v -> { out.writeByte('S'); out.writeShort(v.value()); }
            case IntegerValue v -> { out.writeByte('I'); out.writeInt(v.value()); }
            case LongValue v -> { out.writeByte('J'); out.writeLong(v.value()); }
            case FloatValue v -> { out.writeByte('F'); out.writeFloat(v.value()); }
            case DoubleValue v -> { out.writeByte('D'); out.writeDouble(v.value()); }
            case Boolean v -> { out.writeByte('Z'); out.writeBoolean(v); }
            case Byte v -> { out.writeByte('B'); out.writeByte(v); }
            case Character v -> { out.writeByte('C'); out.writeChar(v); }
            case Short v -> { out.writeByte('S'); out.writeShort(v); }
            case Integer v -> { out.writeByte('I'); out.writeInt(v); }
            case Long v -> { out.writeByte('J'); out.writeLong(v); }
            case Float v -> { out.writeByte('F'); out.writeFloat(v); }
            case Double v -> { out.writeByte('D'); out.writeDouble(v); }
            case Map<?, ?> map -> {
                out.writeByte('m');
                out.writeInt(map.size());
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    writeString(out, String.valueOf(entry.getKey()));
                    write(out, entry.getValue());
                }
            }
            case List<?> list -> {
                out.writeByte('l');
                out.writeInt(list.size());
                for (Object item : list) {
                    write(out, item);
                }
            }
            default -> {
                out.writeByte('s');
                writeString(out, String.valueOf(value));
            }
        }
    }

    /** Length-prefixed UTF-8: unlike writeUTF() not limited to 64 KB. */
    private static void writeString(DataOutputStream out, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }
}
