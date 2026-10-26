package ch.moldexj.demos.mockito;

import ch.moldexj.view.DebugView;
import ch.moldexj.view.DebugViews;
import ch.moldexj.view.annotation.Exception;
import ch.moldexj.view.annotation.PsiQuery;
import ch.moldexj.view.annotation.StackFrames;
import ch.moldexj.view.context.DebugFrame;
import ch.moldexj.view.context.DebugLocal;
import ch.moldexj.view.context.DebugStack;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.mockito.exceptions.misusing.InvalidUseOfMatchersException;
import org.mockito.internal.matchers.Equals;
import org.mockito.internal.matchers.LocalizedMatcher;
import org.mockito.invocation.Invocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Views for {@link InvalidUseOfMatchersException}: a call that mixes raw values and argument matchers.
 *
 * <p>The exception is thrown by {@code MatchersBinder.validateMatchers(invocation, lastMatchers)}. Its locals hold the
 * call as Mockito saw it, where each matcher was replaced by a placeholder (null, 0, false, "" or an empty
 * collection), and the matchers in source order. To find out which arguments were matchers, we try every
 * order-preserving choice of placeholder positions and score it against the calls the mock actually received.
 */
public final class InvalidUseOfMatchersExceptionView {

    /** Asked for by both views, so the plugin reads the stack once for them. */
    private static final int DEPTH = 16;

    private InvalidUseOfMatchersExceptionView() {
    }

    /** The call rebuilt with its matchers in place, the closest real call, and the fix. */
    @DebugView(value = "Matchers", priority = 30)
    public static DebugViews.View matchers(
            @Exception InvalidUseOfMatchersException exception,
            @PsiQuery(query = "") String loc,
            @StackFrames(depth = DEPTH) DebugStack stack) {
        Analysis a = analyze(stack);
        if (a == null) {
            return DebugViews.text(exception.getMessage());
        }
        String name = mockName(a.wanted) + "." + a.wanted.getMethod().getName();
        Object[] args = a.wanted.getArguments();
        StringBuilder text = new StringBuilder();
        List<int[]> spans = new ArrayList<>(); // start, end, kind (0 raw, 1 matcher, 2 added eq)
        List<String> tips = new ArrayList<>();

        text.append("verify:  ").append(name).append('(');
        for (int i = 0, m = 0; i < args.length; i++) {
            if (i > 0) {
                text.append(", ");
            }
            int start = text.length();
            if (a.isMatcher(i)) {
                LocalizedMatcher matcher = a.matchers.get(m++);
                text.append(matcherText(matcher.getMatcher()));
                spans.add(new int[] {start, text.length(), 1});
                tips.add("matcher, created " + matcher.getLocation());
            } else {
                text.append(render(args[i]));
                spans.add(new int[] {start, text.length(), 0});
                tips.add("raw value: when one argument is a matcher, all of them must be");
            }
        }
        text.append(")\n");

        if (a.actual != null) {
            text.append("actual:  ").append(name).append('(');
            Object[] actualArgs = a.actual.getArguments();
            for (int i = 0; i < actualArgs.length; i++) {
                text.append(i > 0 ? ", " : "").append(render(actualArgs[i]));
            }
            text.append(")\n");
        }

        text.append("fix:     ").append(name).append('(');
        for (int i = 0, m = 0; i < args.length; i++) {
            if (i > 0) {
                text.append(", ");
            }
            if (a.isMatcher(i)) {
                text.append(matcherText(a.matchers.get(m++).getMatcher()));
            } else {
                int start = text.length();
                text.append("eq(").append(render(args[i])).append(')');
                spans.add(new int[] {start, text.length(), 2});
                tips.add("wrapped in eq(...)");
            }
        }
        text.append(')');

        DebugViews.HighlightView view = DebugViews.highlight(text.toString());
        for (int i = 0; i < spans.size(); i++) {
            int[] s = spans.get(i);
            view.span(s[0], s[1], s[2] == 0 ? "error" : s[2] == 1 ? "ok" : "accent1", tips.get(i));
        }
        String caption = args.length + " arguments but " + a.matchers.size() + " matcher(s): use matchers for all"
                + " arguments or for none.";
        if (a.positions == null) {
            caption += " Couldn't tell which arguments were matchers.";
        } else if (a.ambiguous) {
            caption += " Several arguments could have been the matchers; this is the best guess.";
        }
        return view.caption(loc == null || loc.isBlank() ? caption : caption + "\n" + loc.strip());
    }

    /** Every call of the verified method on the mock, the one that fits the verification best marked green. */
    @DebugView(value = "Calls", priority = 31)
    public static DebugViews.View calls(
            @Exception InvalidUseOfMatchersException exception,
            @StackFrames(depth = DEPTH) DebugStack stack) {
        Analysis a = analyze(stack);
        if (a == null) {
            return DebugViews.text(exception.getMessage());
        }
        if (a.actuals.isEmpty()) {
            return DebugViews.text(mockName(a.wanted) + "." + a.wanted.getMethod().getName() + "(...) was never called.");
        }
        return DebugViews.styledTable(a.actuals,
                inv -> inv == a.actual ? "ok" : null,
                DebugViews.column("#", inv -> String.valueOf(inv.getSequenceNumber())),
                DebugViews.column("Arguments", inv -> {
                    List<String> parts = new ArrayList<>();
                    for (Object arg : inv.getArguments()) {
                        parts.add(render(arg));
                    }
                    return String.join(", ", parts);
                }),
                DebugViews.column("Fits", inv -> a.positions == null ? "?"
                        : score(a.wanted.getArguments(), inv.getArguments(), a.positions, a.matchers) + "/"
                        + a.wanted.getArguments().length),
                DebugViews.column("Location", inv -> String.valueOf(inv.getLocation())));
    }

