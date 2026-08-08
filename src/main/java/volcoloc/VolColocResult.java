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
import sc.fiji.volcoloc.core.OverlapResult;

import java.util.List;

/**
 * The measured result, plus this plugin's ImageJ-table adapters.
 *
 * <p>The numbers come from {@code volcoloc-core} and are re-exposed unchanged;
 * everything added here is presentation. That split is the point of the core:
 * another plugin can embed the engine and append overlap columns to its own
 * per-object table without inheriting the tables below.
 *
 * <p>Row types are the core's ({@link OverlapResult.ObjectResult} and friends)
 * rather than copies. Copying them would reintroduce, on the presentation side,
 * exactly the duplication the extraction removed.
 */
public final class VolColocResult {

    private final VolColocParameters parameters;
    private final OverlapResult delegate;

    VolColocResult(VolColocParameters parameters, OverlapResult delegate) {
        this.parameters = parameters;
        this.delegate = delegate;
    }

    /**
     * The measured model, if you want the numbers without the tables — the
     * same object a plugin embedding {@code volcoloc-core} would receive.
     */
    public OverlapResult getOverlapResult() {
        return delegate;
    }

    /**
     * The parameters as supplied to this plugin, including the presentation
     * flags the engine has no opinion about.
     */
    public VolColocParameters getParameters() {
        return parameters;
    }

    public List<String> getChannelNames() {
        return delegate.getChannelNames();
    }

    public List<OverlapResult.DirectionResult> getDirectionResults() {
        return delegate.getDirectionResults();
    }

    public List<OverlapResult.MultiChannelResult> getMultiChannelResults() {
        return delegate.getMultiChannelResults();
    }

    public List<OverlapResult.BoundingBoxDirectionResult> getBoundingBoxDirectionResults() {
        return delegate.getBoundingBoxDirectionResults();
    }

    public ResultsTable getPerObjectTable(OverlapResult.DirectionResult direction) {
        ResultsTable table = new ResultsTable();
        declareColumns(table,
                "Source Channel", "Partner Channel", "Source Label",
                "Source Volume (voxels)",
                "Source Volume (" + direction.getVolumeUnit() + ")",
                "Overlap Voxels", "Overlap % of Source", "Best Partner Label",
                "Best Partner Overlap Voxels", "Partner Count", "Threshold %",
                "Colocalized");
        for (OverlapResult.ObjectResult row : direction.getObjects()) {
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

    public ResultsTable getPartnerDetailTable(OverlapResult.DirectionResult direction) {
        ResultsTable table = new ResultsTable();
        declareColumns(table,
                "Source Channel", "Partner Channel", "Source Label",
                "Partner Label", "Overlap Voxels", "Overlap % of Source",
                "Overlap % of Partner");
        for (OverlapResult.PartnerDetail row : direction.getPartnerDetails()) {
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
        for (OverlapResult.DirectionResult direction : getDirectionResults()) {
            OverlapResult.Summary summary = direction.getSummary();
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

    public ResultsTable getMultiPerObjectTable(OverlapResult.MultiChannelResult multi) {
        ResultsTable table = new ResultsTable();
        table.getFreeColumn("Label");
        List<String> channelNames = getChannelNames();
        for (int i = 0; i < channelNames.size(); i++) {
            if (i == multi.getSourceIndex()) continue;
            table.getFreeColumn(channelNames.get(i) + " Coloc");
            table.getFreeColumn(channelNames.get(i) + " Partner");
        }
        table.getFreeColumn("Targets Hit");
        for (OverlapResult.MultiObjectResult row : multi.getObjects()) {
            table.incrementCounter();
            table.addValue("Label", row.getSourceLabel());
            for (OverlapResult.TargetStatus status : row.getTargets()) {
                table.addValue(status.getTargetChannel() + " Coloc",
                        status.isColocalized() ? 1 : 0);
                table.addValue(status.getTargetChannel() + " Partner",
                        status.getPartnerLabel());
            }
            table.addValue("Targets Hit", row.getTargetsHit());
        }
        return table;
    }

    public ResultsTable getMultiSummaryTable() {
        ResultsTable table = new ResultsTable();
        declareColumns(table, "Source", "Pattern", "Count", "% of Source");
        for (OverlapResult.MultiChannelResult multi : getMultiChannelResults()) {
            for (OverlapResult.PatternSummary row : multi.getPatterns()) {
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
            OverlapResult.BoundingBoxDirectionResult direction) {
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
        for (OverlapResult.BoundingBoxObjectResult row : direction.getObjects()) {
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
        for (OverlapResult.BoundingBoxDirectionResult direction
                : getBoundingBoxDirectionResults()) {
            if (direction.isIncludeBoundingBoxOverlap()) {
                addBoundingSummaryRow(table, direction, "BBColoc", false);
            }
            if (direction.isIncludeBoundingBoxCpc()) {
                addBoundingSummaryRow(table, direction, "BB-CPC", false);
            }
            if (direction.isIncludeBoundingBoxVolumeFill()) {
                addBoundingSummaryRow(table, direction, "BBVolColoc", true);
            }
        }
        return table;
    }

    private static void addBoundingSummaryRow(
            ResultsTable table, OverlapResult.BoundingBoxDirectionResult direction,
            String method, boolean includeTotalFill) {
        List<OverlapResult.BoundingBoxObjectResult> objects = direction.getObjects();
        double[] values = new double[objects.size()];
        double totalFill = 0.0;
        int positives = 0;
        for (int i = 0; i < objects.size(); i++) {
            OverlapResult.BoundingBoxObjectResult object = objects.get(i);
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
}
