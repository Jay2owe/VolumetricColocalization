/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package volcoloc.equivalence;

import org.junit.Test;
import volcoloc.VolColoc;
import volcoloc.VolColocParameters;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The standing ship gate: what this plugin produces today, against what it
 * produced before its engine was replaced.
 *
 * <p>Runs {@code volcoloc.VolColoc} — the plugin's own public entry point,
 * whatever is behind it — over the corpus and compares against the committed
 * goldens. It therefore keeps working across every remaining migration stage:
 * core adoption, chassis adoption, shading. Each stage changes what is behind
 * the API and none of them may change what comes out of it.
 *
 * <p>Regenerate only with {@code -Dvolcoloc.captureGoldens=true}, and only for
 * a reason written down in the CHANGELOG. See {@link Goldens}.
 */
public class GoldenEquivalenceTest {

    private static Map<String, String> currentDumps() {
        Map<String, String> dumps = new LinkedHashMap<String, String>();
        List<Corpus.Case> cases = Corpus.cases();
        for (Corpus.Case corpusCase : cases) {
            for (Corpus.Config config : Corpus.configs()) {
                dumps.put(corpusCase.name + " / " + config.name,
                        ResultDump.of(VolColoc.run(parameters(corpusCase, config))));
            }
        }
        return dumps;
    }

    private static VolColocParameters parameters(Corpus.Case corpusCase,
                                                 Corpus.Config config) {
        int n = corpusCase.images.size();
        return VolColocParameters.builder(corpusCase.images)
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

    @Test
    public void outputHasNotMovedSinceTheGoldensWereCaptured() throws Exception {
        Map<String, String> current = currentDumps();

        if (Goldens.capturing()) {
            Goldens.save(current);
            // Capturing is not verifying. Say so loudly rather than letting a
            // capture run look like a green gate.
            System.out.println("CAPTURED " + current.size()
                    + " goldens to " + Goldens.file().getAbsolutePath()
                    + " — this run verified nothing.");
            return;
        }

        assertTrue("goldens missing at " + Goldens.file().getAbsolutePath()
                        + "; capture them from a known-good build with -D"
                        + Goldens.CAPTURE_PROPERTY + "=true",
                Goldens.exists());

        Map<String, String> golden = Goldens.load();
        assertEquals("the corpus changed size, so the goldens no longer "
                        + "describe the same work — this is a corpus change, "
                        + "not a passing gate",
                golden.size(), current.size());
        assertTrue("goldens are empty", golden.size() > 200);

        List<String> moved = new ArrayList<String>();
        for (Map.Entry<String, String> entry : golden.entrySet()) {
            String now = current.get(entry.getKey());
            if (now == null) {
                moved.add(entry.getKey() + ": no longer produced");
            } else if (!entry.getValue().equals(now)) {
                moved.add(entry.getKey() + '\n'
                        + firstDifference(entry.getValue(), now));
            }
        }
        if (!moved.isEmpty()) {
            fail("output moved in " + moved.size() + " of " + golden.size()
                    + " cases:\n" + moved.get(0));
        }
    }

    private static String firstDifference(String expected, String actual) {
        String[] left = expected.split("\n", -1);
        String[] right = actual.split("\n", -1);
        int lines = Math.min(left.length, right.length);
        for (int i = 0; i < lines; i++) {
            if (!left[i].equals(right[i])) {
                return "  line " + (i + 1)
                        + "\n  golden : " + left[i]
                        + "\n  current: " + right[i];
            }
        }
        return "  golden has " + left.length + " lines, current has " + right.length;
    }
}
