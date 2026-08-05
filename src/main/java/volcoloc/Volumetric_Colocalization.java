/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package volcoloc;

import sc.fiji.volcoloc.core.OverlapResult;

import ij.IJ;
import ij.ImagePlus;
import ij.Macro;
import ij.WindowManager;
import ij.gui.YesNoCancelDialog;
import ij.plugin.PlugIn;
import ij.plugin.frame.Recorder;
import ij.text.TextWindow;
import volcoloc.ui.VolColocDialog;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ImageJ 1.x entry point for interactive, macro, and folder-batch workflows.
 */
public class Volumetric_Colocalization implements PlugIn {

    private static final String TITLE = "Volumetric Colocalization";
    static final boolean DEFAULT_AUTO_SAVE = false;
    private static final String NONE = "(none)";
    private static final String[] LETTERS = {"A", "B", "C", "D", "E"};
    @Override
    public void run(String argument) {
        String optionsText = Macro.getOptions();
        if (!hasText(optionsText) && hasText(argument)) optionsText = argument;
        try {
            if (hasText(optionsText) || GraphicsEnvironment.isHeadless()) {
                runMacro(optionsText == null ? "" : optionsText);
            } else {
                runInteractive();
            }
        } catch (Exception exception) {
            IJ.log(TITLE + ": " + exception.getMessage());
            if (GraphicsEnvironment.isHeadless()) {
                throw exception instanceof RuntimeException
                        ? (RuntimeException) exception
                        : new IllegalStateException(exception);
            }
            IJ.error(TITLE, exception.getMessage());
        }
    }

    private void runInteractive() throws Exception {
        VolColocDialog modeDialog = new VolColocDialog(TITLE);
        modeDialog.addHeader("Input");
        modeDialog.addChoice("Input mode",
                new String[]{"Label Images", "ROI Sets", "Folder Batch"},
                "Label Images");
        modeDialog.addHelpText("Label images and ROI sets analyse one image group. "
                + "Folder Batch parses 2-5 channel files per group.");
        modeDialog.showDialog();
        if (modeDialog.wasCanceled()) return;
        String mode = modeDialog.getNextChoice();
        if ("ROI Sets".equals(mode)) {
            runRoiDialog();
        } else if ("Folder Batch".equals(mode)) {
            runBatchDialog();
        } else {
            runLabelDialog();
        }
    }

    private void runLabelDialog() throws Exception {
        String[] openTitles = openImageTitles();
        String[] choices = withNone(openTitles);
        VolColocDialog dialog = new VolColocDialog(TITLE);
        dialog.addHeader("Input");
        for (int i = 0; i < VolColocMacroOptions.MAX_IMAGES; i++) {
            String defaultChoice = i < openTitles.length && i < 2
                    ? openTitles[i] : NONE;
            dialog.addChoice("Image " + LETTERS[i], choices, defaultChoice);
            dialog.addFileField("or image file " + LETTERS[i], "");
            dialog.addStringField("Channel name " + LETTERS[i], "", 20);
            dialog.addNumericField("Threshold " + LETTERS[i] + " (%)",
                    VolColocParameters.DEFAULT_THRESHOLD_PERCENT, 2);
        }
        addAnalysisAndOutputFields(dialog);
        dialog.showDialog();
        if (dialog.wasCanceled()) return;

        VolColocMacroOptions options = new VolColocMacroOptions();
        options.setMode(VolColocMacroOptions.InputMode.LABELS);
        for (int i = 0; i < VolColocMacroOptions.MAX_IMAGES; i++) {
            options.setImageTitle(i, noneToNull(dialog.getNextChoice()));
            options.setImagePath(i, dialog.getNextString());
            options.setChannelName(i, dialog.getNextString());
            options.setThresholdPercent(i, dialog.getNextNumber());
        }
        readAnalysisAndOutputFields(dialog, options);
        options.validate();
        record(options);
        runSingle(options);
    }

