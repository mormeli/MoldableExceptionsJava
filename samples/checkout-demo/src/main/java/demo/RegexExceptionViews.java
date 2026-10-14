package demo;

import dev.moldable.exceptions.DebugView;
import dev.moldable.exceptions.DebugViews;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Views for {@link PatternSyntaxException}, which we don't own: static {@code @DebugView} methods that take the
 * exception as their only parameter. Found by the Moldable Exceptions plugin in the project's sources.
 */
final class RegexExceptionViews {

    private static final String META = "\\^$.|?*+()[]{}";

    private RegexExceptionViews() {
    }

    /**
     * The pattern itself, highlighted: brackets colored by nesting depth (like rainbow brackets), unbalanced brackets
     * and the error position in red, escapes dimmed. Hover a colored part for details.
     */
    @DebugView(value = "Pattern", priority = 10)
    static DebugViews.View pattern(PatternSyntaxException e) {
        String pattern = e.getPattern();
        Scan scan = scan(pattern);
        DebugViews.HighlightView view = DebugViews.highlight(pattern);

        for (int[] escape : scan.escapes) {
            view.span(escape[0], escape[1], "muted", "escaped: " + pattern.substring(escape[0], escape[1]));
        }
        for (Bracket b : scan.brackets) {
            if (b.unmatchedClose) {
                view.span(b.index, b.index + 1, "error", "'" + b.c + "' has no matching opening bracket");
            } else if (b.close < 0) {
                view.span(b.index, b.index + 1, "error", "'" + b.c + "' is never closed");
            } else {
                String style = "accent" + (b.depth % 6 + 1);
                String tip = kindOf(b.c) + " " + pattern.substring(b.index, b.close + 1);
                view.span(b.index, b.index + 1, style, tip);
                view.span(b.close, b.close + 1, style, tip);
            }
        }
        int index = e.getIndex();
        if (index >= 0 && pattern.length() > 0) {
            int at = Math.min(index, pattern.length() - 1);
            view.span(at, at + 1, "error", e.getDescription() + " (index " + index + ")");
        }

        String where = index < 0 ? "no position reported" : "index " + index;
        return view.caption(e.getDescription() + " at " + where + ". Brackets are colored by nesting depth; red marks the "
                + "error and unbalanced brackets. Hover for details.");
    }

    /** Every ( [ { in the pattern and where it is closed. Most regex syntax errors are unbalanced brackets. */
    @DebugView(value = "Brackets", priority = 20)
    static DebugViews.View brackets(PatternSyntaxException e) {
        String pattern = e.getPattern();
        List<Bracket> brackets = scan(pattern).brackets;
        if (brackets.isEmpty()) {
            return DebugViews.text("No brackets in the pattern.");
        }
        return DebugViews.styledTable(brackets,
                b -> b.unmatchedClose || b.close < 0 ? "error" : null,
                DebugViews.column("Index", b -> b.index),
                DebugViews.column("Bracket", b -> b.c + "  " + kindOf(b.c)),
                DebugViews.column("Status", b -> b.unmatchedClose ? "no matching opening bracket"
                        : b.close < 0 ? "never closed"
                        : "closed at " + b.close + "   " + pattern.substring(b.index, b.close + 1)));
    }

