package ch.moldexj.view.pdf.actions;

import ch.moldexj.view.pdf.PdfDiffPanel;

/** Goes to the previous page that differs. */
public final class PreviousPageAction extends PdfDiffAction {

    @Override
    protected boolean isEnabled(PdfDiffPanel panel) {
        return panel.hasPreviousPage();
    }

    @Override
    protected void perform(PdfDiffPanel panel) {
        panel.previousPage();
    }
}
