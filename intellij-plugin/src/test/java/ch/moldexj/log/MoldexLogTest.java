package ch.moldexj.log;

import ch.moldexj.ExceptionSnapshot;
import ch.moldexj.runtime.ExceptionPrinter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoldexLogTest {

    @Test
    void readsWhatExceptionPrinterWrote() throws IOException {
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(file, true, StandardCharsets.UTF_8);
        out.println("starting");
        ExceptionPrinter.print(new IllegalStateException("first", new IllegalArgumentException("cause")), out);
        out.println("java.lang.IllegalStateException: first");
        out.println("\tat some.Where.method(Where.java:1)");
        ExceptionPrinter.print(new IllegalArgumentException("second\nline"), out);

        MoldexLog log = MoldexLog.read(new StringReader(file.toString(StandardCharsets.UTF_8)));

        assertEquals(2, log.entries().size());
        assertEquals(0, log.skipped());

        MoldexLog.Entry first = log.entries().get(0);
        assertEquals(2, first.line());
        assertEquals("java.lang.IllegalStateException", first.root().type);
        assertEquals("first", first.root().message);
        assertEquals(2, first.snapshot().chain.size());
        assertEquals("Cause", first.snapshot().chain.get(1).label);
        assertFalse(first.root().views.isEmpty());

        ExceptionSnapshot.Frame frame = first.root().frames.get(0);
        assertEquals("ch.moldexj.log.MoldexLogTest", frame.className);
        assertEquals("readsWhatExceptionPrinterWrote", frame.methodName);
        assertEquals("MoldexLogTest.java", frame.fileName);
        assertTrue(frame.line > 0);

        MoldexLog.Entry second = log.entries().get(1);
        assertEquals(5, second.line());
        assertEquals("second\nline", second.root().message);
    }

    @Test
    void findsTheLineBehindALoggerPrefix() throws IOException {
        String line = "2026-11-16 18:47:01 ERROR [main] " + ExceptionPrinter.PREFIX
                + "{\"chain\":[{\"label\":\"Exception\",\"depth\":0,\"type\":\"a.B\",\"message\":\"m\",\"moldable\":true,\"views\":[]}],\"notes\":[]}";

        MoldexLog log = MoldexLog.read(new StringReader(line));

        assertEquals(1, log.entries().size());
        assertEquals("a.B", log.entries().get(0).root().type);
        // A snapshot written before the frames field existed.
        assertTrue(log.entries().get(0).root().frames.isEmpty());
    }

    @Test
    void skipsLinesThatAreNotASnapshot() throws IOException {
        String text = String.join("\n",
                ExceptionPrinter.PREFIX + "Could not print the views: java.lang.OutOfMemoryError",
                ExceptionPrinter.PREFIX + "{\"chain\":[{\"label\":\"Exception\",\"type\":\"a.B\"",
                ExceptionPrinter.PREFIX + "{\"chain\":[],\"notes\":[]}",
                ExceptionPrinter.PREFIX + "{\"chain\":[{\"type\":\"a.B\",\"message\":null,\"views\":null}],\"notes\":null}");

        MoldexLog log = MoldexLog.read(new StringReader(text));

        assertEquals(3, log.skipped());
        assertEquals(1, log.entries().size());
        ExceptionSnapshot.Node node = log.entries().get(0).root();
        assertEquals(4, log.entries().get(0).line());
        assertTrue(node.views.isEmpty());
        assertTrue(log.entries().get(0).snapshot().notes.isEmpty());
    }
}
