package demo;

/** One place where the actual JSON breaks the expected contract. */
public final class JsonMismatch {

    private final String path;
    private final String problem;
    private final String expected;
    private final String actual;

    public JsonMismatch(String path, String problem, String expected, String actual) {
        this.path = path;
        this.problem = problem;
        this.expected = expected;
        this.actual = actual;
    }

    public String path() {
        return path;
    }

    public String problem() {
        return problem;
    }

    public String expected() {
        return expected;
    }

    public String actual() {
        return actual;
    }

    @Override
    public String toString() {
        return path + ": " + problem + " (expected " + expected + ", actual " + actual + ")";
    }
}
