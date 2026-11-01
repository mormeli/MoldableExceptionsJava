package ch.moldexj.demos.regex;

import ch.moldexj.view.DebugView;
import ch.moldexj.view.DebugViews;
import ch.moldexj.view.annotation.Exception;
import ch.moldexj.view.annotation.StackFrames;
import ch.moldexj.view.context.DebugFrame;
import ch.moldexj.view.context.DebugLocal;
import ch.moldexj.view.context.DebugStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Views for {@link PatternSyntaxException}, which we don't own: static {@code @DebugView} methods that take the
 * exception as a parameter marked {@code @Exception}. Found by the Moldable Exceptions plugin in the project's sources.
 *
 * <p>The views don't re-parse the pattern. At the throw, the stack still holds {@link Pattern}'s recursive-descent
 * parser: one frame per construct it was inside (group, character class, repetition, ...), with its local variables,
 * and the parser's fields (position, flags, groups so far). Below the parser are the API that took the regex and the
 * line of our code that called it.
 */
final class PatternSyntaxExceptionViews {

    /** Asked for by both stack views, so the plugin reads the stack once for them. */
    private static final int DEPTH = 64;

    /** What each parse method of java.util.regex.Pattern is working on. */
    private static final Map<String, String> CONSTRUCTS = Map.ofEntries(
            Map.entry("compile", "whole pattern"),
            Map.entry("expr", "alternation  a|b"),
            Map.entry("sequence", "sequence"),
            Map.entry("group0", "group  ( … )"),
            Map.entry("clazz", "character class  [ … ]"),
            Map.entry("range", "range  a-z"),
            Map.entry("closure", "repetition  * + ? {n,m}"),
            Map.entry("curly", "repetition  {n,m}"),
            Map.entry("escape", "escape  \\…"),
            Map.entry("atom", "literal text"),
            Map.entry("ref", "back reference"),
            Map.entry("family", "property  \\p{…}"),
            Map.entry("groupname", "group name  <…>"),
            Map.entry("accept", "expected character"));

    /** Locals of the parse methods worth showing; the others are parser plumbing (head, tail, end, ...). */
    private static final List<String> SHOWN_LOCALS = List.of("ch", "capturingGroup", "isNeg", "cmin", "name", "prev");

    private PatternSyntaxExceptionViews() {
    }

    /**
     * The pattern with the error position: green what the parser read, red where it failed, grey what it never reached.
     * Needs no stack, so it also works when the views are opened by hand.
     */
    @DebugView(value = "Pattern", priority = 10)
    static DebugViews.View pattern(@Exception PatternSyntaxException e) {
        String pattern = e.getPattern() == null ? "" : e.getPattern();
        int index = e.getIndex();
        if (index < 0) {
            return DebugViews.highlight(pattern).caption(e.getDescription() + " (no position reported).");
        }
        boolean atEnd = index >= pattern.length();
        String text = atEnd ? pattern + " ⟵ end" : pattern;
        int read = Math.min(index, pattern.length());
        DebugViews.HighlightView view = DebugViews.highlight(text).span(0, read, "ok", "read: " + pattern.substring(0, read));
        if (atEnd) {
            view.span(pattern.length(), text.length(), "error", e.getDescription() + ": the pattern ended here");
        } else {
            view.span(index, index + 1, "error", e.getDescription() + " (index " + index + ")")
                    .span(index + 1, pattern.length(), "muted", "never reached");
        }
        return view.caption(e.getDescription() + " at index " + index + (atEnd ? " (end of pattern)." : "."));
    }

