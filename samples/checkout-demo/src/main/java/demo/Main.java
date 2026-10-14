package demo;

import java.net.URI;

/**
 * Debug this program in IntelliJ IDEA with the Moldable Exceptions plugin installed. No breakpoint is needed.
 *
 * <p>The JVM knows at the throw whether an exception will be caught, so the debugger stops right at the
 * {@code throw new IllegalStateException(...)} in {@link #syncCheckout()}, with the program still alive. The plugin
 * sees that the exception's chain contains a moldable exception and opens its views automatically.
 */
public final class Main {

    static final String EXPECTED_ORDER = String.join("\n",
            "{",
            "  \"orderId\": \"A-1042\",",
            "  \"status\": \"confirmed\",",
            "  \"currency\": \"EUR\",",
            "  \"total\": 59.80,",
            "  \"customer\": { \"id\": 77, \"email\": \"ada@example.com\" },",
            "  \"lines\": [",
            "    { \"sku\": \"BOOK-GT\", \"qty\": 2, \"price\": 19.90 },",
            "    { \"sku\": \"MUG-FEENK\", \"qty\": 1, \"price\": 20.00 }",
            "  ]",
            "}");

    // What the (simulated) service actually returned: a renamed field, a string where a number belongs,
    // a wrong currency and a dropped order line.
    static final String ACTUAL_ORDER = String.join("\n",
            "{",
            "  \"orderId\": \"A-1042\",",
            "  \"status\": \"pending\",",
            "  \"currency\": \"USD\",",
            "  \"total\": \"59.80\",",
            "  \"customer\": { \"id\": 77, \"mail\": \"ada@example.com\" },",
            "  \"lines\": [",
            "    { \"sku\": \"BOOK-GT\", \"qty\": 2, \"price\": 19.90 }",
            "  ],",
            "  \"debug\": true",
            "}");

    public static void main(String[] args) {
        syncCheckout(); // nothing catches what comes out of here
    }

    static void syncCheckout() {
        try {
            confirmOrder();
        } catch (ApiContractMismatchException mismatch) {
            // Wrapped on purpose: the plugin still finds the moldable cause and opens it first.
            IllegalStateException failure = new IllegalStateException("Checkout sync failed for order A-1042.", mismatch);
            failure.addSuppressed(new java.util.concurrent.TimeoutException("Retry 3 of 3 timed out after 2 s."));
            throw failure;
        }
    }

    static void confirmOrder() {
        throw new ApiContractMismatchException("POST", URI.create("https://shop.example.com/api/orders/A-1042/confirm"), 200,
                EXPECTED_ORDER, ACTUAL_ORDER);
    }

    private Main() {

    }
}
