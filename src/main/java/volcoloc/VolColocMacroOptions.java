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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Macro-facing options independent of ImageJ windows and dialogs.
 */
public final class VolColocMacroOptions {

    public enum InputMode {
        LABELS,
        ROIS,
        BATCH
    }

    public static final int MAX_IMAGES = 5;

    private InputMode mode = InputMode.LABELS;
    private final String[] imageTitles = new String[MAX_IMAGES];
    private final String[] imagePaths = new String[MAX_IMAGES];
    private final String[] roiPaths = new String[MAX_IMAGES];
    private final String[] channelNames = new String[MAX_IMAGES];
    private final double[] thresholdsPercent = new double[MAX_IMAGES];
    private final double[] boundingBoxThresholdsPercent =
            new double[MAX_IMAGES];
    private String referenceTitle;
    private String referencePath;
    private boolean bidirectional = true;
    private boolean perObjectTables = true;
    private boolean summaryTable = true;
    private boolean partnerDetails = true;
    private boolean multiColocalization = true;
    private double minimumOverlapPercent = 50.0;
    private boolean boundingBoxOverlap;
    private boolean boundingBoxCpc;
    private boolean boundingBoxVolumeFill;
    private boolean autoSave;
    private boolean hideDisplay;
    private String saveDir;
    private String batchFolder;
    private String batchRegex = "(.+)_([^_]+)[.](?:tif|tiff)$";
    private int varyingGroup = 2;
    private boolean recursive = true;
    private String batchThresholds;
    private String batchBoundingBoxThresholds;

    public VolColocMacroOptions() {
        for (int i = 0; i < thresholdsPercent.length; i++) {
            thresholdsPercent[i] = VolColocParameters.DEFAULT_THRESHOLD_PERCENT;
            boundingBoxThresholdsPercent[i] =
                    VolColocParameters.DEFAULT_BOUNDING_BOX_THRESHOLD_PERCENT;
        }
    }

    public InputMode getMode() {
        return mode;
    }

    public void setMode(InputMode mode) {
        this.mode = mode == null ? InputMode.LABELS : mode;
    }

    public String getImageTitle(int index) {
        return imageTitles[index];
    }

    public void setImageTitle(int index, String value) {
        imageTitles[index] = clean(value);
    }

    public String getImagePath(int index) {
        return imagePaths[index];
    }

    public void setImagePath(int index, String value) {
        imagePaths[index] = clean(value);
    }

    public String getRoiPath(int index) {
        return roiPaths[index];
    }

    public void setRoiPath(int index, String value) {
        roiPaths[index] = clean(value);
    }

    public String getChannelName(int index) {
        return channelNames[index];
    }

    public void setChannelName(int index, String value) {
        channelNames[index] = clean(value);
    }

    public double getThresholdPercent(int index) {
        return thresholdsPercent[index];
    }

    public void setThresholdPercent(int index, double value) {
        thresholdsPercent[index] = value;
    }

    public double getBoundingBoxThresholdPercent(int index) {
        return boundingBoxThresholdsPercent[index];
    }

    public void setBoundingBoxThresholdPercent(int index, double value) {
        boundingBoxThresholdsPercent[index] = value;
    }

    public String getReferenceTitle() {
        return referenceTitle;
    }

    public void setReferenceTitle(String value) {
        referenceTitle = clean(value);
    }

    public String getReferencePath() {
        return referencePath;
    }

    public void setReferencePath(String value) {
        referencePath = clean(value);
    }

    public boolean isBidirectional() {
        return bidirectional;
    }

    public void setBidirectional(boolean bidirectional) {
        this.bidirectional = bidirectional;
    }

    public boolean isPerObjectTables() {
        return perObjectTables;
    }

    public void setPerObjectTables(boolean perObjectTables) {
        this.perObjectTables = perObjectTables;
    }

    public boolean isSummaryTable() {
        return summaryTable;
    }

    public void setSummaryTable(boolean summaryTable) {
        this.summaryTable = summaryTable;
    }

    public boolean isPartnerDetails() {
        return partnerDetails;
    }

    public void setPartnerDetails(boolean partnerDetails) {
        this.partnerDetails = partnerDetails;
    }

    public boolean isMultiColocalization() {
        return multiColocalization;
    }

    public void setMultiColocalization(boolean multiColocalization) {
        this.multiColocalization = multiColocalization;
    }

    public double getMinimumOverlapPercent() {
        return minimumOverlapPercent;
    }

    public void setMinimumOverlapPercent(double minimumOverlapPercent) {
        this.minimumOverlapPercent = minimumOverlapPercent;
    }

    public boolean isBoundingBoxOverlap() {
        return boundingBoxOverlap;
    }

