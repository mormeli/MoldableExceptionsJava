# Moldable Exceptions for IntelliJ IDEA (prototype)

An IntelliJ IDEA plugin that lets **a Java exception decide how it is debugged**: the Java port of the
Visual Studio Moldable Exception Visualizer, after *moldable exceptions* (Chiş, Nierstrasz, Gîrba,
Onward! 2024; Glamorous Toolkit by feenk).

```java
public final class ApiContractMismatchException extends RuntimeException {

    @DebugView(value = "Diff", priority = 10)
    private DebugViews.View diffView() {
        return DebugViews.diff(expectedJson, actualJson, "Expected contract", "Actual response");
    }

    @DebugView(value = "Mismatches", priority = 20)
    private DebugViews.View mismatchesView() {
        return DebugViews.table(mismatches, column("Path", JsonMismatch::path), column("Problem", JsonMismatch::problem));
    }

    @DebugView(value = "Request", priority = 30)
    private String requestView() { return method + " " + endpoint + " → HTTP " + statusCode; }
}
```

When the debugger stops on an exception whose chain (cause, suppressed) contains such an exception, the
plugin opens its views: the chain on the left, one tab per view on the right. Diffs use IntelliJ's own
diff viewer, with JSON highlighting when it applies.

## View kinds

| Factory | Shown as |
| --- | --- |
| `DebugViews.text(s)` or return a `String` | selectable monospaced text |
| `DebugViews.highlight(s).span(start, end, style, tooltip).caption(c)` | an editor viewer with colored ranges, a tooltip per range, and a caption; short one-line texts are shown larger |
| `DebugViews.diff(expected, actual)` | IntelliJ's diff viewer (JSON highlighted when it applies) |
| `DebugViews.table(items, column(...)...)` | a sortable table |
| `DebugViews.styledTable(items, rowStyle, column(...)...)` | a table with colored rows |

Styles: `error` (red background, wavy underline), `warning`, `ok` (green), `muted` (grey), `strong` (bold), and
`accent1` … `accent6` (distinct colors, e.g. for nesting levels). Every color has a light and a dark variant.

## Views for exceptions you don't own

`DateTimeParseException`, `SQLException`, `HttpClientErrorException` and other library exceptions can't get
`@DebugView` methods of their own. Java has no extension methods, so write a **static** `@DebugView` method that takes
the exception as its only parameter, in any class of your project:

```java
final class JdkExceptionViews {

    @DebugView(value = "Parse position", priority = 10)
    static String parsePosition(DateTimeParseException e) {
        return e.getParsedString() + "\n" + "~".repeat(e.getErrorIndex()) + "^";
    }
}
```

The plugin finds these methods through IntelliJ's index of your project's sources; nothing has to be registered. They
show up for every exception in the chain that matches the parameter type, so a view for `SQLException` also applies
to its subclasses. The class only has to be on the debugged program's classpath (main sources, not test sources, unless
you are debugging a test).

`samples/checkout-demo` has `JdkExceptionViews` (the parse position, and a table of which common date formats accept
the input) and `DateDemo`, which throws an uncaught `DateTimeParseException`. The debugger stops inside `java.time`
where it is thrown, and the views open there.

## Layout

| Module | Runs in | Role |
| --- | --- | --- |
| `moldable-views` | your app (Java 8+) | `@DebugView` and `DebugViews.text / diff / table`. Optional: the plugin finds views by annotation **name**, so you can declare your own `@DebugView`. |
| `moldable-snapshot-rt` | the **debugged JVM** | `MoldableSnapshot`: walks the chain, calls the `@DebugView` methods, returns one JSON string. Counterpart of the VS object source. |
| `intellij-plugin` | IntelliJ IDEA | Loads the helper into the paused JVM, shows the dialog, opens it automatically at exception stops. |
| `samples/checkout-demo` | – | Throws an `ApiContractMismatchException`, wraps it in an `IllegalStateException` that nothing catches. |

## Try it

1. Open this folder in IntelliJ IDEA 2025.3 or later as a Gradle project. The build needs Gradle 9 (the wrapper is
   set to 9.1.0) and a JDK 21.
2. Run the Gradle task **intellij-plugin › intellijPlatform › runIde**. A second IDE starts with the plugin.
3. In that IDE, open this same folder and debug `demo.Main`, `demo.DateDemo` or `demo.PatternDemo`. No breakpoint
   is needed: the debugger stops where the uncaught exception is thrown, and the views open.

