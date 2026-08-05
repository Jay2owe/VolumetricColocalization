/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package volcoloc;

import ij.measure.ResultsTable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable analysis output and ImageJ-table adapters.
 */
public final class VolColocResult {

    private final VolColocParameters parameters;
    private final List<String> channelNames;
    private final List<DirectionResult> directions;
    private final List<MultiChannelResult> multiChannelResults;
    private final List<BoundingBoxDirectionResult> boundingBoxDirections;

    VolColocResult(VolColocParameters parameters,
                   List<String> channelNames,
                   List<DirectionResult> directions,
                   List<MultiChannelResult> multiChannelResults,
                   List<BoundingBoxDirectionResult> boundingBoxDirections) {
        this.parameters = parameters;
        this.channelNames = immutableCopy(channelNames);
        this.directions = immutableCopy(directions);
        this.multiChannelResults = immutableCopy(multiChannelResults);
        this.boundingBoxDirections = immutableCopy(boundingBoxDirections);
    }

    public VolColocParameters getParameters() {
        return parameters;
    }

    public List<String> getChannelNames() {
        return channelNames;
    }

    public List<DirectionResult> getDirectionResults() {
        return directions;
    }

    public List<MultiChannelResult> getMultiChannelResults() {
        return multiChannelResults;
    }

    public List<BoundingBoxDirectionResult> getBoundingBoxDirectionResults() {
        return boundingBoxDirections;
    }

    public ResultsTable getPerObjectTable(DirectionResult direction) {
        ResultsTable table = new ResultsTable();
        declareColumns(table,
                "Source Channel", "Partner Channel", "Source Label",
                "Source Volume (voxels)",
                "Source Volume (" + direction.getVolumeUnit() + ")",
                "Overlap Voxels", "Overlap % of Source", "Best Partner Label",
                "Best Partner Overlap Voxels", "Partner Count", "Threshold %",
                "Colocalized");
        for (ObjectResult row : direction.getObjects()) {
            table.incrementCounter();
            table.addValue("Source Channel", direction.getSourceChannel());
            table.addValue("Partner Channel", direction.getTargetChannel());
            table.addValue("Source Label", row.getSourceLabel());
            table.addValue("Source Volume (voxels)", row.getSourceVoxels());
            table.addValue("Source Volume (" + row.getVolumeUnit() + ")",
                    row.getSourceVolume());
            table.addValue("Overlap Voxels", row.getOverlapVoxels());
            table.addValue("Overlap % of Source", row.getOverlapPercent());
            table.addValue("Best Partner Label", row.getBestPartnerLabel());
            table.addValue("Best Partner Overlap Voxels", row.getBestPartnerOverlapVoxels());
            table.addValue("Partner Count", row.getPartnerCount());
            table.addValue("Threshold %", direction.getThresholdPercent());
            table.addValue("Colocalized", row.isColocalized() ? 1 : 0);
        }
        return table;
    }

    public ResultsTable getPartnerDetailTable(DirectionResult direction) {
        ResultsTable table = new ResultsTable();
        declareColumns(table,
                "Source Channel", "Partner Channel", "Source Label",
                "Partner Label", "Overlap Voxels", "Overlap % of Source",
                "Overlap % of Partner");
        for (PartnerDetail row : direction.getPartnerDetails()) {
            table.incrementCounter();
            table.addValue("Source Channel", direction.getSourceChannel());
            table.addValue("Partner Channel", direction.getTargetChannel());
            table.addValue("Source Label", row.getSourceLabel());
            table.addValue("Partner Label", row.getPartnerLabel());
            table.addValue("Overlap Voxels", row.getOverlapVoxels());
            table.addValue("Overlap % of Source", row.getSourceOverlapPercent());
            table.addValue("Overlap % of Partner", row.getPartnerOverlapPercent());
        }
        return table;
    }

    public ResultsTable getSummaryTable() {
        ResultsTable table = new ResultsTable();
        declareColumns(table,
                "Source Channel", "Partner Channel", "Direction",
                "Object Count", "Mean Overlap %", "Median Overlap %",
                "Threshold %", "Colocalized Count", "Colocalized %");
        for (DirectionResult direction : directions) {
            Summary summary = direction.getSummary();
            table.incrementCounter();
            table.addValue("Source Channel", direction.getSourceChannel());
            table.addValue("Partner Channel", direction.getTargetChannel());
            table.addValue("Direction",
                    direction.getSourceChannel() + " -> " + direction.getTargetChannel());
            table.addValue("Object Count", summary.getObjectCount());
            table.addValue("Mean Overlap %", summary.getMeanOverlapPercent());
            table.addValue("Median Overlap %", summary.getMedianOverlapPercent());
            table.addValue("Threshold %", direction.getThresholdPercent());
            table.addValue("Colocalized Count", summary.getColocalizedCount());
            table.addValue("Colocalized %", summary.getColocalizedPercent());
        }
        return table;
    }

