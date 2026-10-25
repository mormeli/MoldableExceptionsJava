package ch.moldexj;

import ch.moldexj.runtime.MoldableSnapshot;
import ch.moldexj.runtime.manifest.ViewManifest;
import ch.moldexj.view.Parameter;
import ch.moldexj.view.View;
import ch.moldexj.view.finder.ViewIndex;
import ch.moldexj.view.request.DataRequestArguments;
import ch.moldexj.view.request.DataRequestContext;
import ch.moldexj.view.request.DataRequestProcessor;
import ch.moldexj.view.request.ParameterSource;
import ch.moldexj.view.request.ProvidedValue;
import ch.moldexj.view.request.impl.DataRequestContextImpl;
import com.google.gson.Gson;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ClassType;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.StringReference;
import com.sun.jdi.Value;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.ExceptionEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Takes a snapshot of an exception in the paused JVM: resolves the debugger data the views ask for, loads the helper
 * ({@link HelperLoader}) and calls {@link MoldableSnapshot#toJson(Throwable, String, byte[], Object[])} there.
 */
public final class SnapshotLoader {

    private static final Logger LOG = Logger.getInstance(SnapshotLoader.class);
    private static final Gson GSON = new Gson();
    private static final String TO_JSON_SIGNATURE =
            "(Ljava/lang/Throwable;Ljava/lang/String;[B[Ljava/lang/Object;)Ljava/lang/String;";
    private static final String ONLY_AT_ROOT =
            "This view needs data from the debugger, which is only available for the exception the debugger stopped at.";

    private SnapshotLoader() {
    }

    /** Talks to the VM: call on the debugger manager thread only. */
    public static boolean isThrowable(ObjectReference value) {
        return DebuggerUtils.instanceOf(value.referenceType(), "java.lang.Throwable");
    }

    /**
     * Must run on the debugger manager thread, inside a suspend context (a SuspendContextCommandImpl).
     *
     * <ol>
     *   <li>Resolve the debugger data the views ask for (e.g. {@code @StackFrames}), once per distinct request and only
     *       for views that apply to the exception. This comes first: every method invocation in the debugged JVM,
     *       defining the helper included, makes the stack frames of the thread invalid.</li>
     *   <li>Send the views (how to fill each parameter), the data as one byte[] and the live objects as one
     *       {@code Object[]} to the helper, which runs the views.</li>
     * </ol>
     *
     * @param views       views with parameters, from {@link ViewIndex}
     * @param throwThread the thread that threw the exception if the debugger is stopped at that throw, else null
     */
    public static ExceptionSnapshot load(EvaluationContextImpl context, ObjectReference exception, List<View> views,
                                         @Nullable ThreadReferenceProxyImpl throwThread) throws EvaluateException {
        List<ProvidedValue> values = new ArrayList<>();
        String spec = spec(views, exception, new DataRequestContextImpl(context, exception, throwThread), values);
        DebuggeeMarshaller marshaller = new DebuggeeMarshaller();
        byte[] injected = marshaller.write(values);

        ClassType helper = HelperLoader.entryClass(context, exception.virtualMachine());
        Method toJson = DebuggerUtils.findMethod(helper, "toJson", TO_JSON_SIGNATURE);
        if (toJson == null) {
            throw new EvaluateException("MoldableSnapshot.toJson not found in the loaded helper");
        }
        Value json;
        try (DebuggeeMirrors mirrors = new DebuggeeMirrors(exception.virtualMachine())) {
            StringReference specMirror = mirrors.string(spec);
            ArrayReference injectedMirror = values.isEmpty() ? null : mirrors.bytes(injected);
            ArrayReference handles = marshaller.handles().isEmpty() ? null : mirrors.objects(marshaller.handles());
            json = context.getDebugProcess().invokeMethod(context, helper, toJson,
                    Arrays.asList(exception, specMirror, injectedMirror, handles));
        } catch (EvaluateException e) {
            throw e;
        } catch (Exception e) {
            throw new EvaluateException("Could not pass the views to the debugged program: " + e, e);
        }
        if (!(json instanceof StringReference)) {
            throw new EvaluateException("The snapshot helper returned " + json);
        }
        ExceptionSnapshot snapshot = GSON.fromJson(((StringReference) json).value(), ExceptionSnapshot.class);
        return snapshot == null ? new ExceptionSnapshot() : snapshot;
    }

    /**
     * The view spec the helper reads ({@code ViewSpec} in moldable-snapshot-rt): per view a {@code view} line and one
     * {@code bind} line per parameter. Resolves the debugger data on the way and adds it to {@code values}.
     */
    private static String spec(List<View> views, ObjectReference exception, DataRequestContext context, List<ProvidedValue> values) {
        Map<DataRequestArguments, Integer> resolved = new HashMap<>();
        StringBuilder spec = new StringBuilder();
        for (View view : views) {
            boolean forRoot = view.needsDebuggerData() && appliesTo(view, exception);
            spec.append("view ").append(ViewManifest.encode(view.className())).append(' ')
                    .append(ViewManifest.encode(view.methodName())).append(view.isStatic() ? " static\n" : " instance\n");
            for (Parameter parameter : view.parameters()) {
                spec.append("bind ").append(parameter.position()).append(' ');
                switch (parameter.source()) {
                    case ParameterSource.ThrownException ignored -> spec.append("exception");
                    case ParameterSource.Unsupported unsupported -> spec.append("missing ").append(ViewManifest.encode(unsupported.reason()));
                    case ParameterSource.Provided provided when !forRoot -> spec.append("missing ").append(ViewManifest.encode(ONLY_AT_ROOT));
                    case ParameterSource.Provided provided -> spec.append("value ").append(resolved.computeIfAbsent(
                            provided.arguments(), arguments -> {
                                values.add(resolve(provided, context));
                                return values.size() - 1;
                            }));
                }
                spec.append('\n');
            }
        }
        return spec.toString();
    }

    /** The thread whose exception event this suspend context stopped at, if it is a throw of {@code exception}. */
    public static @Nullable ThreadReferenceProxyImpl throwThread(SuspendContextImpl suspendContext, ObjectReference exception) {
        EventSet events = suspendContext.getEventSet();
        if (events == null) {
            return null;
        }
        for (Event event : events) {
            if (event instanceof ExceptionEvent exceptionEvent && exception.equals(exceptionEvent.exception())) {
                return suspendContext.getVirtualMachineProxy().getThreadReferenceProxy(exceptionEvent.thread());
            }
        }
        return null;
    }

    /** Debugger data belongs to the root exception, so only views shown for the root need it (a cheap JDI check). */
    private static boolean appliesTo(View view, ObjectReference exception) {
        String type = view.exceptionType();
        return type != null && DebuggerUtils.instanceOf(exception.referenceType(), type);
    }

    @SuppressWarnings("unchecked")
    private static ProvidedValue resolve(ParameterSource.Provided provided, DataRequestContext context) {
        try {
            return ((DataRequestProcessor<DataRequestArguments>) provided.processor()).process(provided.arguments(), context);
        } catch (RuntimeException e) {
            LOG.warn("Moldable Exceptions: " + provided.arguments() + " failed", e);
            return ProvidedValue.unavailable("The debugger could not provide " + provided.arguments() + ": " + e);
        }
    }
}
