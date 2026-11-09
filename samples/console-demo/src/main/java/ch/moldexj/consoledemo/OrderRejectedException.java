package ch.moldexj.consoledemo;

import ch.moldexj.view.DebugView;
import ch.moldexj.view.DebugViews;

import java.util.List;

/** An order that cannot be fulfilled; the exception carries everything its view shows. */
public class OrderRejectedException extends RuntimeException {

    /** One ordered item and what the warehouse has of it. */
    public record Line(String item, int ordered, int inStock) {

        int missing() {
            return Math.max(0, ordered - inStock);
        }
    }

    private final List<Line> lines;

    public OrderRejectedException(List<Line> lines) {
        super("Order rejected: " + lines.stream().filter(line -> line.missing() > 0).count() + " items are out of stock");
        this.lines = List.copyOf(lines);
    }

    @DebugView(value = "Stock", priority = 10)
    private DebugViews.View stock() {
        return DebugViews.table(lines,
                DebugViews.column("Item", Line::item),
                DebugViews.column("Ordered", line -> String.valueOf(line.ordered())),
                DebugViews.column("In stock", line -> String.valueOf(line.inStock())),
                DebugViews.column("Missing", line -> String.valueOf(line.missing())));
    }
}
