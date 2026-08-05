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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Public Java API. It opens no dialogs and displays no result windows.
 */
public final class VolColoc {

    private VolColoc() {
    }

    public static VolColocResult run(ImagePlus imageA, ImagePlus imageB) {
        return run(VolColocParameters.builder(imageA, imageB).build());
    }

    public static VolColocResult run(List<ImagePlus> images) {
        return run(VolColocParameters.builder(images).build());
    }

    public static VolColocResult run(VolColocParameters parameters) {
        Validated validated = validate(parameters);
        return new VolColocAnalysis(
                parameters,
                validated.channelNames,
                validated.thresholds,
                validated.boundingBoxThresholds).run();
    }

    private static Validated validate(VolColocParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException(
                    "Volumetric Colocalization parameters must not be null.");
        }
        List<ImagePlus> images = parameters.getImages();
        if (images.size() < VolColocParameters.MIN_IMAGES) {
            throw new IllegalArgumentException(
                    "Volumetric Colocalization requires at least 2 label images.");
        }
        if (images.size() > VolColocParameters.MAX_IMAGES) {
            throw new IllegalArgumentException(
                    "Volumetric Colocalization supports at most 5 label images.");
        }
        Set<ImagePlus> unique = new HashSet<ImagePlus>();
        ImagePlus first = images.get(0);
        for (int i = 0; i < images.size(); i++) {
            ImagePlus image = images.get(i);
            if (image == null || image.getStack() == null) {
                throw new IllegalArgumentException(
                        "Label image " + (i + 1) + " is null or has no stack.");
            }
            if (image.getWidth() <= 0 || image.getHeight() <= 0
                    || image.getStackSize() <= 0) {
                throw new IllegalArgumentException(
                        "Label image " + (i + 1) + " is empty.");
            }
            // Only RGB is rejected. An indexed-colour image still stores the
            // palette index as its pixel value, which is the label, so images
            // carrying a glasbey-style LUT stay usable.
            if (image.getType() == ImagePlus.COLOR_RGB) {
                throw new IllegalArgumentException(
                        "Label image " + (i + 1) + " is an RGB image, whose "
                                + "pixel values are packed colours rather than "
                                + "object labels. Convert the segmentation to "
                                + "8-, 16-, or 32-bit first.");
            }
            // The engine walks the stack as one Z series. A hyperstack's extra
            // channels or frames would be counted as further Z layers, so an
            // object's volume would be multiplied by the channel and frame
            // count instead of measured. Refuse rather than mismeasure.
            if (image.getNChannels() > 1 || image.getNFrames() > 1) {
                throw new IllegalArgumentException(
                        "Label image " + (i + 1) + " is a hyperstack ("
                                + image.getNChannels() + " channel(s), "
                                + image.getNSlices() + " slice(s), "
                                + image.getNFrames() + " frame(s)). "
                                + "Volumetric Colocalization measures one "
                                + "volume at a time. Split it with Image > "
                                + "Stacks > Tools > Make Substack, or if this "
                                + "is really a z-stack, correct the dimensions "
                                + "in Image > Properties.");
            }
            if (!unique.add(image)) {
                throw new IllegalArgumentException(
                        "Each input slot must use a different ImagePlus.");
            }
            if (image.getWidth() != first.getWidth()
                    || image.getHeight() != first.getHeight()
                    || image.getStackSize() != first.getStackSize()
                    || image.getNChannels() != first.getNChannels()
                    || image.getNSlices() != first.getNSlices()
                    || image.getNFrames() != first.getNFrames()) {
                throw new IllegalArgumentException(
                        "All label images must have identical width, height, "
                                + "channel, slice, and frame dimensions.");
            }
        }
        if (!Double.isFinite(parameters.getMinimumDetailOverlapPercent())
                || parameters.getMinimumDetailOverlapPercent() < 0.0
                || parameters.getMinimumDetailOverlapPercent() > 100.0) {
            throw new IllegalArgumentException(
                    "Minimum partner-detail overlap must be between 0 and 100 percent.");
        }

        List<String> names = normalizeNames(images, parameters.getChannelNames());
        List<Double> thresholds = normalizeThresholds(
                images.size(), parameters.getThresholdsPercent());
        List<Double> boundingBoxThresholds = normalizeThresholds(
                images.size(),
                parameters.getBoundingBoxThresholdsPercent(),
                "Bounding-box threshold",
                VolColocParameters.DEFAULT_BOUNDING_BOX_THRESHOLD_PERCENT);
        return new Validated(names, thresholds, boundingBoxThresholds);
    }

    private static List<String> normalizeNames(List<ImagePlus> images,
                                               List<String> requested) {
        List<String> names = new ArrayList<String>();
        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < images.size(); i++) {
            String name = i < requested.size() ? requested.get(i) : null;
            if (name == null || name.trim().length() == 0) {
                name = images.get(i).getTitle();
            }
            if (name == null || name.trim().length() == 0) {
                name = "Channel " + (char) ('A' + i);
            }
            name = name.trim();
            String unique = name;
            int suffix = 2;
            while (!seen.add(unique.toLowerCase(Locale.ROOT))) {
                unique = name + " " + suffix++;
            }
            names.add(unique);
        }
        return names;
    }

    private static List<Double> normalizeThresholds(int count, List<Double> requested) {
        return normalizeThresholds(
                count,
                requested,
                "Threshold",
                VolColocParameters.DEFAULT_THRESHOLD_PERCENT);
    }

    private static List<Double> normalizeThresholds(
            int count, List<Double> requested, String label, double defaultValue) {
        if (!requested.isEmpty() && requested.size() != count) {
            throw new IllegalArgumentException(
                    label + " list must be empty or match the number of label images.");
        }
        List<Double> thresholds = new ArrayList<Double>();
        for (int i = 0; i < count; i++) {
            if (!requested.isEmpty() && requested.get(i) == null) {
                throw new IllegalArgumentException(
                        label + " " + (i + 1) + " must not be null.");
            }
            double value = requested.isEmpty()
                    ? defaultValue
                    : requested.get(i).doubleValue();
            if (!Double.isFinite(value) || value < 0.0 || value > 100.0) {
                throw new IllegalArgumentException(
                        label + " " + (i + 1)
                                + " must be between 0 and 100 percent.");
            }
            thresholds.add(Double.valueOf(value));
        }
        return thresholds;
    }

    private static final class Validated {
        final List<String> channelNames;
        final List<Double> thresholds;
        final List<Double> boundingBoxThresholds;

        Validated(List<String> channelNames, List<Double> thresholds,
                  List<Double> boundingBoxThresholds) {
            this.channelNames = channelNames;
            this.thresholds = thresholds;
            this.boundingBoxThresholds = boundingBoxThresholds;
        }
    }
}
