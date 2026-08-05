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
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.process.ShortProcessor;

import java.awt.image.IndexColorModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * The fixtures the extraction is gated on.
 *
 * <p>Generated in code and seeded, so the corpus is reproducible without
 * committing binaries. Each case is rebuilt per engine run: the engines only
 * read their inputs, but sharing mutable {@code ImagePlus} instances between
 * two runs would make any future accidental write invisible to this gate.
 */
final class Corpus {

    private Corpus() {
    }

    /** One named set of label images. */
    static final class Case {
        final String name;
        final List<ImagePlus> images;

        Case(String name, List<ImagePlus> images) {
            this.name = name;
            this.images = images;
        }
    }

    static List<Case> cases() {
        List<Case> cases = new ArrayList<Case>();

        // Channel-count ladder, 2 through 5 — the supported range.
        for (int channels = 2; channels <= 5; channels++) {
            cases.add(new Case("channels-" + channels, randomStack(channels, 16, 9, 7, 3, 6L + channels)));
        }

        // Bit depths. Labels are identical; only the storage differs.
        for (int depth : new int[]{8, 16, 32}) {
            cases.add(new Case("depth-" + depth, sameLabelsAtDepth(depth)));
        }

        // 8-bit carrying a colour LUT. Indexed colour still stores the label
        // as its pixel value, so this must be accepted and measured normally.
        cases.add(new Case("lut-8bit", lutPair()));

        // Anisotropic calibration, and calibration on one channel only.
        cases.add(new Case("anisotropic", anisotropic()));
        cases.add(new Case("calibrated-source-only", calibratedSourceOnly()));

        // Degenerate shapes.
        cases.add(new Case("empty-target", Arrays.asList(
                plane("A", 1, 1, 2, 2), plane("B", 0, 0, 0, 0))));
        cases.add(new Case("empty-source", Arrays.asList(
                plane("A", 0, 0, 0, 0), plane("B", 5, 5, 6, 6))));
        cases.add(new Case("both-empty", Arrays.asList(
                plane("A", 0, 0), plane("B", 0, 0))));
        cases.add(new Case("perfect-overlap", Arrays.asList(
                plane("A", 1, 1, 1, 1), plane("B", 5, 5, 5, 5))));
        cases.add(new Case("disjoint", Arrays.asList(
                plane("A", 1, 1, 0, 0), plane("B", 0, 0, 5, 5))));

        // Many partners against one source object — exercises the pair map's
        // resize path and the strongest-partner tie-break.
        int wide = 300;
        int[] source = new int[wide];
        int[] partners = new int[wide];
        for (int i = 0; i < wide; i++) {
            source[i] = 1;
            partners[i] = i + 1;
        }
        cases.add(new Case("many-partners", Arrays.asList(
                plane("A", source), plane("B", partners))));

        // Objects spanning, and clipped by, the stack depth.
        cases.add(new Case("spans-depth", Arrays.asList(
                stack("A", 1, 1, new int[][]{{1}, {1}, {1}, {1}}),
                stack("B", 1, 1, new int[][]{{0}, {5}, {5}, {0}}))));
        cases.add(new Case("touches-first-and-last-slice", Arrays.asList(
                stack("A", 2, 1, new int[][]{{1, 1}, {0, 0}, {2, 2}}),
                stack("B", 2, 1, new int[][]{{5, 0}, {0, 0}, {0, 6}}))));

        // Channel names that collide with the reserved pattern rows, and with
        // the combination separator. Three channels so the multi pass runs.
        cases.add(new Case("reserved-names", Arrays.asList(
                plane("None", 1, 1), plane("\u2014 Any \u2014", 5, 5), plane("A + B", 7, 7))));
        cases.add(new Case("duplicate-names", Arrays.asList(
                plane("Same", 1, 1), plane("Same", 5, 5), plane("Same", 7, 7))));
        cases.add(new Case("blank-names", Arrays.asList(
                plane("", 1, 1), plane("", 5, 5))));

        // Larger randomised volumes, the closest thing here to real data.
        cases.add(new Case("random-3ch", randomStack(3, 24, 18, 5, 4, 99L)));
        cases.add(new Case("random-2ch-dense", randomStack(2, 20, 20, 4, 2, 4242L)));

        return cases;
    }