    private void runRoiDialog() throws Exception {
        String[] openTitles = openImageTitles();
        String[] choices = withNone(openTitles);
        VolColocDialog dialog = new VolColocDialog(TITLE);
        dialog.addHeader("Input");
        dialog.addChoice("Reference image", choices,
                openTitles.length == 0 ? NONE : openTitles[0]);
        dialog.addFileField("or reference file", "");
        for (int i = 0; i < VolColocMacroOptions.MAX_IMAGES; i++) {
            dialog.addFileField("ROI set " + LETTERS[i], "");
            dialog.addStringField("Channel name " + LETTERS[i], "", 20);
            dialog.addNumericField("Threshold " + LETTERS[i] + " (%)",
                    VolColocParameters.DEFAULT_THRESHOLD_PERCENT, 2);
        }
        addAnalysisAndOutputFields(dialog);
        dialog.showDialog();
        if (dialog.wasCanceled()) return;

        VolColocMacroOptions options = new VolColocMacroOptions();
        options.setMode(VolColocMacroOptions.InputMode.ROIS);
        options.setReferenceTitle(noneToNull(dialog.getNextChoice()));
        options.setReferencePath(dialog.getNextString());
        for (int i = 0; i < VolColocMacroOptions.MAX_IMAGES; i++) {
            options.setRoiPath(i, dialog.getNextString());
            options.setChannelName(i, dialog.getNextString());
            options.setThresholdPercent(i, dialog.getNextNumber());
        }
        readAnalysisAndOutputFields(dialog, options);
        options.validate();
        record(options);
        runSingle(options);
    }

    private void addAnalysisAndOutputFields(VolColocDialog dialog) {
        dialog.addHeader("Analysis");
        dialog.addToggle("Bidirectional", true);
        dialog.addNumericField(
                "Minimum source overlap (%) for partner rows", 50, 1);
        dialog.addHelpText("Thresholds classify total occupied source volume. "
                + "The continuous percentage is always retained.");
        dialog.beginCollapsibleSection(
                "Bounding-box analyses (optional)", false);
        dialog.addToggle("Bounding-box overlap (% / BBColoc)", false);
        dialog.addToggle("Bounding-box centroid coincidence (BB-CPC)", false);
        dialog.addToggle("Bounding-box volume fill (BBVolColoc)", false);
        dialog.addHelpText("BBColoc compares object boxes; BB-CPC tests "
                + "centroids against boxes; BBVolColoc measures partner voxels "
                + "filling each source box.");
        for (int i = 0; i < VolColocMacroOptions.MAX_IMAGES; i++) {
            dialog.addNumericField(
                    "BB threshold " + LETTERS[i] + " (%)",
                    VolColocParameters.DEFAULT_BOUNDING_BOX_THRESHOLD_PERCENT,
                    2);
        }
        dialog.endGroup();
        dialog.addHeader("Output");
        dialog.addToggle("Per-object tables", true);
        dialog.addToggle("Summary table", true);
        dialog.addToggle("All-partner detail", true);
        dialog.addToggle("Multi-colocalization patterns", true);
        dialog.addToggle("Auto-save", DEFAULT_AUTO_SAVE);
        dialog.addDirectoryField("Save directory", "");
    }

    private void readAnalysisAndOutputFields(VolColocDialog dialog,
                                             VolColocMacroOptions options) {
        options.setBidirectional(dialog.getNextBoolean());
        options.setMinimumOverlapPercent(dialog.getNextNumber());
        options.setBoundingBoxOverlap(dialog.getNextBoolean());
        options.setBoundingBoxCpc(dialog.getNextBoolean());
        options.setBoundingBoxVolumeFill(dialog.getNextBoolean());
        for (int i = 0; i < VolColocMacroOptions.MAX_IMAGES; i++) {
            options.setBoundingBoxThresholdPercent(
                    i, dialog.getNextNumber());
        }
        options.setPerObjectTables(dialog.getNextBoolean());
        options.setSummaryTable(dialog.getNextBoolean());
        options.setPartnerDetails(dialog.getNextBoolean());
        options.setMultiColocalization(dialog.getNextBoolean());
        options.setAutoSave(dialog.getNextBoolean());
        options.setSaveDir(dialog.getNextString());
    }

