package ch.moldexj.data;

import ch.moldexj.data.frames.StackFramesProvider;
import ch.moldexj.data.psi.PsiQueryProvider;
import ch.moldexj.debuggee.DataRequest;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** The known data providers, and how a request from the debugged program is resolved with them. */
@Service(Service.Level.APP)
public final class DebuggerDataProviders {

    private static final Logger LOG = Logger.getInstance(DebuggerDataProviders.class);

    private final List<DebuggerDataProvider<?>> providers = List.of(new StackFramesProvider(), new PsiQueryProvider());

    public static DebuggerDataProviders getInstance() {
        return ApplicationManager.getApplication().getService(DebuggerDataProviders.class);
    }

    /** One value per request, in the same order. Never throws: what cannot be resolved becomes unavailable. */
    public @NotNull List<ProvidedValue> resolveAll(@NotNull List<DataRequest> requests, @NotNull ProviderContext context) {
        Map<Parsed<?>, ProvidedValue> resolved = new HashMap<>();
        List<ProvidedValue> values = new ArrayList<>(requests.size());
        for (DataRequest request : requests) {
            ProvidedValue value;
            try {
                Parsed<?> parsed = parse(request.annotations());
                value = parsed == null
                        ? ProvidedValue.unavailable("No provider for " + request.annotations().stream()
                                .map(a -> "@" + a.simpleName()).collect(Collectors.joining(", ")) + ".")
                        : resolved.computeIfAbsent(parsed, p -> p.provide(context));
            } catch (IllegalArgumentException e) {
                value = ProvidedValue.unavailable(e.getMessage());
            }
            values.add(value);
        }
        return values;
    }

    /**
     * The first annotation a provider accepts decides, so unrelated annotations such as {@code @NotNull} are skipped.
     * Null if no provider accepts any.
     */
    private Parsed<?> parse(List<AnnotationData> annotations) {
        for (AnnotationData annotation : annotations) {
            for (DebuggerDataProvider<?> provider : providers) {
                Parsed<?> parsed = Parsed.of(provider, annotation);
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return null;
    }

    /** A provider with the arguments it read; equal ones are resolved once. */
    private record Parsed<A>(DebuggerDataProvider<A> provider, A arguments) {

        static <A> Parsed<A> of(DebuggerDataProvider<A> provider, AnnotationData annotation) {
            A arguments = provider.parse(annotation);
            return arguments == null ? null : new Parsed<>(provider, arguments);
        }

        ProvidedValue provide(ProviderContext context) {
            try {
                return provider.provide(context, arguments);
            } catch (RuntimeException e) {
                LOG.warn("Moldable Exceptions: " + arguments + " failed", e);
                return ProvidedValue.unavailable("The debugger could not provide " + arguments + ": " + e);
            }
        }
    }
}