    public ResultsTable getMultiPerObjectTable(MultiChannelResult multi) {
        ResultsTable table = new ResultsTable();
        table.getFreeColumn("Label");
        for (int i = 0; i < channelNames.size(); i++) {
            if (i == multi.getSourceIndex()) continue;
            table.getFreeColumn(channelNames.get(i) + " Coloc");
            table.getFreeColumn(channelNames.get(i) + " Partner");
        }
        table.getFreeColumn("Targets Hit");
        for (MultiObjectResult row : multi.getObjects()) {
            table.incrementCounter();
            table.addValue("Label", row.getSourceLabel());
            int targetsHit = 0;
            for (TargetStatus status : row.getTargets()) {
                table.addValue(status.getTargetChannel() + " Coloc",
                        status.isColocalized() ? 1 : 0);
                table.addValue(status.getTargetChannel() + " Partner",
                        status.getPartnerLabel());
                if (status.isColocalized()) targetsHit++;
            }
            table.addValue("Targets Hit", targetsHit);
        }
        return table;
    }

    public ResultsTable getMultiSummaryTable() {
        ResultsTable table = new ResultsTable();
        declareColumns(table, "Source", "Pattern", "Count", "% of Source");
        for (MultiChannelResult multi : multiChannelResults) {
            for (PatternSummary row : multi.getPatterns()) {
                table.incrementCounter();
                table.addValue("Source", multi.getSourceChannel());
                table.addValue("Pattern", row.getPattern());
                table.addValue("Count", row.getObjectCount());
                table.addValue("% of Source", row.getObjectPercent());
            }
        }
        return table;
    }

    public ResultsTable getBoundingBoxTable(
            BoundingBoxDirectionResult direction) {
        ResultsTable table = new ResultsTable();
        declareColumns(table, "Source Channel", "Partner Channel",
                "Source Label", "Source Box Volume (voxels)");
        if (direction.isIncludeBoundingBoxOverlap()) {
            declareColumns(table, "BBColoc Best Box Overlap %",
                    "BBColoc Partner", "BBColoc Threshold %", "BBColoc");
        }
        if (direction.isIncludeBoundingBoxCpc()) {
            declareColumns(table, "BB-CPC Coloc", "BB-CPC Partner",
                    "BB-CPC Contains Count");
        }
        if (direction.isIncludeBoundingBoxVolumeFill()) {
            declareColumns(table, "BBVolColoc Best Fill %",
                    "BBVolColoc Total Fill %", "BBVolColoc Partner",
                    "BBVolColoc Threshold %", "BBVolColoc");
        }
        for (BoundingBoxObjectResult row : direction.getObjects()) {
            table.incrementCounter();
            table.addValue("Source Channel", direction.getSourceChannel());
            table.addValue("Partner Channel", direction.getTargetChannel());
            table.addValue("Source Label", row.getSourceLabel());
            table.addValue("Source Box Volume (voxels)", row.getBoxVolume());
            if (direction.isIncludeBoundingBoxOverlap()) {
                table.addValue("BBColoc Best Box Overlap %",
                        row.getBoundingBoxOverlapPercent());
                table.addValue("BBColoc Partner",
                        row.getBoundingBoxOverlapPartnerLabel());
                table.addValue("BBColoc Threshold %",
                        direction.getThresholdPercent());
                table.addValue("BBColoc",
                        row.isBoundingBoxOverlapColocalized() ? 1 : 0);
            }
            if (direction.isIncludeBoundingBoxCpc()) {
                table.addValue("BB-CPC Coloc",
                        row.isBoundingBoxCpcColocalized() ? 1 : 0);
                table.addValue("BB-CPC Partner",
                        row.getBoundingBoxCpcPartnerLabel());
                table.addValue("BB-CPC Contains Count",
                        row.getBoundingBoxCpcContainsCount());
            }
            if (direction.isIncludeBoundingBoxVolumeFill()) {
                table.addValue("BBVolColoc Best Fill %",
                        row.getBoundingBoxVolumeBestPercent());
                table.addValue("BBVolColoc Total Fill %",
                        row.getBoundingBoxVolumeTotalPercent());
                table.addValue("BBVolColoc Partner",
                        row.getBoundingBoxVolumePartnerLabel());
                table.addValue("BBVolColoc Threshold %",
                        direction.getThresholdPercent());
                table.addValue("BBVolColoc",
                        row.isBoundingBoxVolumeColocalized() ? 1 : 0);
            }
        }
        return table;
    }

