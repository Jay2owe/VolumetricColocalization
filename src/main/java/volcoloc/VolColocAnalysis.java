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
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ImageProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Single-pass voxel engine. All pair counts survive; no best-partner collapse
 * occurs in the hot path.
 */
final class VolColocAnalysis {

    static final String NO_HITS_PATTERN = "None";
    static final String ANY_PATTERN = "\u2014 Any \u2014";
    private static final String PATTERN_SEPARATOR = " + ";

    private final VolColocParameters parameters;
    private final List<ImagePlus> images;
    private final List<String> channelNames;
    private final List<Double> thresholds;
    private final List<Double> boundingBoxThresholds;
    private PrimitiveMaps.IntIntMap[] sizes;
    private PrimitiveMaps.LongIntMap[][] overlaps;
    /**
     * Directions are requested twice — once for the pairwise output and again
     * per ordered pair by the multi-colocalization pass — so each is built at
     * most once and reused.
     */
    private final Map<Long, VolColocResult.DirectionResult> directionCache =
            new HashMap<Long, VolColocResult.DirectionResult>();

    VolColocAnalysis(VolColocParameters parameters,
                     List<String> channelNames,
                     List<Double> thresholds,
                     List<Double> boundingBoxThresholds) {
        this.parameters = parameters;
        this.images = parameters.getImages();
        this.channelNames = channelNames;
        this.thresholds = thresholds;
        this.boundingBoxThresholds = boundingBoxThresholds;
    }

    VolColocResult run() {
        scan();
        List<VolColocResult.DirectionResult> directions =
                new ArrayList<VolColocResult.DirectionResult>();
        int count = images.size();
        for (int a = 0; a < count; a++) {
            for (int b = a + 1; b < count; b++) {
                directions.add(direction(a, b));
                if (parameters.isBidirectional()) {
                    directions.add(direction(b, a));
                }
            }
        }

        List<VolColocResult.MultiChannelResult> multi =
                parameters.isIncludeMultiColocalization() && images.size() >= 3
                        ? buildMultiResults()
                        : Collections.<VolColocResult.MultiChannelResult>emptyList();
        List<VolColocResult.BoundingBoxDirectionResult> boundingBoxes =
                parameters.hasBoundingBoxAnalysis()
                        ? new BoundingBoxAnalysis(
                                parameters,
                                images,
                                channelNames,
                                boundingBoxThresholds).run()
                        : Collections.<VolColocResult.BoundingBoxDirectionResult>emptyList();
        // Reverse directions built only for the multi pass are not part of the
        // output; drop the cache so they become collectible.
        directionCache.clear();
        return new VolColocResult(
                parameters, channelNames, directions, multi, boundingBoxes);
    }

    private void scan() {
        int channelCount = images.size();
        sizes = new PrimitiveMaps.IntIntMap[channelCount];
        overlaps = new PrimitiveMaps.LongIntMap[channelCount][channelCount];
        for (int i = 0; i < channelCount; i++) {
            sizes[i] = new PrimitiveMaps.IntIntMap();
            for (int j = i + 1; j < channelCount; j++) {
                overlaps[i][j] = new PrimitiveMaps.LongIntMap();
            }
        }

        ImageStack[] stacks = new ImageStack[channelCount];
        for (int i = 0; i < channelCount; i++) stacks[i] = images.get(i).getStack();
        int[] labels = new int[channelCount];
        int slices = stacks[0].getSize();
        for (int slice = 1; slice <= slices; slice++) {
            ImageProcessor[] processors = new ImageProcessor[channelCount];
            for (int c = 0; c < channelCount; c++) {
                processors[c] = stacks[c].getProcessor(slice);
            }
            int pixels = processors[0].getPixelCount();
            for (int pixel = 0; pixel < pixels; pixel++) {
                for (int c = 0; c < channelCount; c++) {
                    int label = readLabel(processors[c], pixel, c, slice);
                    labels[c] = label;
                    if (label > 0) sizes[c].increment(label);
                }
                for (int a = 0; a < channelCount; a++) {
                    if (labels[a] <= 0) continue;
                    for (int b = a + 1; b < channelCount; b++) {
                        if (labels[b] > 0) {
                            overlaps[a][b].increment(pack(labels[a], labels[b]));
                        }
                    }
                }
            }
        }
    }

