/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package volcoloc.equivalence;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;
import org.junit.Test;
import volcoloc.VolColoc;
import volcoloc.VolColocParameters;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * What a user reads when their input is refused.
 *
 * <p>These messages are the plugin's contract, not incidental strings. They are
 * asserted verbatim, against literals written here rather than read from the
 * engine, so that moving the engine into {@code volcoloc-core} — or any later
 * change behind {@code VolColoc} — cannot reword them without a failing test.
 *
 * <p>Refusing is the correct behaviour in every case below. The hyperstack one
 * in particular is a measurement guarantee: walking a hyperstack as one Z series
 * would multiply every object's volume by the channel and frame count, so the
 * plugin refuses rather than quietly returning a wrong number.
 */
public class RejectionContractTest {

    private static String rejectionFor(VolColocParameters parameters) {
        try {
            VolColoc.run(parameters);
            fail("expected a rejection");
            return null;
        } catch (IllegalArgumentException expected) {
            return expected.getMessage();
        }
    }

    private static String rejectionFor(List<ImagePlus> images) {
        return rejectionFor(VolColocParameters.builder(images).build());
    }

    private static ImagePlus plane(String title, int... labels) {
        ShortProcessor processor = new ShortProcessor(labels.length, 1);
        for (int x = 0; x < labels.length; x++) processor.set(x, 0, labels[x]);
        return new ImagePlus(title, processor);
    }

    private static List<ImagePlus> valid() {
        return Arrays.asList(plane("A", 1, 1), plane("B", 5, 5));
    }

    @Test
    public void nullParameters() {
        assertEquals("Volumetric Colocalization parameters must not be null.",
                rejectionFor((VolColocParameters) null));
    }

    @Test
    public void tooFewImages() {
        assertEquals("Volumetric Colocalization requires at least 2 label images.",
                rejectionFor(Collections.singletonList(plane("A", 1, 1))));
    }

    @Test
    public void tooManyImages() {
        assertEquals("Volumetric Colocalization supports at most 5 label images.",
                rejectionFor(Arrays.asList(
                        plane("A", 1), plane("B", 1), plane("C", 1),
                        plane("D", 1), plane("E", 1), plane("F", 1))));
    }

    @Test
    public void rgbIsRefusedBecauseItsPixelsArePackedColours() {
        assertEquals("Label image 2 is an RGB image, whose pixel values are "
                        + "packed colours rather than object labels. Convert the "
                        + "segmentation to 8-, 16-, or 32-bit first.",
                rejectionFor(Arrays.asList(plane("A", 1, 1),
                        new ImagePlus("RGB", new ColorProcessor(2, 1)))));
    }

    @Test
    public void hyperstackIsRefusedRatherThanMismeasured() {
        ImageStack stack = new ImageStack(2, 1);
        stack.addSlice(new ShortProcessor(2, 1));
        stack.addSlice(new ShortProcessor(2, 1));
        ImagePlus hyper = new ImagePlus("Hyper", stack);
        hyper.setDimensions(2, 1, 1);

        assertEquals("Label image 1 is a hyperstack (2 channel(s), 1 slice(s), "
                        + "1 frame(s)). Volumetric Colocalization measures one "
                        + "volume at a time. Split it with Image > Stacks > "
                        + "Tools > Make Substack, or if this is really a "
                        + "z-stack, correct the dimensions in Image > Properties.",
                rejectionFor(Arrays.asList(hyper, plane("B", 1, 1))));
    }

    @Test
    public void theSameImageInTwoSlots() {
        ImagePlus shared = plane("A", 1, 1);
        assertEquals("Each input slot must use a different ImagePlus.",
                rejectionFor(Arrays.asList(shared, shared)));
    }

    @Test
    public void mismatchedDimensions() {
        assertEquals("All label images must have identical width, height, "
                        + "channel, slice, and frame dimensions.",
                rejectionFor(Arrays.asList(plane("A", 1, 1), plane("B", 1, 1, 1))));
    }

    @Test
    public void nonIntegerLabelNamesTheImageAndSlice() {
        FloatProcessor processor = new FloatProcessor(2, 1);
        processor.setf(0, 0, 1.0f);
        processor.setf(1, 0, 1.5f);
        assertEquals("Image 1 contains an invalid label value (1.5) on slice 1. "
                        + "Labels must be non-negative integers.",
                rejectionFor(Arrays.asList(
                        new ImagePlus("A", processor), plane("B", 5, 5))));
    }

    @Test
    public void negativeLabel() {
        FloatProcessor processor = new FloatProcessor(2, 1);
        processor.setf(0, 0, -3.0f);
        processor.setf(1, 0, 1.0f);
        assertEquals("Image 1 contains an invalid label value (-3.0) on slice 1. "
                        + "Labels must be non-negative integers.",
                rejectionFor(Arrays.asList(
                        new ImagePlus("A", processor), plane("B", 5, 5))));
    }

    @Test
    public void thresholdOutOfRangeNamesWhichOne() {
        assertEquals("Threshold 2 must be between 0 and 100 percent.",
                rejectionFor(VolColocParameters.builder(valid())
                        .thresholdsPercent(Arrays.asList(10.0, 140.0)).build()));
        assertEquals("Threshold 1 must be between 0 and 100 percent.",
                rejectionFor(VolColocParameters.builder(valid())
                        .thresholdsPercent(Arrays.asList(-1.0, 10.0)).build()));
    }

    @Test
    public void thresholdListOfTheWrongLength() {
        assertEquals("Threshold list must be empty or match the number of label images.",
                rejectionFor(VolColocParameters.builder(valid())
                        .thresholdsPercent(Collections.singletonList(10.0)).build()));
    }

    @Test
    public void boundingBoxThresholdIsNamedSeparately() {
        assertEquals("Bounding-box threshold 1 must be between 0 and 100 percent.",
                rejectionFor(VolColocParameters.builder(valid())
                        .boundingBoxThresholdsPercent(Arrays.asList(-1.0, 10.0))
                        .build()));
    }

    @Test
    public void minimumDetailOverlapOutOfRange() {
        assertEquals("Minimum partner-detail overlap must be between 0 and 100 percent.",
                rejectionFor(VolColocParameters.builder(valid())
                        .minimumDetailOverlapPercent(101.0).build()));
    }

    @Test
    public void thresholdsAtTheBoundariesAreAccepted() {
        // 0 and 100 are valid thresholds, not out-of-range. Guards the
        // comparison from being tightened to exclusive by accident.
        for (double boundary : new double[]{0.0, 100.0}) {
            assertTrue("threshold " + boundary + " should be accepted",
                    VolColoc.run(VolColocParameters.builder(valid())
                            .thresholdPercent(boundary).build())
                            .getDirectionResults().size() > 0);
        }
    }
}
