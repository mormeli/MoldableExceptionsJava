package ch.moldexj.log.actions;

import ch.moldexj.log.MoldexLogPanel;

/** Goes to the next exception of the log; without a selection, to the first. */
public final class NextExceptionAction extends LogAction {

    @Override
    protected boolean isEnabled(MoldexLogPanel panel) {
        return panel.hasNext();
    }

    @Override
    protected void perform(MoldexLogPanel panel) {
        panel.next();
    }
}
