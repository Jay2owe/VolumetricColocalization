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
import ij.process.ShortProcessor;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Multi-slice coverage. Every other test image is a single plane, so without
 * these the Z axis of a volumetric plugin is never exercised.
 */
public class VolColocVolumeTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void measuresOverlapAcrossSlices() {
        // 1x1x4 column. A occupies z=0..2, B occupies z=2..3, so exactly one
        // of A's three voxels is overlapped: 1/3.
        ImagePlus a = stack("A", 1, 1, new int[][]{{1}, {1}, {1}, {0}});
        ImagePlus b = stack("B", 1, 1, new int[][]{{0}, {0}, {7}, {7}});

        VolColocResult result = VolColoc.run(a, b);

        VolColocResult.DirectionResult aToB = result.getDirectionResults().get(0);
        VolColocResult.ObjectResult a1 = aToB.getObjects().get(0);
        assertEquals(3, a1.getSourceVoxels());
        assertEquals(1, a1.getOverlapVoxels());
        assertEquals(100.0 / 3.0, a1.getOverlapPercent(), 0.0001);
        assertEquals(7, a1.getBestPartnerLabel());

        // The reverse direction uses B's volume as the denominator: 1/2.
        VolColocResult.DirectionResult bToA = result.getDirectionResults().get(1);
        VolColocResult.ObjectResult b7 = bToA.getObjects().get(0);
        assertEquals(2, b7.getSourceVoxels());
        assertEquals(1, b7.getOverlapVoxels());
        assertEquals(50.0, b7.getOverlapPercent(), 0.0001);
    }

    @Test
    public void appliesVoxelDepthToMultiSliceVolumes() {
        ImagePlus a = stack("A", 1, 1, new int[][]{{1}, {1}, {1}, {0}});
        ImagePlus b = stack("B", 1, 1, new int[][]{{0}, {0}, {7}, {7}});
        Calibration calibration = new Calibration();
        calibration.pixelWidth = 0.5;
        calibration.pixelHeight = 0.5;
        calibration.pixelDepth = 4.0;
        calibration.setUnit("um");
        a.setCalibration(calibration);

        VolColocResult.DirectionResult aToB =
                VolColoc.run(a, b).getDirectionResults().get(0);

        // 3 voxels x (0.5 * 0.5 * 4.0) = 3.0
        assertEquals(3.0, aToB.getObjects().get(0).getSourceVolume(), 0.0001);
        assertEquals("µm^3", aToB.getVolumeUnit());
    }

    @Test
    public void boundingBoxesSpanTheZAxis() {
        // A's single object spans all three slices, so its box is 1x1x3.
        ImagePlus a = stack("A", 1, 1, new int[][]{{1}, {0}, {1}});
        ImagePlus b = stack("B", 1, 1, new int[][]{{0}, {5}, {0}});

        VolColocResult result = VolColoc.run(
                VolColocParameters.builder(a, b)
                        .boundingBoxThresholdsPercent(Arrays.asList(20.0, 20.0))
                        .includeBoundingBoxOverlap(true)
                        .includeBoundingBoxCpc(true)
                        .includeBoundingBoxVolumeFill(true)
                        .build());

        VolColocResult.BoundingBoxObjectResult a1 =
                result.getBoundingBoxDirectionResults().get(0).getObjects().get(0);
        assertEquals(3, a1.getBoxVolume());
        // B's box is 1x1x1 at z=1, fully inside A's box: 1/3 of A's box.
        assertEquals(100.0 / 3.0, a1.getBoundingBoxOverlapPercent(), 0.0001);
        assertEquals(100.0 / 3.0, a1.getBoundingBoxVolumeBestPercent(), 0.0001);
        // A's own voxel overlap with B is zero even though the boxes intersect.
        assertEquals(0.0,
                result.getDirectionResults().get(0).getObjects().get(0)
                        .getOverlapPercent(), 0.0);
    }

    @Test
    public void retainsEveryPartnerWellBeyondTwo() {
        // One source object covering 300 voxels, each overlapped by a
        // different partner object.
        int count = 300;
        int[] sourceRow = new int[count];
        int[] partnerRow = new int[count];
        for (int i = 0; i < count; i++) {
            sourceRow[i] = 1;
            partnerRow[i] = i + 1;
        }
        ImagePlus a = plane("A", sourceRow);
        ImagePlus b = plane("B", partnerRow);

        VolColocResult.DirectionResult aToB = VolColoc.run(
                VolColocParameters.builder(a, b)
                        .minimumDetailOverlapPercent(0.0)
                        .build())
                .getDirectionResults().get(0);

        VolColocResult.ObjectResult a1 = aToB.getObjects().get(0);
        assertEquals(count, a1.getPartnerCount());
        assertEquals(count, a1.getOverlapVoxels());
        assertEquals(100.0, a1.getOverlapPercent(), 0.0001);
        assertEquals(count, aToB.getPartnerDetails().size());
    }

    @Test
    public void detailFilterNeverChangesAggregatesAtAnySetting() {
        ImagePlus a = plane("A", 1, 1, 1, 1, 2, 2, 3, 0);
        ImagePlus b = plane("B", 10, 10, 11, 0, 11, 12, 12, 0);
        ImagePlus c = plane("C", 20, 0, 20, 21, 21, 0, 22, 0);

        String reference = null;
        int previousDetails = Integer.MAX_VALUE;
        for (double filter : new double[]{0.0, 10.0, 25.0, 50.0, 75.0, 100.0}) {
            VolColocResult result = VolColoc.run(
                    VolColocParameters.builder(Arrays.asList(a, b, c))
                            .minimumDetailOverlapPercent(filter)
                            .build());
            String aggregates = serializeAggregates(result);
            if (reference == null) {
                reference = aggregates;
            } else {
                assertEquals("filter " + filter + " changed the aggregates",
                        reference, aggregates);
            }
            int details = 0;
            for (VolColocResult.DirectionResult direction
                    : result.getDirectionResults()) {
                details += direction.getPartnerDetails().size();
            }
            assertTrue("detail rows must not grow as the filter rises",
                    details <= previousDetails);
            previousDetails = details;
        }
    }

    @Test
    public void disablingPartnerDetailsLeavesAggregatesUnchanged() {
        ImagePlus a = plane("A", 1, 1, 1, 1, 2, 2);
        ImagePlus b = plane("B", 10, 10, 11, 0, 11, 0);

        String withDetails = serializeAggregates(VolColoc.run(
                VolColocParameters.builder(a, b)
                        .includePartnerDetails(true).build()));
        String withoutDetails = serializeAggregates(VolColoc.run(
                VolColocParameters.builder(a, b)
                        .includePartnerDetails(false).build()));

        assertEquals(withDetails, withoutDetails);
    }

    @Test
    public void breaksBestPartnerTiesOnTheLowestLabel() {
        // Labels 40 and 20 each overlap source object 1 by two voxels.
        ImagePlus a = plane("A", 1, 1, 1, 1);
        ImagePlus b = plane("B", 40, 40, 20, 20);

        VolColocResult.ObjectResult first = VolColoc.run(a, b)
                .getDirectionResults().get(0).getObjects().get(0);
        assertEquals(20, first.getBestPartnerLabel());
        assertEquals(2, first.getBestPartnerOverlapVoxels());

        // Reordering the partner labels in the image must not change it.
        ImagePlus reordered = plane("B", 20, 20, 40, 40);
        VolColocResult.ObjectResult second =
                VolColoc.run(plane("A", 1, 1, 1, 1), reordered)
                        .getDirectionResults().get(0).getObjects().get(0);
        assertEquals(20, second.getBestPartnerLabel());
    }

    @Test
    public void alwaysReportsTheNonePatternEvenWhenNothingIsUncolocalized() {
        // Every source object hits both targets, so None is never observed.
        // It must still be reported at zero: a script deriving the
        // non-colocalized count from that row would otherwise get nothing.
        ImagePlus a = plane("A", 1, 2);
        ImagePlus b = plane("B", 7, 7);
        ImagePlus c = plane("C", 8, 8);

        VolColocResult.MultiChannelResult multi = VolColoc.run(
                Arrays.asList(a, b, c)).getMultiChannelResults().get(0);

        boolean everythingHit = true;
        for (VolColocResult.MultiObjectResult object : multi.getObjects()) {
            if (VolColocAnalysis.NO_HITS_PATTERN.equals(object.getPattern())) {
                everythingHit = false;
            }
        }
        assertTrue("test setup: every object should be colocalized",
                everythingHit);

        VolColocResult.PatternSummary none = null;
        for (VolColocResult.PatternSummary pattern : multi.getPatterns()) {
            if (VolColocAnalysis.NO_HITS_PATTERN.equals(pattern.getPattern())) {
                none = pattern;
            }
        }
        assertEquals("None must be present", 0, none.getObjectCount());
        assertEquals(0.0, none.getObjectPercent(), 0.0);
    }

    @Test
    public void escapesTheAnyPatternWhenUsedAsAChannelName() {
        ImagePlus source = plane("Source", 1, 2);
        ImagePlus any = plane(VolColocAnalysis.ANY_PATTERN, 10, 0);
        ImagePlus other = plane("Other", 0, 30);

        VolColocResult.MultiChannelResult multi = VolColoc.run(
                Arrays.asList(source, any, other)).getMultiChannelResults().get(0);

        assertEquals("\"" + VolColocAnalysis.ANY_PATTERN + "\"",
                multi.getObjects().get(0).getPattern());
        // The escaped channel name stays distinct from the totals row.
        int totals = 0;
        int escaped = 0;
        for (VolColocResult.PatternSummary pattern : multi.getPatterns()) {
            if (VolColocAnalysis.ANY_PATTERN.equals(pattern.getPattern())) totals++;
            if (("\"" + VolColocAnalysis.ANY_PATTERN + "\"")
                    .equals(pattern.getPattern())) escaped++;
        }
        assertEquals(1, totals);
        assertEquals(1, escaped);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsRgbInputInsteadOfInventingLabelsFromColours() {
        ij.process.ColorProcessor colour = new ij.process.ColorProcessor(2, 1);
        colour.set(0, (1 << 16) | (2 << 8) | 3);
        VolColoc.run(new ImagePlus("RGB", colour), plane("B", 1, 0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullThresholdEntries() {
        VolColoc.run(VolColocParameters.builder(plane("A", 1), plane("B", 1))
                .thresholdsPercent(Arrays.asList(Double.valueOf(30.0), null))
                .build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyImages() {
        VolColoc.run(new ImagePlus(), new ImagePlus());
    }

    /**
     * The type check must not over-reach. Every one of these is a legitimate
     * label image and must survive validation.
     */
    @Test
    public void acceptsEveryLegitimateLabelImageType() {
        ij.process.ByteProcessor eightBit = new ij.process.ByteProcessor(2, 1);
        eightBit.set(0, 1);
        ij.process.ShortProcessor sixteenBit = new ij.process.ShortProcessor(2, 1);
        sixteenBit.set(0, 70000 % 65536);
        ij.process.FloatProcessor thirtyTwoBit = new ij.process.FloatProcessor(2, 1);
        thirtyTwoBit.setf(0, 1f);

        assertEquals(1, VolColoc.run(new ImagePlus("8-bit", eightBit),
                plane("B", 1, 0)).getDirectionResults()
                .get(0).getObjects().size());
        assertEquals(1, VolColoc.run(new ImagePlus("16-bit", sixteenBit),
                plane("B", 1, 0)).getDirectionResults()
                .get(0).getObjects().size());
        assertEquals(1, VolColoc.run(new ImagePlus("32-bit", thirtyTwoBit),
                plane("B", 1, 0)).getDirectionResults()
                .get(0).getObjects().size());
    }

    @Test
    public void acceptsIndexedColourLabelImages() {
        // A label image carrying a glasbey-style LUT still stores the label as
        // its pixel value, so it must not be turned away.
        ij.process.ByteProcessor indexed = new ij.process.ByteProcessor(4, 1);
        indexed.set(0, 1);
        indexed.set(1, 2);
        indexed.set(3, 3);
        ImagePlus image = new ImagePlus("indexed", indexed);
        image.setTypeToColor256();

        VolColocResult.DirectionResult direction = VolColoc.run(
                image, plane("B", 9, 9, 0, 0)).getDirectionResults().get(0);

        assertEquals(3, direction.getObjects().size());
        assertEquals(1, direction.getObjects().get(0).getSourceLabel());
        assertEquals(100.0,
                direction.getObjects().get(0).getOverlapPercent(), 0.0);
    }

    @Test
    public void acceptsSingleSliceAndPlainZStackInputs() {
        assertEquals(1, VolColoc.run(plane("A", 1, 0), plane("B", 1, 0))
                .getDirectionResults().get(0).getObjects().size());

        // A plain z-stack — 1 channel, N slices, 1 frame — is the normal case
        // and must keep working.
        assertEquals(1, VolColoc.run(
                        stack("A", 1, 1, new int[][]{{1}, {1}, {1}}),
                        stack("B", 1, 1, new int[][]{{9}, {0}, {9}}))
                .getDirectionResults().get(0).getObjects().size());
    }

    /**
     * The hyperstack rejection must not catch the ordinary saved z-stack the
     * batch runner opens with {@code IJ.openImage}. This is the over-rejection
     * guard: if it ever fails, normal label images are being turned away.
     */
    @Test
    public void acceptsOrdinaryTiffsAfterASaveAndReopenRoundTrip()
            throws Exception {
        File directory = temporary.newFolder("round-trip");

        ImagePlus flat = reopen(directory, "flat.tif", plane("A", 1, 1, 0));
        assertEquals(1, flat.getNSlices());
        assertEquals(1, flat.getNFrames());

        ImagePlus zStack = reopen(directory, "stack.tif",
                stack("A", 1, 1, new int[][]{{1}, {1}, {1}, {0}, {1}}));
        assertEquals("a saved stack must reopen as slices, not frames",
                5, zStack.getNSlices());
        assertEquals(1, zStack.getNFrames());

        // Both must survive validation.
        assertEquals(1, VolColoc.run(flat, plane("B", 9, 0, 0))
                .getDirectionResults().get(0).getObjects().size());
        assertEquals(1, VolColoc.run(zStack,
                        stack("B", 1, 1, new int[][]{{9}, {9}, {0}, {0}, {0}}))
                .getDirectionResults().get(0).getObjects().size());
    }

    private ImagePlus reopen(File directory, String name, ImagePlus image) {
        File file = new File(directory, name);
        ij.IJ.saveAsTiff(image, file.getAbsolutePath());
        return ij.IJ.openImage(file.getAbsolutePath());
    }

    @Test
    public void rejectsMultiChannelHyperstacksRatherThanCountingChannelsAsDepth() {
        // Label 1 fills all 6 planes of a 2c x 3z hyperstack. Flattening
        // reported a volume of 6 for an object only 3 slices deep.
        ImagePlus a = hyperstack("A", 2, 3, 1);
        ImagePlus b = hyperstack("B", 2, 3, 1);
        try {
            VolColoc.run(a, b);
            throw new AssertionError("Expected a hyperstack to be rejected.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("hyperstack"));
            assertTrue(expected.getMessage().contains("2 channel(s)"));
        }
    }

    @Test
    public void rejectsTimeSeriesRatherThanPoolingFramesIntoOneVolume() {
        // One voxel per frame across 4 frames previously reported a volume of
        // 4, pooling the time course into a single object.
        ImagePlus a = hyperstack("A", 1, 1, 4);
        ImagePlus b = hyperstack("B", 1, 1, 4);
        try {
            VolColoc.run(a, b);
            throw new AssertionError("Expected a time series to be rejected.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("hyperstack"));
            assertTrue(expected.getMessage().contains("4 frame(s)"));
        }
    }

    private static ImagePlus hyperstack(
            String title, int channels, int slices, int frames) {
        ImageStack stack = new ImageStack(1, 1);
        for (int i = 0; i < channels * slices * frames; i++) {
            ShortProcessor processor = new ShortProcessor(1, 1);
            processor.set(0, 1);
            stack.addSlice(processor);
        }
        ImagePlus result = new ImagePlus(title, stack);
        result.setDimensions(channels, slices, frames);
        return result;
    }

    @Test
    public void directionsAreBuiltOnceAndReusedByMultiColocalization() {
        ImagePlus a = plane("A", 1, 1, 0);
        ImagePlus b = plane("B", 2, 0, 0);
        ImagePlus c = plane("C", 3, 3, 0);
        List<String> names = Arrays.asList("A", "B", "C");
        List<Double> thresholds = Arrays.asList(30.0, 30.0, 30.0);

        VolColocParameters parameters =
                VolColocParameters.builder(Arrays.asList(a, b, c)).build();
        VolColocAnalysis analysis = new VolColocAnalysis(
                parameters, names, thresholds, thresholds);
        VolColocResult result = analysis.run();

        // Three channels give six ordered pairs. Bidirectional output needs all
        // six and multi-colocalization needs the same six, so a cache miss on
        // reuse would show up here as twelve.
        assertEquals(6, analysis.getDirectionBuilds());
        assertEquals(6, result.getDirectionResults().size());
        assertEquals(3, result.getMultiChannelResults().size());
        for (VolColocResult.DirectionResult direction
                : result.getDirectionResults()) {
            assertFalse(direction.getObjects().isEmpty());
        }
    }

    @Test
    public void unidirectionalRunsStillBuildEachDirectionOnce() {
        VolColocParameters parameters = VolColocParameters.builder(
                        Arrays.asList(plane("A", 1, 1, 0), plane("B", 2, 0, 0),
                                plane("C", 3, 3, 0)))
                .bidirectional(false)
                .build();
        List<String> names = Arrays.asList("A", "B", "C");
        List<Double> thresholds = Arrays.asList(30.0, 30.0, 30.0);

        VolColocAnalysis analysis = new VolColocAnalysis(
                parameters, names, thresholds, thresholds);
        VolColocResult result = analysis.run();

        // Only three directions are reported, but multi-colocalization still
        // needs all six ordered pairs; none may be built twice.
        assertEquals(3, result.getDirectionResults().size());
        assertEquals(6, analysis.getDirectionBuilds());
    }

    @Test
    public void cachedDirectionsMatchAnUncachedRecomputation() {
        // Guards against the cache ever returning a direction built for a
        // different (source, target) ordering.
        ImagePlus a = plane("A", 1, 1, 2, 2, 3, 0);
        ImagePlus b = plane("B", 7, 0, 7, 8, 8, 0);
        ImagePlus c = plane("C", 5, 5, 0, 6, 6, 6);
        List<ImagePlus> images = Arrays.asList(a, b, c);

        VolColocResult cached = VolColoc.run(
                VolColocParameters.builder(images)
                        .thresholdsPercent(Arrays.asList(10.0, 40.0, 70.0))
                        .build());

        for (VolColocResult.DirectionResult direction
                : cached.getDirectionResults()) {
            // Recompute this one direction on its own, with no other pair in
            // play, and require an identical answer.
            VolColocResult isolated = VolColoc.run(
                    VolColocParameters.builder(Arrays.asList(
                                    images.get(direction.getSourceIndex()),
                                    images.get(direction.getTargetIndex())))
                            .thresholdsPercent(Arrays.asList(
                                    direction.getThresholdPercent(), 30.0))
                            .bidirectional(false)
                            .build());
            VolColocResult.DirectionResult reference =
                    isolated.getDirectionResults().get(0);
            assertEquals(reference.getObjects().size(),
                    direction.getObjects().size());
            for (int i = 0; i < reference.getObjects().size(); i++) {
                VolColocResult.ObjectResult expected = reference.getObjects().get(i);
                VolColocResult.ObjectResult actual = direction.getObjects().get(i);
                assertEquals(expected.getSourceLabel(), actual.getSourceLabel());
                assertEquals(expected.getOverlapVoxels(), actual.getOverlapVoxels());
                assertEquals(expected.getOverlapPercent(),
                        actual.getOverlapPercent(), 0.0);
                assertEquals(expected.getBestPartnerLabel(),
                        actual.getBestPartnerLabel());
                assertEquals(expected.getPartnerCount(), actual.getPartnerCount());
                assertEquals(expected.isColocalized(), actual.isColocalized());
            }
        }
    }

    private static String serializeAggregates(VolColocResult result) {
        StringBuilder text = new StringBuilder();
        for (VolColocResult.DirectionResult direction
                : result.getDirectionResults()) {
            text.append(direction.getSourceChannel()).append("->")
                    .append(direction.getTargetChannel()).append('\n');
            for (VolColocResult.ObjectResult object : direction.getObjects()) {
                text.append(object.getSourceLabel()).append('|')
                        .append(object.getSourceVoxels()).append('|')
                        .append(object.getOverlapVoxels()).append('|')
                        .append(object.getOverlapPercent()).append('|')
                        .append(object.getBestPartnerLabel()).append('|')
                        .append(object.getBestPartnerOverlapVoxels()).append('|')
                        .append(object.getPartnerCount()).append('|')
                        .append(object.isColocalized()).append('\n');
            }
            VolColocResult.Summary summary = direction.getSummary();
            text.append("summary|").append(summary.getObjectCount()).append('|')
                    .append(summary.getMeanOverlapPercent()).append('|')
                    .append(summary.getMedianOverlapPercent()).append('|')
                    .append(summary.getColocalizedCount()).append('|')
                    .append(summary.getColocalizedPercent()).append('\n');
        }
        for (VolColocResult.MultiChannelResult multi
                : result.getMultiChannelResults()) {
            text.append("multi ").append(multi.getSourceChannel()).append('\n');
            for (VolColocResult.MultiObjectResult object : multi.getObjects()) {
                text.append(object.getSourceLabel()).append('|')
                        .append(object.getPattern()).append('\n');
            }
            for (VolColocResult.PatternSummary pattern : multi.getPatterns()) {
                text.append(pattern.getPattern()).append('|')
                        .append(pattern.getObjectCount()).append('|')
                        .append(pattern.getObjectPercent()).append('\n');
            }
        }
        return text.toString();
    }

    private static ImagePlus plane(String title, int... values) {
        ShortProcessor processor = new ShortProcessor(values.length, 1);
        for (int i = 0; i < values.length; i++) processor.set(i, values[i]);
        return new ImagePlus(title, processor);
    }

    /** One {@code int[]} per slice, each holding {@code width * height} labels. */
    private static ImagePlus stack(
            String title, int width, int height, int[][] slices) {
        ImageStack stack = new ImageStack(width, height);
        List<int[]> planes = new ArrayList<int[]>(Arrays.asList(slices));
        for (int[] plane : planes) {
            ShortProcessor processor = new ShortProcessor(width, height);
            for (int i = 0; i < plane.length; i++) processor.set(i, plane[i]);
            stack.addSlice(processor);
        }
        return new ImagePlus(title, stack);
    }
}
