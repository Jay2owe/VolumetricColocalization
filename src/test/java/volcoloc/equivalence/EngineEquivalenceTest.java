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
import org.junit.Test;
import sc.fiji.volcoloc.core.DirectionalPairRunner;
import sc.fiji.volcoloc.core.OverlapParameters;
import volcoloc.VolColoc;
import volcoloc.VolColocParameters;
import volcoloc.VolColocResult;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The ship gate for the {@code volcoloc-core} extraction.
 *
 * <p>Extraction is a refactor: <strong>outputs must not move.</strong> Rather
 * than capture golden files from one build and diff a later one against them,
 * this runs both engines in the same JVM over the same corpus and compares
 * their full output. That is the stronger form of the same contract — it cannot
 * drift, cannot be silently regenerated to make a diff go away, and covers
 * every field rather than the ones someone remembered to write down.
 *
 * <p>Tier 1, bit-identical, no tolerance: occupied voxel counts and
 * percentages, strongest-partner labels, overlapping-partner counts,
 * thresholded flags, Targets Hit, combination-pattern counts including
 * {@code None} and {@code — Any —}, and all summary counts, means, medians and
 * percentages. See {@code ../../oc3d-core/EQUIVALENCE_HARNESS.md}.
 *
 * <p><strong>Not covered here, by design:</strong> ROI and label ingest. The
 * engine's input is a label image; how that image was produced is the chassis's
 * job and is gated separately when the plugin adopts {@code oc3d-core}.
 */
public class EngineEquivalenceTest {

    @Test
    public void extractedEngineReproducesEveryFieldOfTheCurrentEngine() {
        List<Corpus.Case> before = Corpus.cases();
        List<Corpus.Case> after = Corpus.cases();
        List<Corpus.Config> configs = Corpus.configs();
        assertEquals(before.size(), after.size());

        int compared = 0;
        List<String> differences = new ArrayList<String>();
        for (int c = 0; c < before.size(); c++) {
            for (Corpus.Config config : configs) {
                String label = before.get(c).name + " / " + config.name;
                String expected = ResultDump.of(
                        VolColoc.run(pluginParameters(before.get(c).images, config)));
                String actual = ResultDump.of(
                        DirectionalPairRunner.run(coreParameters(after.get(c).images, config)));
                if (!expected.equals(actual)) {
                    differences.add(label + '\n' + firstDifference(expected, actual));
                }
                compared++;
            }
        }

        // The corpus is only evidence if it actually ran.
        assertTrue("corpus produced no comparisons", compared > 200);
        if (!differences.isEmpty()) {
            fail("Tier 1 differences in " + differences.size() + " of " + compared
                    + " comparisons:\n" + differences.get(0));
        }
    }

    @Test
    public void bothEnginesRejectTheSameInputsWithTheSameMessages() {
        List<Rejection> rejections = Rejections.all();
        assertTrue(rejections.size() >= 6);
        for (Rejection rejection : rejections) {
            String fromPlugin = messageFromPlugin(rejection);
            String fromCore = messageFromCore(rejection);
            assertEquals(rejection.name + ": rejection message drifted",
                    fromPlugin, fromCore);
            assertTrue(rejection.name + ": expected a rejection, both engines accepted",
                    fromPlugin != null);
        }
    }

