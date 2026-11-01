package ch.moldexj.view.pdf.actions;

import ch.moldexj.view.pdf.PdfDiffPanel;

public final class FitWidthAction extends PdfDiffAction {

    @Override
    protected void perform(PdfDiffPanel panel) {
        panel.fitWidth();
    }
}
