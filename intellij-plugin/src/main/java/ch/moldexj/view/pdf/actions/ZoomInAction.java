package ch.moldexj.view.pdf.actions;

import ch.moldexj.view.pdf.PdfDiffPanel;

public final class ZoomInAction extends PdfDiffAction {

    @Override
    protected void perform(PdfDiffPanel panel) {
        panel.zoomIn();
    }
}
