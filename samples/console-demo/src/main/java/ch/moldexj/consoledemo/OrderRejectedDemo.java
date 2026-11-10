package ch.moldexj.consoledemo;

import ch.moldexj.consoledemo.OrderRejectedException.Line;
import ch.moldexj.runtime.ExceptionPrinter;

import java.util.List;

/**
 * A program with the runtime (moldable-views-rt) on its classpath.
 *
 * <p>Run it without a debugger: the views of both exceptions are printed to the console as {@code [moldex] {...}}
 * lines. Debug it: the plugin stops at the uncaught exception and uses the program's runtime instead of injecting its
 * own.
 */
public class OrderRejectedDemo {
    public static void main(String[] args) {
        // Uncaught exceptions that have views are printed from now on.
        ExceptionPrinter.install();

        // A caught exception is printed by hand.
        try {
            throw new OrderRejectedException(List.of(new Line("headset", 1, 0)));
        } catch (OrderRejectedException e) {
            ExceptionPrinter.print(e);
        }

        throw new OrderRejectedException(List.of(
                new Line("keyboard", 3, 1),
                new Line("monitor", 1, 0),
                new Line("mouse", 2, 5)));
    }
}
