package ch.moldexj.demos.pdf;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ComparisonFailedDemo {
    public static void main(String[] args) {
        Path expected = Paths.get(System.getProperty("user.dir"), "data", "lorem.pdf");
        Path actual = Paths.get(System.getProperty("user.dir"), "data", "lorem-edited.pdf");

        throw new PdfComparisonFailedException(expected, actual);
    }
}

