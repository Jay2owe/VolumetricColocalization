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

import sc.fiji.volcoloc.core.OverlapResult;
import sc.fiji.oc3d.core.io.RegexGroupDiscovery;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Headless folder-batch API with a computation-free grouping preview.
 */
public final class VolColocBatchRunner {

    private VolColocBatchRunner() {
    }

    public static String preview(VolColocBatchParameters parameters) {
        Compiled compiled = compile(parameters);
        return previewGroups(discover(parameters, compiled.pattern));
    }

    public static VolColocBatchResult run(VolColocBatchParameters parameters) {
        Compiled compiled = compile(parameters);
        Map<String, Map<String, List<File>>> folders =
                discover(parameters, compiled.pattern);
        if (folders.isEmpty()) {
            throw new IllegalArgumentException(
                    "No files matched the batch regular expression.");
        }

        int total = 0;
        int runnable = 0;
        for (Map<String, List<File>> groups : folders.values()) {
            for (List<File> files : groups.values()) {
                total++;
                if (files.size() >= VolColocParameters.MIN_IMAGES
                        && files.size() <= VolColocParameters.MAX_IMAGES) {
                    runnable++;
                }
            }
        }

        int processed = 0;
        int skipped = 0;
        int errors = 0;
        ResultsTable batchSummary = new ResultsTable();
        ResultsTable batchMultiSummary = new ResultsTable();
        ResultsTable batchBoundingBoxSummary = new ResultsTable();
        File saveBase = parameters.getSaveDirectory() == null
                ? parameters.getInputFolder()
                : parameters.getSaveDirectory();
        File outputRoot = null;
        VolColocIO.OutputDirectories outputDirectories = null;
        Map<String, String> outputFolderNames =
                uniqueFolderNames(folders.keySet());
        if (parameters.isAutoSave()) {
            try {
                outputDirectories = VolColocIO.ensureTree(
                        new File(saveBase, VolColocIO.ROOT_DIRECTORY));
                outputRoot = outputDirectories.root;
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "Could not create batch output tree: "
                                + exception.getMessage(), exception);
            }
        }