    private void runBatchDialog() {
        VolColocDialog dialog = new VolColocDialog(TITLE + " - Folder Batch");
        dialog.addHeader("Input");
        dialog.addDirectoryField("Label folder", "");
        dialog.addStringField("Filename regular expression",
                "(.+)_([^_]+)[.](?:tif|tiff)$", 45);
        dialog.addNumericField("Channel capture group", 2, 0);
        dialog.addToggle("Search subfolders", true);
        dialog.addStringField("Channel thresholds (name=%, comma-separated)",
                "", 45);
        dialog.addHelpText("Unlisted channels use 30%. Example: DAPI=20,GFAP=40");
        dialog.addHeader("Analysis");
        dialog.addToggle("Bidirectional", true);
        dialog.addNumericField(
                "Minimum source overlap (%) for partner rows", 50, 1);
        dialog.beginCollapsibleSection(
                "Bounding-box analyses (optional)", false);
        dialog.addToggle("Bounding-box overlap (% / BBColoc)", false);
        dialog.addToggle("Bounding-box centroid coincidence (BB-CPC)", false);
        dialog.addToggle("Bounding-box volume fill (BBVolColoc)", false);
        dialog.addStringField(
                "BB thresholds (name=%, comma-separated)", "", 45);
        dialog.addHelpText(
                "Unlisted channels use 30%. Example: DAPI=25,GFAP=40");
        dialog.endGroup();
        dialog.addHeader("Output");
        dialog.addToggle("Per-object tables", true);
        dialog.addToggle("Summary table", true);
        dialog.addToggle("All-partner detail", true);
        dialog.addToggle("Multi-colocalization patterns", true);
        dialog.addToggle("Auto-save", DEFAULT_AUTO_SAVE);
        dialog.addDirectoryField("Save directory (blank = label folder)", "");
        dialog.showDialog();
        if (dialog.wasCanceled()) return;

        VolColocMacroOptions options = new VolColocMacroOptions();
        options.setMode(VolColocMacroOptions.InputMode.BATCH);
        options.setBatchFolder(dialog.getNextString());
        options.setBatchRegex(dialog.getNextString());
        options.setVaryingGroup((int) Math.round(dialog.getNextNumber()));
        options.setRecursive(dialog.getNextBoolean());
        options.setBatchThresholds(dialog.getNextString());
        options.setBidirectional(dialog.getNextBoolean());
        options.setMinimumOverlapPercent(dialog.getNextNumber());
        options.setBoundingBoxOverlap(dialog.getNextBoolean());
        options.setBoundingBoxCpc(dialog.getNextBoolean());
        options.setBoundingBoxVolumeFill(dialog.getNextBoolean());
        options.setBatchBoundingBoxThresholds(dialog.getNextString());
        options.setPerObjectTables(dialog.getNextBoolean());
        options.setSummaryTable(dialog.getNextBoolean());
        options.setPartnerDetails(dialog.getNextBoolean());
        options.setMultiColocalization(dialog.getNextBoolean());
        options.setAutoSave(dialog.getNextBoolean());
        options.setSaveDir(dialog.getNextString());
        options.validate();

        VolColocBatchParameters parameters = batchParameters(options);
        String preview = VolColocBatchRunner.preview(parameters);
        new TextWindow(TITLE + " - Batch Preview", preview, 820, 600);
        YesNoCancelDialog confirm = new YesNoCancelDialog(
                IJ.getInstance(), TITLE,
                "The parsed groups are shown in the preview window.\n"
                        + "Run this batch now?");
        if (!confirm.yesPressed()) return;
        record(options);
        runBatch(parameters, false);
    }

    private void runMacro(String optionsText) throws Exception {
        if (!hasText(optionsText)) {
            throw new IllegalArgumentException(
                    "Headless use requires ImageJ macro options.");
        }
        VolColocMacroOptions options =
                VolColocMacroOptionsParser.parse(optionsText);
        if (options.getMode() == VolColocMacroOptions.InputMode.BATCH) {
            VolColocBatchParameters parameters = batchParameters(options);
            IJ.log(TITLE + " batch preview:\n"
                    + VolColocBatchRunner.preview(parameters));
            runBatch(parameters, options.isHideDisplay());
        } else {
            runSingle(options);
        }
    }

