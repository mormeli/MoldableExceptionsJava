package ch.moldexj.view.pdf.actions;

import ch.moldexj.view.pdf.PdfDiffPanel;

/** Zooms to 100%, the size the page has when printed. */
public final class ActualSizeAction extends PdfDiffAction {

    @Override
    protected void perform(PdfDiffPanel panel) {
        panel.actualSize();
    }
}
