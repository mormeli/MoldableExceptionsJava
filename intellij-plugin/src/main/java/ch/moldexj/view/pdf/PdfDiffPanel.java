package ch.moldexj.view.pdf;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.ClassLoaderUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import de.redsix.pdfcompare.CompareResultImpl;
import de.redsix.pdfcompare.ImageWithDimension;
import de.redsix.pdfcompare.PageDiffCalculator;
import de.redsix.pdfcompare.PdfComparator;
import de.redsix.pdfcompare.env.SimpleEnvironment;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;

/**
 * Embeddable Swing component that shows the visual diff of two PDFs made by pdfcompare.
 * Paging through the pages that differ, zoom (buttons or Ctrl + mouse wheel), drag-to-pan,
 * and switching between the diff, the expected and the actual page.
 *
 * <p>The toolbar is the action group {@code Moldable.PdfDiff.Toolbar} from plugin.xml; its actions
 * ({@code ch.moldexj.view.pdf.actions}) find this panel through {@link #KEY}.
 */
public class PdfDiffPanel extends JBPanel<PdfDiffPanel> implements UiDataProvider {

    public static final DataKey<PdfDiffPanel> KEY = DataKey.create("Moldable.PdfDiffPanel");

    private static final Logger LOG = Logger.getInstance(PdfDiffPanel.class);

    public enum View {
        DIFF("Diff"),
        EXPECTED("Expected"),
        ACTUAL("Actual");

        private final String label;

        View(String label) {
            this.label = label;
        }

        @Override public String toString() {
            return label;
        }
    }

    /**
     * Resolution the pages are rendered at. pdfcompare's default of 300 DPI needs about 35 MB per page image,
     * which runs a longer document out of memory; 150 DPI is still sharp at twice the 100% zoom.
     */
    private static final int DPI = 150;

    private final ImageCanvas canvas = new ImageCanvas();
    private final JBScrollPane scroll = new JBScrollPane(canvas);
    private final JBLabel pageLabel = new JBLabel("–");
    private final JBLabel status = new JBLabel(" ");
    private final ComboBox<View> viewBox = new ComboBox<>(View.values());
    private final ActionToolbar toolbar;

    private Path expected;
    private Path actual;
    private DiffPages result;
    private final List<Integer> pages = new ArrayList<>();   // 0-based indices of the pages that differ
    private int current = 0;
    private int pageRequest = 0;                             // only the newest page render may show its image
    private double zoom = 72.0 / DPI;

