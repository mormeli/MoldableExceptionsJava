package ch.moldexj.runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExceptionPrinterTest {

    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    private PrintStream originalErr;
    private Thread.UncaughtExceptionHandler originalHandler;

    @BeforeEach
    void captureErr() throws UnsupportedEncodingException {
        originalErr = System.err;
        originalHandler = Thread.getDefaultUncaughtExceptionHandler();
        System.setErr(new PrintStream(captured, true, "UTF-8"));
    }

    @AfterEach
    void restore() {
        ExceptionPrinter.uninstall();
        Thread.setDefaultUncaughtExceptionHandler(originalHandler);
        System.setErr(originalErr);
    }

    @Test
    void printWritesOneLineWithTheViewsThatNeedOnlyTheException() throws Exception {
        ExceptionPrinter.print(new ViewedException("boom"));

        List<String> lines = moldexLines();
        assertEquals(1, lines.size(), output());
        String line = lines.get(0);
        assertTrue(line.startsWith("[moldex] {\"chain\":["), line);
        assertTrue(line.contains("\"type\":\"ch.moldexj.runtime.ViewedException\""), line);
        assertTrue(line.contains("\"title\":\"Info\",\"kind\":\"text\""), line);
        assertTrue(line.contains("\"text\":\"info about boom\""), line);
        assertTrue(line.contains("\"title\":\"Message\",\"kind\":\"text\""), line);
        assertTrue(line.contains("\"text\":\"message: boom\""), line);
    }

    @Test
    void viewThatNeedsTheDebuggerIsUnavailableWithoutANote() throws Exception {
        ExceptionPrinter.print(new ViewedException("boom"));

        String line = moldexLines().get(0);
        assertTrue(line.contains("\"title\":\"Stack\",\"kind\":\"unavailable\""), line);
        assertTrue(line.contains("This view needs data from the debugger"), line);
        assertFalse(line.contains("frames: "), line);
        assertTrue(line.endsWith("\"notes\":[]}"), line);
    }

    @Test
    void printToAStreamLeavesSystemErrAlone() throws Exception {
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        ExceptionPrinter.print(new ViewedException("boom"), new PrintStream(file, true, "UTF-8"));

        assertTrue(file.toString("UTF-8").startsWith("[moldex] {"));
        assertEquals("", output());
    }

    @Test
    void printAlsoWritesAnExceptionWithoutViewsOfItsOwn() throws Exception {
        ExceptionPrinter.print(new IllegalStateException("plain"));

        List<String> lines = moldexLines();
        assertEquals(1, lines.size(), output());
        assertTrue(lines.get(0).contains("\"moldable\":false"), lines.get(0));
    }

    @Test
    void handlerPrintsThenCallsThePreviousHandler() throws Exception {
        AtomicReference<Throwable> seen = new AtomicReference<>();
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> seen.set(e));
        ExceptionPrinter.install();
        ExceptionPrinter.install(); // a second call must not wrap the handler again

        ViewedException thrown = new ViewedException("uncaught");
        throwInThread(thrown);

        assertEquals(1, moldexLines().size(), output());
        assertSame(thrown, seen.get());
    }

    @Test
    void handlerWithoutAPreviousHandlerPrintsTheStackTraceOnce() throws Exception {
        Thread.setDefaultUncaughtExceptionHandler(null);
        ExceptionPrinter.install();

        throwInThread(new ViewedException("uncaught"));

        String output = output();
        assertEquals(1, moldexLines().size(), output);
        String header = "Exception in thread \"thrower\" ch.moldexj.runtime.ViewedException: uncaught";
        assertTrue(output.contains(header), output);
        assertEquals(output.indexOf(header), output.lastIndexOf(header), output);
        assertTrue(output.indexOf("[moldex] ") < output.indexOf(header), output);
    }

    @Test
    void handlerPrintsNoLineForAnExceptionWithoutViews() throws Exception {
        Thread.setDefaultUncaughtExceptionHandler(null);
        ExceptionPrinter.install();

        throwInThread(new IllegalStateException("plain"));

        assertEquals(0, moldexLines().size(), output());
        assertTrue(output().contains("java.lang.IllegalStateException: plain"), output());
    }

    @Test
    void uninstallRestoresThePreviousHandler() {
        Thread.UncaughtExceptionHandler previous = (thread, e) -> { };
        Thread.setDefaultUncaughtExceptionHandler(previous);
        ExceptionPrinter.install();
        ExceptionPrinter.uninstall();

        assertSame(previous, Thread.getDefaultUncaughtExceptionHandler());
    }

    private static void throwInThread(RuntimeException e) throws InterruptedException {
        Thread thread = new Thread(() -> {
            throw e;
        }, "thrower");
        thread.start();
        thread.join();
    }

    private String output() throws UnsupportedEncodingException {
        System.err.flush();
        return captured.toString("UTF-8");
    }

    private List<String> moldexLines() throws UnsupportedEncodingException {
        List<String> lines = new ArrayList<>();
        for (String line : output().split("\\R")) {
            if (line.startsWith(ExceptionPrinter.PREFIX)) {
                lines.add(line);
            }
        }
        return lines;
    }
}
