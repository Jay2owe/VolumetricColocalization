/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package volcoloc;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable input bundle for folder batch processing.
 */
public final class VolColocBatchParameters {

    private final File inputFolder;
    private final String labelRegex;
    private final int varyingGroup;
    private final boolean recursive;
    private final Map<String, Double> thresholdsByChannel;
    private final Map<String, Double> boundingBoxThresholdsByChannel;
    private final boolean bidirectional;
    private final boolean includePerObjectTables;
    private final boolean includeSummaryTable;
    private final boolean includePartnerDetails;
    private final boolean includeMultiColocalization;
    private final double minimumDetailOverlapPercent;
    private final boolean includeBoundingBoxOverlap;
    private final boolean includeBoundingBoxCpc;
    private final boolean includeBoundingBoxVolumeFill;
    private final boolean autoSave;
    private final File saveDirectory;

    private VolColocBatchParameters(Builder builder) {
        inputFolder = builder.inputFolder;
        labelRegex = builder.labelRegex;
        varyingGroup = builder.varyingGroup;
        recursive = builder.recursive;
        thresholdsByChannel = Collections.unmodifiableMap(
                new LinkedHashMap<String, Double>(builder.thresholdsByChannel));
        boundingBoxThresholdsByChannel = Collections.unmodifiableMap(
                new LinkedHashMap<String, Double>(
                        builder.boundingBoxThresholdsByChannel));
        bidirectional = builder.bidirectional;
        includePerObjectTables = builder.includePerObjectTables;
        includeSummaryTable = builder.includeSummaryTable;
        includePartnerDetails = builder.includePartnerDetails;
        includeMultiColocalization = builder.includeMultiColocalization;
        minimumDetailOverlapPercent = builder.minimumDetailOverlapPercent;
        includeBoundingBoxOverlap = builder.includeBoundingBoxOverlap;
        includeBoundingBoxCpc = builder.includeBoundingBoxCpc;
        includeBoundingBoxVolumeFill = builder.includeBoundingBoxVolumeFill;
        autoSave = builder.autoSave;
        saveDirectory = builder.saveDirectory;
    }

    public static Builder builder(File inputFolder, String labelRegex,
                                  int varyingGroup) {
        return new Builder(inputFolder, labelRegex, varyingGroup);
    }

    public File getInputFolder() {
        return inputFolder;
    }

    public String getLabelRegex() {
        return labelRegex;
    }

    public int getVaryingGroup() {
        return varyingGroup;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public Map<String, Double> getThresholdsByChannel() {
        return thresholdsByChannel;
    }

    public Map<String, Double> getBoundingBoxThresholdsByChannel() {
        return boundingBoxThresholdsByChannel;
    }

    public boolean isBidirectional() {
        return bidirectional;
    }

    public boolean isIncludePerObjectTables() {
        return includePerObjectTables;
    }

    public boolean isIncludeSummaryTable() {
        return includeSummaryTable;
    }

    public boolean isIncludePartnerDetails() {
        return includePartnerDetails;
    }

    public boolean isIncludeMultiColocalization() {
        return includeMultiColocalization;
    }

    public double getMinimumDetailOverlapPercent() {
        return minimumDetailOverlapPercent;
    }

    public boolean isIncludeBoundingBoxOverlap() {
        return includeBoundingBoxOverlap;
    }

    public boolean isIncludeBoundingBoxCpc() {
        return includeBoundingBoxCpc;
    }

    public boolean isIncludeBoundingBoxVolumeFill() {
        return includeBoundingBoxVolumeFill;
    }

    public boolean isAutoSave() {
        return autoSave;
    }

    public File getSaveDirectory() {
        return saveDirectory;
    }

    public static final class Builder {
        private final File inputFolder;
        private final String labelRegex;
        private final int varyingGroup;
        private boolean recursive = true;
        private Map<String, Double> thresholdsByChannel =
                new LinkedHashMap<String, Double>();
        private Map<String, Double> boundingBoxThresholdsByChannel =
                new LinkedHashMap<String, Double>();
        private boolean bidirectional = true;
        private boolean includePerObjectTables = true;
        private boolean includeSummaryTable = true;
        private boolean includePartnerDetails = true;
        private boolean includeMultiColocalization = true;
        private double minimumDetailOverlapPercent = 50.0;
        private boolean includeBoundingBoxOverlap;
        private boolean includeBoundingBoxCpc;
        private boolean includeBoundingBoxVolumeFill;
        private boolean autoSave;
        private File saveDirectory;

        private Builder(File inputFolder, String labelRegex, int varyingGroup) {
            this.inputFolder = inputFolder;
            this.labelRegex = labelRegex;
            this.varyingGroup = varyingGroup;
        }

        public Builder recursive(boolean recursive) {
            this.recursive = recursive;
            return this;
        }

        public Builder thresholdsByChannel(Map<String, Double> thresholds) {
            thresholdsByChannel = thresholds == null
                    ? new LinkedHashMap<String, Double>()
                    : new LinkedHashMap<String, Double>(thresholds);
            return this;
        }

        public Builder boundingBoxThresholdsByChannel(
                Map<String, Double> thresholds) {
            boundingBoxThresholdsByChannel = thresholds == null
                    ? new LinkedHashMap<String, Double>()
                    : new LinkedHashMap<String, Double>(thresholds);
            return this;
        }

        public Builder bidirectional(boolean bidirectional) {
            this.bidirectional = bidirectional;
            return this;
        }

        public Builder includePerObjectTables(boolean value) {
            includePerObjectTables = value;
            return this;
        }

        public Builder includeSummaryTable(boolean value) {
            includeSummaryTable = value;
            return this;
        }

        public Builder includePartnerDetails(boolean value) {
            includePartnerDetails = value;
            return this;
        }

        public Builder includeMultiColocalization(boolean value) {
            includeMultiColocalization = value;
            return this;
        }

        public Builder minimumDetailOverlapPercent(double value) {
            minimumDetailOverlapPercent = value;
            return this;
        }

        public Builder includeBoundingBoxOverlap(boolean value) {
            includeBoundingBoxOverlap = value;
            return this;
        }

        public Builder includeBoundingBoxCpc(boolean value) {
            includeBoundingBoxCpc = value;
            return this;
        }

        public Builder includeBoundingBoxVolumeFill(boolean value) {
            includeBoundingBoxVolumeFill = value;
            return this;
        }

        public Builder autoSave(boolean value) {
            autoSave = value;
            return this;
        }

        public Builder saveDirectory(File directory) {
            saveDirectory = directory;
            return this;
        }

        public VolColocBatchParameters build() {
            return new VolColocBatchParameters(this);
        }
    }
}
