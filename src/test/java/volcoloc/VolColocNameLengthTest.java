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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Output names are capped so long channel and group names cannot overflow the
 * 255-character limit on a path component. The cap must not make two distinct
 * outputs share a path — the de-duplication in {@link VolColocBatchRunner}
 * appends {@code _2} to break ties, and a plain truncation would cut that back
 * off, so the loop would never end and two groups would land in one directory.
 */
public class VolColocNameLengthTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void longNamesAreCappedButStayDistinct() {
        String stem = "20260731_mouse00_CA1_hippocampus_section_A_";
        String first = VolColocIO.safeName(stem + "GFAP_labels");
        String second = VolColocIO.safeName(stem + "Iba1_labels");

        assertTrue(first.length() <= 48);
        assertTrue(second.length() <= 48);
        assertFalse("names differing only past the cap must stay distinct",
                first.equals(second));
    }

    @Test
    public void appendingADisambiguatingSuffixChangesTheCappedName() {
        // This is what makes the de-duplication loop terminate.
        String base = "20260731_mouse00_CA1_hippocampus_section_A_GFAP_labels";
        assertFalse(VolColocIO.safeName(base)
                .equals(VolColocIO.safeName(base + "_2")));
    }

    @Test
    public void capHoldsAndIsIdempotentForAstralCharacters() {
        // The cap must be measured in the same unit the filesystem counts.
        // Counting the budget in chars but cutting by code-point index let a
        // name of emoji come back longer than the cap, and a second pass
        // shorten it again — which broke the idempotence the batch runner
        // relies on when it reserves a name and then writes it.
        StringBuilder astral = new StringBuilder();
        for (int i = 0; i < 30; i++) astral.append("😀");

        String once = VolColocIO.safeName(astral.toString());
        assertTrue("capped to " + once.length() + " chars",
                once.length() <= 48);
        assertEquals("must be idempotent", once, VolColocIO.safeName(once));
        assertFalse("must not split a surrogate pair",
                Character.isHighSurrogate(once.charAt(
                        once.length() - 1 - 9)));
    }

    @Test
    public void shortNamesAreLeftAlone() {
        assertEquals("GFAP", VolColocIO.safeName("GFAP"));
        assertEquals("20260731_mouse00_CA1_GFAP_labels.tif",
                VolColocIO.safeName("20260731_mouse00_CA1_GFAP_labels.tif"));
    }

    @Test(timeout = 60000)
    public void batchGroupsWithLongSharedPrefixesGetSeparateDirectories()
            throws Exception {
        File labels = temporary.newFolder("long-groups");
        // The two stems agree well past the cap and differ only at the end.
        String stemA = "20260731_mouse00_CA1_hippocampus_section_AAAAAA_run1";
        String stemB = "20260731_mouse00_CA1_hippocampus_section_AAAAAA_run2";
        save(labels, stemA + "_GFAP.tif", 1, 1, 0);
        save(labels, stemA + "_Iba1.tif", 2, 0, 0);
        save(labels, stemB + "_GFAP.tif", 3, 3, 0);
        save(labels, stemB + "_Iba1.tif", 4, 0, 0);

        VolColocBatchResult result = VolColocBatchRunner.run(
                VolColocBatchParameters.builder(
                                labels, "(.+)_([^_]+)[.](?:tif|tiff)$", 2)
                        .recursive(false)
                        .autoSave(true)
                        .saveDirectory(labels)
                        .build());

        assertEquals(2, result.getProcessedGroups());
        assertEquals("each group needs its own directory", 2,
                directoryCount(new File(result.getOutputDirectory(), "Folder")));
    }

    @Test(timeout = 60000)
    public void recursiveFoldersWithLongSharedPrefixesGetSeparatePaths()
            throws Exception {
        File labels = temporary.newFolder("long-folders");
        String base = "region_of_interest_hippocampus_CA1_dorsal_AAAAAA";
        File first = new File(labels, base + "_first");
        File second = new File(labels, base + "_second");
        assertTrue(first.mkdirs());
        assertTrue(second.mkdirs());
        save(first, "s_A.tif", 1, 1, 0);
        save(first, "s_B.tif", 2, 0, 0);
        save(second, "s_A.tif", 3, 3, 0);
        save(second, "s_B.tif", 4, 0, 0);

        VolColocBatchResult result = VolColocBatchRunner.run(
                VolColocBatchParameters.builder(
                                labels, "(.+)_([^_]+)[.](?:tif|tiff)$", 2)
                        .recursive(true)
                        .autoSave(true)
                        .saveDirectory(labels)
                        .build());

        assertEquals(2, result.getProcessedGroups());
        File folder = new File(result.getOutputDirectory(), "Folder");
        assertEquals("one folder's results must not overwrite the other's",
                2, directoryCount(folder));
        // Both must actually contain output, not just exist.
        File[] directories = folder.listFiles();
        for (File directory : directories) {
            if (directory.isDirectory()) {
                assertTrue(directory.getName() + " is empty",
                        fileCount(directory) > 0);
            }
        }
    }

    private static int directoryCount(File folder) {
        int count = 0;
        File[] entries = folder.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                if (entry.isDirectory()) count++;
            }
        }
        return count;
    }

    private static int fileCount(File folder) {
        int count = 0;
        File[] entries = folder.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                count += entry.isFile() ? 1 : fileCount(entry);
            }
        }
        return count;
    }

    private static void save(File folder, String name, int... labels) {
        ShortProcessor processor = new ShortProcessor(labels.length, 1);
        for (int i = 0; i < labels.length; i++) processor.set(i, labels[i]);
        ImagePlus image = new ImagePlus(name, processor);
        IJ.saveAsTiff(image, new File(folder, name).getAbsolutePath());
        image.close();
    }
}