    public void setBoundingBoxOverlap(boolean boundingBoxOverlap) {
        this.boundingBoxOverlap = boundingBoxOverlap;
    }

    public boolean isBoundingBoxCpc() {
        return boundingBoxCpc;
    }

    public void setBoundingBoxCpc(boolean boundingBoxCpc) {
        this.boundingBoxCpc = boundingBoxCpc;
    }

    public boolean isBoundingBoxVolumeFill() {
        return boundingBoxVolumeFill;
    }

    public void setBoundingBoxVolumeFill(boolean boundingBoxVolumeFill) {
        this.boundingBoxVolumeFill = boundingBoxVolumeFill;
    }

    public boolean isAutoSave() {
        return autoSave;
    }

    public void setAutoSave(boolean autoSave) {
        this.autoSave = autoSave;
    }

    public boolean isHideDisplay() {
        return hideDisplay;
    }

    public void setHideDisplay(boolean hideDisplay) {
        this.hideDisplay = hideDisplay;
    }

    public String getSaveDir() {
        return saveDir;
    }

    public void setSaveDir(String saveDir) {
        this.saveDir = clean(saveDir);
    }

    public String getBatchFolder() {
        return batchFolder;
    }

    public void setBatchFolder(String batchFolder) {
        this.batchFolder = clean(batchFolder);
    }

    public String getBatchRegex() {
        return batchRegex;
    }

    public void setBatchRegex(String batchRegex) {
        this.batchRegex = clean(batchRegex);
    }

    public int getVaryingGroup() {
        return varyingGroup;
    }

