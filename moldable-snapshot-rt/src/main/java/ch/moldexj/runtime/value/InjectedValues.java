package ch.moldexj.runtime.value;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the values the plugin resolved for {@code bind ... value k}, written with a {@code DataOutputStream} by
 * {@code DebuggeeMarshaller}: an int count, then one value per request.
 *
 * <p>A value starts with a tag. Upper case are the primitives, by their JVM descriptor letter; lower case is structure:
 * <pre>
 * Z boolean  B byte  C char  S short  I int  J long  F float  D double
 * n null
 * s string      int length, UTF-8 bytes
 * l list        int count, values
 * m map         int count, (string key, value) pairs
 * h live object int index into the handles array
 * u unavailable string reason
 * </pre>
 */
public final class InjectedValues {

    private InjectedValues() {
    }

    public static List<Object> read(byte[] data, Object[] handles) throws IOException {
        List<Object> values = new ArrayList<>();
        if (data == null || data.length == 0) {
            return values;
        }
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        int count = in.readInt();
        for (int i = 0; i < count; i++) {
            values.add(value(in, handles == null ? new Object[0] : handles));
        }
        return values;
    }

    private static Object value(DataInputStream in, Object[] handles) throws IOException {
        char tag = (char) in.readByte();
        switch (tag) {
            case 'Z': return in.readBoolean();
            case 'B': return in.readByte();
            case 'C': return in.readChar();
            case 'S': return in.readShort();
            case 'I': return in.readInt();
            case 'J': return in.readLong();
            case 'F': return in.readFloat();
            case 'D': return in.readDouble();
            case 'n': return null;
            case 's': return string(in);
            case 'l': {
                int count = in.readInt();
                List<Object> list = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    list.add(value(in, handles));
                }
                return list;
            }
            case 'm': {
                int count = in.readInt();
                Map<String, Object> map = new LinkedHashMap<>();
                for (int i = 0; i < count; i++) {
                    map.put(string(in), value(in, handles));
                }
                return map;
            }
            case 'h': {
                int index = in.readInt();
                return index >= 0 && index < handles.length ? handles[index] : null;
            }
            case 'u': return new Unavailable(string(in));
            default: throw new IOException("unknown tag '" + tag + "' in the values from the debugger");
        }
    }

    /** Length-prefixed UTF-8, unlike readUTF() not limited to 64 KB. */
    private static String string(DataInputStream in) throws IOException {
        byte[] bytes = new byte[in.readInt()];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
