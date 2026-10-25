package ch.moldexj.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns what a view returned into a view of the snapshot, by duck typing: a String, or an object with
 * {@code kind()} and the accessors of that kind ({@code text()}, {@code expected()}, {@code rows()}, ...), as the
 * classes of {@code DebugViews} have them.
 */
final class ResultConverter {

    private ResultConverter() {
    }

    static String convert(String title, int priority, Object result) {
        if (result == null) {
            return ViewJson.text(title, priority, "text", "(the view returned null)").toString();
        }
        if (result instanceof CharSequence) {
            return ViewJson.text(title, priority, "text", result.toString()).toString();
        }
        String kind = Reflection.readString(result, "kind");
        if (kind == null) {
            kind = Reflection.readString(result, "getKind");
        }
        switch (kind == null ? "" : kind.toLowerCase()) {
            case "text":
                return ViewJson.text(title, priority, "text", Reflection.readString(result, "text")).toString();
            case "diff":
                return ViewJson.custom(title, priority, "diff")
                        .diff(Reflection.readString(result, "expected"), Reflection.readString(result, "actual"),
                                Reflection.readString(result, "expectedLabel"), Reflection.readString(result, "actualLabel"))
                        .toString();
            case "table":
                return table(title, priority, result);
            case "highlight":
                return highlight(title, priority, result);
            default:
                // Unknown shape: fall back to toString(), so a quick prototype view still shows something.
                return ViewJson.text(title, priority, "text", Safe.toString(result)).toString();
        }
    }

    private static String table(String title, int priority, Object result) {
        List<List<String>> rows = new ArrayList<>();
        List<Object> items = Reflection.items(Reflection.read(result, "rows"));
        for (Object row : items.subList(0, Math.min(items.size(), Limits.ROWS))) {
            rows.add(Reflection.strings(row));
        }
        if (items.size() > Limits.ROWS) {
            List<String> marker = new ArrayList<>();
            marker.add("… " + (items.size() - Limits.ROWS) + " more rows not shown");
            rows.add(marker);
        }
        ViewJson view = ViewJson.custom(title, priority, "table").table(Reflection.strings(Reflection.read(result, "columns")), rows);
        List<String> rowStyles = Reflection.strings(Reflection.read(result, "rowStyles"));
        if (!rowStyles.isEmpty()) {
            view.member("rowStyles", Json.strings(rowStyles.subList(0, Math.min(rowStyles.size(), rows.size()))));
        }
        return view.toString();
    }

    /** Text with styled ranges; the ranges are clipped to the text. */
    private static String highlight(String title, int priority, Object result) {
        String text = Reflection.readString(result, "text");
        text = text == null ? "" : Safe.truncate(text);
        List<String> spans = new ArrayList<>();
        for (Object span : Reflection.items(Reflection.read(result, "spans"))) {
            Object start = Reflection.read(span, "start");
            Object end = Reflection.read(span, "end");
            if (!(start instanceof Integer) || !(end instanceof Integer) || spans.size() >= Limits.ROWS) {
                continue;
            }
            int from = Math.max(0, Math.min((Integer) start, text.length()));
            int to = Math.max(from, Math.min((Integer) end, text.length()));
            spans.add("{\"start\":" + from + ",\"end\":" + to
                    + ",\"style\":" + Json.string(Reflection.readString(span, "style"))
                    + ",\"tooltip\":" + Json.string(Reflection.readString(span, "tooltip")) + "}");
        }
        return ViewJson.text(title, priority, "highlight", text)
                .member("spans", Json.array(spans))
                .member("caption", Json.string(Reflection.readString(result, "caption")))
                .toString();
    }
}
