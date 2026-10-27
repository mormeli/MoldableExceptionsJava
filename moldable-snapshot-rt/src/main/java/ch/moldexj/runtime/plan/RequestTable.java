package ch.moldexj.runtime.plan;

import ch.moldexj.runtime.mold.Json;

import ch.moldexj.runtime.view.ViewManifest;
import ch.moldexj.runtime.view.ViewManifest.AnnotationEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The debugger data a plan asks the plugin for, one request per distinct parameter declaration. Read by
 * {@code DebuggeeMolder} in the plugin from {@link ch.moldexj.runtime.MoldPlan#requests}:
 *
 * <pre>
 * [{"annotations":[{"name":"ch.moldexj.view.annotation.StackFrames","attributes":{"depth":"i:5","fields":"z:false"}}]}]
 * </pre>
 *
 * The position in the array is the request index, and the plugin sends the values in that order. Attribute values keep
 * the tagged form of the manifest ({@link ViewManifest#encodeValue}), since JSON numbers lose their type.
 */
public final class RequestTable {

    private final Map<String, Integer> indexByJson = new LinkedHashMap<>();

    /** The index of the request for a parameter with these annotations; equal declarations share one request. */
    public int indexOf(List<AnnotationEntry> annotations) {
        String json = json(annotations);
        Integer index = indexByJson.get(json);
        if (index == null) {
            index = indexByJson.size();
            indexByJson.put(json, index);
        }
        return index;
    }

    public int size() {
        return indexByJson.size();
    }

    public String toJson() {
        return Json.array(new ArrayList<>(indexByJson.keySet()));
    }

    private static String json(List<AnnotationEntry> annotations) {
        List<String> entries = new ArrayList<>();
        for (AnnotationEntry annotation : annotations) {
            List<String> attributes = new ArrayList<>();
            for (Map.Entry<String, Object> attribute : annotation.attributes.entrySet()) {
                String value = ViewManifest.encodeValue(attribute.getValue());
                if (value != null) {
                    attributes.add(Json.string(attribute.getKey()) + ":" + Json.string(value));
                }
            }
            entries.add("{\"name\":" + Json.string(annotation.name) + ",\"attributes\":{" + String.join(",", attributes) + "}}");
        }
        return "{\"annotations\":" + Json.array(entries) + "}";
    }
}