    private int readLabel(ImageProcessor processor, int pixel, int channel, int slice) {
        double value = processor.getf(pixel);
        if (!Double.isFinite(value) || value < 0.0
                || value > Integer.MAX_VALUE
                || value != Math.rint(value)) {
            throw new IllegalArgumentException("Image " + (channel + 1)
                    + " contains an invalid label value (" + value + ") on slice "
                    + slice + ". Labels must be non-negative integers.");
        }
        return (int) value;
    }

    /** Number of {@link #buildDirection} calls; asserted by the tests. */
    private int directionBuilds;

    int getDirectionBuilds() {
        return directionBuilds;
    }

    private VolColocResult.DirectionResult direction(int source, int target) {
        Long key = Long.valueOf(pack(source, target));
        VolColocResult.DirectionResult cached = directionCache.get(key);
        if (cached == null) {
            directionBuilds++;
            cached = buildDirection(source, target);
            directionCache.put(key, cached);
        }
        return cached;
    }

    private VolColocResult.DirectionResult buildDirection(final int source,
                                                          final int target) {
        final boolean naturalOrder = source < target;
        PrimitiveMaps.LongIntMap pairMap = naturalOrder
                ? overlaps[source][target]
                : overlaps[target][source];
        final Map<Integer, List<RawPartner>> bySource =
                new TreeMap<Integer, List<RawPartner>>();

        pairMap.forEach(new PrimitiveMaps.LongIntConsumer() {
            @Override
            public void accept(long key, int overlapVoxels) {
                int first = first(key);
                int second = second(key);
                int sourceLabel = naturalOrder ? first : second;
                int targetLabel = naturalOrder ? second : first;
                List<RawPartner> partners = bySource.get(Integer.valueOf(sourceLabel));
                if (partners == null) {
                    partners = new ArrayList<RawPartner>();
                    bySource.put(Integer.valueOf(sourceLabel), partners);
                }
                partners.add(new RawPartner(targetLabel, overlapVoxels));
            }
        });

        List<VolColocResult.ObjectResult> objectRows =
                new ArrayList<VolColocResult.ObjectResult>();
        List<VolColocResult.PartnerDetail> detailRows =
                new ArrayList<VolColocResult.PartnerDetail>();
        int[] sourceLabels = sizes[source].sortedKeys();
        Calibration calibration = images.get(source).getCalibration();
        double voxelVolume = voxelVolume(calibration);
        String volumeUnit = volumeUnit(calibration);
        double threshold = thresholds.get(source).doubleValue();
        int colocalized = 0;
        double[] percentages = new double[sourceLabels.length];

        for (int rowIndex = 0; rowIndex < sourceLabels.length; rowIndex++) {
            int sourceLabel = sourceLabels[rowIndex];
            int sourceVoxels = sizes[source].get(sourceLabel);
            List<RawPartner> partners = bySource.get(Integer.valueOf(sourceLabel));
            int totalOverlap = 0;
            int bestLabel = 0;
            int bestOverlap = 0;
            int partnerCount = partners == null ? 0 : partners.size();
            if (partners != null) {
                Collections.sort(partners, new Comparator<RawPartner>() {
                    @Override
                    public int compare(RawPartner left, RawPartner right) {
                        return Integer.compare(left.label, right.label);
                    }
                });
                for (RawPartner partner : partners) {
                    totalOverlap += partner.overlap;
                    if (partner.overlap > bestOverlap
                            || (partner.overlap == bestOverlap && partner.label < bestLabel)) {
                        bestOverlap = partner.overlap;
                        bestLabel = partner.label;
                    }
                    double sourcePartnerPercent =
                            percent(partner.overlap, sourceVoxels);
                    if (parameters.isIncludePartnerDetails()
                            && sourcePartnerPercent
                            >= parameters.getMinimumDetailOverlapPercent()) {
                        int targetVoxels = sizes[target].get(partner.label);
                        detailRows.add(new VolColocResult.PartnerDetail(
                                sourceLabel,
                                partner.label,
                                partner.overlap,
                                sourcePartnerPercent,
                                percent(partner.overlap, targetVoxels)));
                    }
                }
            }
            double overlapPercent = percent(totalOverlap, sourceVoxels);
            percentages[rowIndex] = overlapPercent;
            boolean passes = overlapPercent >= threshold;
            if (passes) colocalized++;
            objectRows.add(new VolColocResult.ObjectResult(
                    sourceLabel,
                    sourceVoxels,
                    sourceVoxels * voxelVolume,
                    volumeUnit,
                    totalOverlap,
                    overlapPercent,
                    bestLabel,
                    bestOverlap,
                    partnerCount,
                    passes));
        }

        double mean = mean(percentages);
        double median = median(percentages);
        VolColocResult.Summary summary = new VolColocResult.Summary(
                sourceLabels.length,
                mean,
                median,
                colocalized,
                percent(colocalized, sourceLabels.length));
        return new VolColocResult.DirectionResult(
                source,
                target,
                channelNames.get(source),
                channelNames.get(target),
                threshold,
                volumeUnit,
                objectRows,
                detailRows,
                summary);
    }

