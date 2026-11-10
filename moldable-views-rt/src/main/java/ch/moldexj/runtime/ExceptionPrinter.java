package ch.moldexj.runtime;

import ch.moldexj.runtime.util.Safe;

import java.io.PrintStream;

/**
 * Prints the moldable views of an exception without a debugger: one line, {@code [moldex] } followed by the same JSON
 * the plugin gets from {@link ExceptionMolder#mold}. Views that need debugger data ({@code @StackFrames}, ...) say so
 * instead of running, since without a debugger a view only sees the exception itself.
 *
 * <pre>{@code
 * ExceptionPrinter.install();              // once, e.g. in main: prints uncaught exceptions that have views
 *
 * try { ... } catch (OrderRejectedException e) {
 *     ExceptionPrinter.print(e);           // a caught exception
 * }
 * }</pre>
 */
public final class ExceptionPrinter {

    /** Starts every line, to find them in mixed output (stack traces, the program's own logs). */
    public static final String PREFIX = "[moldex] ";

    private static UncaughtHandler installed;

    private ExceptionPrinter() {
    }

    /** Prints the views of the chain of {@code t} to {@code System.err}. Never throws. */
    public static void print(Throwable t) {
        print(t, System.err);
    }

    /** Prints the views of the chain of {@code t} to {@code out}, for example a log file. Never throws. */
    public static void print(Throwable t, PrintStream out) {
        if (t == null || out == null) {
            return;
        }
        try {
            String json = ExceptionMolder.mold(ExceptionMolder.plan(t), null, null);
            // One println writes the whole line at once, so lines of several threads do not mix.
            out.println(PREFIX + json);
        } catch (Throwable e) {
            try {
                out.println(PREFIX + "Could not print the views: " + Safe.describe(e));
            } catch (Throwable ignored) {
                // nothing left to report to
            }
        }
    }

    /**
     * Installs the JVM-wide default handler for uncaught exceptions. It prints the views of an uncaught exception that
     * has views of its own, then does what would have happened without it: calls the handler that was installed
     * before, or prints the stack trace. Calling it again does nothing.
     *
     * <p>The default handler covers every thread without a handler of its own, including {@code main} and the Swing
     * event thread. It does not see exceptions the program or a framework catches; use {@link #print} there.
     */
    public static synchronized void install() {
        if (installed != null) {
            return;
        }
        installed = new UncaughtHandler(Thread.getDefaultUncaughtExceptionHandler());
        Thread.setDefaultUncaughtExceptionHandler(installed);
    }

    /** Restores the handler that was installed before {@link #install}. For tests. */
    public static synchronized void uninstall() {
        if (installed == null) {
            return;
        }
        if (Thread.getDefaultUncaughtExceptionHandler() == installed) {
            Thread.setDefaultUncaughtExceptionHandler(installed.previous);
        }
        installed = null;
    }

    private static final class UncaughtHandler implements Thread.UncaughtExceptionHandler {

        final Thread.UncaughtExceptionHandler previous;

        UncaughtHandler(Thread.UncaughtExceptionHandler previous) {
            this.previous = previous;
        }

        @Override
        public void uncaughtException(Thread thread, Throwable e) {
            try {
                // Only exceptions with views of their own: the built-in views would repeat the stack trace below.
                MoldPlan plan = ExceptionMolder.plan(e);
                if (plan.moldable) {
                    System.err.println(PREFIX + ExceptionMolder.mold(plan, null, null));
                }
            } catch (Throwable ignored) {
                // the stack trace below still appears
            }
            if (previous != null) {
                previous.uncaughtException(thread, e);
            } else {
                // What the JVM does without a default handler. Not thread.getThreadGroup().uncaughtException(...):
                // that calls the default handler, which is this one, and would never return.
                System.err.print("Exception in thread \"" + thread.getName() + "\" ");
                e.printStackTrace(System.err);
            }
        }
    }
}
