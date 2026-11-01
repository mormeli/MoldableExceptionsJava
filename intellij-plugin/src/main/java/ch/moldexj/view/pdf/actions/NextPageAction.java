package ch.moldexj.view.pdf.actions;

import ch.moldexj.view.pdf.PdfDiffPanel;

/** Goes to the next page that differs. */
public final class NextPageAction extends PdfDiffAction {

    @Override
    protected boolean isEnabled(PdfDiffPanel panel) {
        return panel.hasNextPage();
    }

    @Override
    protected void perform(PdfDiffPanel panel) {
        panel.nextPage();
    }
}
