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
import ij.gui.Roi;
import ij.io.RoiDecoder;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utilities for converting ImageJ ROI sets to label images.
 */
final class LabelUtils {

    private static final int MAX_EXACT_FLOAT_LABEL = 1 << 24;

    private LabelUtils() {
    }

    static Roi[] loadRoiSet(String path) throws IOException {
        if (path.toLowerCase(Locale.ROOT).endsWith(".roi")) {
            Roi roi = new RoiDecoder(path).getRoi();
            return roi == null ? new Roi[0] : new Roi[]{roi};
        }
        List<Roi> rois = new ArrayList<Roi>();
        byte[] buffer = new byte[65536];
        ZipInputStream input = new ZipInputStream(new FileInputStream(path));
        try {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!entry.getName().toLowerCase(Locale.ROOT)
                        .endsWith(".roi")) continue;
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                int read;
                while ((read = input.read(buffer)) > 0) {
                    bytes.write(buffer, 0, read);
                }
                Roi roi = new RoiDecoder(bytes.toByteArray(), entry.getName()).getRoi();
                if (roi != null) rois.add(roi);
            }
        } finally {
            input.close();
        }
        return rois.toArray(new Roi[0]);
    }

    static ImagePlus roiSetToLabelImage(ImagePlus reference, Roi[] rois) {
        if (rois.length > MAX_EXACT_FLOAT_LABEL) {
            throw new IllegalArgumentException(
                    "ROI sets support at most " + MAX_EXACT_FLOAT_LABEL
                            + " distinct labels.");
        }
        int width = reference.getWidth();
        int height = reference.getHeight();
        int slices = Math.max(1, reference.getNSlices());
        ImageStack stack = new ImageStack(width, height);
        for (int z = 0; z < slices; z++) {
            stack.addSlice(new FloatProcessor(width, height));
        }

        for (int index = 0; index < rois.length; index++) {
            Roi roi = rois[index];
            int label = index + 1;
            // isArea() rather than isLine(): it also excludes point and
            // multipoint selections, which isLine() lets through. A line's mask
            // is null (straight lines) or the filled polygon of its vertices
            // (polylines), so filling one would invent a solid block spanning
            // its bounding box; a point marker would become a stray one-voxel
            // object competing for overlap.
            if (!roi.isArea()) {
                throw new IllegalArgumentException(
                        describe(roi, label) + " is a line, polyline, angle or "
                                + "point selection. Volumetric Colocalization "
                                + "measures object volumes, so every ROI must "
                                + "enclose an area. Convert traced lines to "
                                + "areas first (Edit > Selection > Line to "
                                + "Area).");
            }
            if (roi.getBounds().isEmpty()) {
                // Caught before the bounds test below, which reports empty
                // rectangles as lying outside and sends the user hunting for
                // the wrong problem.
                throw new IllegalArgumentException(
                        describe(roi, label) + " encloses no pixels.");
            }
            if (!roi.getBounds().intersects(0, 0, width, height)) {
                // Silently dropping it would delete the object from every
                // table and shift all the summary denominators.
                throw new IllegalArgumentException(
                        describe(roi, label) + " lies entirely outside the "
                                + "reference image (" + width + "x" + height
                                + "). Use the reference image the ROIs were "
                                + "drawn on.");
            }
            int zPosition = roi.getZPosition();
            if (zPosition > slices) {
                // Named, not just numbered: one stray ROI in a 500-entry
                // manager set is otherwise impossible to find.
                // Do not fall through to the project-everywhere branch: an ROI
                // recorded on a taller stack than the reference would be
                // silently smeared across every slice, inflating its volume.
                throw new IllegalArgumentException(
                        describe(roi, label) + " is positioned on slice "
                                + zPosition + " but the reference image has "
                                + "only " + slices + " slice(s). Use the "
                                + "reference image the ROIs were drawn on.");
            }
            int filled;
            if (zPosition > 0) {
                filled = fillRoi(stack.getProcessor(zPosition), roi, label);
            } else {
                filled = 0;
                for (int z = 1; z <= slices; z++) {
                    filled += fillRoi(stack.getProcessor(z), roi, label);
                }
            }
            if (filled == 0) {
                // Two ways to get here: an empty mask despite ordinary bounds
                // (a polygon with collinear vertices is the usual one), or a
                // box that overlaps the image while the shape inside it does
                // not. The message covers both rather than guessing. Letting
                // either through would drop the object from every table and
                // shift each summary denominator, with nothing to show it.
                throw new IllegalArgumentException(
                        describe(roi, label) + " encloses no pixels inside the "
                                + "reference image (" + width + "x" + height
                                + ").");
            }
        }

        ImagePlus labels = new ImagePlus("Labels from ROIs", stack);
        if (reference.getCalibration() != null) {
            labels.setCalibration(reference.getCalibration().copy());
        }
        return labels;
    }

    /** {@code ROI 12 ("cell_17")}, or just {@code ROI 12} when unnamed. */
    private static String describe(Roi roi, int label) {
        String name = roi.getName();
        return name == null || name.trim().length() == 0
                ? "ROI " + label
                : "ROI " + label + " (\"" + name.trim() + "\")";
    }

    /** @return how many pixels this ROI actually labelled on this slice. */
    private static int fillRoi(ImageProcessor processor, Roi roi, int label) {
        Rectangle bounds = roi.getBounds();
        ImageProcessor mask = roi.getMask();
        int filled = 0;
        for (int y = 0; y < bounds.height; y++) {
            for (int x = 0; x < bounds.width; x++) {
                if (mask != null && mask.getPixel(x, y) == 0) continue;
                int globalX = bounds.x + x;
                int globalY = bounds.y + y;
                if (globalX >= 0 && globalX < processor.getWidth()
                        && globalY >= 0 && globalY < processor.getHeight()) {
                    processor.setf(globalX, globalY, label);
                    filled++;
                }
            }
        }
        return filled;
    }
}