    /**
     * Configuration sweep applied to every case. The threshold sweep at 0, 30
     * and 100 is required by the module spec; 0 and 100 are the boundaries
     * where the inclusive comparison is observable.
     */
    static final class Config {
        final String name;
        final double threshold;
        final double boundingBoxThreshold;
        final boolean bidirectional;
        final boolean multi;
        final boolean partnerDetails;
        final double minimumDetailPercent;
        final boolean bbOverlap;
        final boolean bbCpc;
        final boolean bbVolumeFill;

        Config(String name, double threshold, double boundingBoxThreshold,
               boolean bidirectional, boolean multi, boolean partnerDetails,
               double minimumDetailPercent, boolean bbOverlap, boolean bbCpc,
               boolean bbVolumeFill) {
            this.name = name;
            this.threshold = threshold;
            this.boundingBoxThreshold = boundingBoxThreshold;
            this.bidirectional = bidirectional;
            this.multi = multi;
            this.partnerDetails = partnerDetails;
            this.minimumDetailPercent = minimumDetailPercent;
            this.bbOverlap = bbOverlap;
            this.bbCpc = bbCpc;
            this.bbVolumeFill = bbVolumeFill;
        }
    }

    static List<Config> configs() {
        List<Config> configs = new ArrayList<Config>();
        // Threshold sweep at the documented values.
        configs.add(new Config("t0", 0.0, 30.0, true, true, true, 50.0, false, false, false));
        configs.add(new Config("t30-default", 30.0, 30.0, true, true, true, 50.0, false, false, false));
        configs.add(new Config("t100", 100.0, 30.0, true, true, true, 50.0, false, false, false));
        // Direction, multi and detail switches.
        configs.add(new Config("one-directional", 30.0, 30.0, false, true, true, 50.0, false, false, false));
        configs.add(new Config("no-multi", 30.0, 30.0, true, false, true, 50.0, false, false, false));
        configs.add(new Config("no-details", 30.0, 30.0, true, true, false, 50.0, false, false, false));
        configs.add(new Config("all-details", 30.0, 30.0, true, true, true, 0.0, false, false, false));
        // Bounding-box families, singly and together, at both extremes.
        configs.add(new Config("bb-overlap", 30.0, 20.0, true, true, true, 50.0, true, false, false));
        configs.add(new Config("bb-cpc", 30.0, 20.0, true, true, true, 50.0, false, true, false));
        configs.add(new Config("bb-fill", 30.0, 20.0, true, true, true, 50.0, false, false, true));
        configs.add(new Config("bb-all", 30.0, 20.0, true, true, true, 50.0, true, true, true));
        configs.add(new Config("bb-all-t0", 0.0, 0.0, true, true, true, 50.0, true, true, true));
        configs.add(new Config("bb-all-t100", 100.0, 100.0, true, true, true, 50.0, true, true, true));
        return configs;
    }

    static List<Double> repeat(double value, int count) {
        List<Double> values = new ArrayList<Double>();
        for (int i = 0; i < count; i++) values.add(Double.valueOf(value));
        return values;
    }

    // ------------------------------------------------------------------
    // Builders
    // ------------------------------------------------------------------

    private static ImagePlus plane(String title, int... labels) {
        ShortProcessor processor = new ShortProcessor(labels.length, 1);
        for (int x = 0; x < labels.length; x++) processor.set(x, 0, labels[x]);
        return new ImagePlus(title, processor);
    }

    private static ImagePlus stack(String title, int width, int height, int[][] slices) {
        ImageStack imageStack = new ImageStack(width, height);
        for (int[] slice : slices) {
            ShortProcessor processor = new ShortProcessor(width, height);
            for (int i = 0; i < slice.length; i++) {
                processor.set(i % width, i / width, slice[i]);
            }
            imageStack.addSlice(processor);
        }
        return new ImagePlus(title, imageStack);
    }

