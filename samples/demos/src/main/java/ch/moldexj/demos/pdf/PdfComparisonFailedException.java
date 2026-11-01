package ch.moldexj.demos.pdf;

import ch.moldexj.view.DebugView;
import ch.moldexj.view.DebugViews;

import java.nio.file.Path;

public class PdfComparisonFailedException extends RuntimeException {

    private final Path expected;
    private final Path actual;


    public PdfComparisonFailedException(Path expected, Path actual) {
        this.expected = expected;
        this.actual = actual;
    }


    public Path getExpected() {
        return expected;
    }

    public Path getActual() {
        return actual;
    }

    @DebugView(value = "Diff", priority = 10)
    public DebugViews.View pdfDiffView()
    {
        return DebugViews.pdfDiff(getExpected().toString(), getActual().toString());
    }


}
