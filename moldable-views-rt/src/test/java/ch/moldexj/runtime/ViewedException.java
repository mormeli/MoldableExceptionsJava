package ch.moldexj.runtime;

import ch.moldexj.view.DebugView;

/** An exception with a view of its own; {@link TestViews} adds static views for it. */
class ViewedException extends RuntimeException {

    ViewedException(String message) {
        super(message);
    }

    @DebugView(value = "Info", priority = 10)
    private String info() {
        return "info about " + getMessage();
    }
}
