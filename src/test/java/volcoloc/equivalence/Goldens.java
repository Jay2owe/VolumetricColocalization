/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package volcoloc.equivalence;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The immutable record of what this plugin produced <em>before</em> its engine
 * was replaced.
 *
 * <p>Captured once, from the pre-migration build, and committed. The A/B test
 * that first proved the extraction could only run while both engines existed in
 * the tree; the moment the plugin delegates to {@code volcoloc-core} that
 * comparison is the core against itself and proves nothing. These goldens are
 * what survives that transition, and they go on gating every later stage —
 * chassis adoption, shading, release.
 *
 * <p><strong>Do not regenerate to make a diff go away.</strong> A golden that
 * turns out to be wrong is a bug report against the shipped plugin, fixed as its
 * own change with its own release note. Regeneration is deliberately awkward:
 * it requires {@code -Dvolcoloc.captureGoldens=true} and it rewrites a tracked
 * file, so it shows up in review as a diff rather than as silence.
 */
final class Goldens {

    static final String CAPTURE_PROPERTY = "volcoloc.captureGoldens";
    private static final String PATH = "src/test/resources/golden/engine-dump.txt";
    private static final String SEPARATOR = "### ";

    private Goldens() {
    }

    static boolean capturing() {
        return Boolean.getBoolean(CAPTURE_PROPERTY);
    }

    static File file() {
        return new File(PATH);
    }

    static boolean exists() {
        return file().isFile();
    }

    static void save(Map<String, String> dumps) throws IOException {
        File target = file();
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("could not create " + parent);
        }
        Writer writer = new OutputStreamWriter(
                new FileOutputStream(target), "UTF-8");
        try {
            writer.write("# Volumetric Colocalization engine output, captured "
                    + "from the pre-migration build.\n");
            writer.write("# Immutable. See Goldens.java before changing anything here.\n");
            for (Map.Entry<String, String> entry : dumps.entrySet()) {
                writer.write(SEPARATOR);
                writer.write(entry.getKey());
                writer.write('\n');
                writer.write(entry.getValue());
            }
        } finally {
            writer.close();
        }
    }

    static Map<String, String> load() throws IOException {
        Map<String, String> dumps = new LinkedHashMap<String, String>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file()), "UTF-8"));
        try {
            String key = null;
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                // Section markers also begin with '#', so they must be tested
                // before the file-header comments are skipped.
                if (line.startsWith(SEPARATOR)) {
                    if (key != null) dumps.put(key, body.toString());
                    key = line.substring(SEPARATOR.length());
                    body = new StringBuilder();
                } else if (key != null) {
                    body.append(line).append('\n');
                }
            }
            if (key != null) dumps.put(key, body.toString());
        } finally {
            reader.close();
        }
        return dumps;
    }
}