    private static List<ImagePlus> randomStack(int channels, int width, int height,
                                               int depth, int objects, long seed) {
        Random random = new Random(seed);
        List<ImagePlus> images = new ArrayList<ImagePlus>();
        for (int c = 0; c < channels; c++) {
            ImageStack imageStack = new ImageStack(width, height);
            for (int z = 0; z < depth; z++) {
                ShortProcessor processor = new ShortProcessor(width, height);
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        // Blocky regions rather than salt-and-pepper, so
                        // objects have real bounding boxes and centroids.
                        int region = ((x / 5) + (y / 4) + z) % (objects + 2);
                        processor.set(x, y, region <= objects && random.nextInt(4) > 0
                                ? region : 0);
                    }
                }
                imageStack.addSlice(processor);
            }
            images.add(new ImagePlus("C" + (c + 1), imageStack));
        }
        return images;
    }

    private static List<ImagePlus> sameLabelsAtDepth(int depth) {
        int[] a = {1, 1, 1, 2, 2, 0, 3, 3};
        int[] b = {5, 5, 0, 6, 0, 0, 7, 0};
        return Arrays.asList(rowOfDepth("A", depth, a), rowOfDepth("B", depth, b));
    }

    private static ImagePlus rowOfDepth(String title, int depth, int[] labels) {
        ImageProcessor processor;
        if (depth == 8) {
            processor = new ByteProcessor(labels.length, 1);
        } else if (depth == 16) {
            processor = new ShortProcessor(labels.length, 1);
        } else {
            processor = new FloatProcessor(labels.length, 1);
        }
        for (int x = 0; x < labels.length; x++) processor.setf(x, 0, labels[x]);
        return new ImagePlus(title, processor);
    }

    /** 8-bit with a glasbey-style palette: still a label image, not RGB. */
    private static List<ImagePlus> lutPair() {
        ImagePlus a = rowOfDepth("A", 8, new int[]{1, 1, 1, 2, 2, 0, 3, 3});
        ImagePlus b = rowOfDepth("B", 8, new int[]{5, 5, 0, 6, 0, 0, 7, 0});
        a.getProcessor().setLut(glasbeyish());
        b.getProcessor().setLut(glasbeyish());
        return Arrays.asList(a, b);
    }

    private static LUT glasbeyish() {
        byte[] reds = new byte[256];
        byte[] greens = new byte[256];
        byte[] blues = new byte[256];
        Random random = new Random(7L);
        for (int i = 1; i < 256; i++) {
            reds[i] = (byte) random.nextInt(256);
            greens[i] = (byte) random.nextInt(256);
            blues[i] = (byte) random.nextInt(256);
        }
        return new LUT(new IndexColorModel(8, 256, reds, greens, blues), 0.0, 255.0);
    }

    private static List<ImagePlus> anisotropic() {
        ImagePlus a = stack("A", 2, 2, new int[][]{{1, 1, 1, 0}, {1, 0, 2, 2}});
        ImagePlus b = stack("B", 2, 2, new int[][]{{5, 0, 5, 0}, {0, 6, 6, 0}});
        Calibration calibration = new Calibration();
        calibration.pixelWidth = 0.107;
        calibration.pixelHeight = 0.107;
        calibration.pixelDepth = 0.535;
        calibration.setUnit("micron");
        a.setCalibration(calibration);
        b.setCalibration((Calibration) calibration.clone());
        return Arrays.asList(a, b);
    }

    private static List<ImagePlus> calibratedSourceOnly() {
        ImagePlus a = plane("A", 1, 1, 2, 2);
        ImagePlus b = plane("B", 5, 0, 6, 6);
        Calibration calibration = new Calibration();
        calibration.pixelWidth = 2.0;
        calibration.pixelHeight = 2.0;
        calibration.pixelDepth = 2.0;
        calibration.setUnit("mm");
        a.setCalibration(calibration);
        return Arrays.asList(a, b);
    }
}
