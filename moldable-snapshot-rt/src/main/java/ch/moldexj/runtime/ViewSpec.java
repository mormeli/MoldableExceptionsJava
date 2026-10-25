package ch.moldexj.runtime;

import ch.moldexj.runtime.manifest.ViewManifest;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the view spec the plugin sends: every view the IDE knows, with how to fill each parameter.
 *
 * <pre>
 * view com.acme.Views locals static
 * bind 0 exception             the exception the view is shown for
 * bind 1 value 0               injected value 0
 * bind 2 missing No%20provider  cannot be filled, with the reason
 * </pre>
 *
 * Tokens are encoded like the manifest ({@link ViewManifest#encode}).
 */
final class ViewSpec {

    private ViewSpec() {
    }

    static List<ViewDeclaration> parse(String spec) {
        List<ViewDeclaration> views = new ArrayList<>();
        if (spec == null) {
            return views;
        }
        ViewDeclaration current = null;
        for (String line : spec.split("\n")) {
            String[] tokens = line.trim().split(" ");
            if (tokens[0].equals("view") && tokens.length >= 4) {
                current = new ViewDeclaration(ViewManifest.decode(tokens[1]), ViewManifest.decode(tokens[2]), tokens[3].equals("static"));
                views.add(current);
            } else if (tokens[0].equals("bind") && tokens.length >= 3 && current != null) {
                int index = parseInt(tokens[1]);
                Binding binding = binding(tokens);
                if (index >= 0 && binding != null) {
                    current.bind(index, binding);
                }
            }
        }
        return views;
    }

    private static Binding binding(String[] tokens) {
        switch (tokens[2]) {
            case "exception":
                return Binding.exception();
            case "value":
                return tokens.length > 3 && parseInt(tokens[3]) >= 0 ? Binding.value(parseInt(tokens[3])) : null;
            case "missing":
                return Binding.missing(tokens.length > 3 ? ViewManifest.decode(tokens[3]) : "Not available.");
            default:
                return Binding.missing("Unknown binding '" + tokens[2] + "' from the plugin.");
        }
    }

    private static int parseInt(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
