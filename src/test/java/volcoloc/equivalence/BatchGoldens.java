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

/** Immutable, pre-chassis snapshots of the public folder-batch workflow. */
final class BatchGoldens {

    static final String CAPTURE_PROPERTY = "volcoloc.captureBatchGoldens";
    static final String SHA_PROPERTY = "volcoloc.batchGoldenSha";
    private static final String ROOT = "src/test/resources/golden/batch";
    private static final String FILE = "batch-dump.txt";
    private static final String SEPARATOR = "### ";

    private BatchGoldens() {
    }

    static boolean capturing() {
        return Boolean.getBoolean(CAPTURE_PROPERTY);
    }

    static File file() {
        String sha = System.getProperty(SHA_PROPERTY);
        if (sha == null || sha.trim().length() == 0) {
            File root = new File(ROOT);
            File[] children = root.listFiles();
            File chosen = null;
            if (children != null) {
                for (File child : children) {
                    if (!child.isDirectory()) continue;
                    if (chosen != null) {
                        throw new IllegalStateException(
                                "Several batch golden sets exist; select one with -D"
                                        + SHA_PROPERTY + "=<git-sha>.");
                    }
                    chosen = child;
                }
            }
            if (chosen == null) return new File(new File(ROOT, "missing"), FILE);
            return new File(chosen, FILE);
        }
        return new File(new File(ROOT, sha.trim()), FILE);
    }

    static void save(Map<String, String> dumps) throws IOException {
        File target = file();
        if (target.isFile()) {
            throw new IOException("Batch goldens are immutable and already exist at "
                    + target.getAbsolutePath());
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }
        Writer writer = new OutputStreamWriter(
                new FileOutputStream(target), "UTF-8");
        try {
            writer.write("# Volumetric Colocalization batch output captured before chassis adoption.\n");
            writer.write("# Tier 1 throughout: byte-identical text, counts, rows, paths and messages.\n");
            writer.write("# Immutable. Do not regenerate to hide a difference.\n");
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