        for (Map.Entry<String, Map<String, List<File>>> folder
                : folders.entrySet()) {
            Map<String, String> groupNames =
                    uniqueGroupNames(folder.getValue());
            for (Map.Entry<String, List<File>> group
                    : folder.getValue().entrySet()) {
                List<File> files = group.getValue();
                String groupName = groupNames.get(group.getKey());
                if (files.size() < VolColocParameters.MIN_IMAGES
                        || files.size() > VolColocParameters.MAX_IMAGES) {
                    skipped++;
                    IJ.log("Volumetric Colocalization batch: skipped "
                            + groupName + " (" + files.size()
                            + " images; expected 2-5).");
                    continue;
                }
                List<ImagePlus> opened = new ArrayList<ImagePlus>();
                VolColocResult result = null;
                try {
                    List<String> names = new ArrayList<String>();
                    List<Double> thresholds = new ArrayList<Double>();
                    List<Double> boundingBoxThresholds =
                            new ArrayList<Double>();
                    Set<String> uniqueNames = new HashSet<String>();
                    for (File file : files) {
                        ImagePlus image = IJ.openImage(file.getAbsolutePath());
                        if (image == null) {
                            throw new IOException("Could not open " + file);
                        }
                        opened.add(image);
                        String channel = channelName(
                                file, compiled.pattern, parameters.getVaryingGroup());
                        if (!uniqueNames.add(channel.toLowerCase(Locale.ROOT))) {
                            throw new IllegalArgumentException(
                                    "The batch group contains channel '" + channel
                                            + "' more than once.");
                        }
                        names.add(channel);
                        thresholds.add(Double.valueOf(thresholdFor(
                                channel,
                                parameters.getThresholdsByChannel(),
                                VolColocParameters.DEFAULT_THRESHOLD_PERCENT)));
                        boundingBoxThresholds.add(Double.valueOf(thresholdFor(
                                channel,
                                parameters.getBoundingBoxThresholdsByChannel(),
                                VolColocParameters.DEFAULT_BOUNDING_BOX_THRESHOLD_PERCENT)));
                    }

                    VolColocParameters analysisParameters =
                            VolColocParameters.builder(opened)
                                    .channelNames(names)
                                    .thresholdsPercent(thresholds)
                                    .boundingBoxThresholdsPercent(
                                            boundingBoxThresholds)
                                    .bidirectional(parameters.isBidirectional())
                                    .includePerObjectTables(
                                            parameters.isIncludePerObjectTables())
                                    .includeSummaryTable(
                                            parameters.isIncludeSummaryTable())
                                    .includePartnerDetails(
                                            parameters.isIncludePartnerDetails())
                                    .includeMultiColocalization(
                                            parameters.isIncludeMultiColocalization())
                                    .minimumDetailOverlapPercent(
                                            parameters.getMinimumDetailOverlapPercent())
                                    .includeBoundingBoxOverlap(
                                            parameters.isIncludeBoundingBoxOverlap())
                                    .includeBoundingBoxCpc(
                                            parameters.isIncludeBoundingBoxCpc())
                                    .includeBoundingBoxVolumeFill(
                                            parameters.isIncludeBoundingBoxVolumeFill())
                                    .build();
                    result = VolColoc.run(analysisParameters);
                } catch (Exception exception) {
                    errors++;
                    IJ.log("Volumetric Colocalization batch error in "
                            + groupName + ": "
                            + exception.getMessage());
                } finally {
                    for (ImagePlus image : opened) {
                        image.changes = false;
                        image.close();
                    }
                }
                if (result == null) continue;
                if (parameters.isAutoSave()) {
                    try {
                        VolColocIO.saveBatchGroup(
                                result, saveBase,
                                outputFolderNames.get(folder.getKey()),
                                groupName);
                    } catch (IOException exception) {
                        throw new IllegalStateException(
                                "Could not save batch group " + groupName
                                        + ": " + exception.getMessage(),
                                exception);
                    }
                }
                if (parameters.isIncludeSummaryTable()) {
                    appendSummary(batchSummary, result.getSummaryTable(),
                            folder.getKey(), groupName);
                }
                if (parameters.isIncludeMultiColocalization()) {
                    appendMultiSummary(batchMultiSummary, result,
                            folder.getKey(), groupName);
                }
                if (!result.getBoundingBoxDirectionResults().isEmpty()) {
                    appendSummary(
                            batchBoundingBoxSummary,
                            result.getBoundingBoxSummaryTable(),
                            folder.getKey(),
                            groupName);
                }
                processed++;
            }
        }