**Stopping without a breakpoint.** When a debug session starts, the plugin registers its own request for uncaught
exceptions (the mechanism IntelliJ's breakpoints use internally). At such an exception it takes the snapshot, like a
breakpoint condition, and stops only if the chain has views. Uncaught exceptions without views behave as before, and
your breakpoint settings are not touched. Switch it off with **Run → Moldable: Stop at Uncaught Moldable Exceptions**.

You can also open the views by hand: right-click any exception in the Variables view → **Show Moldable Views**.
**Run → Moldable: Open Views at Exception Breakpoints** controls whether the dialog opens automatically at a stop.

## Why this is simpler than in Visual Studio

In Visual Studio, the "Exception Unhandled" stop comes after .NET has declared the exception unhandled, and
Visual Studio refuses to create custom visualizers there. You either had to break at every throw or run twice.

The JVM decides "uncaught" **at the throw**: before unwinding, JVMTI looks for a handler and reports the catch
location (none means uncaught). An exception breakpoint set to "Uncaught" therefore stops at the throw site, with
the program alive, and the helper can run there. One run is enough, and caught exceptions don't stop the program.

| Visual Studio version | IntelliJ version |
| --- | --- |
| object source DLL, loaded by the debugger | `MoldableSnapshot`, defined by the plugin in a fresh class loader inside the debugged JVM |
| `DebuggerVisualizerProvider` + Remote UI | `MoldableViewsDialog` (Swing, IntelliJ diff viewer) |
| magnifier on `$exception` | **Show Moldable Views** in the Variables context menu |
| `BreakAtThrowPackage` (two runs) | own "uncaught" request: stops only at moldable exceptions, no breakpoint needed (one run) |

## Design notes

- **No dependency needed in the app.** The plugin creates a `SecureClassLoader` in the debugged JVM (child of the
  application's class loader), passes it the bytes of `MoldableSnapshot.class` from the plugin jar, and calls
  `toJson`. This happens once per debug session. The helper is compiled for Java 8 and is a single class (no
  lambdas or nested classes), so it loads into any JVM from its bytes alone.
  IntelliJ's own helper mechanism (`DebuggerUtilsImpl.invokeHelperMethod`) is not used: in 2025.3 it only reads
  helper classes from `idea_rt.jar`.
- **Duck typing.** Views are found by an annotation named `DebugView` (`value()` or `title()`, `priority()`).
  View results are read through `kind()`, `text()`, `expected()`, `actual()`, `columns()`, `rows()`. A view
  may also return a `String`, and anything else is shown with `toString()`.
- **Safe.** A view that throws becomes a red error tab; a broken `getMessage()` or `getCause()`, cycles and very
  large values are handled (32 exceptions, depth 12, 100k characters per text, 1,000 table rows).
- **Built-in views** for every exception: Details, Stack trace, and Fields (the exception class's own fields).

## Status: what was tested

- `moldable-views`, `moldable-snapshot-rt` and the demo were compiled (Java 8 bytecode) and run here. The snapshot
  of the demo was checked: chain, cause and suppressed exception, the three views, built-in views. So were the
  guards: a view that throws, a view that returns null, a broken `getMessage()`, a cycle, and a project's own
  `@DebugView` annotation.
- **The plugin itself has not been compiled**: the IntelliJ Platform artifacts could not be downloaded where it
  was written. Every IntelliJ API it uses was checked against the `intellij-community` sources of the 253
  branch (`ClassLoadingUtils.getClassLoader`, `DebugProcess.invokeMethod`, `SuspendContextCommandImpl`, `EvaluationContextImpl`, `XDebuggerTreeActionBase`,
  `DebugProcessListener.paused`, `JavaExceptionBreakpointProperties`, `DiffManager.createRequestPanel`, …).
  Expect small fixes; send the compiler errors.

## Limits and next steps

- **Frameworks catch exceptions.** In Spring, JUnit or executor threads, an exception that escapes your code is
  caught by the framework, so it is not "uncaught" and the plugin does not stop. Use a caught-exception breakpoint for
  the moldable types there; the views still open automatically at that stop.
- **Java modules.** In named modules, private `@DebugView` methods may not be accessible by reflection; they show up
  as error tabs. Make them public or open the package.
- **Remote development (split mode)** is not handled: the action needs the backend `JavaValue`.
- **Kotlin** works too: `@DebugView` on a Kotlin method of an exception class is found the same way.
- Views for library exceptions are found in your project's sources only, not in libraries: a library cannot ship
  views for its own exceptions yet.
- Next: **actions** next to views (for example "accept actual as expected", or re-run a request).