    public void setVaryingGroup(int varyingGroup) {
        this.varyingGroup = varyingGroup;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public String getBatchThresholds() {
        return batchThresholds;
    }

    public void setBatchThresholds(String batchThresholds) {
        this.batchThresholds = clean(batchThresholds);
    }

    public String getBatchBoundingBoxThresholds() {
        return batchBoundingBoxThresholds;
    }

    public void setBatchBoundingBoxThresholds(
            String batchBoundingBoxThresholds) {
        this.batchBoundingBoxThresholds =
                clean(batchBoundingBoxThresholds);
    }

    /**
     * Build analysis parameters for {@code images}.
     *
     * <p>Slot {@code i} of this option set supplies the name and thresholds
     * for {@code images.get(i)}, so the caller must pass images in slot order
     * with no gaps. The plugin's own entry point resolves slots to images
     * itself and does not use this method.</p>
     */
    public VolColocParameters toParameters(List<ImagePlus> images) {
        List<String> names = new ArrayList<String>();
        List<Double> thresholds = new ArrayList<Double>();
        List<Double> boundingThresholds = new ArrayList<Double>();
        for (int i = 0; i < images.size(); i++) {
            names.add(channelNames[i]);
            thresholds.add(Double.valueOf(thresholdsPercent[i]));
            boundingThresholds.add(
                    Double.valueOf(boundingBoxThresholdsPercent[i]));
        }
        return VolColocParameters.builder(images)
                .channelNames(names)
                .thresholdsPercent(thresholds)
                .boundingBoxThresholdsPercent(boundingThresholds)
                .bidirectional(bidirectional)
                .includePerObjectTables(perObjectTables)
                .includeSummaryTable(summaryTable)
                .includePartnerDetails(partnerDetails)
                .includeMultiColocalization(multiColocalization)
                .minimumDetailOverlapPercent(minimumOverlapPercent)
                .includeBoundingBoxOverlap(boundingBoxOverlap)
                .includeBoundingBoxCpc(boundingBoxCpc)
                .includeBoundingBoxVolumeFill(boundingBoxVolumeFill)
                .build();
    }

    public String toMacroOptions() {
        List<String> tokens = new ArrayList<String>();
        // Locale.ROOT to match the parser: a Turkish-locale default turns
        // "ROIS" into "roıs" (dotless i), and the recorded macro line would
        // then be unparseable.
        tokens.add("mode=" + mode.name().toLowerCase(Locale.ROOT));
        if (mode == InputMode.BATCH) {
            append(tokens, "batch_folder", batchFolder);
            appendLiteral(tokens, "regex", batchRegex);
            tokens.add("varying_group=" + varyingGroup);
            append(tokens, "batch_thresholds", batchThresholds);
            append(tokens, "batch_bb_thresholds",
                    batchBoundingBoxThresholds);
            tokens.add(recursive ? "recursive" : "non_recursive");
        } else if (mode == InputMode.ROIS) {
            append(tokens, "reference", referenceTitle);
            append(tokens, "reference_path", referencePath);
            for (int i = 0; i < MAX_IMAGES; i++) {
                append(tokens, "roi" + (i + 1), roiPaths[i]);
                appendSlotMetadata(tokens, i);
            }
        } else {
            for (int i = 0; i < MAX_IMAGES; i++) {
                append(tokens, "image" + (i + 1), imageTitles[i]);
                append(tokens, "image" + (i + 1) + "_path", imagePaths[i]);
                appendSlotMetadata(tokens, i);
            }
        }
        tokens.add(bidirectional ? "bidirectional" : "unidirectional");
        tokens.add(perObjectTables ? "objects" : "hide_objects");
        tokens.add(summaryTable ? "summary" : "hide_summary");
        tokens.add(partnerDetails ? "partners" : "hide_partners");
        tokens.add(multiColocalization ? "multi" : "hide_multi");
        tokens.add("min_overlap_percent=" + minimumOverlapPercent);
        if (boundingBoxOverlap) tokens.add("bb_overlap");
        if (boundingBoxCpc) tokens.add("bb_cpc");
        if (boundingBoxVolumeFill) tokens.add("bb_volume_fill");
        if (autoSave) tokens.add("auto_save");
        append(tokens, "save_dir", saveDir);
        if (hideDisplay) tokens.add("hide_display");
        return join(tokens);
    }

    void validate() {
        if (!Double.isFinite(minimumOverlapPercent)
                || minimumOverlapPercent < 0.0 || minimumOverlapPercent > 100.0) {
            throw new IllegalArgumentException(
                    "min_overlap_percent must be between 0 and 100.");
        }
        for (int i = 0; i < MAX_IMAGES; i++) {
            if (hasText(imageTitles[i]) && hasText(imagePaths[i])) {
                throw new IllegalArgumentException("Use either image" + (i + 1)
                        + " or image" + (i + 1) + "_path, not both.");
            }
            double threshold = thresholdsPercent[i];
            if (!Double.isFinite(threshold) || threshold < 0.0 || threshold > 100.0) {
                throw new IllegalArgumentException("threshold" + (i + 1)
                        + " must be between 0 and 100.");
            }
            double boundingThreshold = boundingBoxThresholdsPercent[i];
            if (!Double.isFinite(boundingThreshold)
                    || boundingThreshold < 0.0
                    || boundingThreshold > 100.0) {
                throw new IllegalArgumentException("bb_threshold" + (i + 1)
                        + " must be between 0 and 100.");
            }
        }
        if (hasText(referenceTitle) && hasText(referencePath)) {
            throw new IllegalArgumentException(
                    "Use either reference or reference_path, not both.");
        }
        if (mode == InputMode.BATCH) {
            if (!hasText(batchFolder)) {
                throw new IllegalArgumentException(
                        "batch_folder is required for mode=batch.");
            }
            if (!hasText(batchRegex)) {
                throw new IllegalArgumentException("regex is required for mode=batch.");
            }
            if (varyingGroup < 1) {
                throw new IllegalArgumentException("varying_group must be at least 1.");
            }
        }
    }

    static boolean hasText(String value) {
        return value != null && value.trim().length() > 0;
    }

    static String encodeValue(String value) {
        String normalized = value.trim().replace('\\', '/');
        if (normalized.indexOf('[') >= 0 || normalized.indexOf(']') >= 0
                || normalized.indexOf('"') >= 0 || normalized.indexOf('\n') >= 0
                || normalized.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(
                    "Macro option values must not contain brackets, quotes, or line breaks.");
        }
        return "[" + normalized + "]";
    }

    static String encodeLiteralValue(String value) {
        String normalized = value.trim();
        if (!balancedBrackets(normalized)
                || normalized.indexOf('"') >= 0 || normalized.indexOf('\n') >= 0
                || normalized.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(
                    "Macro option values must have balanced brackets and contain no quotes or line breaks.");
        }
        return "[" + normalized + "]";
    }

    private static boolean balancedBrackets(String value) {
        int depth = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '[') depth++;
            if (c == ']' && --depth < 0) return false;
        }
        return depth == 0;
    }

    private void appendSlotMetadata(List<String> tokens, int index) {
        if (hasText(channelNames[index])) {
            append(tokens, "name" + (index + 1), channelNames[index]);
        }
        tokens.add("threshold" + (index + 1) + "=" + thresholdsPercent[index]);
        tokens.add("bb_threshold" + (index + 1) + "="
                + boundingBoxThresholdsPercent[index]);
    }

    private static void append(List<String> tokens, String key, String value) {
        if (hasText(value)) tokens.add(key + "=" + encodeValue(value));
    }

    private static void appendLiteral(List<String> tokens, String key, String value) {
        if (hasText(value)) tokens.add(key + "=" + encodeLiteralValue(value));
    }

    private static String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() == 0 ? null : trimmed;
    }

    private static String join(List<String> tokens) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) builder.append(' ');
            builder.append(tokens.get(i));
        }
        return builder.toString();
    }
}