    public PdfDiffPanel() {
        super(new BorderLayout());

        ActionManager actions = ActionManager.getInstance();
        toolbar = actions.createActionToolbar("MoldablePdfDiff", (ActionGroup) actions.getAction("Moldable.PdfDiff.Toolbar"), true);
        toolbar.setTargetComponent(this);
        viewBox.addActionListener(e -> showPage(current));

        JPanel controls = new JBPanel<>(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
        controls.add(toolbar.getComponent());
        controls.add(pageLabel);
        controls.add(viewBox);
        JPanel header = new JBPanel<>(new BorderLayout());
        header.setBorder(JBUI.Borders.customLineBottom(JBColor.border()));
        header.add(controls, BorderLayout.WEST);
        status.setBorder(JBUI.Borders.emptyRight(8));
        header.add(status, BorderLayout.EAST);

        scroll.getViewport().setBackground(new JBColor(new Color(0x9A9A9A), new Color(0x9A9A9A)));
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.getHorizontalScrollBar().setUnitIncrement(24);
        installMouseHandling();

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        updateControls();
    }

    // ---------------------------------------------------------------- public API

    /** Compares two files in the background and shows the pages that differ. */
    public void compare(Path expected, Path actual) {
        this.expected = expected;
        this.actual = actual;
        setBusy(true);
        new SwingWorker<DiffPages, Void>() {
            @Override protected DiffPages doInBackground() throws Exception {
                // pdfcompare loads its reference.conf through the context class loader,
                // which in the IDE is the platform's and cannot see the plugin's jars.
                return ClassLoaderUtil.computeWithClassLoader(PdfDiffPanel.class.getClassLoader(), () ->
                        new PdfComparator<>(expected, actual, new DiffPages())
                                .withEnvironment(new SimpleEnvironment().setDPI(DPI).setAddEqualPagesToResult(false))
                                .compare());
            }
            @Override protected void done() {
                setBusy(false);
                try {
                    setResult(get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    LOG.warn("Comparing " + expected + " with " + actual + " failed", ex.getCause());
                    status.setText("Error: " + ex.getCause());
                }
            }
        }.execute();
    }

    public void setView(View view) { viewBox.setSelectedItem(view); }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
        sink.set(KEY, this);
    }

    // ---------------------------------------------------------------- toolbar actions

    public boolean hasPage() { return !pages.isEmpty(); }

    public boolean hasPreviousPage() { return current > 0; }

    public boolean hasNextPage() { return current < pages.size() - 1; }

    public void previousPage() { showPage(current - 1); }

    public void nextPage() { showPage(current + 1); }

    public void zoomIn() { setZoom(zoom * 1.25); }

    public void zoomOut() { setZoom(zoom / 1.25); }

    public void actualSize() { setZoom(72.0 / DPI); }

    public void fitWidth() {
        BufferedImage img = canvas.image;
        if (img == null) return;
        int available = scroll.getViewport().getWidth() - 2 * ImageCanvas.MARGIN;
        setZoom((double) available / img.getWidth());
    }

    // ---------------------------------------------------------------- internals

    private void setResult(DiffPages result) {
        this.result = result;
        pages.clear();
        pages.addAll(result.diffImages.keySet());
        status.setText(result.isEqual() ? "Documents are identical" : "Differences on " + pages.size() + " page(s)");
        showPage(0);
    }

    private void showPage(int index) {
        int request = ++pageRequest;
        if (pages.isEmpty()) {
            canvas.setImage(null);
        } else {
            current = Math.clamp(index, 0, pages.size() - 1);
            int page = pages.get(current);
            View view = (View) viewBox.getSelectedItem();
            if (view == View.EXPECTED || view == View.ACTUAL) {
                // Only the diff images are kept; the original pages are rendered when asked for.
                Path file = view == View.EXPECTED ? expected : actual;
                new SwingWorker<BufferedImage, Void>() {
                    @Override protected BufferedImage doInBackground() throws IOException {
                        return renderPage(file, page);
                    }
                    @Override protected void done() {
                        if (request != pageRequest) return;
                        try {
                            display(get());
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        } catch (ExecutionException ex) {
                            LOG.warn("Rendering page " + (page + 1) + " of " + file + " failed", ex.getCause());
                            status.setText("Error: " + ex.getCause());
                        }
                    }
                }.execute();
            } else {
                display(result.diffImages.get(page));
            }
        }
        updateControls();
    }

    private void display(BufferedImage image) {
        canvas.setImage(image);
        scroll.getViewport().setViewPosition(new Point(0, 0));
    }

    /** Renders one page like pdfcompare does, or returns null if the document has fewer pages. */
    private static BufferedImage renderPage(Path file, int page) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            return page < document.getNumberOfPages() ? new PDFRenderer(document).renderImageWithDPI(page, DPI) : null;
        }
    }

    /**
     * Keeps only the diff images of the pages that differ. pdfcompare's CompareResultWithExpectedAndActual
     * also keeps the expected and actual image of every page, which does not fit in memory for longer documents.
     */
    private static final class DiffPages extends CompareResultImpl {
        final Map<Integer, BufferedImage> diffImages = new TreeMap<>();

        @Override
        public synchronized void addPage(PageDiffCalculator diffCalculator, int pageIndex, ImageWithDimension expectedImage,
                                         ImageWithDimension actualImage, ImageWithDimension diffImage) {
            super.addPage(diffCalculator, pageIndex, expectedImage, actualImage, diffImage);
            if (diffCalculator.differencesFound()) {
                diffImages.put(pageIndex, diffImage.bufferedImage);
            }
        }
    }

    private void setZoom(double newZoom) {
        newZoom = Math.clamp(newZoom, 0.05, 4);
        JViewport viewport = scroll.getViewport();
        Rectangle view = viewport.getViewRect();
        // remember the image point at the centre of the view, keep it centred
        double cx = (view.getCenterX() - canvas.offsetX()) / zoom;
        double cy = (view.getCenterY() - canvas.offsetY()) / zoom;
        zoom = newZoom;
        canvas.revalidate();
        scroll.validate();
        int x = (int) (cx * zoom + canvas.offsetX() - view.width / 2.0);
        int y = (int) (cy * zoom + canvas.offsetY() - view.height / 2.0);
        Dimension size = canvas.getSize();
        viewport.setViewPosition(new Point(
                Math.clamp(x, 0, size.width - view.width),
                Math.clamp(y, 0, size.height - view.height)));
        canvas.repaint();
    }

    private void installMouseHandling() {
        MouseAdapter mouse = new MouseAdapter() {
            private Point dragStart;

            @Override public void mousePressed(MouseEvent e) {
                dragStart = e.getLocationOnScreen();
                canvas.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }
            @Override public void mouseReleased(MouseEvent e) {
                canvas.setCursor(Cursor.getDefaultCursor());
            }
            @Override public void mouseDragged(MouseEvent e) {     // drag to pan
                Point now = e.getLocationOnScreen();
                JViewport viewport = scroll.getViewport();
                Point pos = viewport.getViewPosition();
                Dimension size = canvas.getSize(), extent = viewport.getExtentSize();
                pos.translate(dragStart.x - now.x, dragStart.y - now.y);
                pos.x = Math.clamp(pos.x, 0, size.width - extent.width);
                pos.y = Math.clamp(pos.y, 0, size.height - extent.height);
                viewport.setViewPosition(pos);
                dragStart = now;
            }
            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                if (e.isControlDown()) {                            // Ctrl + wheel zooms
                    setZoom(zoom * (e.getWheelRotation() < 0 ? 1.1 : 1 / 1.1));
                } else {                                            // otherwise scroll as usual
                    scroll.dispatchEvent(SwingUtilities.convertMouseEvent(canvas, e, scroll));
                }
            }
        };
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);
        canvas.addMouseWheelListener(mouse);
    }

    private void setBusy(boolean busy) {
        setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : null);
        if (busy) status.setText("Comparing…");
        viewBox.setEnabled(!busy);
    }

    private void updateControls() {
        boolean has = !pages.isEmpty();
        pageLabel.setText(has ? "Page " + (pages.get(current) + 1) + " (" + (current + 1) + " / " + pages.size() + ")" : "–");
        toolbar.updateActionsAsync();   // enable or disable previous/next right away
    }

    /** Paints the current page image scaled by the zoom factor, centred with a margin. */
    private final class ImageCanvas extends JComponent implements Scrollable {
        static final int MARGIN = 16;
        private BufferedImage image;

        void setImage(BufferedImage image) {
            this.image = image;
            revalidate();
            repaint();
        }

        int scaledWidth()  { return image == null ? 0 : (int) Math.round(image.getWidth() * zoom); }
        int scaledHeight() { return image == null ? 0 : (int) Math.round(image.getHeight() * zoom); }
        int offsetX() { return Math.max(MARGIN, (getWidth() - scaledWidth()) / 2); }
        int offsetY() { return MARGIN; }

        @Override public Dimension getPreferredSize() {
            return new Dimension(scaledWidth() + 2 * MARGIN, scaledHeight() + 2 * MARGIN);
        }

        @Override protected void paintComponent(Graphics g) {
            if (image == null) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    zoom < 1 ? RenderingHints.VALUE_INTERPOLATION_BILINEAR
                            : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            int x = offsetX(), y = offsetY(), w = scaledWidth(), h = scaledHeight();
            g2.setColor(new JBColor(new Color(0, 0, 0, 60), new Color(0, 0, 0, 60)));                    // simple drop shadow
            g2.fillRect(x + 3, y + 3, w, h);
            g2.drawImage(image, x, y, w, h, null);
            g2.dispose();
        }

        // Scrollable: stretch to the viewport when the page is smaller, so it stays centred
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
            return o == SwingConstants.VERTICAL ? r.height - 24 : r.width - 24;
        }
        @Override public boolean getScrollableTracksViewportWidth() {
            return getParent() instanceof JViewport && getParent().getWidth() > getPreferredSize().width;
        }
        @Override public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport && getParent().getHeight() > getPreferredSize().height;
        }
    }
}
