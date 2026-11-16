package ch.moldexj.log;

import ch.moldexj.ExceptionSnapshot;
import ch.moldexj.runtime.ExceptionPrinter;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * The exceptions of a log file: every line a program wrote with {@code ExceptionPrinter}, that is {@code [moldex] }
 * followed by the snapshot JSON. Everything else in the file (stack traces, the program's own log lines) is ignored.
 *
 * @param entries the exceptions, in the order of the file
 * @param skipped lines that have the prefix but no readable snapshot
 */
public record MoldexLog(@NotNull List<Entry> entries, int skipped) {

    private static final Gson GSON = new Gson();

    /**
     * @param line     1-based line of the log file
     * @param snapshot never empty: {@code chain} has at least the exception itself
     */
    public record Entry(int line, @NotNull ExceptionSnapshot snapshot) {

        /** The exception that was printed; its causes follow in the chain. */
        public @NotNull ExceptionSnapshot.Node root() {
            return snapshot.chain.get(0);
        }
    }

    public static @NotNull MoldexLog read(@NotNull Reader reader) throws IOException {
        List<Entry> entries = new ArrayList<>();
        int skipped = 0;
        BufferedReader lines = new BufferedReader(reader);
        int number = 0;
        for (String line = lines.readLine(); line != null; line = lines.readLine()) {
            number++;
            // Not startsWith: a logger puts its own prefix (time, level, thread) in front.
            int prefix = line.indexOf(ExceptionPrinter.PREFIX);
            if (prefix < 0) {
                continue;
            }
            ExceptionSnapshot snapshot = parse(line.substring(prefix + ExceptionPrinter.PREFIX.length()));
            if (snapshot == null) {
                skipped++;
            } else {
                entries.add(new Entry(number, snapshot));
            }
        }
        return new MoldexLog(entries, skipped);
    }

    /** Null if the text is not a snapshot with at least one exception, e.g. "Could not print the views: ...". */
    private static @Nullable ExceptionSnapshot parse(String json) {
        if (!json.startsWith("{")) {
            return null;
        }
        ExceptionSnapshot snapshot;
        try {
            snapshot = GSON.fromJson(json, ExceptionSnapshot.class);
        } catch (JsonParseException e) {
            return null;
        }
        if (snapshot == null || snapshot.chain == null || snapshot.chain.isEmpty() || snapshot.chain.contains(null)) {
            return null;
        }
        // An explicit null in the JSON overwrites the defaults of ExceptionSnapshot; the dialog expects lists.
        if (snapshot.notes == null) {
            snapshot.notes = new ArrayList<>();
        }
        for (ExceptionSnapshot.Node node : snapshot.chain) {
            if (node.type == null) {
                node.type = "";
            }
            if (node.label == null) {
                node.label = "";
            }
            if (node.frames == null) {
                node.frames = new ArrayList<>();
            }
            if (node.views == null) {
                node.views = new ArrayList<>();
            }
        }
        return snapshot;
    }
}