    /**
     * What the parser was doing when it failed: the constructs it was inside, outermost first, from its own frames,
     * and its state from the fields of the Pattern being compiled.
     */
    @DebugView(value = "Parser", priority = 20)
    static DebugViews.View parser(@Exception PatternSyntaxException e,
                                  @StackFrames(depth = DEPTH, fields = true) DebugStack stack) {
        List<DebugFrame> frames = parserFrames(stack);
        if (frames.isEmpty()) {
            return DebugViews.text("This exception was not thrown by the regex parser, so its frames are not on the stack.");
        }
        DebugFrame innermost = frames.get(frames.size() - 1);
        String pattern = String.valueOf(field(innermost, "pattern"));
        int cursor = field(innermost, "cursor") instanceof Integer c ? Math.min(c, pattern.length()) : -1;
        int groups = field(innermost, "capturingGroupCount") instanceof Integer n ? n - 1 : -1;
        long open = frames.stream().filter(f -> f.methodName().equals("group0")).count();

        StringBuilder text = new StringBuilder();
        text.append("Pattern ").append(literal(pattern)).append("   flags: ")
                .append(flags(field(innermost, "flags0") instanceof Integer f ? f : 0)).append('\n');
        text.append("The parser read ").append(cursor).append(" of ").append(pattern.length()).append(" characters");
        if (groups > 0) {
            text.append(", opened ").append(groups).append(groups == 1 ? " group" : " groups")
                    .append(", ").append(open).append(" still open");
        }
        text.append('.');
        if (field(innermost, "namedGroups") instanceof Map<?, ?> named && !named.isEmpty()) {
            text.append(" Named groups: ").append(named.keySet()).append('.');
        }
        text.append("\n\nWhere it was, outermost first:\n");

        List<int[]> rows = new ArrayList<>();
        int width = 0; // indentation and construct, so the method names line up
        for (int depth = 0; depth < frames.size(); depth++) {
            width = Math.max(width, 2 * depth + construct(frames.get(depth)).length() + 2);
        }
        for (int depth = 0; depth < frames.size(); depth++) {
            DebugFrame frame = frames.get(depth);
            int start = text.length();
            String construct = construct(frame);
            text.append("  ".repeat(depth)).append(construct).append(" ".repeat(width - 2 * depth - construct.length()))
                    .append(frame.methodName()).append("  ").append(details(frame));
            rows.add(new int[]{start, start + 2 * depth, start + 2 * depth + construct.length(), text.length()});
            text.append('\n');
        }

        DebugViews.HighlightView view = DebugViews.highlight(text.toString().stripTrailing());
        for (int depth = 0; depth < rows.size(); depth++) {
            int[] row = rows.get(depth);
            DebugFrame frame = frames.get(depth);
            String where = "Pattern." + frame.methodName() + ", line " + frame.line();
            boolean failed = depth == rows.size() - 1;
            view.span(row[1], row[2], failed ? "error" : "accent" + (depth % 6 + 1), failed ? where + ": " + e.getDescription() : where);
            view.span(row[2], row[3], failed ? "error" : "muted", null);
        }
        return view.caption(e.getDescription() + ". Each line is a frame of the parser, the last one threw. "
                + "Read from java.util.regex.Pattern's own frames and fields; nothing is re-parsed.");
    }

