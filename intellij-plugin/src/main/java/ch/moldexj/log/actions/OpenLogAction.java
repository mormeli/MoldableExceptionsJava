package ch.moldexj.log.actions;

import ch.moldexj.log.MoldexLogPanel;

/** Asks for a log file and lists its exceptions. */
public final class OpenLogAction extends LogAction {

    @Override
    protected boolean isEnabled(MoldexLogPanel panel) {
        return true;
    }

    @Override
    protected void perform(MoldexLogPanel panel) {
        panel.chooseFile();
    }
}