    private void runSingle(VolColocMacroOptions options) throws Exception {
        ResolvedInputs resolved = options.getMode()
                == VolColocMacroOptions.InputMode.ROIS
                ? resolveRoiInputs(options)
                : resolveLabelInputs(options);
        try {
            VolColocParameters parameters =
                    VolColocParameters.builder(resolved.images)
                            .channelNames(resolved.names)
                            .thresholdsPercent(resolved.thresholds)
                            .boundingBoxThresholdsPercent(
                                    resolved.boundingBoxThresholds)
                            .bidirectional(options.isBidirectional())
                            .includePerObjectTables(options.isPerObjectTables())
                            .includeSummaryTable(options.isSummaryTable())
                            .includePartnerDetails(options.isPartnerDetails())
                            .includeMultiColocalization(
                                    options.isMultiColocalization())
                            .minimumDetailOverlapPercent(
                                    options.getMinimumOverlapPercent())
                            .includeBoundingBoxOverlap(
                                    options.isBoundingBoxOverlap())
                            .includeBoundingBoxCpc(options.isBoundingBoxCpc())
                            .includeBoundingBoxVolumeFill(
                                    options.isBoundingBoxVolumeFill())
                            .build();
            IJ.showStatus(TITLE + ": analysing...");
            VolColocResult result = VolColoc.run(parameters);
            // Display before saving: a failed save must not also cost the user
            // the result tables of a completed analysis.
            if (!options.isHideDisplay()) display(result);
            if (options.isAutoSave()) {
                File base = saveBase(options, resolved);
                File root = VolColocIO.save(
                        result, base, filePrefix(resolved.names));
                IJ.log(TITLE + ": saved results to " + root);
            }
            IJ.showStatus(TITLE + ": complete.");
        } finally {
            resolved.closeOwnedImages();
        }
    }

    private void runBatch(VolColocBatchParameters parameters,
                          boolean hideDisplay) {
        IJ.showStatus(TITLE + ": running folder batch...");
        VolColocBatchResult result = VolColocBatchRunner.run(parameters);
        String message = result.getProcessedGroups() + " processed, "
                + result.getSkippedGroups() + " skipped, "
                + result.getErrorGroups() + " error(s).";
        IJ.log(TITLE + " batch complete: " + message);
        IJ.showStatus(TITLE + ": batch complete.");
        if (!GraphicsEnvironment.isHeadless() && !hideDisplay) {
            displayBatch(result);
            IJ.showMessage(TITLE, message
                    + (result.getOutputDirectory() == null
                    ? "" : "\n\nSaved to:\n" + result.getOutputDirectory()));
        }
    }

    private static void displayBatch(VolColocBatchResult result) {
        if (result.getSummaryTable().getCounter() > 0) {
            result.getSummaryTable().show(TITLE + " - Batch Summary");
            result.getFolderSummaryTable().show(
                    TITLE + " - Batch Folder Summary");
        }
        if (result.getMultiSummaryTable().getCounter() > 0) {
            result.getMultiSummaryTable().show(
                    TITLE + " - Batch Multi Summary");
            result.getFolderMultiSummaryTable().show(
                    TITLE + " - Batch Folder Multi Summary");
        }
        if (result.getBoundingBoxSummaryTable().getCounter() > 0) {
            result.getBoundingBoxSummaryTable().show(
                    TITLE + " - Batch Bounding Box Summary");
            result.getFolderBoundingBoxSummaryTable().show(
                    TITLE + " - Batch Folder Bounding Box Summary");
        }
    }

