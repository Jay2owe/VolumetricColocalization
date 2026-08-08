/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package volcoloc.equivalence;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Golden-master gate for folder discovery, batch execution and saved output. */
public class BatchEquivalenceTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void harnessIsByteIdenticalTwiceInARow() throws Exception {
        Map<String, String> first = BatchHarness.captureAll(
                temporary.newFolder("batch-first"));
        Map<String, String> second = BatchHarness.captureAll(
                temporary.newFolder("batch-second"));
        assertTrue("batch corpus is empty", first.size() > 5);
        assertEquals(first, second);
    }

    @Test
    public void batchOutputMatchesPreChassisGoldens() throws Exception {
        Map<String, String> current = BatchHarness.captureAll(
                temporary.newFolder("batch-current"));
        if (BatchGoldens.capturing()) {
            BatchGoldens.save(current);
            System.out.println("CAPTURED " + current.size()
                    + " immutable batch goldens to "
                    + BatchGoldens.file().getAbsolutePath()
                    + " -- this run verified nothing.");
            return;
        }
        assertTrue("batch goldens missing at "
                        + BatchGoldens.file().getAbsolutePath(),
                BatchGoldens.file().isFile());
        Map<String, String> golden = BatchGoldens.load();
        assertEquals("batch corpus changed size", golden.size(), current.size());

        List<String> moved = new ArrayList<String>();
        StringBuilder diagnostics = new StringBuilder();
        for (Map.Entry<String, String> entry : golden.entrySet()) {
            String value = current.get(entry.getKey());
            if (value == null || !entry.getValue().equals(value)) {
                moved.add(entry.getKey());
                diagnostics.append('\n').append(entry.getKey()).append(": ")
                        .append(firstDifference(entry.getValue(), value));
            }
        }
        if (!moved.isEmpty()) {
            fail("Tier 1 batch output moved in " + moved + diagnostics);
        }
    }

    private static String firstDifference(String expected, String actual) {
        if (actual == null) return "scenario is missing";
        String[] expectedLines = expected.split("\n", -1);
        String[] actualLines = actual.split("\n", -1);
        int common = Math.min(expectedLines.length, actualLines.length);
        for (int line = 0; line < common; line++) {
            if (!expectedLines[line].equals(actualLines[line])) {
                return "line " + (line + 1)
                        + " expected <" + expectedLines[line] + ">"
                        + " but was <" + actualLines[line] + ">";
            }
        }
        return "line count expected " + expectedLines.length
                + " but was " + actualLines.length;
    }
}
