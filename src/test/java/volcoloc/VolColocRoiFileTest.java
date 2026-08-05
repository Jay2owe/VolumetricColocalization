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
import ij.gui.OvalRoi;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.io.RoiEncoder;
import ij.process.ByteProcessor;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The ROI-set file path — the route every interactive and macro ROI run
 * actually takes. Other ROI tests enter through {@code fromRois(Roi[])} and so
 * never touch decoding, the zip branch, or title derivation.
 */
public class VolColocRoiFileTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void loadsAZipOfRoisAndLabelsThemInOrder() throws Exception {
        File zip = new File(temporary.newFolder("zip"), "cells.zip");
        writeZip(zip,
                named(new Roi(0, 0, 2, 2), "cell_01"),
                named(new OvalRoi(4, 0, 2, 2), "cell_02"),
                named(new Roi(0, 2, 2, 2), "cell_03"));
        ImagePlus reference = new ImagePlus("ref", new ByteProcessor(8, 4));

        Roi[] loaded = VolColocLabelImages.loadRoiSet(zip.getAbsolutePath());
        assertEquals(3, loaded.length);
        assertEquals("cell_01", loaded[0].getName());

        ImagePlus labels = VolColocLabelImages.fromRoiSetFile(
                reference, zip.getAbsolutePath());
        assertEquals("cells", labels.getTitle());
        assertEquals(1.0, labels.getProcessor().getf(0, 0), 0.0);
        assertEquals(2.0, labels.getProcessor().getf(5, 1), 0.0);
        assertEquals(3.0, labels.getProcessor().getf(0, 2), 0.0);
    }

    @Test
    public void loadsASingleRoiFile() throws Exception {
        File file = new File(temporary.newFolder("single"), "one.roi");
        RoiEncoder.save(new Roi(1, 1, 2, 2), file.getAbsolutePath());
        ImagePlus reference = new ImagePlus("ref", new ByteProcessor(6, 6));

        Roi[] loaded = VolColocLabelImages.loadRoiSet(file.getAbsolutePath());
        assertEquals(1, loaded.length);

        ImagePlus labels = VolColocLabelImages.fromRoiSetFile(
                reference, file.getAbsolutePath());
        assertEquals("one", labels.getTitle());
        assertEquals(1.0, labels.getProcessor().getf(1, 1), 0.0);
        assertEquals(0.0, labels.getProcessor().getf(0, 0), 0.0);
    }

    @Test
    public void ignoresNonRoiEntriesInsideTheZip() throws Exception {
        File zip = new File(temporary.newFolder("mixed"), "mixed.zip");
        FileOutputStream out = new FileOutputStream(zip);
        ZipOutputStream zos = new ZipOutputStream(out);
        try {
            zos.putNextEntry(new ZipEntry("notes.txt"));
            zos.write("ignore me".getBytes("UTF-8"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("only.roi"));
            zos.write(RoiEncoder.saveAsByteArray(new Roi(0, 0, 2, 2)));
            zos.closeEntry();
        } finally {
            zos.close();
        }

        assertEquals(1,
                VolColocLabelImages.loadRoiSet(zip.getAbsolutePath()).length);
    }

    @Test
    public void namesTheOffendingRoiWhenRejectingOne() throws Exception {
        // One stray point marker in a set must be identifiable by name, not
        // just by index.
        File zip = new File(temporary.newFolder("stray"), "stray.zip");
        writeZip(zip,
                named(new Roi(0, 0, 2, 2), "cell_01"),
                named(new PointRoi(3, 3), "seed-marker"),
                named(new Roi(0, 2, 2, 2), "cell_03"));
        ImagePlus reference = new ImagePlus("ref", new ByteProcessor(8, 4));

        try {
            VolColocLabelImages.fromRoiSetFile(
                    reference, zip.getAbsolutePath());
            throw new AssertionError("Expected the point ROI to be rejected.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("seed-marker"));
            assertTrue(expected.getMessage().contains("ROI 2"));
        }
    }

    @Test
    public void roiSetsFlowAllTheWayThroughAnAnalysis() throws Exception {
        ImagePlus reference = new ImagePlus("ref", new ByteProcessor(4, 4));
        File directory = temporary.newFolder("analysis");
        File first = new File(directory, "GFAP.zip");
        File second = new File(directory, "Iba1.zip");
        writeZip(first, named(new Roi(0, 0, 4, 4), "a1"));
        writeZip(second, named(new Roi(0, 0, 2, 2), "b1"));

        ImagePlus a = VolColocLabelImages.fromRoiSetFile(
                reference, first.getAbsolutePath());
        ImagePlus b = VolColocLabelImages.fromRoiSetFile(
                reference, second.getAbsolutePath());

        VolColocResult.DirectionResult aToB =
                VolColoc.run(a, b).getDirectionResults().get(0);
        // 4x4 source, 2x2 partner fully inside it: 4 of 16 voxels.
        assertEquals(16, aToB.getObjects().get(0).getSourceVoxels());
        assertEquals(4, aToB.getObjects().get(0).getOverlapVoxels());
        assertEquals(25.0, aToB.getObjects().get(0).getOverlapPercent(), 0.0001);
    }

    private static Roi named(Roi roi, String name) {
        roi.setName(name);
        return roi;
    }

    private static void writeZip(File destination, Roi... rois)
            throws Exception {
        FileOutputStream out = new FileOutputStream(destination);
        ZipOutputStream zos = new ZipOutputStream(out);
        try {
            for (Roi roi : rois) {
                zos.putNextEntry(new ZipEntry(roi.getName() + ".roi"));
                zos.write(RoiEncoder.saveAsByteArray(roi));
                zos.closeEntry();
            }
        } finally {
            zos.close();
        }
    }
}
