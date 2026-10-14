package demo;

import dev.moldable.exceptions.DebugView;
import dev.moldable.exceptions.DebugViews;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static dev.moldable.exceptions.DebugViews.column;

/**
 * Raised when an API response does not match the contract the caller expected.
 *
 * <p>A generic debugger shows this as a message and two long JSON strings. The exception knows better: it carries
 * the expected and actual documents, so it offers a diff, a table of the exact mismatching paths, and the request
 * that produced the response. This is the "moldable exception" idea (Chiş, Nierstrasz, Gîrba, Onward! 2024): the
 * exception, not the tool, supplies the debugging views.
 */
public final class ApiContractMismatchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String method;
    private final URI endpoint;
    private final int statusCode;
    private final String expectedJson;
    private final String actualJson;
    private final transient List<JsonMismatch> mismatches;

    public ApiContractMismatchException(String method, URI endpoint, int statusCode, String expectedJson, String actualJson) {
        this(method, endpoint, statusCode, expectedJson, actualJson, compare(expectedJson, actualJson));
    }

    private ApiContractMismatchException(String method, URI endpoint, int statusCode, String expectedJson, String actualJson,
                                         List<JsonMismatch> mismatches) {
        super(method + " " + endpoint.getPath() + " returned a response that breaks its contract in " + mismatches.size()
                + " place(s)." + (mismatches.isEmpty() ? "" : " First: " + mismatches.get(0).path() + " (" + mismatches.get(0).problem() + ")."));
        this.method = method;
        this.endpoint = endpoint;
        this.statusCode = statusCode;
        this.expectedJson = expectedJson;
        this.actualJson = actualJson;
        this.mismatches = Collections.unmodifiableList(mismatches);
    }

    public List<JsonMismatch> mismatches() {
        return mismatches;
    }

    // ---- Debugger views, found by the Moldable Exceptions IntelliJ plugin through @DebugView ----

    @DebugView(value = "Diff", priority = 10)
    private DebugViews.View diffView() {
        return DebugViews.diff(Json.canonical(Json.parse(expectedJson)), Json.canonical(Json.parse(actualJson)),
                "Expected contract", "Actual response");
    }

    @DebugView(value = "Mismatches", priority = 20)
    private DebugViews.View mismatchesView() {
        return DebugViews.table(mismatches,
                column("Path", JsonMismatch::path),
                column("Problem", JsonMismatch::problem),
                column("Expected", JsonMismatch::expected),
                column("Actual", JsonMismatch::actual));
    }

    @DebugView(value = "Request", priority = 30)
    private String requestView() {
        return method + " " + endpoint + "\n"
                + "→ HTTP " + statusCode + "\n\n"
                + "Reproduce:\n"
                + "  curl -X " + method + " \"" + endpoint + "\" -H \"Accept: application/json\"\n";
    }

    // ---- comparison ----

    private static List<JsonMismatch> compare(String expectedJson, String actualJson) {
        List<JsonMismatch> result = new ArrayList<>();
        walk("$", Json.parse(expectedJson), Json.parse(actualJson), result);
        return result;
    }

    private static void walk(String path, Object expected, Object actual, List<JsonMismatch> out) {
        if (expected instanceof Map && actual instanceof Map) {
            Map<?, ?> e = (Map<?, ?>) expected;
            Map<?, ?> a = (Map<?, ?>) actual;
            for (Map.Entry<?, ?> entry : e.entrySet()) {
                String child = path + "." + entry.getKey();
                if (a.containsKey(entry.getKey())) {
                    walk(child, entry.getValue(), a.get(entry.getKey()), out);
                } else {
                    out.add(new JsonMismatch(child, "missing", Json.compact(entry.getValue()), "—"));
                }
            }
            for (Map.Entry<?, ?> entry : a.entrySet()) {
                if (!e.containsKey(entry.getKey())) {
                    out.add(new JsonMismatch(path + "." + entry.getKey(), "unexpected", "—", Json.compact(entry.getValue())));
                }
            }
        } else if (expected instanceof List && actual instanceof List) {
            List<?> e = (List<?>) expected;
            List<?> a = (List<?>) actual;
            if (e.size() != a.size()) {
                out.add(new JsonMismatch(path, "length differs", e.size() + " items", a.size() + " items"));
            }
            for (int i = 0; i < Math.min(e.size(), a.size()); i++) {
                walk(path + "[" + i + "]", e.get(i), a.get(i), out);
            }
        } else if (!Json.typeOf(expected).equals(Json.typeOf(actual))) {
            out.add(new JsonMismatch(path, "type " + Json.typeOf(expected) + " → " + Json.typeOf(actual),
                    Json.compact(expected), Json.compact(actual)));
        } else if (!sameValue(expected, actual)) {
            out.add(new JsonMismatch(path, "value differs", Json.compact(expected), Json.compact(actual)));
        }
    }

    private static boolean sameValue(Object expected, Object actual) {
        if (expected instanceof BigDecimal && actual instanceof BigDecimal) {
            return ((BigDecimal) expected).compareTo((BigDecimal) actual) == 0;
        }
        return Objects.equals(expected, actual);
    }
}
