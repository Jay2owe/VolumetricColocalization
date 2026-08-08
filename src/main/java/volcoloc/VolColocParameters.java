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
import java.util.Collections;
import java.util.List;

/**
 * Immutable input bundle for volumetric-colocalization analysis.
 */
public final class VolColocParameters {

    // Sourced from the engine rather than restated, so the dialog's defaults
    // and the engine's cannot drift apart.
    public static final int MIN_IMAGES =
            sc.fiji.volcoloc.core.OverlapParameters.MIN_IMAGES;
    public static final int MAX_IMAGES =
            sc.fiji.volcoloc.core.OverlapParameters.MAX_IMAGES;
    public static final double DEFAULT_THRESHOLD_PERCENT =
            sc.fiji.volcoloc.core.OverlapParameters.DEFAULT_THRESHOLD_PERCENT;
    public static final double DEFAULT_BOUNDING_BOX_THRESHOLD_PERCENT =
            sc.fiji.volcoloc.core.OverlapParameters.DEFAULT_BOUNDING_BOX_THRESHOLD_PERCENT;

    private final List<ImagePlus> images;
    private final List<String> channelNames;
    private final List<Double> thresholdsPercent;
    private final List<Double> boundingBoxThresholdsPercent;
    private final boolean bidirectional;
    private final boolean includePerObjectTables;
    private final boolean includeSummaryTable;
    private final boolean includePartnerDetails;
    private final boolean includeMultiColocalization;
    private final double minimumDetailOverlapPercent;
    private final boolean includeBoundingBoxOverlap;
    private final boolean includeBoundingBoxCpc;
    private final boolean includeBoundingBoxVolumeFill;

    private VolColocParameters(Builder builder) {
        images = immutableCopy(builder.images);
        channelNames = immutableCopy(builder.channelNames);
        thresholdsPercent = immutableCopy(builder.thresholdsPercent);
        boundingBoxThresholdsPercent =
                immutableCopy(builder.boundingBoxThresholdsPercent);
        bidirectional = builder.bidirectional;
        includePerObjectTables = builder.includePerObjectTables;
        includeSummaryTable = builder.includeSummaryTable;
        includePartnerDetails = builder.includePartnerDetails;
        includeMultiColocalization = builder.includeMultiColocalization;
        minimumDetailOverlapPercent = builder.minimumDetailOverlapPercent;
        includeBoundingBoxOverlap = builder.includeBoundingBoxOverlap;
        includeBoundingBoxCpc = builder.includeBoundingBoxCpc;
        includeBoundingBoxVolumeFill = builder.includeBoundingBoxVolumeFill;
    }

    public static Builder builder(List<ImagePlus> images) {
        return new Builder(images);
    }

    public static Builder builder(ImagePlus imageA, ImagePlus imageB) {
        List<ImagePlus> images = new ArrayList<ImagePlus>();
        images.add(imageA);
        images.add(imageB);
        return builder(images);
    }

    public List<ImagePlus> getImages() {
        return images;
    }

    /**
     * User-facing names parallel to {@link #getImages()}. Empty entries are
     * replaced with image titles by the facade.
     */
    public List<String> getChannelNames() {
        return channelNames;
    }

    /**
     * One outgoing-overlap threshold per source channel.
     */
    public List<Double> getThresholdsPercent() {
        return thresholdsPercent;
    }