    private VolColocBatchParameters batchParameters(
            VolColocMacroOptions options) {
        Map<String, Double> thresholds =
                VolColocBatchRunner.parseThresholds(options.getBatchThresholds());
        Map<String, Double> boundingBoxThresholds =
                VolColocBatchRunner.parseThresholds(
                        options.getBatchBoundingBoxThresholds());
        File input = new File(options.getBatchFolder());
        File save = hasText(options.getSaveDir())
                ? new File(options.getSaveDir()) : input;
        return VolColocBatchParameters.builder(
                        input, options.getBatchRegex(), options.getVaryingGroup())
                .recursive(options.isRecursive())
                .thresholdsByChannel(thresholds)
                .boundingBoxThresholdsByChannel(boundingBoxThresholds)
                .bidirectional(options.isBidirectional())
                .includePerObjectTables(options.isPerObjectTables())
                .includeSummaryTable(options.isSummaryTable())
                .includePartnerDetails(options.isPartnerDetails())
                .includeMultiColocalization(options.isMultiColocalization())
                .minimumDetailOverlapPercent(options.getMinimumOverlapPercent())
                .includeBoundingBoxOverlap(options.isBoundingBoxOverlap())
                .includeBoundingBoxCpc(options.isBoundingBoxCpc())
                .includeBoundingBoxVolumeFill(
                        options.isBoundingBoxVolumeFill())
                .autoSave(options.isAutoSave())
                .saveDirectory(save)
                .build();
    }

    private ResolvedInputs resolveLabelInputs(
            VolColocMacroOptions options) {
        ResolvedInputs resolved = new ResolvedInputs();
        boolean gap = false;
        for (int i = 0; i < VolColocMacroOptions.MAX_IMAGES; i++) {
            boolean selected = hasText(options.getImageTitle(i))
                    || hasText(options.getImagePath(i));
            if (!selected) {
                gap = resolved.images.size() > 0;
                continue;
            }
            if (gap) {
                resolved.closeOwnedImages();
                throw new IllegalArgumentException(
                        "Fill image slots in order without gaps.");
            }
            ImagePlus image;
            if (hasText(options.getImagePath(i))) {
                image = IJ.openImage(options.getImagePath(i));
                if (image != null) resolved.owned.add(image);
            } else {
                image = WindowManager.getImage(options.getImageTitle(i));
            }
            if (image == null) {
                resolved.closeOwnedImages();
                throw new IllegalArgumentException(
                        "Could not resolve image " + LETTERS[i] + ".");
            }
            resolved.add(image, options.getChannelName(i),
                    options.getThresholdPercent(i),
                    options.getBoundingBoxThresholdPercent(i));
        }
        requireImageCount(resolved);
        return resolved;
    }

    private ResolvedInputs resolveRoiInputs(
            VolColocMacroOptions options) throws Exception {
        ResolvedInputs resolved = new ResolvedInputs();
        try {
            ImagePlus reference;
            if (hasText(options.getReferencePath())) {
                reference = IJ.openImage(options.getReferencePath());
                if (reference != null) resolved.owned.add(reference);
            } else {
                reference = WindowManager.getImage(options.getReferenceTitle());
            }
            if (reference == null) {
                throw new IllegalArgumentException(
                        "Could not resolve the ROI reference image.");
            }
            boolean gap = false;
            for (int i = 0; i < VolColocMacroOptions.MAX_IMAGES; i++) {
                if (!hasText(options.getRoiPath(i))) {
                    gap = resolved.images.size() > 0;
                    continue;
                }
                if (gap) {
                    throw new IllegalArgumentException(
                            "Fill ROI slots in order without gaps.");
                }
                ImagePlus labels = VolColocLabelImages.fromRoiSetFile(
                        reference, options.getRoiPath(i));
                resolved.owned.add(labels);
                resolved.add(labels, options.getChannelName(i),
                        options.getThresholdPercent(i),
                        options.getBoundingBoxThresholdPercent(i));
            }
            requireImageCount(resolved);
            return resolved;
        } catch (Exception exception) {
            resolved.closeOwnedImages();
            throw exception;
        }
    }

    private void requireImageCount(ResolvedInputs resolved) {
        if (resolved.images.size() < VolColocParameters.MIN_IMAGES) {
            resolved.closeOwnedImages();
            throw new IllegalArgumentException(
                    "Select at least 2 label images or ROI sets.");
        }
    }

