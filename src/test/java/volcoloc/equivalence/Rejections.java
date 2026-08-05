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
import sc.fiji.volcoloc.core.OverlapParameters;
import volcoloc.VolColocParameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Every documented rejection, asserted to survive the extraction unchanged. */
final class Rejections {

    private Rejections() {
    }

    static List<Rejection> all() {
        List<Rejection> rejections = new ArrayList<Rejection>();

        rejections.add(new Simple("too-few-images") {
            @Override
            List<ImagePlus> images() {
                return Collections.singletonList(plane("A", 1, 1));
            }
        });

        rejections.add(new Simple("too-many-images") {
            @Override
            List<ImagePlus> images() {
                return Arrays.asList(plane("A", 1), plane("B", 1), plane("C", 1),
                        plane("D", 1), plane("E", 1), plane("F", 1));
            }
        });

        rejections.add(new Simple("rgb") {
            @Override
            List<ImagePlus> images() {
                return Arrays.asList(plane("A", 1, 1),
                        new ImagePlus("RGB", new ColorProcessor(2, 1)));
            }
        });

        rejections.add(new Simple("hyperstack") {
            @Override
            List<ImagePlus> images() {
                ImageStack stack = new ImageStack(2, 1);
                stack.addSlice(new ShortProcessor(2, 1));
                stack.addSlice(new ShortProcessor(2, 1));
                ImagePlus hyper = new ImagePlus("Hyper", stack);
                hyper.setDimensions(2, 1, 1);
                return Arrays.asList(hyper, plane("B", 1, 1));
            }
        });

        rejections.add(new Simple("same-image-twice") {
            @Override
            List<ImagePlus> images() {
                ImagePlus shared = plane("A", 1, 1);
                return Arrays.asList(shared, shared);
            }
        });

        rejections.add(new Simple("mismatched-dimensions") {
            @Override
            List<ImagePlus> images() {
                return Arrays.asList(plane("A", 1, 1), plane("B", 1, 1, 1));
            }
        });

        rejections.add(new Simple("non-integer-label") {
            @Override
            List<ImagePlus> images() {
                FloatProcessor processor = new FloatProcessor(2, 1);
                processor.setf(0, 0, 1.0f);
                processor.setf(1, 0, 1.5f);
                return Arrays.asList(new ImagePlus("A", processor), plane("B", 5, 5));
            }
        });

        rejections.add(new Simple("negative-label") {
            @Override
            List<ImagePlus> images() {
                FloatProcessor processor = new FloatProcessor(2, 1);
                processor.setf(0, 0, -3.0f);
                processor.setf(1, 0, 1.0f);
                return Arrays.asList(new ImagePlus("A", processor), plane("B", 5, 5));
            }
        });

        // Threshold and detail ranges, which are validated rather than clamped.
        rejections.add(new Tuned("threshold-too-high", Arrays.asList(10.0, 140.0), null, 50.0));
        rejections.add(new Tuned("threshold-negative", Arrays.asList(-1.0, 10.0), null, 50.0));
        rejections.add(new Tuned("threshold-wrong-length",
                Collections.singletonList(10.0), null, 50.0));
        rejections.add(new Tuned("bbox-threshold-out-of-range", null,
                Arrays.asList(-1.0, 10.0), 50.0));
        rejections.add(new Tuned("detail-percent-out-of-range", null, null, 101.0));

        return rejections;
    }

    private static ImagePlus plane(String title, int... labels) {
        ShortProcessor processor = new ShortProcessor(labels.length, 1);
        for (int x = 0; x < labels.length; x++) processor.set(x, 0, labels[x]);
        return new ImagePlus(title, processor);
    }

    /** A rejection driven purely by the images. */
    private abstract static class Simple extends Rejection {

        Simple(String name) {
            super(name);
        }

        abstract List<ImagePlus> images();

        @Override
        VolColocParameters pluginParameters() {
            return VolColocParameters.builder(images()).build();
        }

        @Override
        OverlapParameters coreParameters() {
            return OverlapParameters.builder(images()).build();
        }
    }

    /** A rejection driven by an out-of-range setting on valid images. */
    private static final class Tuned extends Rejection {

        private final List<Double> thresholds;
        private final List<Double> boundingBoxThresholds;
        private final double minimumDetailPercent;

        Tuned(String name, List<Double> thresholds,
              List<Double> boundingBoxThresholds, double minimumDetailPercent) {
            super(name);
            this.thresholds = thresholds;
            this.boundingBoxThresholds = boundingBoxThresholds;
            this.minimumDetailPercent = minimumDetailPercent;
        }

        private List<ImagePlus> images() {
            return Arrays.asList(plane("A", 1, 1), plane("B", 5, 5));
        }

        @Override
        VolColocParameters pluginParameters() {
            VolColocParameters.Builder builder = VolColocParameters.builder(images())
                    .minimumDetailOverlapPercent(minimumDetailPercent);
            if (thresholds != null) builder.thresholdsPercent(thresholds);
            if (boundingBoxThresholds != null) {
                builder.boundingBoxThresholdsPercent(boundingBoxThresholds);
            }
            return builder.build();
        }

        @Override
        OverlapParameters coreParameters() {
            OverlapParameters.Builder builder = OverlapParameters.builder(images())
                    .minimumDetailOverlapPercent(minimumDetailPercent);
            if (thresholds != null) builder.thresholdsPercent(thresholds);
            if (boundingBoxThresholds != null) {
                builder.boundingBoxThresholdsPercent(boundingBoxThresholds);
            }
            return builder.build();
        }
    }
}