    /**
     * One outgoing bounding-box threshold per source channel, used by
     * BBColoc and BBVolColoc.
     */
    public List<Double> getBoundingBoxThresholdsPercent() {
        return boundingBoxThresholdsPercent;
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

    /**
     * Minimum percentage of the source object's volume contributed by one
     * partner before that pair receives a detail row.
     */
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

    public boolean hasBoundingBoxAnalysis() {
        return includeBoundingBoxOverlap
                || includeBoundingBoxCpc
                || includeBoundingBoxVolumeFill;
    }

    /**
     * The engine's view of these parameters.
     *
     * <p>{@link #isIncludePerObjectTables()} and {@link #isIncludeSummaryTable()}
     * are deliberately absent: they decide which windows this plugin opens and
     * the engine has never read them. Everything else crosses unchanged, and
     * the constants above are the core's own, so the defaults cannot drift
     * apart.
     */
    sc.fiji.volcoloc.core.OverlapParameters toCoreParameters() {
        return sc.fiji.volcoloc.core.OverlapParameters.builder(images)
                .channelNames(channelNames)
                .thresholdsPercent(thresholdsPercent)
                .boundingBoxThresholdsPercent(boundingBoxThresholdsPercent)
                .bidirectional(bidirectional)
                .includePartnerDetails(includePartnerDetails)
                .includeMultiColocalization(includeMultiColocalization)
                .minimumDetailOverlapPercent(minimumDetailOverlapPercent)
                .includeBoundingBoxOverlap(includeBoundingBoxOverlap)
                .includeBoundingBoxCpc(includeBoundingBoxCpc)
                .includeBoundingBoxVolumeFill(includeBoundingBoxVolumeFill)
                .build();
    }

    private static <T> List<T> immutableCopy(List<T> source) {
        if (source == null) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<T>(source));
    }

    public static final class Builder {
        private List<ImagePlus> images;
        private List<String> channelNames = new ArrayList<String>();
        private List<Double> thresholdsPercent = new ArrayList<Double>();
        private List<Double> boundingBoxThresholdsPercent =
                new ArrayList<Double>();
        private boolean bidirectional = true;
        private boolean includePerObjectTables = true;
        private boolean includeSummaryTable = true;
        private boolean includePartnerDetails = true;
        private boolean includeMultiColocalization = true;
        private double minimumDetailOverlapPercent = 50.0;
        private boolean includeBoundingBoxOverlap;
        private boolean includeBoundingBoxCpc;
        private boolean includeBoundingBoxVolumeFill;

        private Builder(List<ImagePlus> images) {
            this.images = images == null
                    ? new ArrayList<ImagePlus>()
                    : new ArrayList<ImagePlus>(images);
        }

        public Builder images(List<ImagePlus> images) {
            this.images = images == null
                    ? new ArrayList<ImagePlus>()
                    : new ArrayList<ImagePlus>(images);
            return this;
        }

        public Builder channelNames(List<String> channelNames) {
            this.channelNames = channelNames == null
                    ? new ArrayList<String>()
                    : new ArrayList<String>(channelNames);
            return this;
        }

        public Builder thresholdsPercent(List<Double> thresholdsPercent) {
            this.thresholdsPercent = thresholdsPercent == null
                    ? new ArrayList<Double>()
                    : new ArrayList<Double>(thresholdsPercent);
            return this;
        }

        public Builder boundingBoxThresholdsPercent(
                List<Double> boundingBoxThresholdsPercent) {
            this.boundingBoxThresholdsPercent =
                    boundingBoxThresholdsPercent == null
                            ? new ArrayList<Double>()
                            : new ArrayList<Double>(
                                    boundingBoxThresholdsPercent);
            return this;
        }

        public Builder thresholdPercent(double thresholdPercent) {
            this.thresholdsPercent = new ArrayList<Double>();
            int count = Math.max(images.size(), MIN_IMAGES);
            for (int i = 0; i < count; i++) {
                this.thresholdsPercent.add(Double.valueOf(thresholdPercent));
            }
            return this;
        }

        public Builder bidirectional(boolean bidirectional) {
            this.bidirectional = bidirectional;
            return this;
        }

        public Builder includePerObjectTables(boolean includePerObjectTables) {
            this.includePerObjectTables = includePerObjectTables;
            return this;
        }

        public Builder includeSummaryTable(boolean includeSummaryTable) {
            this.includeSummaryTable = includeSummaryTable;
            return this;
        }

        public Builder includePartnerDetails(boolean includePartnerDetails) {
            this.includePartnerDetails = includePartnerDetails;
            return this;
        }

        public Builder includeMultiColocalization(boolean includeMultiColocalization) {
            this.includeMultiColocalization = includeMultiColocalization;
            return this;
        }

        public Builder minimumDetailOverlapPercent(double minimumDetailOverlapPercent) {
            this.minimumDetailOverlapPercent = minimumDetailOverlapPercent;
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

        public VolColocParameters build() {
            return new VolColocParameters(this);
        }
    }
}