    /** The verification and the calls it is compared to; null if the throw site isn't MatchersBinder. */
    private static Analysis analyze(DebugStack stack) {
        DebugFrame frame = stack.frameOf("org.mockito.internal.invocation.MatchersBinder", "validateMatchers");
        DebugLocal invocationLocal = frame == null ? null : frame.local("invocation");
        DebugLocal matchersLocal = frame == null ? null : frame.local("lastMatchers");
        if (invocationLocal == null || matchersLocal == null) {
            return null;
        }
        Invocation wanted = (Invocation) invocationLocal.value();
        @SuppressWarnings("unchecked")
        List<LocalizedMatcher> matchers = (List<LocalizedMatcher>) matchersLocal.value();

        // the failed verification never gets registered, so these are only real calls
        List<Invocation> actuals = Mockito.mockingDetails(wanted.getMock())
                .getInvocations()
                .stream()
                .filter(inv -> inv.getMethod().equals(wanted.getMethod()))
                .toList();

        Object[] args = wanted.getArguments();
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (isPlaceholder(args[i])) {
                candidates.add(i);
            }
        }
        List<int[]> choices = new ArrayList<>();
        combinations(candidates, matchers.size(), 0, new int[matchers.size()], 0, choices);

        int[] best = null;
        Invocation bestActual = null;
        int bestScore = -1;
        boolean ambiguous = false;
        for (int[] positions : choices) {
            int choiceScore = 0;
            Invocation choiceActual = null;
            for (Invocation actual : actuals) {
                int s = score(args, actual.getArguments(), positions, matchers);
                if (s > choiceScore) {
                    choiceScore = s;
                    choiceActual = actual;
                }
            }
            if (choiceScore > bestScore) {
                best = positions;
                bestActual = choiceActual;
                bestScore = choiceScore;
                ambiguous = false;
            } else if (choiceScore == bestScore) {
                ambiguous = true;
            }
        }
        return new Analysis(wanted, matchers, actuals, best, ambiguous, bestActual);
    }

    /** How many arguments of the actual call fit: matchers that match it, raw values that equal it. */
    private static int score(Object[] wanted, Object[] actual, int[] matcherPositions, List<LocalizedMatcher> matchers) {
        if (actual.length != wanted.length) {
            return 0;
        }
        int score = 0;
        for (int i = 0, m = 0; i < wanted.length; i++) {
            if (m < matcherPositions.length && matcherPositions[m] == i) {
                if (matches(matchers.get(m++).getMatcher(), actual[i])) {
                    score++;
                }
            } else if (Objects.deepEquals(wanted[i], actual[i])) {
                score++;
            }
        }
        return score;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean matches(ArgumentMatcher matcher, Object value) {
        try {
            return matcher.matches(value);
        } catch (RuntimeException e) { // typed matchers throw ClassCastException on the wrong type
            return false;
        }
    }

    /** All order-preserving choices of {@code k} positions out of {@code from}. */
    private static void combinations(List<Integer> from, int k, int start, int[] current, int depth, List<int[]> out) {
        if (depth == k) {
            out.add(current.clone());
            return;
        }
        for (int i = start; i <= from.size() - (k - depth); i++) {
            current[depth] = from.get(i);
            combinations(from, k, i + 1, current, depth + 1, out);
        }
    }

    /** What a matcher method returns in place of the argument: defaults, "" (anyString) and empty collections (anyList, ...). */
    private static boolean isPlaceholder(Object value) {
        return value == null || "".equals(value) || Boolean.FALSE.equals(value) || Character.valueOf('\0').equals(value)
                || (value instanceof Number n && n.doubleValue() == 0)
                || (value instanceof Collection<?> c && c.isEmpty())
                || (value instanceof Map<?, ?> m && m.isEmpty());
    }

    private static String matcherText(ArgumentMatcher<?> matcher) {
        return matcher instanceof Equals ? "eq(" + matcher + ")" : matcher.toString();
    }

    private static String render(Object value) {
        if (value instanceof String s) {
            return '"' + s + '"';
        }
        if (value instanceof Character c) {
            return "'" + c + "'";
        }
        return String.valueOf(value);
    }

    private static String mockName(Invocation invocation) {
        return Mockito.mockingDetails(invocation.getMock()).getMockCreationSettings().getMockName().toString();
    }

    private record Analysis(Invocation wanted, List<LocalizedMatcher> matchers, List<Invocation> actuals,
                            int[] positions, boolean ambiguous, Invocation actual) {

        boolean isMatcher(int argument) {
            if (positions == null) {
                return false;
            }
            for (int p : positions) {
                if (p == argument) {
                    return true;
                }
            }
            return false;
        }
    }
}