    /**
     * The line of our code that handed the pattern to a regex API, and how to write it without a regex if the text was
     * meant literally. Uses the live objects of the frames: the receiver of {@code replaceAll} is matched to the
     * caller's variable by identity.
     */
    @DebugView(value = "Fix", priority = 30)
    static DebugViews.View fix(@Exception PatternSyntaxException e,
                               @StackFrames(depth = DEPTH, fields = true) DebugStack stack) {
        List<DebugFrame> frames = stack.frames();
        int caller = 0;
        while (caller < frames.size() && isJdk(frames.get(caller).className())) {
            caller++;
        }
        String regex = literal(e.getPattern());
        if (caller == 0 || caller >= frames.size()) {
            return DebugViews.highlight("Pattern.quote(" + regex + ")").caption(advice(e)
                    + " The call into the regex API was not within the " + DEPTH + " frames read.");
        }
        DebugFrame api = frames.get(caller - 1);
        DebugFrame call = frames.get(caller);
        String receiver = receiverName(api, call);

        String head = simpleName(call.className()) + "." + call.methodName() + ", line " + call.line() + "\n"
                + "  calls " + simpleName(api.className()) + "." + api.methodName() + "(" + arguments(api) + ")"
                + (api.thisObject() == null ? "" : " on " + receiver) + "\n\n";
        String suggestion = switch (api.className() + "#" + api.methodName()) {
            case "java.lang.String#replaceAll" -> receiver + ".replace(" + regex + ", " + argument(api, 1) + ")";
            case "java.lang.String#replaceFirst" ->
                    receiver + ".replaceFirst(Pattern.quote(" + regex + "), Matcher.quoteReplacement(" + argument(api, 1) + "))";
            case "java.lang.String#split" -> receiver + ".split(Pattern.quote(" + regex + "))";
            case "java.lang.String#matches" -> receiver + ".equals(" + regex + ")";
            case "java.util.regex.Pattern#compile" -> "Pattern.compile(" + regex + ", Pattern.LITERAL)";
            default -> "Pattern.quote(" + regex + ")";
        };
        String text = head + api.methodName() + "() reads its argument as a regex. If the text is meant literally:\n\n  "
                + suggestion;

        int callStart = head.indexOf("calls ") + "calls ".length();
        return DebugViews.highlight(text)
                .span(0, head.indexOf('\n'), "strong", call.className() + "." + call.methodName())
                .span(callStart, head.indexOf('\n', callStart), "accent3", "the regex API, " + api.className())
                .span(text.length() - suggestion.length(), text.length(), "ok", "same result, no regex parsing of the text")
                .caption(advice(e));
    }

    // ---- reading the frames ----

    /** The frames of Pattern's parser, outermost (compile) first: instance methods of Pattern above its constructor. */
    private static List<DebugFrame> parserFrames(DebugStack stack) {
        List<DebugFrame> frames = new ArrayList<>();
        for (DebugFrame frame : stack.frames()) {
            boolean parsing = frame.className().equals(Pattern.class.getName()) && frame.thisObject() != null
                    && !frame.methodName().equals("<init>");
            if (!parsing) {
                if (!frames.isEmpty()) {
                    break; // left the parser
                }
                continue;
            }
            frames.add(frame);
        }
        Collections.reverse(frames);
        return frames;
    }

    private static Object field(DebugFrame frame, String name) {
        DebugLocal field = frame.field(name);
        return field == null ? null : field.value();
    }

    private static String construct(DebugFrame frame) {
        return CONSTRUCTS.getOrDefault(frame.methodName(), frame.methodName());
    }

    /** The interesting locals of a parse method: the current character, repetition bounds, a group name, ... */
    private static String details(DebugFrame frame) {
        StringJoiner details = new StringJoiner(", ");
        for (String name : SHOWN_LOCALS) {
            DebugLocal local = frame.local(name);
            Object value = local == null ? null : local.value();
            if (value == null) {
                continue;
            }
            switch (name) {
                // accept(ch, message) checks for an expected character; elsewhere ch is the character being parsed
                case "ch" -> details.add(!(value instanceof Integer c) || c == 0 ? "at the end"
                        : (frame.methodName().equals("accept") ? "expects " : "at ") + quoteChar(c));
                case "capturingGroup" -> details.add(Boolean.TRUE.equals(value) ? "capturing" : "non-capturing");
                case "isNeg" -> {
                    if (Boolean.TRUE.equals(value)) {
                        details.add("negated [^ … ]");
                    }
                }
                case "cmin" -> details.add(bounds(frame));
                case "prev" -> {
                    if (!(value instanceof Number)) { // a node in closure(prev); in atom() it is just an index
                        details.add("of " + node(value));
                    }
                }
                default -> details.add(name + " " + (value instanceof String s ? literal(s) : value));
            }
        }
        return details.toString();
    }

