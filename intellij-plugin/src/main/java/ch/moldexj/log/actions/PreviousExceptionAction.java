package ch.moldexj.log.actions;

import ch.moldexj.log.MoldexLogPanel;

/** Goes to the previous exception of the log. */
public final class PreviousExceptionAction extends LogAction {

    @Override
    protected boolean isEnabled(MoldexLogPanel panel) {
        return panel.hasPrevious();
    }

    @Override
    protected void perform(MoldexLogPanel panel) {
        panel.previous();
    }
}
