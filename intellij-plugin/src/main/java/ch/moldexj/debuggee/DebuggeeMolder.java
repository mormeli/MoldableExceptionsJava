package ch.moldexj.debuggee;

import ch.moldexj.ExceptionSnapshot;
import ch.moldexj.data.AnnotationData;
import ch.moldexj.data.DebuggerDataProviders;
import ch.moldexj.data.ProvidedValue;
import ch.moldexj.data.ProviderContext;
import ch.moldexj.runtime.ExceptionMolder;
import ch.moldexj.runtime.MoldPlan;
import ch.moldexj.runtime.view.ViewManifest;
import com.google.gson.Gson;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.ClassType;
import com.sun.jdi.Field;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.StringReference;
import com.sun.jdi.Value;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.ExceptionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Talks to {@link ExceptionMolder} in the paused JVM, in two calls:
 *
 * <ol>
 *   <li>{@link #plan}: the debugged program finds the views of the exception chain and says which debugger data they
 *       need. Enough to decide whether the exception is moldable.</li>
 *   <li>{@link #mold}: the plugin resolves that data with the {@link DebuggerDataProviders} and sends it back, the
 *       program runs the views and returns the snapshot.</li>
 * </ol>
 *
 * Everything here talks to the VM: call it on the debugger manager thread, inside a suspend context.
 */
public final class DebuggeeMolder {

    private static final Gson GSON = new Gson();
    private static final String PLAN = "L" + MoldPlan.class.getName().replace('.', '/') + ";";
    private static final String PLAN_SIGNATURE = "(Ljava/lang/Throwable;)" + PLAN;
    private static final String MOLD_SIGNATURE = "(" + PLAN + "[B[Ljava/lang/Object;)Ljava/lang/String;";

    private DebuggeeMolder() {
    }

    public static boolean isThrowable(@NotNull ObjectReference value) {
        return DebuggerUtils.instanceOf(value.referenceType(), "java.lang.Throwable");
    }

    /** The thread whose exception event this suspend context stopped at, if it is a throw of {@code exception}. */
    public static @Nullable ThreadReferenceProxyImpl throwThread(@NotNull SuspendContextImpl suspendContext, @NotNull ObjectReference exception) {
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

    /** Calls {@code ExceptionMolder.plan(exception)}. Close the plan when done with it. */
    public static @NotNull DebuggeePlan plan(
            @NotNull EvaluationContextImpl context,
            @NotNull ObjectReference exception) throws EvaluateException {
        ClassType helper = HelperLoader.entryClass(context, exception.virtualMachine());
        Value result = context.getDebugProcess().invokeMethod(context, helper, method(helper, "plan", PLAN_SIGNATURE),
                List.of(exception));
        if (!(result instanceof ObjectReference plan)) {
            throw new EvaluateException("ExceptionMolder.plan returned " + result);
        }
        plan.disableCollection();
        try {
            // Plain field reads: no invocation, so the stack frames stay valid for the providers.
            Value moldable = plan.getValue(field(plan, "moldable"));
            Value requests = plan.getValue(field(plan, "requests"));
            return new DebuggeePlan(plan,
                    moldable instanceof BooleanValue b && b.value(),
                    requests instanceof StringReference json ? requests(json.value()) : List.of());
        } catch (RuntimeException | EvaluateException e) {
            plan.enableCollection();
            throw e;
        }
    }

    /**
     * Resolves the plan's requests, then calls {@code ExceptionMolder.mold(plan, data, handles)}.
     *
     * @param providerContext where the providers read the debugger data
     */
    public static @NotNull ExceptionSnapshot mold(
            @NotNull EvaluationContextImpl context,
            @NotNull DebuggeePlan plan,
            @NotNull ProviderContext providerContext) throws EvaluateException {
        List<ProvidedValue> values = DebuggerDataProviders.getInstance().resolveAll(plan.requests(), providerContext);
        DebuggeeMarshaller marshaller = new DebuggeeMarshaller();
        byte[] data = marshaller.write(values);

        ClassType helper = HelperLoader.entryClass(context, plan.reference().virtualMachine());
        Method mold = method(helper, "mold", MOLD_SIGNATURE);
        Value json;
        try (DebuggeeMirrors mirrors = new DebuggeeMirrors(plan.reference().virtualMachine())) {
            ArrayReference dataMirror = values.isEmpty() ? null : mirrors.bytes(data);
            ArrayReference handles = marshaller.handles().isEmpty() ? null : mirrors.objects(marshaller.handles());
            json = context.getDebugProcess().invokeMethod(context, helper, mold,
                    Arrays.asList(plan.reference(), dataMirror, handles));
        } catch (EvaluateException e) {
            throw e;
        } catch (Exception e) {
            throw new EvaluateException("Could not pass the debugger data to the debugged program: " + e, e);
        }
        if (!(json instanceof StringReference)) {
            throw new EvaluateException("ExceptionMolder.mold returned " + json);
        }
        ExceptionSnapshot snapshot = GSON.fromJson(((StringReference) json).value(), ExceptionSnapshot.class);
        return snapshot == null ? new ExceptionSnapshot() : snapshot;
    }

    /** The requests JSON of {@code RequestTable}: attribute values in the tagged form of the manifest. */
    private static List<DataRequest> requests(String json) {
        RequestJson[] parsed = GSON.fromJson(json, RequestJson[].class);
        List<DataRequest> requests = new ArrayList<>();
        for (RequestJson request : parsed == null ? new RequestJson[0] : parsed) {
            List<AnnotationData> annotations = new ArrayList<>();
            for (AnnotationJson annotation : request.annotations == null ? List.<AnnotationJson>of() : request.annotations) {
                Map<String, Object> attributes = new LinkedHashMap<>();
                if (annotation.attributes != null) {
                    annotation.attributes.forEach((name, tagged) -> {
                        Object value = tagged == null ? null : ViewManifest.decodeValue(tagged);
                        if (value != null) {
                            attributes.put(name, value);
                        }
                    });
                }
                annotations.add(new AnnotationData(annotation.name, attributes));
            }
            requests.add(new DataRequest(annotations));
        }
        return requests;
    }

    private static Method method(ClassType helper, String name, String signature) throws EvaluateException {
        Method method = DebuggerUtils.findMethod(helper, name, signature);
        if (method == null) {
            throw new EvaluateException("ExceptionMolder." + name + " not found in the loaded helper");
        }
        return method;
    }

    private static Field field(ObjectReference plan, String name) throws EvaluateException {
        Field field = plan.referenceType().fieldByName(name);
        if (field == null) {
            throw new EvaluateException("MoldPlan." + name + " not found in the loaded helper");
        }
        return field;
    }

    /** Gson shapes of the requests JSON. */
    private static final class RequestJson {
        List<AnnotationJson> annotations;
    }

    private static final class AnnotationJson {
        String name = "";
        Map<String, String> attributes;
    }
}
