# checkout-demo

This is a small program to test and demonstrate the capabilities of the moldable exceptions plugin.
Every package under `ch.moldexj.demos` is one demo: a class with a `main` that throws, and a class containing views
for the throw exception.

| Package | Run | Exception | Views in use | What it shows |
| --- | --- | --- | --- | --- |
| `domain` | `ApiContractMismatchExceptionDemo` | `ApiContractMismatchException` (custom), wrapped in an `IllegalStateException` | Diff, Mismatches, Request | Views as instance methods of our own exception; the exception chain |
| `date` | `DateTimeParseExceptionDemo` | `DateTimeParseException` (JDK) | Parse position, Other formats | Views for an exception we don't own: static methods with `@Exception` |
| `regex` | `PatternSyntaxExceptionDemo` | `PatternSyntaxException` (JDK) | Pattern, Parser, Fix | `@StackFrames(fields = true)`: the regex parser's own frames and private fields |
| `constraints` | `ConstraintViolationExceptionDemo` | `ConstraintViolationException` (Jakarta Validation) | Violations, Throw site | `@StackFrames`: the validated object, live from the locals of the throw |
| `mockito` | `MockitoDemo` | `InvalidUseOfMatchersException` (Mockito) | Matchers, Calls | Live library objects from a frame inside Mockito |

## Running a demo
To work with the demos, follow these steps:
1. Start the sandbox IDE with the plugin: Gradle task **intellij-plugin › intellijPlatform › runIde** (see the
   [main README](../../README.md#try-it)).
2. In that IDE, open this repository and **Debug** the `main` method of a demo class (gutter icon next to `main`).
3. When the exception is uncaught, no breakpoint is needed: the debugger stops at the throw and the views open.

If a demo catches its exception (the `date`, `regex`, `ee` and `mockito` demos currently do, to print the message), the
"uncaught" stop does not fire. Add a Java Exception Breakpoint for the exception class with **Caught exception** on;
the views open at that stop too. You can also open them by hand: right-click the exception in the Variables view →
**Show Moldable Views**.

Views that use `@StackFrames` need the debugger to be stopped at the throw of that exception. When the views are opened
by hand somewhere else, these tabs say why they are not available; the views without parameters still work.

## The demos

### `domain`: an exception of our own

`ApiContractMismatchExceptionDemo` simulates an order API whose response breaks its contract: a renamed field, a
string where a number belongs, a wrong currency, a dropped order line and an extra field. `confirmOrder()` throws an
`ApiContractMismatchException`; `syncCheckout()` wraps it in an `IllegalStateException` with a suppressed
`TimeoutException` and rethrows, and nothing catches that.

The views are private `@DebugView` methods of `ApiContractMismatchException` itself, so they read its fields directly:

- **Diff**: expected and actual JSON, keys sorted so that the lines match, in IntelliJ's diff viewer.
- **Mismatches**: one row per JSON path that differs, with the problem, the expected and the actual value.
- **Request**: method, endpoint, status, and a `curl` command to reproduce the call.

The plugin finds the moldable exception inside the chain (cause of the `IllegalStateException`) and opens it first.
`Json` and `JsonMismatch` are just enough JSON for the demo, without dependencies.

### `date`: a JDK exception

`LocalDate.parse("2026-10-8")` fails because the day is not zero-padded. `DateTimeParseException` is a JDK class, so
its views can't be methods of the exception. `DateTimeParseExceptionViews` has static methods that take it as a
parameter marked `@Exception`:

- **Parse position**: the input highlighted, green what was parsed, red where the parser stopped, grey the rest.
- **Other formats**: which common date formats accept the input and what they produce, matches first.

### `regex`: reading the library's frames

`template.split("[b-chm-pP]{5at|ot")` fails with "Unclosed counted closure": `{` starts a repetition `{n,m}`. The views
in `PatternSyntaxExceptionViews` don't parse the pattern again. They read what `java.util.regex.Pattern`'s parser was
doing, from its stack frames and its private fields (`@StackFrames(depth = 64, fields = true)`):

- **Pattern**: the pattern with the error position highlighted. Needs no stack.
- **Parser**: the constructs the parser was inside when it failed (alternation, sequence, repetition, …), outermost
  first, with their locals, plus the parser's state: characters read, groups opened, flags.
- **Fix**: the line of our code that passed the regex, the API it called, and a literal replacement if the text was
  not meant as a regex, here `template.split(Pattern.quote(...))`.

### `ee`: data the exception doesn't have

`ConstraintViolationExceptionDemo` validates a `User` with a name that is too short and an age under 18, using
Hibernate Validator, and throws a `ConstraintViolationException`.

- **Violations**: one row per violation, with the field, the constraint (for example `2 <= size(name) <= 2147483647`) and the
  invalid value.
- **Throw site**: the locals of the top 3 frames at the throw (`@StackFrames(depth = 3)`). The exception only knows
  the violations; the debugger also knows which `User` was validated, and that row is marked.

### `mockito`: a library's internal objects

`verify(socket).setPerformancePreferences(1234, eq(10), 5)` mixes raw values and an argument matcher, so Mockito
throws an `InvalidUseOfMatchersException`. At the throw, the frame of `MatchersBinder.validateMatchers` holds the
verification and its matchers as live objects. `InvalidUseOfMatchersExceptionView` uses them:

- **Matchers**: the verification rebuilt with its matcher in place, the closest real call, and the fix
  (`eq(1234), eq(10), eq(5)`).
- **Calls**: every call of that method on the mock, the best fit marked green.

Mockito replaces each matcher with a placeholder (`0`, `null`, `""`, …) in the recorded call, so the view tries every
possible position of the matchers and keeps the one that fits the real calls best. **Matchers** also takes a
`@PsiQuery` parameter, an experimental data provider of the plugin that currently returns the first source line of the
project on the stack.

## Build

```kotlin
dependencies {
    implementation(project(":moldable-views"))
    annotationProcessor(project(":moldable-views-processor"))
    // the libraries the demos throw from
    implementation("org.hibernate.validator:hibernate-validator:9.1.5.Final")
    implementation("org.glassfish:jakarta.el:4.0.2")
    implementation("org.mockito:mockito-core:5.+")
}
```

The annotation processor is required: it writes the view manifest (`META-INF/moldexj/views`) that the debugged program
reads to find the static views and their parameters. Without it, views with parameters say so instead of running.
The demos are compiled with `--release 21`.

## Adding a demo

1. Create a package under `ch.moldexj.demos` with a class whose `main` throws the exception.
2. For an exception you own, add `@DebugView` methods to it. For any other exception, add a class with static
   `@DebugView` methods that take it as an `@Exception` parameter; it only has to be in this module's main sources.
3. Rebuild, so the processor updates the manifest, and debug the `main`.
