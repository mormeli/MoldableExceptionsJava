package ch.moldexj.runtime;

import java.util.List;
import java.util.stream.Collectors;

/**
 * One view of the snapshot, as {@code ExceptionSnapshot.View} in the plugin reads it:
 * {@code new ViewJson("Diff", "diff", true, 10).diff(expected, actual, "Expected", "Actual").toString()}.
 */
final class ViewJson {

    private final StringBuilder out = new StringBuilder();

    ViewJson(String title, String kind, boolean custom, int priority) {
        out.append("{\"title\":").append(Json.string(title))
                .append(",\"kind\":").append(Json.string(kind))
                .append(",\"custom\":").append(custom)
                .append(",\"priority\":").append(priority);
    }

    /** A view of the exception itself, sorted by the priority of its {@code @DebugView}. */
    static ViewJson custom(String title, int priority, String kind) {
        return new ViewJson(title, kind, true, priority);
    }

    static ViewJson text(String title, int priority, String kind, String text) {
        return custom(title, priority, kind).text(text);
    }

    ViewJson text(String text) {
        return member("text", Json.string(Safe.truncate(text == null ? "" : text)));
    }

    ViewJson diff(String expected, String actual, String expectedLabel, String actualLabel) {
        return member("expected", Json.string(Safe.truncate(expected == null ? "" : expected)))
                .member("actual", Json.string(Safe.truncate(actual == null ? "" : actual)))
                .member("expectedLabel", Json.string(expectedLabel == null ? "Expected" : expectedLabel))
                .member("actualLabel", Json.string(actualLabel == null ? "Actual" : actualLabel));
    }

    ViewJson table(List<String> columns, List<List<String>> rows) {
        return member("columns", Json.strings(columns))
                .member("rows", Json.array(rows.stream().map(Json::strings).collect(Collectors.toList())));
    }

    /** Adds a member whose value is already JSON. */
    ViewJson member(String name, String json) {
        out.append(',').append(Json.string(name)).append(':').append(json);
        return this;
    }

    @Override
    public String toString() {
        return out + "}";
    }
}