    public ResultsTable getBoundingBoxSummaryTable() {
        ResultsTable table = new ResultsTable();
        declareColumns(table, "Source Channel", "Partner Channel", "Direction",
                "Method", "Object Count", "Mean %", "Median %", "Threshold %",
                "Positive Count", "Positive %", "Mean Total Fill %");
        for (BoundingBoxDirectionResult direction : boundingBoxDirections) {
            if (direction.isIncludeBoundingBoxOverlap()) {
                addBoundingSummaryRow(
                        table, direction, "BBColoc", false);
            }
            if (direction.isIncludeBoundingBoxCpc()) {
                addBoundingSummaryRow(
                        table, direction, "BB-CPC", false);
            }
            if (direction.isIncludeBoundingBoxVolumeFill()) {
                addBoundingSummaryRow(
                        table, direction, "BBVolColoc", true);
            }
        }
        return table;
    }

    private static void addBoundingSummaryRow(
            ResultsTable table, BoundingBoxDirectionResult direction,
            String method, boolean includeTotalFill) {
        List<BoundingBoxObjectResult> objects = direction.getObjects();
        double[] values = new double[objects.size()];
        double totalFill = 0.0;
        int positives = 0;
        for (int i = 0; i < objects.size(); i++) {
            BoundingBoxObjectResult object = objects.get(i);
            if ("BBColoc".equals(method)) {
                values[i] = object.getBoundingBoxOverlapPercent();
                if (object.isBoundingBoxOverlapColocalized()) positives++;
            } else if ("BB-CPC".equals(method)) {
                values[i] = object.isBoundingBoxCpcColocalized() ? 100.0 : 0.0;
                if (object.isBoundingBoxCpcColocalized()) positives++;
            } else {
                values[i] = object.getBoundingBoxVolumeBestPercent();
                totalFill += object.getBoundingBoxVolumeTotalPercent();
                if (object.isBoundingBoxVolumeColocalized()) positives++;
            }
        }
        table.incrementCounter();
        table.addValue("Source Channel", direction.getSourceChannel());
        table.addValue("Partner Channel", direction.getTargetChannel());
        table.addValue("Direction",
                direction.getSourceChannel() + " -> " + direction.getTargetChannel());
        table.addValue("Method", method);
        table.addValue("Object Count", objects.size());
        table.addValue("Mean %", mean(values));
        table.addValue("Median %", median(values));
        table.addValue("Threshold %",
                "BB-CPC".equals(method)
                        ? Double.NaN : direction.getThresholdPercent());
        table.addValue("Positive Count", positives);
        table.addValue("Positive %",
                objects.isEmpty() ? 0.0 : positives * 100.0 / objects.size());
        if (includeTotalFill) {
            table.addValue("Mean Total Fill %",
                    objects.isEmpty() ? 0.0 : totalFill / objects.size());
        } else {
            table.addValue("Mean Total Fill %", Double.NaN);
        }
    }

    /**
     * Reserve the table's columns up front so a table with no rows still
     * writes a header line instead of a zero-byte CSV.
     */
    private static void declareColumns(ResultsTable table, String... headings) {
        for (String heading : headings) table.getFreeColumn(heading);
    }

    private static double mean(double[] values) {
        if (values.length == 0) return 0.0;
        double sum = 0.0;
        for (double value : values) sum += value;
        return sum / values.length;
    }

    private static double median(double[] values) {
        if (values.length == 0) return 0.0;
        double[] sorted = java.util.Arrays.copyOf(values, values.length);
        java.util.Arrays.sort(sorted);
        int middle = sorted.length / 2;
        return (sorted.length & 1) == 1
                ? sorted[middle]
                : (sorted[middle - 1] + sorted[middle]) / 2.0;
    }

    public static final class DirectionResult {
        private final int sourceIndex;
        private final int targetIndex;
        private final String sourceChannel;
        private final String targetChannel;
        private final double thresholdPercent;
        private final String volumeUnit;
        private final List<ObjectResult> objects;
        private final List<PartnerDetail> partnerDetails;
        private final Summary summary;

