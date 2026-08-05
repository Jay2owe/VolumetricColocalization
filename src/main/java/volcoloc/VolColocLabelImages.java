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
import sc.fiji.oc3d.core.ingest.RoiLabelImages;

import java.io.File;
import java.io.IOException;

/**
 * Public ROI-to-label API.
 *
 * <p>The conversion is {@code oc3d-core}'s, shaded into this jar. This plugin
 * previously carried its own copy, and it was the stricter of the two: it
 * refused line and point selections, ROIs lying outside the reference, and
 * ROIs positioned beyond the stack, where the shared version quietly accepted
 * them and mismeasured. Those rules were promoted into the chassis rather than
 * dropped on adoption, so every plugin in the family now has them and nothing
 * here became weaker.
 *
 * <p>The argument checks below stay here because their wording is this
 * plugin's, and they answer before the shared code is reached.
 */
public final class VolColocLabelImages {

    private VolColocLabelImages() {
    }

    public static Roi[] loadRoiSet(String path) throws IOException {
        return RoiLabelImages.loadRoiSet(path);
    }

    public static ImagePlus fromRois(ImagePlus reference, Roi[] rois) {
        if (reference == null) {
            throw new IllegalArgumentException("Reference image must not be null.");
        }
        if (rois == null || rois.length == 0) {
            throw new IllegalArgumentException("ROI array must contain at least one ROI.");
        }
        return RoiLabelImages.fromRois(reference, rois);
    }

    public static ImagePlus fromRoiSetFile(ImagePlus reference, String path)
            throws IOException {
        if (path == null || path.trim().length() == 0) {
            throw new IllegalArgumentException("ROI set path must not be empty.");
        }
        // One load, one conversion. Calling the chassis's own fromRoiSetFile
        // would repeat both just to have it apply the title.
        ImagePlus labels = fromRois(reference, loadRoiSet(path));
        String name = new File(path).getName();
        int dot = name.lastIndexOf('.');
        labels.setTitle(dot > 0 ? name.substring(0, dot) : name);
        return labels;
    }
}
