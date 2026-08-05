/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package volcoloc;

import sc.fiji.volcoloc.core.MultiTargetSummary;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;
import ij.process.ShortProcessor;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class VolColocBatchRunnerTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void previewsRunsAndSavesAParsedGroup() throws Exception {
        File labels = temporary.newFolder("labels");
        save(labels, "sample_DAPI.tif", 1, 1, 1, 1, 0, 0);
        save(labels, "sample_GFAP.tif", 2, 2, 0, 0, 2, 0);
        Map<String, Double> thresholds = new LinkedHashMap<String, Double>();
        thresholds.put("DAPI", Double.valueOf(60.0));

        VolColocBatchParameters parameters =
                VolColocBatchParameters.builder(
                                labels,
                                "(.+)_([^_]+)[.](?:tif|tiff)$",
                                2)
                        .recursive(false)
                        .thresholdsByChannel(thresholds)
                        .boundingBoxThresholdsByChannel(thresholds)
                        .includeBoundingBoxOverlap(true)
                        .includeBoundingBoxCpc(true)
                        .includeBoundingBoxVolumeFill(true)
                        .autoSave(true)
                        .saveDirectory(labels)
                        .build();

        String preview = VolColocBatchRunner.preview(parameters);
        assertTrue(preview.contains("1 runnable"));
        assertTrue(preview.contains("sample_DAPI.tif"));
        assertTrue(preview.contains("sample_GFAP.tif"));

        VolColocBatchResult result = VolColocBatchRunner.run(parameters);
        assertEquals(1, result.getProcessedGroups());
        assertEquals(0, result.getErrorGroups());
        assertTrue(new File(result.getOutputDirectory(),
                "Folder/Batch_Summary.csv").isFile());
        assertTrue(new File(result.getOutputDirectory(),
                "Folder/Batch_Folder_Summary.csv").isFile());
        assertTrue(new File(result.getOutputDirectory(),
                "Folder/Batch_Bounding_Box_Summary.csv").isFile());
        assertTrue(new File(result.getOutputDirectory(),
                "Folder/Batch_Folder_Bounding_Box_Summary.csv").isFile());
        assertTrue(new File(result.getOutputDirectory(),
                "Folder/sample/Objects").isDirectory());
        assertTrue(new File(result.getOutputDirectory(),
                "Maps/README.txt").isFile());
    }

    @Test
    public void parsesNamedThresholdsCaseInsensitively() {
        Map<String, Double> parsed =
                VolColocBatchRunner.parseThresholds("DAPI=20, GFAP=40.5");
        assertEquals(20.0, parsed.get("DAPI").doubleValue(), 0.0);
        assertEquals(40.5, parsed.get("GFAP").doubleValue(), 0.0);
    }

    @Test
    public void returnsUnsavedAggregateTablesAndKeepsMultiPatternsDistinct()
            throws Exception {
        File labels = temporary.newFolder("unsaved");
        save(labels, "sample_A.tif", 1, 2, 3);
        save(labels, "sample_None.tif", 10, 0, 0);
        save(labels, "sample_X + Y.tif", 0, 0, 20);

        VolColocBatchParameters parameters =
                VolColocBatchParameters.builder(
                                labels,
                                "(.+)_([^_]+)[.](?:tif|tiff)$",
                                2)
                        .recursive(false)
                        .saveDirectory(labels)
                        .build();

        VolColocBatchResult result = VolColocBatchRunner.run(parameters);

        assertNull(result.getOutputDirectory());
        assertEquals(6, result.getSummaryTable().getCounter());
        assertEquals(6, result.getFolderSummaryTable().getCounter());
        ResultsTable multi = result.getFolderMultiSummaryTable();
        assertEquals(1.0, value(multi, "A", "None", "Count"), 0.0);
        assertEquals(1.0, value(multi, "A", "\"None\"", "Count"), 0.0);
        assertEquals(1.0, value(multi, "A", "\"X + Y\"", "Count"), 0.0);
        assertEquals(2.0,
                value(multi, "A", "\u2014 Any \u2014", "Count"), 0.0);
        assertEquals(66.67,
                value(multi, "A", "\u2014 Any \u2014", "% of Source"), 0.0);
    }

    @Test
    public void disabledSummaryOmitsBatchSummaryTablesAndFiles()
            throws Exception {
        File labels = temporary.newFolder("no-summary");
        save(labels, "sample_A.tif", 1, 1, 0);
        save(labels, "sample_B.tif", 2, 0, 0);

        VolColocBatchResult result = VolColocBatchRunner.run(
                VolColocBatchParameters.builder(
                                labels,
                                "(.+)_([^_]+)[.](?:tif|tiff)$",
                                2)
                        .recursive(false)
                        .includeSummaryTable(false)
                        .autoSave(true)
                        .saveDirectory(labels)
                        .build());

        assertEquals(0, result.getSummaryTable().getCounter());
        assertEquals(0, result.getFolderSummaryTable().getCounter());
        assertFalse(new File(result.getOutputDirectory(),
                "Folder/Batch_Summary.csv").exists());
        assertFalse(new File(result.getOutputDirectory(),
                "Folder/Batch_Folder_Summary.csv").exists());
    }

    @Test
    public void givesCollidingParsedGroupsDistinctOutputNames()
            throws Exception {
        File labels = temporary.newFolder("colliding-groups");
        save(labels, "sample_A.tif", 1, 1, 0);
        save(labels, "sample_B.tif", 2, 0, 0);
        save(labels, "sample_A.tiff", 3, 3, 0);
        save(labels, "sample_B.tiff", 4, 0, 0);

        VolColocBatchResult result = VolColocBatchRunner.run(
                VolColocBatchParameters.builder(
                                labels,
                                "(.+)_([^_]+)[.](?:tif|tiff)$",
                                2)
                        .recursive(false)
                        .autoSave(true)
                        .saveDirectory(labels)
                        .build());

        assertEquals(2, result.getProcessedGroups());
        File folder = new File(result.getOutputDirectory(), "Folder");
        File[] groupDirectories = folder.listFiles();
        int directories = 0;
        if (groupDirectories != null) {
            for (File candidate : groupDirectories) {
                if (candidate.isDirectory()) directories++;
            }
        }
        assertEquals(2, directories);
        assertEquals(2, distinctGroupCount(result.getSummaryTable()));
    }

    @Test
    public void givesSanitizedChannelNamesDistinctCsvFiles()
            throws Exception {
        File labels = temporary.newFolder("colliding-channels");
        save(labels, "sample_A B.tif", 1, 1, 0);
        save(labels, "sample_A_B.tif", 2, 0, 0);

        VolColocBatchResult result = VolColocBatchRunner.run(
                VolColocBatchParameters.builder(
                                labels,
                                "(.+?)_(A B|A_B)[.](?:tif|tiff)$",
                                2)
                        .recursive(false)
                        .autoSave(true)
                        .saveDirectory(labels)
                        .build());

        File objects = new File(result.getOutputDirectory(),
                "Folder/sample/Objects");
        assertEquals(2, countFilesEndingWith(objects, "_Objects.csv"));
    }

    @Test
    public void givesCollidingRecursiveFoldersDistinctOutputPaths()
            throws Exception {
        File labels = temporary.newFolder("colliding-folders");
        File spaced = new File(labels, "A B");
        File underscored = new File(labels, "A_B");
        assertTrue(spaced.mkdirs());
        assertTrue(underscored.mkdirs());
        save(spaced, "sample_A.tif", 1, 1, 0);
        save(spaced, "sample_B.tif", 2, 0, 0);
        save(underscored, "sample_A.tif", 3, 3, 0);
        save(underscored, "sample_B.tif", 4, 0, 0);

        VolColocBatchResult result = VolColocBatchRunner.run(
                VolColocBatchParameters.builder(
                                labels,
                                "(.+)_([^_]+)[.](?:tif|tiff)$",
                                2)
                        .recursive(true)
                        .autoSave(true)
                        .saveDirectory(labels)
                        .build());

        File folder = new File(result.getOutputDirectory(), "Folder");
        assertTrue(new File(folder, "A_B/sample/Objects").isDirectory());
        assertTrue(new File(folder, "A_B_2/sample/Objects").isDirectory());
    }

    @Test
    public void separatesFolderMultiPatternsByTargetChannelSet()
            throws Exception {
        File labels = temporary.newFolder("mixed-target-sets");
        save(labels, "one_A.tif", 1);
        save(labels, "one_B.tif", 2);
        save(labels, "one_C.tif", 0);
        save(labels, "two_A.tif", 1);
        save(labels, "two_B.tif", 2);
        save(labels, "two_D.tif", 0);

        VolColocBatchResult result = VolColocBatchRunner.run(
                VolColocBatchParameters.builder(
                                labels,
                                "(.+)_([^_]+)[.](?:tif|tiff)$",
                                2)
                        .recursive(false)
                        .build());

        ResultsTable multi = result.getFolderMultiSummaryTable();
        int matching = 0;
        Map<String, Boolean> targetSets =
                new LinkedHashMap<String, Boolean>();
        for (int row = 0; row < multi.getCounter(); row++) {
            if ("A".equals(multi.getStringValue("Source", row))
                    && "B".equals(multi.getStringValue("Pattern", row))) {
                matching++;
                targetSets.put(
                        multi.getStringValue("Target Channels", row),
                        Boolean.TRUE);
                assertEquals(1.0, multi.getValue("Count", row), 0.0);
            }
        }
        assertEquals(2, matching);
        assertEquals(2, targetSets.size());
    }

    @Test
    public void folderMultiSummaryGroupsEachSourceAndEndsWithNoneThenAny()
            throws Exception {
        File labels = temporary.newFolder("folder-multi-order");
        // Two groups whose objects hit different pattern combinations, so the
        // folder aggregate has to merge patterns first seen in either group.
        save(labels, "one_A.tif", 1, 2, 3);
        save(labels, "one_B.tif", 9, 0, 9);
        save(labels, "one_C.tif", 0, 0, 8);
        save(labels, "two_A.tif", 1, 2, 3);
        save(labels, "two_B.tif", 0, 0, 0);
        save(labels, "two_C.tif", 7, 7, 0);

        VolColocBatchResult result = VolColocBatchRunner.run(
                VolColocBatchParameters.builder(
                                labels, "(.+)_([^_]+)[.](?:tif|tiff)$", 2)
                        .recursive(false)
                        .build());

        ResultsTable multi = result.getFolderMultiSummaryTable();
        assertTrue(multi.getCounter() > 0);

        Map<String, Boolean> seenKeys = new LinkedHashMap<String, Boolean>();
        String currentSource = null;
        Map<String, Boolean> finishedSources = new LinkedHashMap<String, Boolean>();
        int nonAnyTotal = 0;
        for (int row = 0; row < multi.getCounter(); row++) {
            String source = multi.getStringValue("Source", row);
            String targets = multi.getStringValue("Target Channels", row);
            String pattern = multi.getStringValue("Pattern", row);
            String sourceKey = source + " " + targets;

            // No duplicated (source, targets, pattern) rows.
            assertNull("duplicate row for " + sourceKey + "/" + pattern,
                    seenKeys.put(sourceKey + " " + pattern, Boolean.TRUE));

            if (!sourceKey.equals(currentSource)) {
                // Each source's rows must be contiguous.
                assertNull("rows for " + sourceKey + " are not contiguous",
                        finishedSources.put(sourceKey, Boolean.TRUE));
                if (currentSource != null) {
                    assertEquals("previous block must end with — Any —",
                            MultiTargetSummary.ANY_PATTERN,
                            multi.getStringValue("Pattern", row - 1));
                    assertEquals("None must sit just before — Any —",
                            MultiTargetSummary.NO_HITS_PATTERN,
                            multi.getStringValue("Pattern", row - 2));
                }
                currentSource = sourceKey;
                nonAnyTotal = 0;
            }
            if (!MultiTargetSummary.ANY_PATTERN.equals(pattern)) {
                nonAnyTotal += (int) Math.round(multi.getValue("Count", row));
            } else {
                // Every source object is counted by exactly one non-total
                // pattern, and the total can never exceed that.
                assertTrue(sourceKey + " has no objects", nonAnyTotal > 0);
                assertTrue("— Any — cannot exceed the source total",
                        Math.round(multi.getValue("Count", row)) <= nonAnyTotal);
                // Percentages are against that same denominator.
                double percent = multi.getValue("% of Source", row);
                assertEquals(sourceKey + " percentage must match its count",
                        Math.round(multi.getValue("Count", row))
                                * 10000.0 / nonAnyTotal / 100.0,
                        percent, 0.005);
            }
        }
        assertEquals(MultiTargetSummary.ANY_PATTERN,
                multi.getStringValue("Pattern", multi.getCounter() - 1));
        assertEquals(MultiTargetSummary.NO_HITS_PATTERN,
                multi.getStringValue("Pattern", multi.getCounter() - 2));
    }

    @Test(expected = IOException.class)
    public void propagatesCsvSaveFailures() throws Exception {
        File output = temporary.newFolder("blocked-save");
        File blockingDirectory = new File(output, "blocked.csv");
        assertTrue(blockingDirectory.mkdir());
        ResultsTable table = new ResultsTable();
        table.incrementCounter();
        table.addValue("Value", 1);

        VolColocIO.saveTable(table, output, "blocked.csv");
    }

    @Test
    public void propagatesPerGroupBatchSaveFailures() throws Exception {
        File labels = temporary.newFolder("batch-save-input");
        File output = temporary.newFolder("batch-save-output");
        save(labels, "sample_A.tif", 1, 1, 0);
        save(labels, "sample_B.tif", 2, 0, 0);
        File group = new File(output,
                VolColocIO.ROOT_DIRECTORY + "/Folder/sample");
        assertTrue(group.mkdirs());
        assertTrue(new File(group, "Objects").createNewFile());

        try {
            VolColocBatchRunner.run(
                    VolColocBatchParameters.builder(
                                    labels,
                                    "(.+)_([^_]+)[.](?:tif|tiff)$",
                                    2)
                            .recursive(false)
                            .autoSave(true)
                            .saveDirectory(output)
                            .build());
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains(
                    "Could not save batch group sample"));
            assertFalse(new File(output,
                    VolColocIO.ROOT_DIRECTORY
                            + "/Folder/Batch_Summary.csv").exists());
            return;
        }
        throw new AssertionError("Expected the batch save failure to propagate.");
    }

    @Test
    public void propagatesAggregateBatchSaveFailures() throws Exception {
        File labels = temporary.newFolder("aggregate-save-input");
        File output = temporary.newFolder("aggregate-save-output");
        save(labels, "sample_A.tif", 1, 1, 0);
        save(labels, "sample_B.tif", 2, 0, 0);
        File folder = new File(output,
                VolColocIO.ROOT_DIRECTORY + "/Folder");
        assertTrue(folder.mkdirs());
        assertTrue(new File(folder, "Batch_Summary.csv").mkdir());

        try {
            VolColocBatchRunner.run(
                    VolColocBatchParameters.builder(
                                    labels,
                                    "(.+)_([^_]+)[.](?:tif|tiff)$",
                                    2)
                            .recursive(false)
                            .autoSave(true)
                            .saveDirectory(output)
                            .build());
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains(
                    "Could not save aggregate batch tables"));
            return;
        }
        throw new AssertionError(
                "Expected the aggregate save failure to propagate.");
    }

    @Test
    public void excludesFailedAnalysisGroupsFromAggregates()
            throws Exception {
        File labels = temporary.newFolder("mixed-validity");
        save(labels, "good_A.tif", 1, 1);
        save(labels, "good_B.tif", 2, 0);
        save(labels, "bad_A.tif", 1, 1);
        save(labels, "bad_B.tif", 2, 0, 0);

        VolColocBatchResult result = VolColocBatchRunner.run(
                VolColocBatchParameters.builder(
                                labels,
                                "(.+)_([^_]+)[.](?:tif|tiff)$",
                                2)
                        .recursive(false)
                        .build());

        assertEquals(1, result.getProcessedGroups());
        assertEquals(1, result.getErrorGroups());
        assertEquals(2, result.getSummaryTable().getCounter());
        assertEquals("good",
                result.getSummaryTable().getStringValue("Group", 0));
    }

    @Test
    public void reservesAggregateNamesForGroupsAndRecursiveFolders()
            throws Exception {
        File labels = temporary.newFolder("reserved-names");
        save(labels, "Batch_Summary.csv_A.tif", 1, 1, 0);
        save(labels, "Batch_Summary.csv_B.tif", 2, 0, 0);
        File reservedFolder = new File(labels, "Batch_Summary.csv");
        assertTrue(reservedFolder.mkdir());
        save(reservedFolder, "sample_A.tif", 3, 3, 0);
        save(reservedFolder, "sample_B.tif", 4, 0, 0);

        VolColocBatchResult result = VolColocBatchRunner.run(
                VolColocBatchParameters.builder(
                                labels,
                                "(.+)_([^_]+)[.](?:tif|tiff)$",
                                2)
                        .recursive(true)
                        .autoSave(true)
                        .saveDirectory(labels)
                        .build());

        File folder = new File(result.getOutputDirectory(), "Folder");
        assertTrue(new File(folder, "Batch_Summary.csv").isFile());
        assertTrue(new File(folder,
                "Batch_Summary.csv_2/Objects").isDirectory());
        assertTrue(new File(folder,
                "Batch_Summary.csv_folder/sample/Objects").isDirectory());
        assertEquals(2, result.getProcessedGroups());
        assertEquals(0, result.getErrorGroups());
    }

    @Test
    public void skipsFilesWhoseOptionalChannelGroupDidNotParticipate()
            throws Exception {
        File labels = temporary.newFolder("optional-group");
        save(labels, "sample.tif", 0);
        save(labels, "sample_A.tif", 1);
        save(labels, "sample_B.tif", 2);
        VolColocBatchParameters parameters =
                VolColocBatchParameters.builder(
                                labels,
                                "(.+?)(?:_([^_]+))?[.]tif$",
                                2)
                        .recursive(false)
                        .build();

        String preview = VolColocBatchRunner.preview(parameters);

        assertTrue(preview.contains("1 runnable"));
        assertFalse(preview.contains("sample.tif\n"));
    }

    private static int countFilesEndingWith(File folder, String suffix) {
        File[] files = folder.listFiles();
        int count = 0;
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(suffix)) count++;
            }
        }
        return count;
    }

    private static int distinctGroupCount(ResultsTable table) {
        Map<String, Boolean> groups = new LinkedHashMap<String, Boolean>();
        for (int row = 0; row < table.getCounter(); row++) {
            groups.put(table.getStringValue("Group", row), Boolean.TRUE);
        }
        return groups.size();
    }

    private static double value(
            ResultsTable table, String source, String pattern, String column) {
        for (int row = 0; row < table.getCounter(); row++) {
            if (source.equals(table.getStringValue("Source", row))
                    && pattern.equals(
                    table.getStringValue("Pattern", row))) {
                return table.getValue(column, row);
            }
        }
        return Double.NaN;
    }

    private static void save(File folder, String name, int... labels) {
        ShortProcessor processor = new ShortProcessor(labels.length, 1);
        for (int i = 0; i < labels.length; i++) processor.set(i, labels[i]);
        ImagePlus image = new ImagePlus(name, processor);
        IJ.saveAsTiff(image, new File(folder, name).getAbsolutePath());
        image.close();
    }
}