        DirectionResult(int sourceIndex, int targetIndex,
                        String sourceChannel, String targetChannel,
                        double thresholdPercent,
                        String volumeUnit,
                        List<ObjectResult> objects,
                        List<PartnerDetail> partnerDetails,
                        Summary summary) {
            this.sourceIndex = sourceIndex;
            this.targetIndex = targetIndex;
            this.sourceChannel = sourceChannel;
            this.targetChannel = targetChannel;
            this.thresholdPercent = thresholdPercent;
            this.volumeUnit = volumeUnit;
            this.objects = immutableCopy(objects);
            this.partnerDetails = immutableCopy(partnerDetails);
            this.summary = summary;
        }

        public int getSourceIndex() {
            return sourceIndex;
        }

        public int getTargetIndex() {
            return targetIndex;
        }

        public String getSourceChannel() {
            return sourceChannel;
        }

        public String getTargetChannel() {
            return targetChannel;
        }

        public double getThresholdPercent() {
            return thresholdPercent;
        }

        /**
         * Calibrated volume unit of the source channel, such as
         * {@code µm^3} or {@code pixel^3}.
         */
        public String getVolumeUnit() {
            return volumeUnit;
        }

        public List<ObjectResult> getObjects() {
            return objects;
        }

        public List<PartnerDetail> getPartnerDetails() {
            return partnerDetails;
        }

        public Summary getSummary() {
            return summary;
        }
    }

    public static final class ObjectResult {
        private final int sourceLabel;
        private final int sourceVoxels;
        private final double sourceVolume;
        private final String volumeUnit;
        private final int overlapVoxels;
        private final double overlapPercent;
        private final int bestPartnerLabel;
        private final int bestPartnerOverlapVoxels;
        private final int partnerCount;
        private final boolean colocalized;

        ObjectResult(int sourceLabel, int sourceVoxels,
                     double sourceVolume, String volumeUnit,
                     int overlapVoxels, double overlapPercent,
                     int bestPartnerLabel, int bestPartnerOverlapVoxels,
                     int partnerCount, boolean colocalized) {
            this.sourceLabel = sourceLabel;
            this.sourceVoxels = sourceVoxels;
            this.sourceVolume = sourceVolume;
            this.volumeUnit = volumeUnit;
            this.overlapVoxels = overlapVoxels;
            this.overlapPercent = overlapPercent;
            this.bestPartnerLabel = bestPartnerLabel;
            this.bestPartnerOverlapVoxels = bestPartnerOverlapVoxels;
            this.partnerCount = partnerCount;
            this.colocalized = colocalized;
        }

        public int getSourceLabel() {
            return sourceLabel;
        }

        public int getSourceVoxels() {
            return sourceVoxels;
        }

        public double getSourceVolume() {
            return sourceVolume;
        }

        public String getVolumeUnit() {
            return volumeUnit;
        }

        public int getOverlapVoxels() {
            return overlapVoxels;
        }

        public double getOverlapPercent() {
            return overlapPercent;
        }

        public int getBestPartnerLabel() {
            return bestPartnerLabel;
        }

        public int getBestPartnerOverlapVoxels() {
            return bestPartnerOverlapVoxels;
        }

        public int getPartnerCount() {
            return partnerCount;
        }

        public boolean isColocalized() {
            return colocalized;
        }
    }

    public static final class PartnerDetail {
        private final int sourceLabel;
        private final int partnerLabel;
        private final int overlapVoxels;
        private final double sourceOverlapPercent;
        private final double partnerOverlapPercent;

        PartnerDetail(int sourceLabel, int partnerLabel, int overlapVoxels,
                      double sourceOverlapPercent, double partnerOverlapPercent) {
            this.sourceLabel = sourceLabel;
            this.partnerLabel = partnerLabel;
            this.overlapVoxels = overlapVoxels;
            this.sourceOverlapPercent = sourceOverlapPercent;
            this.partnerOverlapPercent = partnerOverlapPercent;
        }

        public int getSourceLabel() {
            return sourceLabel;
        }

        public int getPartnerLabel() {
            return partnerLabel;
        }

        public int getOverlapVoxels() {
            return overlapVoxels;
        }

        public double getSourceOverlapPercent() {
            return sourceOverlapPercent;
        }

        public double getPartnerOverlapPercent() {
            return partnerOverlapPercent;
        }
    }

