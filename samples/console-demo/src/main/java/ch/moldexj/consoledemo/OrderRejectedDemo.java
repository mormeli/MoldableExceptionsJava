package ch.moldexj.consoledemo;

import ch.moldexj.consoledemo.OrderRejectedException.Line;

import java.util.List;

/**
 * A program with the runtime (moldable-views-rt) on its classpath. Debug it: the plugin uses the program's runtime
 * instead of injecting its own.
 */
public class OrderRejectedDemo {
    public static void main(String[] args) {
        throw new OrderRejectedException(List.of(
                new Line("keyboard", 3, 1),
                new Line("monitor", 1, 0),
                new Line("mouse", 2, 5)));
    }
}
