/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package volcoloc.equivalence;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;
import ij.process.ShortProcessor;
import volcoloc.VolColocBatchParameters;
import volcoloc.VolColocBatchResult;
import volcoloc.VolColocBatchRunner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds a deterministic on-disk corpus and snapshots the whole batch surface. */
final class BatchHarness {

    private static final String REGEX = "(.+)_([^_]+)[.](?:tif|tiff)$";

    private BatchHarness() {
    }

    static Map<String, String> captureAll(File workspace) throws Exception {
        Map<String, String> dumps = new LinkedHashMap<String, String>();
        for (String scenario : Arrays.asList(
                "flat-default", "recursive-full", "outputs-disabled",
                "threshold-boundaries", "degenerate", "errors-and-skips",
                "optional-group", "non-recursive")) {
            dumps.put(scenario, normalizeLines(
                    captureScenario(workspace, scenario)));
        }
        dumps.put("rejections", normalizeLines(captureRejections(workspace)));
        return dumps;
    }

    private static String normalizeLines(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String captureScenario(File workspace, String scenario)
            throws Exception {
        File root = new File(workspace, scenario);
        File labels = new File(root, "labels");
        File output = new File(root, "output");
        mkdirs(labels);
        mkdirs(output);
        layout(labels, scenario);

        VolColocBatchParameters.Builder builder =
                VolColocBatchParameters.builder(labels,
                        "optional-group".equals(scenario)
                                ? "(.+?)(?:_([^_]+))?[.]tif$" : REGEX,
                        2)
                        .recursive(!"flat-default".equals(scenario)
                                && !"non-recursive".equals(scenario))
                        .autoSave(!"outputs-disabled".equals(scenario)
                                && !"degenerate".equals(scenario)
                                && !"errors-and-skips".equals(scenario))
                        .saveDirectory(output);

        if ("recursive-full".equals(scenario)) {
            Map<String, Double> thresholds = new LinkedHashMap<String, Double>();
            thresholds.put("A", Double.valueOf(20.0));
            thresholds.put("B", Double.valueOf(40.0));
            Map<String, Double> bounding = new LinkedHashMap<String, Double>();
            bounding.put("A", Double.valueOf(25.0));
            bounding.put("B", Double.valueOf(75.0));
            builder.thresholdsByChannel(thresholds)
                    .boundingBoxThresholdsByChannel(bounding)
                    .includeBoundingBoxOverlap(true)
                    .includeBoundingBoxCpc(true)
                    .includeBoundingBoxVolumeFill(true);
        } else if ("outputs-disabled".equals(scenario)) {
            builder.includePerObjectTables(false)
                    .includeSummaryTable(false)
                    .includePartnerDetails(false)
                    .includeMultiColocalization(false);
        } else if ("threshold-boundaries".equals(scenario)) {
            Map<String, Double> thresholds = new LinkedHashMap<String, Double>();
            thresholds.put("A", Double.valueOf(0.0));
            thresholds.put("B", Double.valueOf(100.0));
            builder.thresholdsByChannel(thresholds)
                    .boundingBoxThresholdsByChannel(thresholds)
                    .bidirectional(false)
                    .minimumDetailOverlapPercent(0.0)
                    .includeBoundingBoxOverlap(true)
                    .includeBoundingBoxCpc(true)
                    .includeBoundingBoxVolumeFill(true);
        }

        VolColocBatchParameters parameters = builder.build();
        StringBuilder out = new StringBuilder();
        out.append("== preview ==\n");
        appendCall(out, new Call() {
            public String run() {
                return VolColocBatchRunner.preview(parameters);
            }
        });
        out.append("== run ==\n");
        try {
            VolColocBatchResult result = VolColocBatchRunner.run(parameters);
            out.append("total=").append(result.getTotalGroups())
                    .append(" runnable=").append(result.getRunnableGroups())
                    .append(" processed=").append(result.getProcessedGroups())
                    .append(" skipped=").append(result.getSkippedGroups())
                    .append(" errors=").append(result.getErrorGroups())
                    .append(" hasErrors=").append(result.hasErrors()).append('\n');
            out.append("output=").append(result.getOutputDirectory() == null
                    ? "(null)" : result.getOutputDirectory().getName()).append('\n');
            appendTable(out, "summary", result.getSummaryTable());
            appendTable(out, "folder-summary", result.getFolderSummaryTable());
            appendTable(out, "multi", result.getMultiSummaryTable());
            appendTable(out, "folder-multi", result.getFolderMultiSummaryTable());
            appendTable(out, "bounding", result.getBoundingBoxSummaryTable());
            appendTable(out, "folder-bounding",
                    result.getFolderBoundingBoxSummaryTable());
        } catch (RuntimeException exception) {
            appendException(out, exception);
        }
        out.append("== output-tree ==\n").append(tree(output));
        return out.toString();
    }

    private static void layout(File labels, String scenario) throws IOException {
        if ("recursive-full".equals(scenario)) {
            save(labels, "root_A.tif", 1, 1, 2, 0);
            save(labels, "root_B.tif", 8, 0, 8, 0);
            save(labels, "root_C.tif", 0, 7, 7, 0);
            File nested = new File(labels, "plate-2");
            mkdirs(nested);
            save(nested, "nested_A.tif", 1, 2, 3, 0);
            save(nested, "nested_B.tif", 9, 0, 9, 0);
            save(nested, "nested_C.tif", 0, 8, 8, 0);
        } else if ("outputs-disabled".equals(scenario)) {
            save(labels, "sample_A.tif", 1, 1, 0);
            save(labels, "sample_B.tif", 5, 0, 0);
        } else if ("threshold-boundaries".equals(scenario)) {
            save(labels, "sample_A.tif", 1, 1, 2, 2);
            save(labels, "sample_B.tif", 9, 0, 8, 8);
        } else if ("degenerate".equals(scenario)) {
            save(labels, "empty_A.tif", 0, 0, 0, 0);
            save(labels, "empty_B.tif", 0, 0, 0, 0);
            save(labels, "match_A.tif", 1, 1, 1, 1);
            save(labels, "match_B.tif", 5, 5, 5, 5);
            save(labels, "disjoint_A.tif", 1, 1, 0, 0);
            save(labels, "disjoint_B.tif", 0, 0, 5, 5);
            save(labels, "single_A.tif", 1);
            save(labels, "single_B.tif", 2);
        } else if ("errors-and-skips".equals(scenario)) {
            save(labels, "good_A.tif", 1, 1);
            save(labels, "good_B.tif", 2, 0);
            save(labels, "bad_A.tif", 1, 1);
            save(labels, "bad_B.tif", 2, 0, 0);
            save(labels, "lonely_A.tif", 1);
            for (int channel = 1; channel <= 6; channel++) {
                save(labels, "large_C" + channel + ".tif", channel);
            }
        } else if ("optional-group".equals(scenario)) {
            save(labels, "sample.tif", 0);
            save(labels, "sample_A.tif", 1);
            save(labels, "sample_B.tif", 2);
        } else if ("non-recursive".equals(scenario)) {
            save(labels, "root_A.tif", 1);
            save(labels, "root_B.tif", 2);
            File nested = new File(labels, "ignored");
            mkdirs(nested);
            save(nested, "nested_A.tif", 3);
            save(nested, "nested_B.tif", 4);
        } else {
            save(labels, "sample_A.tif", 1, 1, 0);
            save(labels, "sample_B.tif", 5, 0, 0);
            save(labels, "lonely_A.tif", 2);
        }
    }

    private static String captureRejections(File workspace) throws IOException {
        File root = new File(workspace, "rejections");
        File labels = new File(root, "labels");
        mkdirs(labels);
        File notDirectory = new File(root, "not-directory");
        Files.write(notDirectory.toPath(), Collections.singletonList("x"),
                StandardCharsets.UTF_8);

        StringBuilder out = new StringBuilder();
        reject(out, "null", root, new Action() {
            public void run() { VolColocBatchRunner.preview(null); }
        });
        reject(out, "missing-folder", root, new Action() {
            public void run() {
                VolColocBatchRunner.preview(VolColocBatchParameters.builder(
                        new File(root, "missing"), REGEX, 2).build());
            }
        });
        reject(out, "empty-regex", root, new Action() {
            public void run() {
                VolColocBatchRunner.preview(VolColocBatchParameters.builder(
                        labels, "  ", 2).build());
            }
        });
        reject(out, "invalid-regex", root, new Action() {
            public void run() {
                VolColocBatchRunner.preview(VolColocBatchParameters.builder(
                        labels, "([", 1).build());
            }
        });
        reject(out, "varying-zero", root, new Action() {
            public void run() {
                VolColocBatchRunner.preview(VolColocBatchParameters.builder(
                        labels, REGEX, 0).build());
            }
        });
        reject(out, "varying-too-large", root, new Action() {
            public void run() {
                VolColocBatchRunner.preview(VolColocBatchParameters.builder(
                        labels, REGEX, 3).build());
            }
        });
        reject(out, "detail-overlap", root, new Action() {
            public void run() {
                VolColocBatchRunner.preview(VolColocBatchParameters.builder(
                        labels, REGEX, 2).minimumDetailOverlapPercent(101).build());
            }
        });
        reject(out, "threshold", root, new Action() {
            public void run() {
                Map<String, Double> values = new LinkedHashMap<String, Double>();
                values.put("A", Double.valueOf(-1));
                VolColocBatchRunner.preview(VolColocBatchParameters.builder(
                        labels, REGEX, 2).thresholdsByChannel(values).build());
            }
        });
        reject(out, "save-file", root, new Action() {
            public void run() {
                VolColocBatchRunner.preview(VolColocBatchParameters.builder(
                        labels, REGEX, 2).saveDirectory(notDirectory).build());
            }
        });
        reject(out, "no-matches", root, new Action() {
            public void run() {
                VolColocBatchRunner.run(VolColocBatchParameters.builder(
                        labels, REGEX, 2).build());
            }
        });
        reject(out, "parsed-threshold", root, new Action() {
            public void run() { VolColocBatchRunner.parseThresholds("A=101"); }
        });
        return out.toString();
    }

    private static void reject(StringBuilder out, String name, File workspace,
                               Action action) {
        out.append(name).append(':');
        try {
            action.run();
            out.append("ACCEPTED\n");
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            if (message != null) {
                message = message.replace(workspace.getAbsolutePath(),
                        "<workspace>");
            }
            out.append(exception.getClass().getName()).append(':')
                    .append(message).append('\n');
        }
    }

    private static void appendCall(StringBuilder out, Call call) {
        try {
            String value = call.run();
            out.append(value);
            if (!value.endsWith("\n")) out.append('\n');
        } catch (RuntimeException exception) {
            appendException(out, exception);
        }
    }

    private static void appendException(StringBuilder out,
                                        RuntimeException exception) {
        out.append("THREW ").append(exception.getClass().getName())
                .append(':').append(exception.getMessage()).append('\n');
    }

    private static void appendTable(StringBuilder out, String name,
                                    ResultsTable table) {
        out.append("-- ").append(name).append(" rows=")
                .append(table == null ? -1 : table.getCounter()).append(" --\n");
        if (table == null) return;
        String[] headings = table.getHeadings();
        out.append(Arrays.toString(headings)).append('\n');
        for (int row = 0; row < table.getCounter(); row++) {
            out.append(row).append(':');
            for (String heading : headings) {
                out.append('|').append(escape(table.getStringValue(heading, row)));
            }
            out.append('\n');
        }
    }

    private static String tree(File root) throws IOException {
        List<File> files = new ArrayList<File>();
        collect(root, files);
        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                String leftPath = relativePath(root, left);
                String rightPath = relativePath(root, right);
                int insensitive = leftPath.compareToIgnoreCase(rightPath);
                return insensitive != 0
                        ? insensitive : leftPath.compareTo(rightPath);
            }
        });
        StringBuilder out = new StringBuilder();
        out.append("files=").append(files.size()).append('\n');
        for (File file : files) {
            String path = relativePath(root, file);
            out.append("--- ").append(path).append(" ---\n");
            out.append(new String(Files.readAllBytes(file.toPath()),
                    StandardCharsets.UTF_8));
            if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                out.append('\n');
            }
        }
        return out.toString();
    }

    private static String relativePath(File root, File file) {
        return root.toPath().relativize(file.toPath()).toString()
                .replace(File.separatorChar, '/');
    }

    private static void collect(File current, List<File> files) {
        File[] children = current.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) collect(child, files);
            else files.add(child);
        }
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\r", "\\r").replace("\n", "\\n")
                .replace("|", "\\|");
    }

    private static void save(File folder, String name, int... labels) {
        ShortProcessor processor = new ShortProcessor(labels.length, 1);
        for (int i = 0; i < labels.length; i++) processor.set(i, labels[i]);
        ImagePlus image = new ImagePlus(name, processor);
        IJ.saveAsTiff(image, new File(folder, name).getAbsolutePath());
        image.close();
    }

    private static void mkdirs(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create " + directory);
        }
    }

    private interface Call {
        String run();
    }

    private interface Action {
        void run();
    }
}