        ResultsTable folderSummary = batchSummary.getCounter() == 0
                ? new ResultsTable()
                : buildFolderSummary(batchSummary);
        ResultsTable folderMultiSummary =
                batchMultiSummary.getCounter() == 0
                        ? new ResultsTable()
                        : buildFolderMultiSummary(batchMultiSummary);
        ResultsTable folderBoundingBoxSummary =
                batchBoundingBoxSummary.getCounter() == 0
                        ? new ResultsTable()
                        : buildFolderBoundingBoxSummary(
                                batchBoundingBoxSummary);
        if (parameters.isAutoSave() && outputDirectories != null) {
            try {
                if (batchSummary.getCounter() > 0) {
                    VolColocIO.saveTable(batchSummary,
                            outputDirectories.folder,
                            "Batch_Summary.csv");
                    VolColocIO.saveTable(folderSummary,
                            outputDirectories.folder,
                            "Batch_Folder_Summary.csv");
                }
                if (batchMultiSummary.getCounter() > 0) {
                    VolColocIO.saveTable(batchMultiSummary,
                            outputDirectories.folder,
                            "Batch_Multi_Summary.csv");
                    VolColocIO.saveTable(folderMultiSummary,
                            outputDirectories.folder,
                            "Batch_Folder_Multi_Summary.csv");
                }
                if (batchBoundingBoxSummary.getCounter() > 0) {
                    VolColocIO.saveTable(
                            batchBoundingBoxSummary,
                            outputDirectories.folder,
                            "Batch_Bounding_Box_Summary.csv");
                    VolColocIO.saveTable(folderBoundingBoxSummary,
                            outputDirectories.folder,
                            "Batch_Folder_Bounding_Box_Summary.csv");
                }
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "Could not save aggregate batch tables: "
                                + exception.getMessage(), exception);
            }
        }
        return new VolColocBatchResult(
                total, runnable, processed, skipped, errors, outputRoot,
                batchSummary, folderSummary,
                batchMultiSummary, folderMultiSummary,
                batchBoundingBoxSummary, folderBoundingBoxSummary);
    }

    public static Map<String, Double> parseThresholds(String text) {
        Map<String, Double> result = new LinkedHashMap<String, Double>();
        Set<String> normalizedNames = new HashSet<String>();
        if (text == null || text.trim().length() == 0) return result;
        for (String token : text.split(",")) {
            String[] parts = token.trim().split("=", 2);
            if (parts.length != 2 || parts[0].trim().length() == 0) {
                throw new IllegalArgumentException(
                        "Batch thresholds must use channel=percent pairs.");
            }
            double value;
            try {
                value = Double.parseDouble(parts[1].trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "Invalid threshold for channel " + parts[0].trim() + ".");
            }
            if (!Double.isFinite(value) || value < 0.0 || value > 100.0) {
                throw new IllegalArgumentException(
                        "Threshold for channel " + parts[0].trim()
                                + " must be between 0 and 100.");
            }
            String channel = parts[0].trim();
            if (!normalizedNames.add(channel.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(
                        "Batch threshold channel appears more than once: " + channel);
            }
            result.put(channel, Double.valueOf(value));
        }
        return result;
    }

    /**
     * The shared CPC/OC3D folder chassis. VolColoc states its visible policy
     * explicitly: case-insensitive channel order, two-to-five runnable inputs,
     * and its own preview/table presentation.
     */
    private static Map<String, Map<String, List<File>>> discover(
            VolColocBatchParameters parameters, Pattern pattern) {
        File saveBase = parameters.getSaveDirectory() == null
                ? parameters.getInputFolder()
                : parameters.getSaveDirectory();
        Set<File> excluded = Collections.singleton(
                new File(saveBase, VolColocIO.ROOT_DIRECTORY));
        return RegexGroupDiscovery.findGroupsRecursive(
                parameters.getInputFolder(), pattern,
                parameters.getVaryingGroup(), parameters.isRecursive(),
                RegexGroupDiscovery.GroupOrder.FILENAME_IGNORE_CASE,
                excluded);
    }

    static String previewGroups(
            Map<String, Map<String, List<File>>> folders) {
        if (folders.isEmpty()) return "No matching files found.";
        int groups = 0;
        int runnable = 0;
        int files = 0;
        for (Map<String, List<File>> folder : folders.values()) {
            groups += folder.size();
            for (List<File> group : folder.values()) {
                files += group.size();
                if (group.size() >= 2 && group.size() <= 5) runnable++;
            }
        }
        StringBuilder preview = new StringBuilder();
        preview.append(folders.size()).append(" folder(s), ")
                .append(groups).append(" group(s), ")
                .append(runnable).append(" runnable, ")
                .append(files).append(" files\n\n");
        for (Map.Entry<String, Map<String, List<File>>> folder
                : folders.entrySet()) {
            preview.append(folder.getKey().length() == 0
                    ? "(root)\n" : folder.getKey() + "/\n");
            for (Map.Entry<String, List<File>> group
                    : folder.getValue().entrySet()) {
                int count = group.getValue().size();
                preview.append("  ").append(group.getKey())
                        .append(" (").append(count).append(")");
                if (count < 2 || count > 5) preview.append(" - SKIP");
                preview.append('\n');
                for (File file : group.getValue()) {
                    preview.append("    ").append(file.getName()).append('\n');
                }
            }
        }
        return preview.toString();
    }

    private static Compiled compile(VolColocBatchParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException(
                    "Batch parameters must not be null.");
        }
        if (parameters.getInputFolder() == null
                || !parameters.getInputFolder().isDirectory()) {
            throw new IllegalArgumentException(
                    "Batch input folder does not exist.");
        }
        if (parameters.getLabelRegex() == null
                || parameters.getLabelRegex().trim().length() == 0) {
            throw new IllegalArgumentException(
                    "Batch regular expression must not be empty.");
        }
        Pattern pattern;
        try {
            pattern = Pattern.compile(parameters.getLabelRegex());
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException(
                    "Invalid batch regular expression: "
                            + exception.getMessage(), exception);
        }
        int groups = pattern.matcher("").groupCount();
        if (parameters.getVaryingGroup() < 1
                || parameters.getVaryingGroup() > groups) {
            throw new IllegalArgumentException(
                    "varying_group must identify one of the regular expression's "
                            + groups + " capture groups.");
        }
        if (!Double.isFinite(parameters.getMinimumDetailOverlapPercent())
                || parameters.getMinimumDetailOverlapPercent() < 0.0
                || parameters.getMinimumDetailOverlapPercent() > 100.0) {
            throw new IllegalArgumentException(
                    "Minimum partner-detail overlap must be between 0 and 100 percent.");
        }
        for (Map.Entry<String, Double> threshold
                : parameters.getThresholdsByChannel().entrySet()) {
            double value = threshold.getValue().doubleValue();
            if (!Double.isFinite(value) || value < 0.0 || value > 100.0) {
                throw new IllegalArgumentException(
                        "Batch thresholds must be between 0 and 100.");
            }
        }
        for (Map.Entry<String, Double> threshold
                : parameters.getBoundingBoxThresholdsByChannel().entrySet()) {
            double value = threshold.getValue().doubleValue();
            if (!Double.isFinite(value) || value < 0.0 || value > 100.0) {
                throw new IllegalArgumentException(
                        "Batch bounding-box thresholds must be between 0 and 100.");
            }
        }
        File save = parameters.getSaveDirectory();
        if (save != null && save.exists() && !save.isDirectory()) {
            throw new IllegalArgumentException(
                    "Batch save path is not a directory: " + save);
        }
        return new Compiled(pattern);
    }

    private static String channelName(
            File file, Pattern pattern, int varyingGroup) {
        Matcher matcher = pattern.matcher(file.getName());
        if (!matcher.matches()) return file.getName();
        String channel = matcher.group(varyingGroup);
        return channel == null ? file.getName() : channel;
    }

    private static double thresholdFor(
            String channel, Map<String, Double> thresholds,
            double defaultValue) {
        Double exact = thresholds.get(channel);
        if (exact != null) return exact.doubleValue();
        for (Map.Entry<String, Double> entry : thresholds.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT)
                    .equals(channel.toLowerCase(Locale.ROOT))) {
                return entry.getValue().doubleValue();
            }
        }
        return defaultValue;
    }

    private static String displayGroup(String key) {
        String value = key.replace("*", "");
        value = value.replaceAll("\\.[^.]+$", "");
        value = value.replaceAll("^[_\\-./\\\\]+|[_\\-./\\\\]+$", "");
        return value.length() == 0 ? "batch" : value;
    }

    private static Map<String, String> uniqueGroupNames(
            Map<String, List<File>> groups) {
        Map<String, String> names = new LinkedHashMap<String, String>();
        Set<String> used = reservedFolderEntryNames();
        for (String key : groups.keySet()) {
            String base = displayGroup(key);
            String candidate = base;
            int suffix = 2;
            while (!used.add(VolColocIO.safeName(candidate)
                    .toLowerCase(Locale.ROOT))) {
                candidate = base + "_" + suffix++;
            }
            names.put(key, candidate);
        }
        return names;
    }

    private static Map<String, String> uniqueFolderNames(
            Set<String> folders) {
        Map<String, String> names = new LinkedHashMap<String, String>();
        Set<String> used = reservedFolderEntryNames();
        for (String folder : folders) {
            if (folder.length() == 0) {
                names.put(folder, "");
                continue;
            }
            String base = safeRelativeFolder(folder);
            String candidate = base;
            int suffix = 2;
            while (!used.add(candidate.toLowerCase(Locale.ROOT))) {
                int slash = base.lastIndexOf('/');
                String parent = slash < 0 ? "" : base.substring(0, slash + 1);
                String leaf = slash < 0 ? base : base.substring(slash + 1);
                // Normalise the disambiguated leaf here, so what is reserved is
                // exactly what saveBatchGroup will put on disk. Without this a
                // long leaf is re-shortened at save time and two folders that
                // looked distinct here collide there.
                candidate = parent + VolColocIO.safeName(leaf + "_" + suffix++);
            }
            names.put(folder, candidate);
        }
        return names;
    }

    private static String safeRelativeFolder(String folder) {
        StringBuilder result = new StringBuilder();
        for (String part : folder.replace('\\', '/').split("/")) {
            if (part.length() == 0) continue;
            if (result.length() > 0) result.append('/');
            String safe = VolColocIO.safeName(part);
            if (result.length() == 0
                    && isReservedFolderEntry(safe)) {
                safe += "_folder";
            }
            result.append(safe);
        }
        return result.toString();
    }

    private static Set<String> reservedFolderEntryNames() {
        Set<String> names = new HashSet<String>();
        names.add("readme.txt");
        names.add("batch_summary.csv");
        names.add("batch_folder_summary.csv");
        names.add("batch_multi_summary.csv");
        names.add("batch_folder_multi_summary.csv");
        names.add("batch_bounding_box_summary.csv");
        names.add("batch_folder_bounding_box_summary.csv");
        return names;
    }

    private static boolean isReservedFolderEntry(String name) {
        return reservedFolderEntryNames().contains(
                name.toLowerCase(Locale.ROOT));
    }

    private static void appendSummary(ResultsTable aggregate,
                                      ResultsTable source,
                                      String folder, String group) {
        String[] headings = VolColocIO.columnHeadings(source);
        for (int row = 0; row < source.getCounter(); row++) {
            int targetRow = aggregate.getCounter();
            aggregate.incrementCounter();
            aggregate.setValue("Folder", targetRow,
                    folder.length() == 0 ? "(root)" : folder);
            aggregate.setValue("Group", targetRow, group);
            for (String heading : headings) {
                double numeric = source.getValue(heading, row);
                if (Double.isNaN(numeric)) {
                    aggregate.setValue(
                            heading, targetRow, source.getStringValue(heading, row));
                } else {
                    aggregate.setValue(heading, targetRow, numeric);
                }
            }
        }
    }

    private static void appendMultiSummary(
            ResultsTable aggregate, VolColocResult result,
            String folder, String group) {
        for (OverlapResult.MultiChannelResult multi
                : result.getMultiChannelResults()) {
            String targets = targetSet(
                    result.getChannelNames(), multi.getSourceIndex());
            for (OverlapResult.PatternSummary pattern
                    : multi.getPatterns()) {
                int row = aggregate.getCounter();
                aggregate.incrementCounter();
                aggregate.setValue("Folder", row,
                        folder.length() == 0 ? "(root)" : folder);
                aggregate.setValue("Group", row, group);
                aggregate.setValue(
                        "Source", row, multi.getSourceChannel());
                aggregate.setValue("Target Channels", row, targets);
                aggregate.setValue("Pattern", row, pattern.getPattern());
                aggregate.setValue("Count", row, pattern.getObjectCount());
                aggregate.setValue(
                        "% of Source", row, pattern.getObjectPercent());
            }
        }
    }

    private static String targetSet(
            List<String> channels, int sourceIndex) {
        List<String> targets = new ArrayList<String>();
        for (int i = 0; i < channels.size(); i++) {
            if (i != sourceIndex) targets.add(channels.get(i));
        }
        Collections.sort(targets, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                int insensitive = left.compareToIgnoreCase(right);
                return insensitive != 0 ? insensitive : left.compareTo(right);
            }
        });
        StringBuilder result = new StringBuilder("[");
        for (String target : targets) {
            if (result.length() > 1) result.append(", ");
            result.append('"')
                    .append(target.replace("\\", "\\\\")
                            .replace("\"", "\\\""))
                    .append('"');
        }
        return result.append(']').toString();
    }

    private static ResultsTable buildFolderSummary(ResultsTable source) {
        Map<String, FolderPairAggregate> aggregates =
                new LinkedHashMap<String, FolderPairAggregate>();
        for (int row = 0; row < source.getCounter(); row++) {
            String folder = source.getStringValue("Folder", row);
            String sourceChannel = source.getStringValue("Source Channel", row);
            String targetChannel = source.getStringValue("Partner Channel", row);
            double threshold = source.getValue("Threshold %", row);
            String key = folder + "\u0000" + sourceChannel + "\u0000"
                    + targetChannel + "\u0000" + threshold;
            FolderPairAggregate aggregate = aggregates.get(key);
            if (aggregate == null) {
                aggregate = new FolderPairAggregate(
                        folder, sourceChannel, targetChannel, threshold);
                aggregates.put(key, aggregate);
            }
            int objects = (int) Math.round(source.getValue("Object Count", row));
            aggregate.objects += objects;
            aggregate.weightedOverlap +=
                    source.getValue("Mean Overlap %", row) * objects;
            aggregate.colocalized += (int) Math.round(
                    source.getValue("Colocalized Count", row));
        }

        ResultsTable result = new ResultsTable();
        for (FolderPairAggregate aggregate : aggregates.values()) {
            int row = result.getCounter();
            result.incrementCounter();
            result.setValue("Folder", row, aggregate.folder);
            result.setValue("Source Channel", row, aggregate.sourceChannel);
            result.setValue("Partner Channel", row, aggregate.targetChannel);
            result.setValue("Direction", row,
                    aggregate.sourceChannel + " -> " + aggregate.targetChannel);
            result.setValue("Object Count", row, aggregate.objects);
            result.setValue("Mean Overlap %", row,
                    aggregate.objects == 0 ? 0.0
                            : aggregate.weightedOverlap / aggregate.objects);
            result.setValue("Threshold %", row, aggregate.threshold);
            result.setValue("Colocalized Count", row, aggregate.colocalized);
            result.setValue("Colocalized %", row,
                    aggregate.objects == 0 ? 0.0
                            : aggregate.colocalized * 100.0 / aggregate.objects);
        }
        return result;
    }

    private static ResultsTable buildFolderMultiSummary(ResultsTable source) {
        Map<String, Integer> patternCounts =
                new LinkedHashMap<String, Integer>();
        Map<String, Integer> sourceTotals =
                new LinkedHashMap<String, Integer>();
        Map<String, String[]> metadata =
                new LinkedHashMap<String, String[]>();
        for (int row = 0; row < source.getCounter(); row++) {
            String folder = source.getStringValue("Folder", row);
            String sourceChannel = source.getStringValue("Source", row);
            String targetChannels =
                    source.getStringValue("Target Channels", row);
            String pattern = source.getStringValue("Pattern", row);
            int count = (int) Math.round(source.getValue("Count", row));
            String sourceKey = folder + "\u0000" + sourceChannel
                    + "\u0000" + targetChannels;
            String patternKey = sourceKey + "\u0000" + pattern;
            Integer previousPattern = patternCounts.get(patternKey);
            patternCounts.put(patternKey, Integer.valueOf(
                    (previousPattern == null ? 0 : previousPattern.intValue()) + count));
            if (!MultiTargetSummary.ANY_PATTERN.equals(pattern)) {
                Integer previousTotal = sourceTotals.get(sourceKey);
                sourceTotals.put(sourceKey, Integer.valueOf(
                        (previousTotal == null ? 0 : previousTotal.intValue()) + count));
            }
            metadata.put(patternKey, new String[]{
                    folder, sourceChannel, targetChannels, pattern, sourceKey});
        }

        // Emit one contiguous block per source, each ending None then
        // — Any —, so the folder aggregate reads the same way as the
        // per-image summary it aggregates.
        Map<String, List<String>> bySource =
                new LinkedHashMap<String, List<String>>();
        for (String patternKey : patternCounts.keySet()) {
            String sourceKey = metadata.get(patternKey)[4];
            List<String> keys = bySource.get(sourceKey);
            if (keys == null) {
                keys = new ArrayList<String>();
                bySource.put(sourceKey, keys);
            }
            keys.add(patternKey);
        }

        ResultsTable result = new ResultsTable();
        for (Map.Entry<String, List<String>> group : bySource.entrySet()) {
            List<String> ordered = new ArrayList<String>();
            String noHits = null;
            String any = null;
            for (String patternKey : group.getValue()) {
                String pattern = metadata.get(patternKey)[3];
                if (MultiTargetSummary.NO_HITS_PATTERN.equals(pattern)) {
                    noHits = patternKey;
                } else if (MultiTargetSummary.ANY_PATTERN.equals(pattern)) {
                    any = patternKey;
                } else {
                    ordered.add(patternKey);
                }
            }
            if (noHits != null) ordered.add(noHits);
            if (any != null) ordered.add(any);

            for (String patternKey : ordered) {
                String[] values = metadata.get(patternKey);
                int count = patternCounts.get(patternKey).intValue();
                Integer sourceTotal = sourceTotals.get(values[4]);
                int total = sourceTotal == null ? 0 : sourceTotal.intValue();
                int row = result.getCounter();
                result.incrementCounter();
                result.setValue("Folder", row, values[0]);
                result.setValue("Source", row, values[1]);
                result.setValue("Target Channels", row, values[2]);
                result.setValue("Pattern", row, values[3]);
                result.setValue("Count", row, count);
                result.setValue("% of Source", row,
                        roundedPercent(count, total));
            }
        }
        return result;
    }

    private static double roundedPercent(int numerator, int denominator) {
        if (denominator == 0) return 0.0;
        return Math.round(numerator * 10000.0 / denominator) / 100.0;
    }

    private static ResultsTable buildFolderBoundingBoxSummary(
            ResultsTable source) {
        Map<String, FolderBoundingAggregate> aggregates =
                new LinkedHashMap<String, FolderBoundingAggregate>();
        for (int row = 0; row < source.getCounter(); row++) {
            String folder = source.getStringValue("Folder", row);
            String sourceChannel =
                    source.getStringValue("Source Channel", row);
            String targetChannel =
                    source.getStringValue("Partner Channel", row);
            String method = source.getStringValue("Method", row);
            double threshold = source.getValue("Threshold %", row);
            String key = folder + "\u0000" + sourceChannel + "\u0000"
                    + targetChannel + "\u0000" + method + "\u0000"
                    + threshold;
            FolderBoundingAggregate aggregate = aggregates.get(key);
            if (aggregate == null) {
                aggregate = new FolderBoundingAggregate(
                        folder, sourceChannel, targetChannel,
                        method, threshold);
                aggregates.put(key, aggregate);
            }
            int objects =
                    (int) Math.round(source.getValue("Object Count", row));
            aggregate.objects += objects;
            aggregate.weightedMean +=
                    source.getValue("Mean %", row) * objects;
            aggregate.positives += (int) Math.round(
                    source.getValue("Positive Count", row));
            double totalFill = source.getValue("Mean Total Fill %", row);
            if (!Double.isNaN(totalFill)) {
                aggregate.weightedTotalFill += totalFill * objects;
                aggregate.hasTotalFill = true;
            }
        }
        ResultsTable result = new ResultsTable();
        for (FolderBoundingAggregate aggregate : aggregates.values()) {
            int row = result.getCounter();
            result.incrementCounter();
            result.setValue("Folder", row, aggregate.folder);
            result.setValue(
                    "Source Channel", row, aggregate.sourceChannel);
            result.setValue(
                    "Partner Channel", row, aggregate.targetChannel);
            result.setValue("Direction", row,
                    aggregate.sourceChannel + " -> "
                            + aggregate.targetChannel);
            result.setValue("Method", row, aggregate.method);
            result.setValue("Object Count", row, aggregate.objects);
            result.setValue("Mean %", row,
                    aggregate.objects == 0 ? 0.0
                            : aggregate.weightedMean / aggregate.objects);
            result.setValue("Threshold %", row, aggregate.threshold);
            result.setValue(
                    "Positive Count", row, aggregate.positives);
            result.setValue("Positive %", row,
                    aggregate.objects == 0 ? 0.0
                            : aggregate.positives * 100.0
                                    / aggregate.objects);
            result.setValue("Mean Total Fill %", row,
                    !aggregate.hasTotalFill || aggregate.objects == 0
                            ? Double.NaN
                            : aggregate.weightedTotalFill
                                    / aggregate.objects);
        }
        return result;
    }

    private static final class FolderPairAggregate {
        final String folder;
        final String sourceChannel;
        final String targetChannel;
        final double threshold;
        int objects;
        int colocalized;
        double weightedOverlap;

        FolderPairAggregate(String folder, String sourceChannel,
                            String targetChannel, double threshold) {
            this.folder = folder;
            this.sourceChannel = sourceChannel;
            this.targetChannel = targetChannel;
            this.threshold = threshold;
        }
    }

    private static final class FolderBoundingAggregate {
        final String folder;
        final String sourceChannel;
        final String targetChannel;
        final String method;
        final double threshold;
        int objects;
        int positives;
        double weightedMean;
        double weightedTotalFill;
        boolean hasTotalFill;

        FolderBoundingAggregate(
                String folder, String sourceChannel, String targetChannel,
                String method, double threshold) {
            this.folder = folder;
            this.sourceChannel = sourceChannel;
            this.targetChannel = targetChannel;
            this.method = method;
            this.threshold = threshold;
        }
    }

    private static final class Compiled {
        final Pattern pattern;

        Compiled(Pattern pattern) {
            this.pattern = pattern;
        }
    }
}
