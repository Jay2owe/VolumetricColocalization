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
import ij.gui.Roi;

import java.io.File;
import java.io.IOException;

/**
 * Public ROI-to-label API.
 */
public final class VolColocLabelImages {

    private VolColocLabelImages() {
    }

    public static Roi[] loadRoiSet(String path) throws IOException {
        return LabelUtils.loadRoiSet(path);
    }

    public static ImagePlus fromRois(ImagePlus reference, Roi[] rois) {
        if (reference == null) {
            throw new IllegalArgumentException("Reference image must not be null.");
        }
        if (rois == null || rois.length == 0) {
            throw new IllegalArgumentException("ROI array must contain at least one ROI.");
        }
        return LabelUtils.roiSetToLabelImage(reference, rois);
    }

    public static ImagePlus fromRoiSetFile(ImagePlus reference, String path)
            throws IOException {
        if (path == null || path.trim().length() == 0) {
            throw new IllegalArgumentException("ROI set path must not be empty.");
        }
        ImagePlus labels = fromRois(reference, loadRoiSet(path));
        String name = new File(path).getName();
        int dot = name.lastIndexOf('.');
        labels.setTitle(dot > 0 ? name.substring(0, dot) : name);
        return labels;
    }
}