    private void display(VolColocResult result) {
        if (result.getParameters().isIncludePerObjectTables()
                || result.getParameters().isIncludePartnerDetails()) {
            for (OverlapResult.DirectionResult direction
                    : result.getDirectionResults()) {
                String suffix = direction.getSourceChannel()
                        + " in " + direction.getTargetChannel();
                if (result.getParameters().isIncludePerObjectTables()) {
                    result.getPerObjectTable(direction).show(
                            TITLE + " - Objects - " + suffix);
                }
                if (result.getParameters().isIncludePartnerDetails()) {
                    result.getPartnerDetailTable(direction).show(
                            TITLE + " - Partners - " + suffix);
                }
            }
        }
        if (result.getParameters().isIncludeSummaryTable()) {
            result.getSummaryTable().show(TITLE + " - Summary");
        }
        if (!result.getMultiChannelResults().isEmpty()) {
            for (OverlapResult.MultiChannelResult channel
                    : result.getMultiChannelResults()) {
                result.getMultiPerObjectTable(channel).show(
                        TITLE + " - Multi - " + channel.getSourceChannel());
            }
            result.getMultiSummaryTable().show(TITLE + " - Multi Summary");
        }
        if (!result.getBoundingBoxDirectionResults().isEmpty()) {
            for (OverlapResult.BoundingBoxDirectionResult direction
                    : result.getBoundingBoxDirectionResults()) {
                result.getBoundingBoxTable(direction).show(
                        TITLE + " - Bounding Boxes - "
                                + direction.getSourceChannel() + " in "
                                + direction.getTargetChannel());
            }
            result.getBoundingBoxSummaryTable().show(
                    TITLE + " - Bounding Box Summary");
        }
    }

    private File saveBase(VolColocMacroOptions options,
                          ResolvedInputs resolved) {
        if (hasText(options.getSaveDir())) return new File(options.getSaveDir());
        if (!resolved.owned.isEmpty()) {
            ImagePlus image = resolved.owned.get(0);
            if (image.getOriginalFileInfo() != null
                    && hasText(image.getOriginalFileInfo().directory)) {
                return new File(image.getOriginalFileInfo().directory);
            }
        }
        String home = IJ.getDirectory("home");
        return new File(home == null ? "." : home);
    }

    private void record(VolColocMacroOptions options) {
        if (!Recorder.record) return;
        try {
            Recorder.recordString("run(\"" + TITLE + "\", \""
                    + options.toMacroOptions() + "\");\n");
        } catch (RuntimeException exception) {
            IJ.log(TITLE + ": could not record macro: "
                    + exception.getMessage());
        }
    }

    private static String filePrefix(List<String> names) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) builder.append("_vs_");
            builder.append(names.get(i));
        }
        return VolColocIO.safeName(builder.toString());
    }

    private static String[] openImageTitles() {
        int[] ids = WindowManager.getIDList();
        if (ids == null) return new String[0];
        List<String> titles = new ArrayList<String>();
        for (int id : ids) {
            ImagePlus image = WindowManager.getImage(id);
            if (image != null) titles.add(image.getTitle());
        }
        return titles.toArray(new String[0]);
    }

    private static String[] withNone(String[] titles) {
        String[] choices = new String[titles.length + 1];
        choices[0] = NONE;
        System.arraycopy(titles, 0, choices, 1, titles.length);
        return choices;
    }

    private static String noneToNull(String value) {
        return NONE.equals(value) ? null : value;
    }

    private static boolean hasText(String value) {
        return value != null && value.trim().length() > 0;
    }

    private static final class ResolvedInputs {
        final List<ImagePlus> images = new ArrayList<ImagePlus>();
        final List<String> names = new ArrayList<String>();
        final List<Double> thresholds = new ArrayList<Double>();
        final List<Double> boundingBoxThresholds =
                new ArrayList<Double>();
        final List<ImagePlus> owned = new ArrayList<ImagePlus>();

        void add(ImagePlus image, String requestedName, double threshold,
                 double boundingBoxThreshold) {
            images.add(image);
            names.add(hasText(requestedName) ? requestedName : image.getTitle());
            thresholds.add(Double.valueOf(threshold));
            boundingBoxThresholds.add(
                    Double.valueOf(boundingBoxThreshold));
        }

        void closeOwnedImages() {
            for (ImagePlus image : owned) {
                image.changes = false;
                image.close();
            }
            owned.clear();
        }
    }
}
