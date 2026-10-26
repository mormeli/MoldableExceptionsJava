package ch.moldexj.demos.regex;

/**
 * A PatternSyntaxException that nothing catches: replaceAll() takes a regex, and '{' starts a repetition.
 * Just debug it (no breakpoint needed): the debugger stops inside java.util.regex where the exception is thrown,
 * and the plugin opens the views from {@link PatternSyntaxExceptionViews}.
 */
public final class PatternSyntaxExceptionDemo {

    public static void main(String[] args) {
        String template = "Hello {name}, your order {orderId} has shipped.";
        var q = template.split("[b-chm-pP]{5at|ot");
        String mail = template.replaceAll("{name}", "Ada").replaceAll("{orderId}", "A-1042");
        System.out.println(mail);
    }

    private PatternSyntaxExceptionDemo() {
    }
}
