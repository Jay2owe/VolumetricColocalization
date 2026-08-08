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
import ij.gui.Line;
import ij.gui.OvalRoi;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.RotatedRectRoi;
import ij.gui.ShapeRoi;
import ij.process.ByteProcessor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VolColocLabelImagesTest {

    @Test
    public void convertsRoisToDistinctLabels() {
        ImagePlus reference = new ImagePlus(
                "reference", new ByteProcessor(6, 4));
        Roi[] rois = {
                new Roi(0, 0, 2, 2),
                new Roi(3, 1, 2, 2)
        };

        ImagePlus labels = VolColocLabelImages.fromRois(reference, rois);

        assertEquals(1.0, labels.getProcessor().getf(0, 0), 0.0);
        assertEquals(2.0, labels.getProcessor().getf(3, 1), 0.0);
        assertEquals(0.0, labels.getProcessor().getf(2, 2), 0.0);
    }

    @Test
    public void placesPositionedRoisOnTheirOwnSliceAndProjectsUnpositionedOnes() {
        ImagePlus reference = threeSliceReference();
        Roi positioned = new Roi(0, 0, 1, 1);
        positioned.setPosition(0, 2, 0);
        Roi unpositioned = new Roi(1, 0, 1, 1);

        ImagePlus labels = VolColocLabelImages.fromRois(
                reference, new Roi[]{positioned, unpositioned});

        // Label 1 only on slice 2; label 2 projected through all three.
        assertEquals(0.0, labels.getStack().getProcessor(1).getf(0, 0), 0.0);
        assertEquals(1.0, labels.getStack().getProcessor(2).getf(0, 0), 0.0);
        assertEquals(0.0, labels.getStack().getProcessor(3).getf(0, 0), 0.0);
        for (int z = 1; z <= 3; z++) {
            assertEquals(2.0, labels.getStack().getProcessor(z).getf(1, 0), 0.0);
        }
    }

    @Test
    public void acceptsAnRoiOnTheLastSlice() {
        // Boundary: zPosition == slices must be accepted. Tightening the guard
        // to >= would reject every ROI drawn on the last slice of a stack.
        ImagePlus reference = threeSliceReference();
        Roi lastSlice = new Roi(0, 0, 1, 1);
        lastSlice.setPosition(0, 3, 0);

        ImagePlus labels = VolColocLabelImages.fromRois(
                reference, new Roi[]{lastSlice});

        assertEquals(0.0, labels.getStack().getProcessor(1).getf(0, 0), 0.0);
        assertEquals(0.0, labels.getStack().getProcessor(2).getf(0, 0), 0.0);
        assertEquals(1.0, labels.getStack().getProcessor(3).getf(0, 0), 0.0);
    }

    @Test
    public void rejectsLineRoisRatherThanFillingTheirBoundingBox() {
        // A line's mask is null, so filling its bounds would turn a traced
        // 20-pixel diagonal into a 400-pixel solid block.
        ImagePlus reference = new ImagePlus("ref", new ByteProcessor(20, 20));
        try {
            VolColocLabelImages.fromRois(reference,
                    new Roi[]{new Line(0, 0, 19, 19)});
            throw new AssertionError("Expected a line ROI to be rejected.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("must enclose an area"));
        }
    }

    @Test
    public void rejectsPolylineRoisRatherThanFillingTheirPolygon() {
        ImagePlus reference = new ImagePlus("ref", new ByteProcessor(20, 20));
        Roi polyline = new PolygonRoi(
                new int[]{0, 10, 19}, new int[]{0, 15, 2}, 3, Roi.POLYLINE);
        try {
            VolColocLabelImages.fromRois(reference, new Roi[]{polyline});
            throw new AssertionError("Expected a polyline ROI to be rejected.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("must enclose an area"));
        }
    }

    @Test
    public void rejectsAngleRoisWhichAlsoEncloseNoArea() {
        ImagePlus reference = new ImagePlus("ref", new ByteProcessor(20, 20));
        Roi angle = new PolygonRoi(
                new int[]{0, 10, 19}, new int[]{0, 15, 2}, 3, Roi.ANGLE);
        assertFalse("an angle encloses no area", angle.isArea());
        try {
            VolColocLabelImages.fromRois(reference, new Roi[]{angle});
            throw new AssertionError("Expected an angle ROI to be rejected.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("must enclose an area"));
        }
    }

    @Test
    public void stillAcceptsEveryAreaRoiType() {
        ImagePlus reference = new ImagePlus("ref", new ByteProcessor(20, 20));
        ImagePlus labels = VolColocLabelImages.fromRois(reference, new Roi[]{
                new Roi(0, 0, 5, 5),
                new OvalRoi(10, 10, 6, 6),
                new PolygonRoi(new int[]{0, 4, 4}, new int[]{10, 10, 14}, 3,
                        Roi.POLYGON)});

        assertEquals(1.0, labels.getProcessor().getf(0, 0), 0.0);
        assertEquals(2.0, labels.getProcessor().getf(13, 13), 0.0);
        // Well inside the triangle (0,10)-(4,10)-(4,14), not on its hypotenuse.
        assertEquals(3.0, labels.getProcessor().getf(3, 11), 0.0);
    }

    @Test
    public void acceptsCompositeFreehandAndRotatedSelections() {
        // Selection types a real ROI set is likely to contain. None may be
        // caught by the area guard, and each must be filled to its own shape
        // rather than to its bounding box. Exact counts, because a bound like
        // "at most the bounding box" is satisfied by the very regression this
        // guards against.
        ImagePlus reference = new ImagePlus("ref", new ByteProcessor(20, 20));
        Roi composite = new ShapeRoi(new Roi(0, 0, 5, 5))
                .or(new ShapeRoi(new OvalRoi(8, 8, 6, 6)));
        Roi freehandTriangle = new PolygonRoi(
                new int[]{0, 9, 0}, new int[]{0, 0, 9}, 3, Roi.FREEROI);
        Roi rotated = new RotatedRectRoi(2, 2, 16, 16, 4);

        assertEquals(57, filledVoxels(reference, composite));
        assertEquals(45, filledVoxels(reference, freehandTriangle));
        assertEquals(70, filledVoxels(reference, rotated));
        // Their bounding boxes are 196, 81 and 289, so none is box-filled.
    }

    private static int filledVoxels(ImagePlus reference, Roi roi) {
        ImagePlus labels = VolColocLabelImages.fromRois(
                reference, new Roi[]{roi});
        int voxels = 0;
        for (int i = 0; i < labels.getProcessor().getPixelCount(); i++) {
            if (labels.getProcessor().getf(i) > 0) voxels++;
        }
        return voxels;
    }

    @Test
    public void rejectsAPolygonWhoseMaskIsEmptyDespiteNormalBounds() {
        // Collinear vertices: bounds are a normal 10x9 rectangle, but the mask
        // is empty. Previously this filled nothing, so the object vanished
        // from every table and every summary denominator shifted with it.
        ImagePlus reference = new ImagePlus("ref", new ByteProcessor(20, 20));
        Roi collinear = new PolygonRoi(
                new int[]{0, 5, 10}, new int[]{10, 15, 19}, 3, Roi.POLYGON);
        assertFalse("test setup: bounds must look normal",
                collinear.getBounds().isEmpty());

        try {
            VolColocLabelImages.fromRois(reference, new Roi[]{
                    new Roi(0, 0, 2, 2), collinear, new Roi(4, 0, 2, 2)});
            throw new AssertionError("Expected an empty-mask ROI to be rejected.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("encloses no pixels"));
            assertTrue("must identify which ROI",
                    expected.getMessage().contains("ROI 2"));
        }
    }

    @Test
    public void rejectsAnEmptyCompositeSelection() {
        // Disjoint intersection: bounds really are 0x0. Must be reported as
        // enclosing nothing, not as lying outside the reference.
        ImagePlus reference = new ImagePlus("ref", new ByteProcessor(20, 20));
        Roi empty = new ShapeRoi(new Roi(0, 0, 2, 2))
                .and(new ShapeRoi(new Roi(10, 10, 2, 2)));
        assertTrue(empty.getBounds().isEmpty());

        try {
            VolColocLabelImages.fromRois(reference, new Roi[]{empty});
            throw new AssertionError("Expected an empty ShapeRoi to be rejected.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("encloses no pixels"));
        }
    }

    @Test
    public void rejectsPointSelections() {
        // isArea() catches these; isLine() did not, so a leftover multipoint
        // marker set became a stray object.
        ImagePlus reference = new ImagePlus("ref", new ByteProcessor(20, 20));
        Roi points = new PointRoi(
                new int[]{1, 5, 9, 13}, new int[]{1, 5, 9, 13}, 4);
        assertFalse(points.isLine());
        try {
            VolColocLabelImages.fromRois(reference, new Roi[]{points});
            throw new AssertionError("Expected a point selection to be rejected.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("point"));
        }
    }

    @Test
    public void rejectsRoisLyingEntirelyOutsideTheReference() {
        // Previously such an ROI was silently dropped: its object vanished
        // from every table and shifted the summary denominators.
        ImagePlus reference = new ImagePlus("ref", new ByteProcessor(4, 1));
        try {
            VolColocLabelImages.fromRois(reference, new Roi[]{
                    new Roi(0, 0, 2, 1), new Roi(10, 0, 2, 1)});
            throw new AssertionError("Expected an off-image ROI to be rejected.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("entirely outside"));
            assertTrue(expected.getMessage().contains("4x1"));
        }
    }

    @Test
    public void stillClipsRoisThatMerelyStraddleTheEdge() {
        // Partly-outside is legitimate and must be clipped, not rejected.
        ImagePlus reference = new ImagePlus("ref", new ByteProcessor(4, 1));

        ImagePlus labels = VolColocLabelImages.fromRois(
                reference, new Roi[]{new Roi(3, 0, 5, 1)});

        assertEquals(0.0, labels.getProcessor().getf(0, 0), 0.0);
        assertEquals(1.0, labels.getProcessor().getf(3, 0), 0.0);
    }

    @Test
    public void rejectsRoisPositionedBeyondTheReferenceStack() {
        // Previously such an ROI fell through to the project-everywhere branch
        // and was smeared over every slice, inflating its volume silently.
        ImagePlus reference = threeSliceReference();
        Roi beyond = new Roi(0, 0, 1, 1);
        beyond.setPosition(0, 9, 0);

        try {
            VolColocLabelImages.fromRois(reference, new Roi[]{beyond});
            throw new AssertionError("Expected an out-of-range slice to be rejected.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("slice 9"));
            assertTrue(expected.getMessage().contains("only 3"));
        }
    }

    @Test
    public void preservesRoiLabelsAboveUnsignedShortRange() {
        ImagePlus reference = new ImagePlus(
                "reference", new ByteProcessor(1, 1));
        Roi[] rois = new Roi[65536];
        Roi pixel = new Roi(0, 0, 1, 1);
        java.util.Arrays.fill(rois, pixel);

        ImagePlus labels = VolColocLabelImages.fromRois(reference, rois);

        assertEquals(65536.0, labels.getProcessor().getf(0), 0.0);
    }

    private static ImagePlus threeSliceReference() {
        ImageStack stack = new ImageStack(4, 1);
        for (int z = 0; z < 3; z++) stack.addSlice(new ByteProcessor(4, 1));
        return new ImagePlus("reference", stack);
    }
}
