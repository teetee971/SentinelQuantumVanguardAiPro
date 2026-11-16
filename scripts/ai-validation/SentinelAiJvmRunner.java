import java.nio.file.*;
import java.io.*;
import java.util.*;
import javax.xml.stream.*;
import org.junit.runner.*;
import org.junit.runner.notification.*;

/** Standalone JUnit 4 evidence writer, not an Android instrumentation runner. */
class SentinelAiJvmRunner {
    static class Case {
        Description description;
        long start = System.nanoTime();
        double seconds;
        Throwable failure;
        boolean skipped;
        Case(Description d) { description = d; }
    }
    public static void main(String[] args) throws Exception {
        Path output = Paths.get(args[0]); Files.createDirectories(output);
        boolean ok = true;
        for (int i = 1; i < args.length; i++) {
            Map<Description, Case> cases = new LinkedHashMap<>();
            JUnitCore core = new JUnitCore();
            core.addListener(new RunListener() {
                private Case entry(Description d) { return cases.computeIfAbsent(d, Case::new); }
                public void testStarted(Description d) { entry(d); }
                public void testFailure(Failure f) { entry(f.getDescription()).failure = f.getException(); }
                public void testAssumptionFailure(Failure f) { entry(f.getDescription()).skipped = true; }
                public void testIgnored(Description d) { entry(d).skipped = true; }
                public void testFinished(Description d) { Case c = entry(d); c.seconds = (System.nanoTime() - c.start) / 1e9; }
            });
            Result result = core.run(Class.forName(args[i]));
            long skipped = cases.values().stream().filter(c -> c.skipped).count();
            ok &= result.wasSuccessful() && result.getRunCount() > 0 && skipped == 0;
            try (OutputStream stream = Files.newOutputStream(output.resolve("TEST-" + args[i] + ".xml"))) {
                XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(stream, "UTF-8");
                xml.writeStartDocument("UTF-8", "1.0"); xml.writeStartElement("testsuite");
                xml.writeAttribute("name", args[i]);
                xml.writeAttribute("tests", Integer.toString(cases.size()));
                xml.writeAttribute("failures", Integer.toString(result.getFailureCount()));
                xml.writeAttribute("errors", "0");
                xml.writeAttribute("skipped", Long.toString(skipped));
                xml.writeAttribute("time", Double.toString(result.getRunTime() / 1000.0));
                for (Case c : cases.values()) {
                    xml.writeStartElement("testcase");
                    xml.writeAttribute("classname", String.valueOf(c.description.getClassName()));
                    xml.writeAttribute("name", String.valueOf(c.description.getMethodName()));
                    xml.writeAttribute("time", Double.toString(c.seconds));
                    if (c.skipped) xml.writeEmptyElement("skipped");
                    if (c.failure != null) {
                        xml.writeStartElement("failure");
                        xml.writeAttribute("type", c.failure.getClass().getName());
                        StringWriter stack = new StringWriter(); c.failure.printStackTrace(new PrintWriter(stack));
                        xml.writeCharacters(stack.toString()); xml.writeEndElement();
                    }
                    xml.writeEndElement();
                }
                xml.writeEndElement(); xml.writeEndDocument(); xml.close();
            }
            System.out.println(args[i] + ": tests=" + result.getRunCount() + " failures=" + result.getFailureCount() + " skipped=" + skipped);
        }
        System.exit(ok ? 0 : 1);
    }
}