    public static final class Summary {
        private final int objectCount;
        private final double meanOverlapPercent;
        private final double medianOverlapPercent;
        private final int colocalizedCount;
        private final double colocalizedPercent;

        Summary(int objectCount, double meanOverlapPercent,
                double medianOverlapPercent, int colocalizedCount,
                double colocalizedPercent) {
            this.objectCount = objectCount;
            this.meanOverlapPercent = meanOverlapPercent;
            this.medianOverlapPercent = medianOverlapPercent;
            this.colocalizedCount = colocalizedCount;
            this.colocalizedPercent = colocalizedPercent;
        }

        public int getObjectCount() {
            return objectCount;
        }

        public double getMeanOverlapPercent() {
            return meanOverlapPercent;
        }

        public double getMedianOverlapPercent() {
            return medianOverlapPercent;
        }

        public int getColocalizedCount() {
            return colocalizedCount;
        }

        public double getColocalizedPercent() {
            return colocalizedPercent;
        }
    }

    public static final class BoundingBoxDirectionResult {
        private final int sourceIndex;
        private final int targetIndex;
        private final String sourceChannel;
        private final String targetChannel;
        private final double thresholdPercent;
        private final boolean includeBoundingBoxOverlap;
        private final boolean includeBoundingBoxCpc;
        private final boolean includeBoundingBoxVolumeFill;
        private final List<BoundingBoxObjectResult> objects;

        BoundingBoxDirectionResult(
                int sourceIndex, int targetIndex,
                String sourceChannel, String targetChannel,
                double thresholdPercent,
                boolean includeBoundingBoxOverlap,
                boolean includeBoundingBoxCpc,
                boolean includeBoundingBoxVolumeFill,
                List<BoundingBoxObjectResult> objects) {
            this.sourceIndex = sourceIndex;
            this.targetIndex = targetIndex;
            this.sourceChannel = sourceChannel;
            this.targetChannel = targetChannel;
            this.thresholdPercent = thresholdPercent;
            this.includeBoundingBoxOverlap = includeBoundingBoxOverlap;
            this.includeBoundingBoxCpc = includeBoundingBoxCpc;
            this.includeBoundingBoxVolumeFill = includeBoundingBoxVolumeFill;
            this.objects = immutableCopy(objects);
        }

        public int getSourceIndex() {
            return sourceIndex;
        }

        public int getTargetIndex() {
            return targetIndex;
        }

        public String getSourceChannel() {
            return sourceChannel;
        }

        public String getTargetChannel() {
            return targetChannel;
        }

        public double getThresholdPercent() {
            return thresholdPercent;
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

        public List<BoundingBoxObjectResult> getObjects() {
            return objects;
        }
    }

    public static final class BoundingBoxObjectResult {
        private final int sourceLabel;
        private final long boxVolume;
        private final double boundingBoxOverlapPercent;
        private final int boundingBoxOverlapPartnerLabel;
        private final boolean boundingBoxOverlapColocalized;
        private final boolean boundingBoxCpcColocalized;
        private final int boundingBoxCpcPartnerLabel;
        private final int boundingBoxCpcContainsCount;
        private final double boundingBoxVolumeBestPercent;
        private final double boundingBoxVolumeTotalPercent;
        private final int boundingBoxVolumePartnerLabel;
        private final boolean boundingBoxVolumeColocalized;

        BoundingBoxObjectResult(
                int sourceLabel,
                long boxVolume,
                double boundingBoxOverlapPercent,
                int boundingBoxOverlapPartnerLabel,
                boolean boundingBoxOverlapColocalized,
                boolean boundingBoxCpcColocalized,
                int boundingBoxCpcPartnerLabel,
                int boundingBoxCpcContainsCount,
                double boundingBoxVolumeBestPercent,
                double boundingBoxVolumeTotalPercent,
                int boundingBoxVolumePartnerLabel,
                boolean boundingBoxVolumeColocalized) {
            this.sourceLabel = sourceLabel;
            this.boxVolume = boxVolume;
            this.boundingBoxOverlapPercent = boundingBoxOverlapPercent;
            this.boundingBoxOverlapPartnerLabel =
                    boundingBoxOverlapPartnerLabel;
            this.boundingBoxOverlapColocalized =
                    boundingBoxOverlapColocalized;
            this.boundingBoxCpcColocalized = boundingBoxCpcColocalized;
            this.boundingBoxCpcPartnerLabel = boundingBoxCpcPartnerLabel;
            this.boundingBoxCpcContainsCount = boundingBoxCpcContainsCount;
            this.boundingBoxVolumeBestPercent =
                    boundingBoxVolumeBestPercent;
            this.boundingBoxVolumeTotalPercent =
                    boundingBoxVolumeTotalPercent;
            this.boundingBoxVolumePartnerLabel =
                    boundingBoxVolumePartnerLabel;
            this.boundingBoxVolumeColocalized =
                    boundingBoxVolumeColocalized;
        }