    /**
     * Negative control. A comparison that cannot fail proves nothing, so drive
     * the two engines apart deliberately and require the differ to notice —
     * once per output family, so a whole family cannot silently drop out of the
     * dump and still read as agreement.
     */
    @Test
    public void theComparisonDetectsADifferenceWhenThereIsOne() {
        List<ImagePlus> left = Corpus.cases().get(0).images;
        List<ImagePlus> right = Corpus.cases().get(0).images;
        Corpus.Config base = null;
        Corpus.Config bbAll = null;
        for (Corpus.Config config : Corpus.configs()) {
            if ("t30-default".equals(config.name)) base = config;
            if ("bb-all".equals(config.name)) bbAll = config;
        }

        // A different threshold moves the object rows, summaries and patterns.
        String atThirty = ResultDump.of(VolColoc.run(pluginParameters(left, base)));
        String atHundred = ResultDump.of(DirectionalPairRunner.run(
                coreParameters(right, findConfig("t100"))));
        assertTrue("threshold change went undetected", !atThirty.equals(atHundred));

        // Turning the bounding-box families on must change the dump too.
        String withBoxes = ResultDump.of(DirectionalPairRunner.run(
                coreParameters(right, bbAll)));
        assertTrue("bounding-box output went undetected", !atThirty.equals(withBoxes));

        // And the differ must report a line, not an empty string.
        assertTrue("differ produced no detail",
                firstDifference(atThirty, atHundred).contains("line "));
    }

    private static Corpus.Config findConfig(String name) {
        for (Corpus.Config config : Corpus.configs()) {
            if (name.equals(config.name)) return config;
        }
        throw new IllegalStateException("no config named " + name);
    }

    @Test
    public void multiTargetPatternConstantsAreUnchanged() {
        // The batch runner and downstream scripts match on these literally.
        assertEquals("None", sc.fiji.volcoloc.core.MultiTargetSummary.NO_HITS_PATTERN);
        assertEquals("\u2014 Any \u2014", sc.fiji.volcoloc.core.MultiTargetSummary.ANY_PATTERN);
    }

    // ------------------------------------------------------------------

    private static VolColocParameters pluginParameters(List<ImagePlus> images,
                                                       Corpus.Config config) {
        int n = images.size();
        return VolColocParameters.builder(images)
                .thresholdsPercent(Corpus.repeat(config.threshold, n))
                .boundingBoxThresholdsPercent(
                        Corpus.repeat(config.boundingBoxThreshold, n))
                .bidirectional(config.bidirectional)
                .includeMultiColocalization(config.multi)
                .includePartnerDetails(config.partnerDetails)
                .minimumDetailOverlapPercent(config.minimumDetailPercent)
                .includeBoundingBoxOverlap(config.bbOverlap)
                .includeBoundingBoxCpc(config.bbCpc)
                .includeBoundingBoxVolumeFill(config.bbVolumeFill)
                .build();
    }

    private static OverlapParameters coreParameters(List<ImagePlus> images,
                                                    Corpus.Config config) {
        int n = images.size();
        return OverlapParameters.builder(images)
                .thresholdsPercent(Corpus.repeat(config.threshold, n))
                .boundingBoxThresholdsPercent(
                        Corpus.repeat(config.boundingBoxThreshold, n))
                .bidirectional(config.bidirectional)
                .includeMultiColocalization(config.multi)
                .includePartnerDetails(config.partnerDetails)
                .minimumDetailOverlapPercent(config.minimumDetailPercent)
                .includeBoundingBoxOverlap(config.bbOverlap)
                .includeBoundingBoxCpc(config.bbCpc)
                .includeBoundingBoxVolumeFill(config.bbVolumeFill)
                .build();
    }

    private static String messageFromPlugin(Rejection rejection) {
        try {
            VolColoc.run(rejection.pluginParameters());
            return null;
        } catch (IllegalArgumentException expected) {
            return expected.getMessage();
        }
    }

    private static String messageFromCore(Rejection rejection) {
        try {
            DirectionalPairRunner.run(rejection.coreParameters());
            return null;
        } catch (IllegalArgumentException expected) {
            return expected.getMessage();
        }
    }

    /** Points where the two dumps first diverge, with a little context. */
    private static String firstDifference(String expected, String actual) {
        String[] left = expected.split("\n", -1);
        String[] right = actual.split("\n", -1);
        int lines = Math.min(left.length, right.length);
        for (int i = 0; i < lines; i++) {
            if (!left[i].equals(right[i])) {
                return "  line " + (i + 1)
                        + "\n  plugin: " + left[i]
                        + "\n  core  : " + right[i];
            }
        }
        return "  plugin has " + left.length + " lines, core has " + right.length;
    }
}