    private List<VolColocResult.MultiChannelResult> buildMultiResults() {
        List<VolColocResult.MultiChannelResult> result =
                new ArrayList<VolColocResult.MultiChannelResult>();
        // Match CPC: multi-target analysis treats every input image as a
        // source, independently of the pairwise bidirectional setting.
        int sourceCount = images.size();
        for (int source = 0; source < sourceCount; source++) {
            Map<Integer, List<VolColocResult.TargetStatus>> statuses =
                    new TreeMap<Integer, List<VolColocResult.TargetStatus>>();
            int[] sourceLabels = sizes[source].sortedKeys();
            for (int sourceLabel : sourceLabels) {
                statuses.put(Integer.valueOf(sourceLabel),
                        new ArrayList<VolColocResult.TargetStatus>());
            }

            for (int target = 0; target < images.size(); target++) {
                if (target == source) continue;
                VolColocResult.DirectionResult direction = direction(source, target);
                for (VolColocResult.ObjectResult object : direction.getObjects()) {
                    statuses.get(Integer.valueOf(object.getSourceLabel())).add(
                            new VolColocResult.TargetStatus(
                                    target,
                                    channelNames.get(target),
                                    object.getBestPartnerLabel(),
                                    object.getOverlapPercent(),
                                    object.isColocalized()));
                }
            }

            List<VolColocResult.MultiObjectResult> objects =
                    new ArrayList<VolColocResult.MultiObjectResult>();
            Map<String, Integer> patternCounts = new LinkedHashMap<String, Integer>();
            int anyCount = 0;
            for (int sourceLabel : sourceLabels) {
                List<VolColocResult.TargetStatus> targetStatuses =
                        statuses.get(Integer.valueOf(sourceLabel));
                Collections.sort(targetStatuses, new Comparator<VolColocResult.TargetStatus>() {
                    @Override
                    public int compare(VolColocResult.TargetStatus left,
                                       VolColocResult.TargetStatus right) {
                        return Integer.compare(left.getTargetIndex(), right.getTargetIndex());
                    }
                });
                String pattern = pattern(targetStatuses);
                if (!NO_HITS_PATTERN.equals(pattern)) anyCount++;
                objects.add(new VolColocResult.MultiObjectResult(
                        sourceLabel, pattern, targetStatuses));
                Integer previous = patternCounts.get(pattern);
                patternCounts.put(pattern, Integer.valueOf(previous == null ? 1 : previous + 1));
            }

            // Always report None, even at zero. A script deriving the
            // non-colocalized count from this row must not silently get
            // nothing back for a perfectly colocalized image. Pull it out of
            // the observed order and place it immediately before the total, so
            // the last two rows are the same for every image.
            Integer noHits = patternCounts.remove(NO_HITS_PATTERN);
            List<VolColocResult.PatternSummary> patterns =
                    new ArrayList<VolColocResult.PatternSummary>();
            for (Map.Entry<String, Integer> entry : patternCounts.entrySet()) {
                patterns.add(new VolColocResult.PatternSummary(
                        entry.getKey(),
                        entry.getValue().intValue(),
                        roundedPercent(entry.getValue().intValue(), sourceLabels.length)));
            }
            int noHitsCount = noHits == null ? 0 : noHits.intValue();
            patterns.add(new VolColocResult.PatternSummary(
                    NO_HITS_PATTERN,
                    noHitsCount,
                    roundedPercent(noHitsCount, sourceLabels.length)));
            patterns.add(new VolColocResult.PatternSummary(
                    ANY_PATTERN,
                    anyCount,
                    roundedPercent(anyCount, sourceLabels.length)));
            result.add(new VolColocResult.MultiChannelResult(
                    source, channelNames.get(source), objects, patterns));
        }
        return result;
    }

