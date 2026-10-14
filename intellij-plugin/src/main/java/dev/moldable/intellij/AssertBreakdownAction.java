package dev.moldable.intellij;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * While the debugger is paused on a failed {@code assert}, shows each sub-expression's value:
 *
 * <pre>
 * assert a == 1
 *        | |
 *        3 false
 * </pre>
 */
public final class AssertBreakdownAction extends AnAction {

    /** One sub-expression: its column inside the assert line, its source text, and later its value. */
    record Part(int column, PsiExpression expr, String[] value) {}

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        DebuggerContextImpl ctx = DebuggerManagerEx.getInstanceEx(project).getContext();
        SuspendContextImpl suspend = ctx.getSuspendContext();
        StackFrameProxyImpl frame = ctx.getFrameProxy();
        SourcePosition position = ctx.getSourcePosition();
        if (suspend == null || frame == null || position == null || ctx.getDebugProcess() == null) return;

        // Evaluation must run on the debugger's own thread, inside a command for the paused context.
        ctx.getDebugProcess().getManagerThread().schedule(new SuspendContextCommandImpl(suspend) {
            @Override
            public void contextAction(@NotNull SuspendContextImpl suspendContext) {
                String diagram = breakDown(project, suspendContext, frame, position);
                ApplicationManager.getApplication().invokeLater(() -> show(project, diagram));
            }
        });
    }

    private static String breakDown(Project project, SuspendContextImpl suspendContext,
                                    StackFrameProxyImpl frame, SourcePosition position) {
        // 1. Find the assert statement on the line where the debugger stopped (PSI needs a read action).
        PsiAssertStatement assertStmt = ReadAction.compute(() ->
                PsiTreeUtil.getParentOfType(position.getElementAt(), PsiAssertStatement.class, false));
        if (assertStmt == null) return "The debugger is not stopped on an assert statement.";

        // 2. Collect its sub-expressions with the column each value should appear under.
        List<Part> parts = new ArrayList<>();
        String source = ReadAction.compute(() -> {
            int base = assertStmt.getTextRange().getStartOffset();
            collect(assertStmt.getAssertCondition(), base, parts);
            return assertStmt.getText().replaceFirst("\\s*;\\s*$", "");
        });

        // 3. Evaluate each sub-expression in the paused frame, like the Evaluate Expression dialog does.
        EvaluationContextImpl evalContext = new EvaluationContextImpl(suspendContext, frame);
        for (Part part : parts) {
            try {
                ExpressionEvaluator evaluator = ReadAction.compute(() -> EvaluatorBuilderImpl.build(
                        new TextWithImportsImpl(part.expr()), part.expr(), position, project));
                part.value()[0] = DebuggerUtils.getValueAsString(evalContext, evaluator.evaluate(evalContext));
            } catch (Exception ex) {
                part.value()[0] = "<" + ex.getMessage() + ">";
            }
        }

        // 4. Draw the power-assert diagram.
        return render(source, parts);
    }

    /** Walks the condition; skips literals (their value is obvious) and anything that would change state. */
    private static void collect(PsiExpression expr, int base, List<Part> out) {
        if (expr == null || expr instanceof PsiLiteralExpression) return;
        if (expr instanceof PsiParenthesizedExpression p) { collect(p.getExpression(), base, out); return; }
        if (expr instanceof PsiAssignmentExpression) return;
        if (expr instanceof PsiUnaryExpression u
                && (u.getOperationTokenType() == JavaTokenType.PLUSPLUS
                || u.getOperationTokenType() == JavaTokenType.MINUSMINUS)) return;

        // Anchor: the operator for a binary expression, the method name for a call, otherwise the start.
        PsiElement anchor = expr;
        if (expr instanceof PsiBinaryExpression b) anchor = b.getOperationSign();
        else if (expr instanceof PsiMethodCallExpression c && c.getMethodExpression().getReferenceNameElement() != null)
            anchor = c.getMethodExpression().getReferenceNameElement();
        out.add(new Part(anchor.getTextRange().getStartOffset() - base, expr, new String[1]));

        for (PsiElement child : expr.getChildren()) {
            if (child instanceof PsiExpression sub) collect(sub, base, out);
            else if (child instanceof PsiExpressionList args)
                for (PsiExpression arg : args.getExpressions()) collect(arg, base, out);
        }
    }

    /** Source line, a row of pipes, then one row per value, rightmost value first. */
    static String render(String source, List<Part> parts) {
        List<Part> sorted = new ArrayList<>(parts);
        sorted.sort(Comparator.comparingInt(Part::column));
        StringBuilder out = new StringBuilder(source).append('\n');
        out.append(pipes(sorted, sorted.size())).append('\n');
        for (int i = sorted.size() - 1; i >= 0; i--) {
            StringBuilder row = pipes(sorted, i);
            while (row.length() < sorted.get(i).column()) row.append(' ');
            row.setLength(sorted.get(i).column());
            out.append(row).append(sorted.get(i).value()[0]).append('\n');
        }
        return out.toString();
    }

    private static StringBuilder pipes(List<Part> sorted, int count) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < count; i++) {
            int col = sorted.get(i).column();
            while (row.length() < col) row.append(' ');
            if (row.length() == col) row.append('|');
        }
        return row;
    }

    private static void show(Project project, String text) {
        JTextArea area = new JTextArea(text);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setEditable(false);
        new DialogBuilder(project).title("Assert Breakdown").centerPanel(new JBScrollPane(area)).show();
    }
}