    /** The bounds of {n,m} as far as they were parsed: complete once the parser stands on the '}'. */
    private static String bounds(DebugFrame frame) {
        Object cmin = frame.local("cmin").value();
        DebugLocal cmax = frame.local("cmax");
        DebugLocal ch = frame.local("ch");
        boolean closed = ch != null && Integer.valueOf('}').equals(ch.value());
        return closed && cmax != null ? "{" + cmin + "," + cmax.value() + "}" : "{" + cmin + ",…";
    }

    /** Pattern's internal node classes, as what the repetition applies to. */
    private static String node(Object node) {
        String name = node.getClass().getSimpleName();
        if (name.startsWith("Slice")) return "literal text";
        if (name.contains("CharProperty")) return "a single character or class";
        if (name.startsWith("Group")) return "a group";
        return name;
    }

    // ---- the call into the regex API ----

    private static boolean isJdk(String className) {
        return className.startsWith("java.") || className.startsWith("javax.") || className.startsWith("jdk.")
                || className.startsWith("sun.") || className.startsWith("com.sun.");
    }

    /** The caller's variable holding the receiver (the same object), or the receiver itself as a literal. */
    private static String receiverName(DebugFrame api, DebugFrame caller) {
        Object receiver = api.thisObject();
        for (DebugLocal local : caller.locals()) {
            if (local.value() == receiver) {
                return local.name();
            }
        }
        return receiver instanceof String s ? literal(s) : "…";
    }

    private static String arguments(DebugFrame api) {
        StringJoiner arguments = new StringJoiner(", ");
        for (DebugLocal local : api.locals()) {
            if (local.isArgument()) {
                arguments.add(local.name() + " = " + (local.value() instanceof String s ? literal(s) : local.text()));
            }
        }
        return arguments.toString();
    }

    private static String argument(DebugFrame api, int index) {
        return api.locals().stream().filter(DebugLocal::isArgument).skip(index).findFirst()
                .map(local -> local.value() instanceof String s ? literal(s) : local.name())
                .orElse("…");
    }

    // ---- text ----

    /** What the error usually means. */
    private static String advice(PatternSyntaxException e) {
        String description = e.getDescription() == null ? "" : e.getDescription().toLowerCase(Locale.ROOT);
        if (description.contains("illegal repetition")) {
            return "'{' starts a repetition like {2,4}; a literal brace (a {placeholder}) must be escaped as \\{.";
        } else if (description.contains("dangling meta character")) {
            return "A quantifier (* + ?) has nothing to repeat. \"*.txt\" is a glob, not a regex: "
                    + "FileSystem.getPathMatcher(\"glob:*.txt\") matches files.";
        } else if (description.contains("unclosed")) {
            return "A bracket is not closed; the Parser tab shows which construct was still open.";
        } else if (description.contains("named capturing group")) {
            return "The group name is not defined; the Parser tab lists the named groups the parser had seen.";
        }
        return e.getDescription() + ".";
    }

    private static String flags(int flags) {
        StringJoiner names = new StringJoiner(" | ");
        if ((flags & Pattern.CASE_INSENSITIVE) != 0) names.add("CASE_INSENSITIVE");
        if ((flags & Pattern.COMMENTS) != 0) names.add("COMMENTS");
        if ((flags & Pattern.MULTILINE) != 0) names.add("MULTILINE");
        if ((flags & Pattern.DOTALL) != 0) names.add("DOTALL");
        if ((flags & Pattern.UNICODE_CASE) != 0) names.add("UNICODE_CASE");
        if ((flags & Pattern.UNIX_LINES) != 0) names.add("UNIX_LINES");
        if ((flags & Pattern.LITERAL) != 0) names.add("LITERAL");
        if ((flags & Pattern.CANON_EQ) != 0) names.add("CANON_EQ");
        if ((flags & Pattern.UNICODE_CHARACTER_CLASS) != 0) names.add("UNICODE_CHARACTER_CLASS");
        return names.length() == 0 ? "none" : names.toString();
    }

    private static String quoteChar(int codePoint) {
        return "'" + new String(Character.toChars(codePoint)) + "'";
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    private static String literal(String s) {
        return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
