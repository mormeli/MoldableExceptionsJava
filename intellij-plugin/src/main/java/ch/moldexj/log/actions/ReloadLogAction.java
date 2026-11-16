package ch.moldexj.log.actions;

import ch.moldexj.log.MoldexLogPanel;

/** Reads the opened log file again, for a log that is still written to. */
public final class ReloadLogAction extends LogAction {

    @Override
    protected boolean isEnabled(MoldexLogPanel panel) {
        return panel.hasFile();
    }

    @Override
    protected void perform(MoldexLogPanel panel) {
        panel.reload();
    }
}
