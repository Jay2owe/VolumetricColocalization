/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package volcoloc;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ShortProcessor;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FilenameFilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VolumetricColocalizationPluginTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void interactiveAutoSaveDefaultIsOff() {
        assertFalse(Volumetric_Colocalization.DEFAULT_AUTO_SAVE);
    }

    @Test
    public void runsAFileMacroHeadlesslyAndSavesResults() throws Exception {
        File input = temporary.newFolder("input");
        File output = temporary.newFolder("output");
        File a = save(input, "A.tif", 1, 1, 1, 0);
        File b = save(input, "B.tif", 2, 2, 0, 0);
        String options = "mode=labels image1_path=["
                + slash(a) + "] image2_path=[" + slash(b) + "] "
                + "threshold1=30 threshold2=30 bidirectional "
                + "objects summary partners multi min_overlap_percent=50 "
                + "bb_overlap bb_cpc bb_volume_fill "
                + "bb_threshold1=20 bb_threshold2=20 "
                + "auto_save save_dir=[" + slash(output) + "] hide_display";

        new Volumetric_Colocalization().run(options);

        File root = new File(output, VolColocIO.ROOT_DIRECTORY);
        assertTrue(root.isDirectory());
        File[] summaries = new File(root, "Objects").listFiles(
                new FilenameFilter() {
                    @Override
                    public boolean accept(File directory, String name) {
                        return name.endsWith("_Summary.csv")
                                && !name.endsWith("_Bounding_Box_Summary.csv");
                    }
                });
        assertEquals(1, summaries == null ? 0 : summaries.length);
        File[] boundingSummaries = new File(root, "Objects").listFiles(
                new FilenameFilter() {
                    @Override
                    public boolean accept(File directory, String name) {
                        return name.endsWith("_Bounding_Box_Summary.csv");
                    }
                });
        assertEquals(1, boundingSummaries == null ? 0 : boundingSummaries.length);
    }

    @Test
    public void saveDirectoryWithoutAutoSaveDoesNotWriteResults()
            throws Exception {
        File input = temporary.newFolder("unsaved-input");
        File output = temporary.newFolder("unsaved-output");
        File a = save(input, "A.tif", 1, 1, 0);
        File b = save(input, "B.tif", 2, 0, 0);
        String options = "mode=labels image1_path=["
                + slash(a) + "] image2_path=[" + slash(b) + "] "
                + "save_dir=[" + slash(output) + "] hide_display";

        new Volumetric_Colocalization().run(options);

        assertFalse(new File(output, VolColocIO.ROOT_DIRECTORY).exists());
    }

    private static File save(File folder, String name, int... labels) {
        ShortProcessor processor = new ShortProcessor(labels.length, 1);
        for (int i = 0; i < labels.length; i++) processor.set(i, labels[i]);
        ImagePlus image = new ImagePlus(name, processor);
        File file = new File(folder, name);
        IJ.saveAsTiff(image, file.getAbsolutePath());
        image.close();
        return file;
    }

    private static String slash(File file) {
        return file.getAbsolutePath().replace('\\', '/');
    }
}
