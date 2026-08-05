/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package volcoloc;

import ij.ImagePlus;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.process.ShortProcessor;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Output-contract coverage: encoding, empty tables, and column layout. These
 * are what downstream scripts actually consume.
 */
public class VolColocIOTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void writesCsvAsUtf8RegardlessOfPlatformCharset() throws Exception {
        ResultsTable table = new ResultsTable();
        table.incrementCounter();
        table.addValue("Pattern", VolColocAnalysis.ANY_PATTERN);
        table.addValue("Unit", "µm^3");
        File directory = temporary.newFolder("utf8");

        VolColocIO.saveTable(table, directory, "encoding.csv");

        byte[] bytes = read(new File(directory, "encoding.csv"));
        String text = new String(bytes, "UTF-8");
        assertTrue("em dash must survive as UTF-8",
                text.contains(VolColocAnalysis.ANY_PATTERN));
        assertTrue("micro sign must survive as UTF-8", text.contains("µm^3"));
        assertTrue("em dash must be the 3-byte UTF-8 sequence",
                indexOf(bytes, new byte[]{(byte) 0xe2, (byte) 0x80, (byte) 0x94}) >= 0);
    }

    @Test
    public void quotesEmbeddedSeparatorsAndQuotes() throws Exception {
        ResultsTable table = new ResultsTable();
        table.incrementCounter();
        table.addValue("Name", "comma,name");
        table.addValue("Other", "quote\"name");
        File directory = temporary.newFolder("quoting");

        VolColocIO.saveTable(table, directory, "quoting.csv");

        String text = new String(read(new File(directory, "quoting.csv")), "UTF-8");
        assertTrue(text.contains("\"comma,name\""));
        assertTrue(text.contains("\"quote\"\"name\""));
    }

    @Test
    public void emptyTablesStillWriteAHeaderRow() throws Exception {
        // The default 50% partner filter admits nothing here: each partner
        // covers only a quarter of the source object.
        ImagePlus a = plane("A", 1, 1, 1, 1);
        ImagePlus b = plane("B", 10, 11, 12, 13);
        VolColocResult result = VolColoc.run(a, b);
        assertEquals(0,
                result.getDirectionResults().get(0).getPartnerDetails().size());

        File base = temporary.newFolder("empty-tables");
        File root = VolColocIO.save(result, base, "probe");

        File[] partners = new File(root, "Objects").listFiles(
                (directory, name) -> name.endsWith("_Partners.csv"));
        assertTrue(partners != null && partners.length > 0);
        for (File file : partners) {
            String text = new String(read(file), "UTF-8");
            assertTrue(file.getName() + " must not be empty", file.length() > 0);
            assertTrue(file.getName() + " must carry a header",
                    text.startsWith("Source Channel,Partner Channel,Source Label,"
                            + "Partner Label,Overlap Voxels"));
        }
    }

    /**
     * The bounding-box table is the one shape whose column set is conditional,
     * so declaration and population can drift apart per flag combination.
     */
    @Test
    public void boundingBoxHeadersMatchTheRowsUnderEveryFlagCombination()
            throws Exception {
        for (int mask = 1; mask < 8; mask++) {
            ImagePlus a = plane("A", 1, 1, 2, 0);
            ImagePlus b = plane("B", 5, 0, 5, 0);
            VolColocResult result = VolColoc.run(
                    VolColocParameters.builder(a, b)
                            .includeBoundingBoxOverlap((mask & 1) != 0)
                            .includeBoundingBoxCpc((mask & 2) != 0)
                            .includeBoundingBoxVolumeFill((mask & 4) != 0)
                            .build());

            File base = temporary.newFolder("bb-mask-" + mask);
            File root = VolColocIO.save(result, base, "probe");
            File table = new File(new File(root, "Objects"),
                    "probe_C1_A_in_C2_B_Bounding_Boxes.csv");
            assertTrue("mask " + mask, table.isFile());
            String[] lines = new String(read(table), "UTF-8").split("\r\n");
            int columns = lines[0].split(",").length;
            assertTrue("mask " + mask + " must have rows", lines.length > 1);
            for (int line = 1; line < lines.length; line++) {
                assertEquals("mask " + mask + " row " + line + " width",
                        columns, lines[line].split(",").length);
            }
        }
    }

    @Test
    public void emptyMultiTableStillWritesItsConditionalHeader()
            throws Exception {
        // An all-background source channel yields no multi rows at all.
        ImagePlus a = plane("A", 0, 0, 0);
        ImagePlus b = plane("B", 1, 1, 0);
        ImagePlus c = plane("C", 2, 0, 2);

        File base = temporary.newFolder("empty-multi");
        File root = VolColocIO.save(
                VolColoc.run(java.util.Arrays.asList(a, b, c)), base, "probe");

        File table = new File(new File(root, "Multi"),
                "probe_C1_A_Multi_Objects.csv");
        assertTrue(table.isFile());
        String[] lines = new String(read(table), "UTF-8").split("\r\n");
        assertEquals("Label,B Coloc,B Partner,C Coloc,C Partner,Targets Hit",
                lines[0]);
        assertEquals(1, lines.length);
    }

    @Test
    public void perObjectCsvKeepsItsPublishedColumnLayout() throws Exception {
        ImagePlus a = plane("A", 1, 1, 2, 0);
        ImagePlus b = plane("B", 5, 0, 5, 0);
        Calibration calibration = new Calibration();
        calibration.pixelWidth = 1.0;
        calibration.pixelHeight = 1.0;
        calibration.pixelDepth = 1.0;
        calibration.setUnit("um");
        a.setCalibration(calibration);

        File base = temporary.newFolder("layout");
        File root = VolColocIO.save(VolColoc.run(a, b), base, "probe");

        File objects = new File(new File(root, "Objects"),
                "probe_C1_A_in_C2_B_Objects.csv");
        assertTrue(objects.isFile());
        String[] lines = new String(read(objects), "UTF-8").split("\r\n");
        assertEquals("Source Channel,Partner Channel,Source Label,"
                        + "Source Volume (voxels),Source Volume (µm^3),"
                        + "Overlap Voxels,Overlap % of Source,Best Partner Label,"
                        + "Best Partner Overlap Voxels,Partner Count,"
                        + "Threshold %,Colocalized",
                lines[0]);
        assertEquals(3, lines.length);
        assertTrue(lines[1].startsWith("A,B,1,2,2,"));
    }

    @Test
    public void summaryCsvKeepsItsPublishedColumnLayout() throws Exception {
        File base = temporary.newFolder("summary-layout");
        File root = VolColocIO.save(
                VolColoc.run(plane("A", 1, 1, 0), plane("B", 2, 0, 0)),
                base, "probe");

        String[] lines = new String(
                read(new File(new File(root, "Objects"), "probe_Summary.csv")),
                "UTF-8").split("\r\n");
        assertEquals("Source Channel,Partner Channel,Direction,Object Count,"
                        + "Mean Overlap %,Median Overlap %,Threshold %,"
                        + "Colocalized Count,Colocalized %",
                lines[0]);
    }

    /**
     * Channel names default to image titles, which for file input are whole
     * filenames. Five of those must not overflow the 255-character limit on a
     * single path component.
     */
    @Test
    public void savesWithFiveRealisticallyLongChannelNames() throws Exception {
        List<ImagePlus> images = new ArrayList<ImagePlus>();
        List<String> names = new ArrayList<String>();
        String[] markers = {"GFAP", "Iba1", "NeuN", "DAPI", "S100b"};
        for (int i = 0; i < 5; i++) {
            images.add(plane("C" + i, i + 1, 0, i + 1));
            names.add("20260731_mouse00_CA1_" + markers[i] + "_labels.tif");
        }

        File base = temporary.newFolder("long-names");
        File root = VolColocIO.save(
                VolColoc.run(VolColocParameters.builder(images)
                        .channelNames(names).build()),
                base, longPrefix(names));

        File objects = new File(root, "Objects");
        File[] written = objects.listFiles();
        assertTrue(written != null && written.length > 1);
        int objectTables = 0;
        for (File file : written) {
            assertTrue(file.getName() + " is " + file.getName().length()
                            + " chars; NTFS caps a path component at 255",
                    file.getName().length() <= 255);
            assertTrue(file.getName() + " must not be empty", file.length() > 0);
            if (file.getName().endsWith("_Objects.csv")) objectTables++;
        }
        // Five channels, bidirectional: 5x4 = 20 directions, each with its own
        // table. Anything fewer means two directions collided on one filename.
        assertEquals("capped names must not overwrite one another",
                20, objectTables);
    }

    @Test
    public void channelNamesSharingACappedPrefixGetSeparateFiles()
            throws Exception {
        // The names agree for well past the 48-character cap and differ only
        // at the end, so a plain truncation would map them onto one file.
        String stem = "20260731_mouse01_hippocampus_dorsal_CA1_field0001_";
        List<String> names = new ArrayList<String>();
        names.add(stem + "GFAP.tif");
        names.add(stem + "Iba1.tif");
        names.add(stem + "NeuN.tif");
        List<ImagePlus> images = new ArrayList<ImagePlus>();
        for (int i = 0; i < 3; i++) images.add(plane("C" + i, i + 1, 0, i + 1));

        File base = temporary.newFolder("shared-prefix");
        File root = VolColocIO.save(
                VolColoc.run(VolColocParameters.builder(images)
                        .channelNames(names).build()),
                base, longPrefix(names));

        int objectTables = 0;
        for (File file : new File(root, "Objects").listFiles()) {
            if (file.getName().endsWith("_Objects.csv")) objectTables++;
        }
        assertEquals("3 channels bidirectional = 6 directions",
                6, objectTables);
    }

    /** Mirrors what the plugin passes as the file prefix. */
    private static String longPrefix(List<String> names) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) builder.append("_vs_");
            builder.append(names.get(i));
        }
        return VolColocIO.safeName(builder.toString());
    }

    @Test
    public void headingsContainingTabsSurviveTheWrite() throws Exception {
        // getColumnHeadings() joins with tabs, so splitting on them turned a
        // tabbed channel name into two bogus headings and aborted the save.
        ResultsTable table = new ResultsTable();
        table.incrementCounter();
        table.addValue("GFAP\tcortex Coloc", 1);
        table.addValue("Plain", 2);
        File directory = temporary.newFolder("tabbed");

        VolColocIO.saveTable(table, directory, "tabbed.csv");

        assertEquals(2, VolColocIO.columnHeadings(table).length);
        String[] lines = new String(
                read(new File(directory, "tabbed.csv")), "UTF-8").split("\r\n");
        assertEquals(2, lines.length);
        assertEquals(2, lines[1].split(",").length);
    }

    @Test(expected = IOException.class)
    public void stillPropagatesWriteFailures() throws Exception {
        File directory = temporary.newFolder("blocked");
        assertTrue(new File(directory, "blocked.csv").mkdir());
        ResultsTable table = new ResultsTable();
        table.incrementCounter();
        table.addValue("Value", 1);

        VolColocIO.saveTable(table, directory, "blocked.csv");
    }

    @Test
    public void readmeFilesAreWrittenAsUtf8() throws Exception {
        File root = temporary.newFolder("readme");
        VolColocIO.ensureTree(root);
        File readme = new File(new File(root, "Maps"), "README.txt");
        assertTrue(readme.isFile());
        assertTrue(new String(read(readme), "UTF-8").contains("overlap maps"));
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            boolean match = true;
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }

    private static byte[] read(File file) throws IOException {
        byte[] bytes = new byte[(int) file.length()];
        FileInputStream input = new FileInputStream(file);
        try {
            int offset = 0;
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) break;
                offset += read;
            }
        } finally {
            input.close();
        }
        return bytes;
    }

    private static ImagePlus plane(String title, int... values) {
        ShortProcessor processor = new ShortProcessor(values.length, 1);
        for (int i = 0; i < values.length; i++) processor.set(i, values[i]);
        return new ImagePlus(title, processor);
    }
}