    /** What the error usually means, and the pattern escaped for a literal match. */
    @DebugView(value = "Fix", priority = 30)
    static DebugViews.View fix(PatternSyntaxException e) {
        String pattern = e.getPattern();
        String description = e.getDescription() == null ? "" : e.getDescription().toLowerCase(Locale.ROOT);
        String advice;
        if (description.contains("illegal repetition")) {
            advice = "'{' starts a repetition like {2,4}. A literal brace (for example a {placeholder} in a template) "
                    + "must be escaped as \\{ ... \\}, or the whole text quoted with Pattern.quote.";
        } else if (description.contains("unclosed counted closure")) {
            advice = "A repetition like {2,4} is missing its '}'. If the brace is meant literally, escape it as \\{.";
        } else if (description.contains("dangling meta character")) {
            advice = "A quantifier (* + ?) has nothing to repeat, e.g. \"*.txt\", which is a glob, not a regex. "
                    + "Escape it, or match files with FileSystem.getPathMatcher(\"glob:*.txt\").";
        } else if (description.contains("unclosed group")) {
            advice = "A '(' has no matching ')'. The Pattern tab marks it in red.";
        } else if (description.contains("unmatched closing ')'")) {
            advice = "A ')' has no matching '('. Escape it as \\) if it is meant literally.";
        } else if (description.contains("unclosed character class")) {
            advice = "A '[' has no matching ']'. Escape it as \\[ if it is meant literally.";
        } else if (description.contains("escape") || description.contains("backslash") || description.contains("character range")) {
            advice = "A backslash sequence is not valid here. In Java source a regex backslash is written \\\\, so \"\\\\d\" "
                    + "in code is \\d in the regex. File paths need quoting: Pattern.quote(path).";
        } else {
            advice = e.getDescription();
        }

        String quoted = javaLiteral(Pattern.quote(pattern));
        String escaped = javaLiteral(escapeMetaCharacters(pattern));
        boolean escapedCompiles = compiles(escapeMetaCharacters(pattern));
        String header = "If the pattern is meant literally, use one of:\n\n";
        String line1 = "  Pattern.quote:        ";
        String line2 = "  escaped characters:   ";
        String text = header + line1 + quoted + "\n" + line2 + escaped + (escapedCompiles ? "   ✓ compiles" : "   ✗ still invalid")
                + "\n\nOr avoid regex: String.replace(\"{name}\", value) matches literally;\n"
                + "replaceAll(), replaceFirst(), split() and matches() all take a regex.";

        int q = header.length() + line1.length();
        int x = q + quoted.length() + 1 + line2.length();
        DebugViews.HighlightView view = DebugViews.highlight(text)
                .span(q, q + quoted.length(), "strong", "Pattern.quote(pattern)")
                .span(x, x + escaped.length(), "strong", "every regex metacharacter escaped")
                .caption(advice);
        int mark = text.indexOf(escapedCompiles ? "✓" : "✗");
        view.span(mark, text.indexOf('\n', mark), escapedCompiles ? "ok" : "error", null);
        int replace = text.indexOf("String.replace");
        return view.span(replace, replace + "String.replace".length(), "ok", "no regex involved");
    }

    // ---- scanning ----

    static final class Bracket {
        final int index;
        final char c;
        final int depth;
        int close = -1;
        boolean unmatchedClose;

        Bracket(int index, char c, int depth) {
            this.index = index;
            this.c = c;
            this.depth = depth;
        }
    }

    static final class Scan {
        final List<Bracket> brackets = new ArrayList<>();
        final List<int[]> escapes = new ArrayList<>();
    }

    /** A small scanner: honors backslash escapes, \Q...\E quoting, and literal brackets inside [...] classes. */
    static Scan scan(String pattern) {
        Scan scan = new Scan();
        Deque<Bracket> open = new ArrayDeque<>();
        int classDepth = 0;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\\') {
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == 'Q') {
                    int end = pattern.indexOf("\\E", i + 2);
                    int stop = end < 0 ? pattern.length() : end + 2;
                    scan.escapes.add(new int[]{i, stop});
                    i = stop - 1;
                } else {
                    scan.escapes.add(new int[]{i, Math.min(i + 2, pattern.length())});
                    i++;
                }
                continue;
            }
            boolean opens = c == '[' || (classDepth == 0 && (c == '(' || c == '{'));
            boolean closes = (c == ']' && classDepth > 0) || (classDepth == 0 && (c == ')' || c == '}'));
            if (opens) {
                Bracket b = new Bracket(i, c, open.size());
                scan.brackets.add(b);
                open.push(b);
                if (c == '[') {
                    classDepth++;
                    // a ']' right after '[' or '[^' is a literal character, not the end of the class
                    if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '^') {
                        i++;
                    }
                    if (i + 1 < pattern.length() && pattern.charAt(i + 1) == ']') {
                        i++;
                    }
                }
            } else if (closes) {
                char expected = c == ')' ? '(' : c == ']' ? '[' : '{';
                if (!open.isEmpty() && open.peek().c == expected) {
                    open.pop().close = i;
                    if (c == ']') {
                        classDepth--;
                    }
                } else {
                    Bracket b = new Bracket(i, c, open.size());
                    b.unmatchedClose = true;
                    scan.brackets.add(b);
                }
            }
        }
        return scan;
    }

    private static String kindOf(char c) {
        switch (c) {
            case '(':
            case ')':
                return "group";
            case '[':
            case ']':
                return "character class";
            default:
                return "repetition";
        }
    }

    static String escapeMetaCharacters(String pattern) {
        StringBuilder out = new StringBuilder();
        for (char c : pattern.toCharArray()) {
            if (META.indexOf(c) >= 0) {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }

    private static boolean compiles(String regex) {
        try {
            Pattern.compile(regex);
            return true;
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private static String javaLiteral(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
