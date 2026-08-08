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

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VolColocTest {

    @Test
    public void retainsAllPartnersAndUsesTotalOccupiedVolume() {
        ImagePlus a = labels("A", 1, 1, 1, 1, 2, 2);
        ImagePlus b = labels("B", 10, 10, 11, 0, 11, 0);

        VolColocResult result = VolColoc.run(
                VolColocParameters.builder(a, b)
                        .thresholdsPercent(Arrays.asList(60.0, 30.0))
                        .minimumDetailOverlapPercent(0.0)
                        .build());

        assertEquals(2, result.getDirectionResults().size());
        OverlapResult.DirectionResult aToB = result.getDirectionResults().get(0);
        assertEquals("A", aToB.getSourceChannel());
        assertEquals(2, aToB.getObjects().size());

        OverlapResult.ObjectResult a1 = aToB.getObjects().get(0);
        assertEquals(1, a1.getSourceLabel());
        assertEquals(3, a1.getOverlapVoxels());
        assertEquals(75.0, a1.getOverlapPercent(), 0.0001);
        assertEquals(10, a1.getBestPartnerLabel());
        assertEquals(2, a1.getBestPartnerOverlapVoxels());
        assertEquals(2, a1.getPartnerCount());
        assertTrue(a1.isColocalized());

        OverlapResult.ObjectResult a2 = aToB.getObjects().get(1);
        assertEquals(50.0, a2.getOverlapPercent(), 0.0001);
        assertFalse(a2.isColocalized());
        assertEquals(3, aToB.getPartnerDetails().size());
        assertEquals(62.5, aToB.getSummary().getMeanOverlapPercent(), 0.0001);
        assertEquals(1, aToB.getSummary().getColocalizedCount());
    }

    @Test
    public void percentageDetailFilterDoesNotAlterObjectMeasurements() {
        ImagePlus a = labels("A", 1, 1, 1, 1, 2, 2);
        ImagePlus b = labels("B", 10, 10, 11, 0, 11, 0);

        VolColocResult result = VolColoc.run(
                VolColocParameters.builder(a, b)
                        .minimumDetailOverlapPercent(50.0)
                        .build());

        OverlapResult.DirectionResult aToB = result.getDirectionResults().get(0);
        assertEquals(2, aToB.getPartnerDetails().size());
        assertEquals(3, aToB.getObjects().get(0).getOverlapVoxels());
        assertEquals(2, aToB.getObjects().get(0).getPartnerCount());
    }

    @Test
    public void buildsDirectionalMultiChannelPatterns() {
        ImagePlus a = labels("A", 1, 1, 1, 1, 2, 2);
        ImagePlus b = labels("B", 10, 10, 11, 0, 11, 0);
        ImagePlus c = labels("C", 20, 0, 20, 0, 0, 0);

        VolColocResult result = VolColoc.run(
                VolColocParameters.builder(Arrays.asList(a, b, c))
                        .thresholdsPercent(Arrays.asList(60.0, 30.0, 30.0))
                        .build());

        assertEquals(3, result.getMultiChannelResults().size());
        OverlapResult.MultiChannelResult multiA =
                result.getMultiChannelResults().get(0);
        assertEquals("B", multiA.getObjects().get(0).getPattern());
        assertEquals("B", multiA.getPatterns().get(0).getPattern());
        assertEquals("\u2014 Any \u2014",
                multiA.getPatterns().get(multiA.getPatterns().size() - 1)
                        .getPattern());
        assertEquals(1.0,
                result.getMultiPerObjectTable(multiA)
                        .getValue("B Coloc", 0),
                0.0);
        assertEquals(10.0,
                result.getMultiPerObjectTable(multiA)
                        .getValue("B Partner", 0),
                0.0);
    }

    @Test
    public void multiTargetsUseEverySourceLikeCpcWhenPairwiseIsUnidirectional() {
        ImagePlus a = labels("A", 1, 1, 0);
        ImagePlus b = labels("B", 2, 0, 0);
        ImagePlus c = labels("C", 3, 3, 0);

        VolColocResult result = VolColoc.run(
                VolColocParameters.builder(Arrays.asList(a, b, c))
                        .bidirectional(false)
                        .build());

        assertEquals(3, result.getDirectionResults().size());
        assertEquals(3, result.getMultiChannelResults().size());
        assertEquals("A", result.getMultiChannelResults().get(0)
                .getSourceChannel());
        assertEquals("B", result.getMultiChannelResults().get(1)
                .getSourceChannel());
        assertEquals("C", result.getMultiChannelResults().get(2)
                .getSourceChannel());
    }

    @Test
    public void escapesAmbiguousMultiPatternChannelNames() {
        ImagePlus source = labels("Source", 1, 2, 3);
        ImagePlus none = labels("None", 10, 0, 0);
        ImagePlus separator = labels("X + Y", 0, 0, 20);

        VolColocResult result = VolColoc.run(
                Arrays.asList(source, none, separator));
        OverlapResult.MultiChannelResult multi =
                result.getMultiChannelResults().get(0);

        assertEquals("\"None\"", multi.getObjects().get(0).getPattern());
        assertEquals("None", multi.getObjects().get(1).getPattern());
        assertEquals("\"X + Y\"", multi.getObjects().get(2).getPattern());
        assertEquals(2, patternCount(multi, "\u2014 Any \u2014"));
        assertEquals(66.67,
                patternPercent(multi, "\u2014 Any \u2014"), 0.0);
    }

    @Test
    public void supportsFiveInputMultiAnalysis() {
        VolColocResult result = VolColoc.run(Arrays.asList(
                labels("A", 1),
                labels("B", 2),
                labels("C", 3),
                labels("D", 4),
                labels("E", 5)));

        assertEquals(5, result.getMultiChannelResults().size());
        assertEquals(4, result.getMultiChannelResults().get(0)
                .getObjects().get(0).getTargets().size());
    }

    @Test
    public void computesAllThreeBoundingBoxFamilies() {
        ImagePlus a = labels2d("A", 5, 3,
                1, 0, 1, 0, 2,
                0, 0, 0, 0, 2,
                1, 0, 1, 0, 0);
        ImagePlus b = labels2d("B", 5, 3,
                0, 0, 11, 0, 0,
                0, 10, 0, 10, 0,
                0, 10, 0, 10, 0);

        VolColocResult result = VolColoc.run(
                VolColocParameters.builder(a, b)
                        .boundingBoxThresholdsPercent(
                                Arrays.asList(20.0, 20.0))
                        .includeBoundingBoxOverlap(true)
                        .includeBoundingBoxCpc(true)
                        .includeBoundingBoxVolumeFill(true)
                        .build());

        assertEquals(2, result.getBoundingBoxDirectionResults().size());
        OverlapResult.BoundingBoxDirectionResult aToB =
                result.getBoundingBoxDirectionResults().get(0);
        OverlapResult.BoundingBoxObjectResult a1 =
                aToB.getObjects().get(0);
        assertEquals(9, a1.getBoxVolume());
        assertEquals(4.0 / 9.0 * 100.0,
                a1.getBoundingBoxOverlapPercent(), 0.0001);
        assertEquals(10, a1.getBoundingBoxOverlapPartnerLabel());
        assertTrue(a1.isBoundingBoxOverlapColocalized());
        assertTrue(a1.isBoundingBoxCpcColocalized());
        assertEquals(10, a1.getBoundingBoxCpcPartnerLabel());
        assertEquals(2, a1.getBoundingBoxCpcContainsCount());
        assertEquals(2.0 / 9.0 * 100.0,
                a1.getBoundingBoxVolumeBestPercent(), 0.0001);
        assertEquals(3.0 / 9.0 * 100.0,
                a1.getBoundingBoxVolumeTotalPercent(), 0.0001);
        assertEquals(10, a1.getBoundingBoxVolumePartnerLabel());
        assertTrue(a1.isBoundingBoxVolumeColocalized());
        assertEquals(6, result.getBoundingBoxSummaryTable().getCounter());
    }

    @Test
    public void reportsCalibratedVolume() {
        ImagePlus a = labels("A", 1, 1);
        ImagePlus b = labels("B", 2, 0);
        Calibration calibration = new Calibration();
        calibration.pixelWidth = 0.5;
        calibration.pixelHeight = 0.5;
        calibration.pixelDepth = 2.0;
        calibration.setUnit("um");
        a.setCalibration(calibration);

        OverlapResult.ObjectResult object = VolColoc.run(a, b)
                .getDirectionResults().get(0).getObjects().get(0);
        assertEquals(1.0, object.getSourceVolume(), 0.0001);
        assertEquals("\u00b5m^3", object.getVolumeUnit());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsFractionalLabels() {
        FloatProcessor fractional = new FloatProcessor(2, 1);
        fractional.setf(0, 1.5f);
        ImagePlus a = new ImagePlus("A", fractional);
        ImagePlus b = labels("B", 1, 0);
        VolColoc.run(a, b);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsLabelsAboveIntegerRange() {
        FloatProcessor tooLarge = new FloatProcessor(1, 1);
        tooLarge.setf(0, 2147483648f);
        VolColoc.run(
                new ImagePlus("Too large", tooLarge),
                labels("B", 1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMismatchedDimensions() {
        VolColoc.run(labels("A", 1, 1), labels("B", 1, 1, 1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMismatchedHyperstackLayouts() {
        ImagePlus a = hyperstack("A", 2, 3, 1);
        ImagePlus b = hyperstack("B", 3, 2, 1);
        VolColoc.run(a, b);
    }

    private static int patternCount(
            OverlapResult.MultiChannelResult result, String pattern) {
        for (OverlapResult.PatternSummary row : result.getPatterns()) {
            if (pattern.equals(row.getPattern())) return row.getObjectCount();
        }
        return -1;
    }

    private static double patternPercent(
            OverlapResult.MultiChannelResult result, String pattern) {
        for (OverlapResult.PatternSummary row : result.getPatterns()) {
            if (pattern.equals(row.getPattern())) return row.getObjectPercent();
        }
        return Double.NaN;
    }

    private static ImagePlus labels(String title, int... values) {
        ShortProcessor processor = new ShortProcessor(values.length, 1);
        for (int i = 0; i < values.length; i++) {
            processor.set(i, values[i]);
        }
        return new ImagePlus(title, processor);
    }

    private static ImagePlus hyperstack(
            String title, int channels, int slices, int frames) {
        ImageStack stack = new ImageStack(1, 1);
        for (int i = 0; i < channels * slices * frames; i++) {
            ShortProcessor processor = new ShortProcessor(1, 1);
            processor.set(0, i + 1);
            stack.addSlice(processor);
        }
        ImagePlus result = new ImagePlus(title, stack);
        result.setDimensions(channels, slices, frames);
        return result;
    }

    private static ImagePlus labels2d(
            String title, int width, int height, int... values) {
        ShortProcessor processor = new ShortProcessor(width, height);
        for (int i = 0; i < values.length; i++) {
            processor.set(i, values[i]);
        }
        return new ImagePlus(title, processor);
    }
}