    private static String pattern(List<VolColocResult.TargetStatus> statuses) {
        StringBuilder builder = new StringBuilder();
        for (VolColocResult.TargetStatus status : statuses) {
            if (!status.isColocalized()) continue;
            if (builder.length() > 0) builder.append(PATTERN_SEPARATOR);
            builder.append(patternComponent(status.getTargetChannel()));
        }
        return builder.length() == 0 ? NO_HITS_PATTERN : builder.toString();
    }

    private static String patternComponent(String channel) {
        if (!NO_HITS_PATTERN.equals(channel)
                && !ANY_PATTERN.equals(channel)
                && channel.indexOf(PATTERN_SEPARATOR) < 0
                && channel.indexOf('"') < 0
                && channel.indexOf('\\') < 0) {
            return channel;
        }
        return "\"" + channel.replace("\\", "\\\\")
                .replace("\"", "\\\"") + "\"";
    }

    private static double roundedPercent(int numerator, int denominator) {
        if (denominator == 0) return 0.0;
        return Math.round(numerator * 10000.0 / denominator) / 100.0;
    }

    private static double mean(double[] values) {
        if (values.length == 0) return 0.0;
        double sum = 0.0;
        for (double value : values) sum += value;
        return sum / values.length;
    }

    private static double median(double[] values) {
        if (values.length == 0) return 0.0;
        double[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        if ((sorted.length & 1) == 1) return sorted[middle];
        return (sorted[middle - 1] + sorted[middle]) / 2.0;
    }

    private static double percent(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : numerator * 100.0 / denominator;
    }

    private static double voxelVolume(Calibration calibration) {
        if (calibration == null) return 1.0;
        double width = positiveOrOne(calibration.pixelWidth);
        double height = positiveOrOne(calibration.pixelHeight);
        double depth = positiveOrOne(calibration.pixelDepth);
        return width * height * depth;
    }

    private static double positiveOrOne(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 1.0;
    }

    private static String volumeUnit(Calibration calibration) {
        if (calibration == null) return "pixel^3";
        String unit = calibration.getUnit();
        if (unit == null || unit.trim().length() == 0 || "pixel".equalsIgnoreCase(unit)) {
            return "pixel^3";
        }
        String normalized = unit.trim();
        if ("micron".equalsIgnoreCase(normalized)
                || "microns".equalsIgnoreCase(normalized)
                || "um".equalsIgnoreCase(normalized)
                || "\u00b5m".equalsIgnoreCase(normalized)) {
            return "\u00b5m^3";
        }
        return normalized + "^3";
    }

    private static long pack(int first, int second) {
        return ((long) first << 32) | (second & 0xffffffffL);
    }

    private static int first(long key) {
        return (int) (key >>> 32);
    }

    private static int second(long key) {
        return (int) key;
    }

    private static final class RawPartner {
        final int label;
        final int overlap;

        RawPartner(int label, int overlap) {
            this.label = label;
            this.overlap = overlap;
        }
    }
}