        public int getSourceLabel() {
            return sourceLabel;
        }

        public long getBoxVolume() {
            return boxVolume;
        }

        public double getBoundingBoxOverlapPercent() {
            return boundingBoxOverlapPercent;
        }

        public int getBoundingBoxOverlapPartnerLabel() {
            return boundingBoxOverlapPartnerLabel;
        }

        public boolean isBoundingBoxOverlapColocalized() {
            return boundingBoxOverlapColocalized;
        }

        public boolean isBoundingBoxCpcColocalized() {
            return boundingBoxCpcColocalized;
        }

        public int getBoundingBoxCpcPartnerLabel() {
            return boundingBoxCpcPartnerLabel;
        }

        public int getBoundingBoxCpcContainsCount() {
            return boundingBoxCpcContainsCount;
        }

        public double getBoundingBoxVolumeBestPercent() {
            return boundingBoxVolumeBestPercent;
        }

        public double getBoundingBoxVolumeTotalPercent() {
            return boundingBoxVolumeTotalPercent;
        }

        public int getBoundingBoxVolumePartnerLabel() {
            return boundingBoxVolumePartnerLabel;
        }

        public boolean isBoundingBoxVolumeColocalized() {
            return boundingBoxVolumeColocalized;
        }
    }

    public static final class MultiChannelResult {
        private final int sourceIndex;
        private final String sourceChannel;
        private final List<MultiObjectResult> objects;
        private final List<PatternSummary> patterns;

        MultiChannelResult(int sourceIndex, String sourceChannel,
                           List<MultiObjectResult> objects,
                           List<PatternSummary> patterns) {
            this.sourceIndex = sourceIndex;
            this.sourceChannel = sourceChannel;
            this.objects = immutableCopy(objects);
            this.patterns = immutableCopy(patterns);
        }

        public int getSourceIndex() {
            return sourceIndex;
        }

        public String getSourceChannel() {
            return sourceChannel;
        }

        public List<MultiObjectResult> getObjects() {
            return objects;
        }

        public List<PatternSummary> getPatterns() {
            return patterns;
        }
    }

    public static final class MultiObjectResult {
        private final int sourceLabel;
        private final String pattern;
        private final List<TargetStatus> targets;

        MultiObjectResult(int sourceLabel, String pattern, List<TargetStatus> targets) {
            this.sourceLabel = sourceLabel;
            this.pattern = pattern;
            this.targets = immutableCopy(targets);
        }

        public int getSourceLabel() {
            return sourceLabel;
        }

        public String getPattern() {
            return pattern;
        }

        public List<TargetStatus> getTargets() {
            return targets;
        }
    }

    public static final class TargetStatus {
        private final int targetIndex;
        private final String targetChannel;
        private final int partnerLabel;
        private final double overlapPercent;
        private final boolean colocalized;

        TargetStatus(int targetIndex, String targetChannel, int partnerLabel,
                     double overlapPercent, boolean colocalized) {
            this.targetIndex = targetIndex;
            this.targetChannel = targetChannel;
            this.partnerLabel = partnerLabel;
            this.overlapPercent = overlapPercent;
            this.colocalized = colocalized;
        }

        public int getTargetIndex() {
            return targetIndex;
        }

        public String getTargetChannel() {
            return targetChannel;
        }

        public int getPartnerLabel() {
            return partnerLabel;
        }

        public double getOverlapPercent() {
            return overlapPercent;
        }

        public boolean isColocalized() {
            return colocalized;
        }
    }

    public static final class PatternSummary {
        private final String pattern;
        private final int objectCount;
        private final double objectPercent;

        PatternSummary(String pattern, int objectCount, double objectPercent) {
            this.pattern = pattern;
            this.objectCount = objectCount;
            this.objectPercent = objectPercent;
        }

        public String getPattern() {
            return pattern;
        }

        public int getObjectCount() {
            return objectCount;
        }

        public double getObjectPercent() {
            return objectPercent;
        }
    }

    private static <T> List<T> immutableCopy(List<T> source) {
        if (source == null) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<T>(source));
    }
}
