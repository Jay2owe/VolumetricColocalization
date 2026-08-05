/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package volcoloc;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VolColocMacroOptionsParserTest {

    @Test
    public void parsesEveryPerChannelThreshold() {
        VolColocMacroOptions options = VolColocMacroOptionsParser.parse(
                "mode=labels image1=A image2=B image3=C "
                        + "name1=DAPI name2=GFAP name3=NeuN "
                        + "threshold1=10 threshold2=20.5 threshold3=99 "
                        + "unidirectional min_overlap_percent=50.5 "
                        + "bb_overlap bb_cpc bb_volume_fill bb_threshold1=25 "
                        + "hide_partners hide_multi "
                        + "auto_save save_dir=[C:/results]");

        assertEquals(10.0, options.getThresholdPercent(0), 0.0);
        assertEquals(20.5, options.getThresholdPercent(1), 0.0);
        assertEquals(99.0, options.getThresholdPercent(2), 0.0);
        assertFalse(options.isBidirectional());
        assertFalse(options.isPartnerDetails());
        assertFalse(options.isMultiColocalization());
        assertEquals(50.5, options.getMinimumOverlapPercent(), 0.0);
        assertTrue(options.isBoundingBoxOverlap());
        assertTrue(options.isBoundingBoxCpc());
        assertTrue(options.isBoundingBoxVolumeFill());
        assertEquals(25.0, options.getBoundingBoxThresholdPercent(0), 0.0);
        assertTrue(options.isAutoSave());
        assertEquals("C:/results", options.getSaveDir());
    }

    @Test
    public void roundTripsBatchRegexWithBackslashes() {
        VolColocMacroOptions source = new VolColocMacroOptions();
        source.setMode(VolColocMacroOptions.InputMode.BATCH);
        source.setBatchFolder("C:\\labels");
        source.setBatchRegex("(.+)_([^_]+)\\.(?:tif|tiff)$");
        source.setBatchThresholds("DAPI=20,GFAP=40");
        source.setVaryingGroup(2);

        VolColocMacroOptions parsed =
                VolColocMacroOptionsParser.parse(source.toMacroOptions());

        assertEquals("C:/labels", parsed.getBatchFolder());
        assertEquals("(.+)_([^_]+)\\.(?:tif|tiff)$",
                parsed.getBatchRegex());
        assertEquals("DAPI=20,GFAP=40", parsed.getBatchThresholds());
    }

    @Test
    public void recordedMacrosReplayUnderALocaleWithNoDottedI() {
        // "ROIS".toLowerCase() is "roıs" under tr/az, which the Locale.ROOT
        // parser cannot read back — so a recorded ROI-mode macro line would be
        // unreplayable on a Turkish Fiji.
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            for (VolColocMacroOptions.InputMode mode
                    : VolColocMacroOptions.InputMode.values()) {
                VolColocMacroOptions source = new VolColocMacroOptions();
                source.setMode(mode);
                if (mode == VolColocMacroOptions.InputMode.BATCH) {
                    source.setBatchFolder("C:/labels");
                } else {
                    source.setImageTitle(0, "A");
                    source.setImageTitle(1, "B");
                }

                String recorded = source.toMacroOptions();
                assertEquals(mode + " must round-trip", mode,
                        VolColocMacroOptionsParser.parse(recorded).getMode());
            }
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    public void saveDirectoryDoesNotEnableAutoSave() {
        VolColocMacroOptions options = VolColocMacroOptionsParser.parse(
                "mode=labels image1=A image2=B save_dir=[C:/results]");

        assertFalse(options.isAutoSave());
        assertEquals("C:/results", options.getSaveDir());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownOptions() {
        VolColocMacroOptionsParser.parse("image1=A image2=B thresholdd1=20");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDuplicateOptions() {
        VolColocMacroOptionsParser.parse(
                "image1=A image2=B threshold1=20 threshold1=30");
    }
}
