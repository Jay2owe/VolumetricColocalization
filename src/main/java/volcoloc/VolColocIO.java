/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package volcoloc;

import ij.measure.ResultsTable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Stable CSV output layout shared by the UI, macros, and batch runner.
 */
public final class VolColocIO {

    public static final String ROOT_DIRECTORY = "Volumetric Colocalization";

    private VolColocIO() {
    }

    /**
     * Save one analysis under {@code baseDirectory/Volumetric Colocalization}.
     *
     * @return the plugin output root
     */
    public static File save(VolColocResult result, File baseDirectory,
                            String filePrefix) throws IOException {
        if (result == null) throw new IllegalArgumentException("Result must not be null.");
        if (baseDirectory == null) {
            throw new IllegalArgumentException("Base directory must not be null.");
        }
        String prefix = safeName(filePrefix == null ? "analysis" : filePrefix);
        File root = new File(baseDirectory, ROOT_DIRECTORY);
        OutputDirectories directories = ensureTree(root);
        saveResultFiles(result, directories.objects, directories.multi, prefix);
        return root;
    }

    static File saveBatchGroup(VolColocResult result, File baseDirectory,
                               String relativeFolder, String groupName)
            throws IOException {
        File root = new File(baseDirectory, ROOT_DIRECTORY);
        OutputDirectories directories = ensureTree(root);
        File folder = directories.folder;
        if (relativeFolder != null && relativeFolder.trim().length() > 0) {
            String[] parts = relativeFolder.replace('\\', '/').split("/");
            for (String part : parts) {
                if (part.length() > 0) folder = new File(folder, safeName(part));
            }
        }
        File groupFolder = new File(folder, safeName(groupName));
        File objects = new File(groupFolder, "Objects");
        File multi = new File(groupFolder, "Multi");
        mkdirs(objects);
        mkdirs(multi);
        saveResultFiles(result, objects, multi, safeName(groupName));
        return groupFolder;
    }

    static OutputDirectories ensureTree(File root) throws IOException {
        File objects = new File(root, "Objects");
        File multi = new File(root, "Multi");
        File maps = new File(root, "Maps");
        File folder = new File(root, "Folder");
        mkdirs(objects);
        mkdirs(multi);
        mkdirs(maps);
        mkdirs(folder);
        writeReadme(objects, "Per-object directional overlap tables, all-partner detail "
                + "tables, pair summaries, and optional BBColoc, BB-CPC, and "
                + "BBVolColoc tables.\n");
        writeReadme(multi, "Source-anchored multi-colocalization object patterns and "
                + "pattern summaries.\n");
        writeReadme(maps, "Reserved for overlap maps. Maps are outside the v0.1.0 scope "
                + "and are not generated yet.\n");
        writeReadme(folder, "Folder-batch results. Each parsed group has its own "
                + "subdirectory. Batch_Summary.csv retains group-level rows; "
                + "Batch_Folder_Summary.csv combines groups within each folder. "
                + "Equivalent Multi files contain combination patterns.\n");
        return new OutputDirectories(root, objects, multi, maps, folder);
    }

    private static void saveResultFiles(VolColocResult result, File objects,
                                        File multi, String prefix) throws IOException {
        if (result.getParameters().isIncludePerObjectTables()
                || result.getParameters().isIncludePartnerDetails()) {
            for (VolColocResult.DirectionResult direction : result.getDirectionResults()) {
                String directionName = channelFileName(
                        direction.getSourceIndex(),
                        direction.getSourceChannel())
                        + "_in_" + channelFileName(
                        direction.getTargetIndex(),
                        direction.getTargetChannel());
                if (result.getParameters().isIncludePerObjectTables()) {
                    saveTable(result.getPerObjectTable(direction), objects,
                            prefix + "_" + directionName + "_Objects.csv");
                }
                if (result.getParameters().isIncludePartnerDetails()) {
                    saveTable(result.getPartnerDetailTable(direction), objects,
                            prefix + "_" + directionName + "_Partners.csv");
                }
            }
        }
        if (result.getParameters().isIncludeSummaryTable()) {
            saveTable(result.getSummaryTable(), objects, prefix + "_Summary.csv");
        }
        if (!result.getMultiChannelResults().isEmpty()) {
            for (VolColocResult.MultiChannelResult channel
                    : result.getMultiChannelResults()) {
                saveTable(result.getMultiPerObjectTable(channel), multi,
                        prefix + "_" + channelFileName(
                        channel.getSourceIndex(),
                        channel.getSourceChannel())
                                + "_Multi_Objects.csv");
            }
            saveTable(result.getMultiSummaryTable(), multi,
                    prefix + "_Multi_Summary.csv");
        }
        if (!result.getBoundingBoxDirectionResults().isEmpty()) {
            for (VolColocResult.BoundingBoxDirectionResult direction
                    : result.getBoundingBoxDirectionResults()) {
                String directionName = channelFileName(
                        direction.getSourceIndex(),
                        direction.getSourceChannel())
                        + "_in_" + channelFileName(
                        direction.getTargetIndex(),
                        direction.getTargetChannel());
                saveTable(result.getBoundingBoxTable(direction), objects,
                        prefix + "_" + directionName
                                + "_Bounding_Boxes.csv");
            }
            saveTable(result.getBoundingBoxSummaryTable(), objects,
                    prefix + "_Bounding_Box_Summary.csv");
        }
    }

    static void saveTable(ResultsTable table, File directory, String name)
            throws IOException {
        if (table == null) return;
        mkdirs(directory);
        writeCsv(table, new File(directory, name));
    }

    /**
     * Write a table as UTF-8 CSV.
     *
     * <p>{@code ResultsTable.save} encodes in the JVM's default charset, which
     * is the legacy platform charset on the Java 8 that Fiji bundles. That
     * mangles the non-ASCII output this plugin produces — the
     * {@code — Any —} pattern row and the {@code µm^3} volume unit — for
     * anything that reads the CSV as UTF-8. It also leaves embedded quotes
     * unescaped. Writing the file here fixes both.</p>
     */
    private static void writeCsv(ResultsTable table, File destination)
            throws IOException {
        String[] headings = columnHeadings(table);
        OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(destination), "UTF-8");
        try {
            writeRow(writer, headings);
            for (int row = 0; row < table.getCounter(); row++) {
                String[] values = new String[headings.length];
                for (int column = 0; column < headings.length; column++) {
                    values[column] = table.getStringValue(headings[column], row);
                }
                writeRow(writer, values);
            }
        } finally {
            writer.close();
        }
    }

    private static void writeRow(Writer writer, String[] values)
            throws IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) writer.write(',');
            writer.write(escape(values[i]));
        }
        writer.write("\r\n");
    }

    private static String escape(String value) {
        String text = value == null ? "" : value;
        if (text.indexOf(',') < 0 && text.indexOf('"') < 0
                && text.indexOf('\n') < 0 && text.indexOf('\r') < 0) {
            return text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    /**
     * Real column headings only.
     *
     * <p>Uses {@code getHeadings()} rather than splitting
     * {@code getColumnHeadings()} on tabs: a channel name containing a tab
     * would otherwise be split into two bogus headings and every later lookup
     * of them would fail. {@code getHeadings()} already omits the {@code " "}
     * row-number pseudo-column, but it does report {@code "Label"} for the
     * row-label column, which is not addressable by column index.</p>
     */
    static String[] columnHeadings(ResultsTable table) {
        List<String> result = new ArrayList<String>();
        for (String heading : table.getHeadings()) {
            if (heading == null || heading.length() == 0
                    || " ".equals(heading)) {
                continue;
            }
            if ("Label".equals(heading) && table.getColumnIndex("Label")
                    == ResultsTable.COLUMN_NOT_FOUND) {
                continue;
            }
            result.add(heading);
        }
        return result.toArray(new String[0]);
    }

    private static void writeReadme(File directory, String text) throws IOException {
        File readme = new File(directory, "README.txt");
        if (readme.isFile()) return;
        Writer writer = new OutputStreamWriter(
                new FileOutputStream(readme), "UTF-8");
        try {
            writer.write(text);
        } finally {
            writer.close();
        }
    }

    private static void mkdirs(File directory) throws IOException {
        if (directory.isDirectory()) return;
        if (!directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Could not create output directory: " + directory);
        }
    }

    /**
     * Longest name component kept from any single channel, group or prefix.
     *
     * <p>Channel names default to image titles, which for file input are whole
     * filenames. A five-channel analysis puts every channel name in the prefix
     * and then two of them again in the direction segment, so uncapped names
     * blow past the 255-character limit on one path component and the save
     * fails outright. 48 keeps a name like
     * {@code 20260731_mouse00_CA1_GFAP_labels.tif} whole while holding the
     * worst-case filename to about 174 characters.</p>
     *
     * <p>That bounds the component, not the whole path. A deeply nested batch
     * output under an already-long save directory can still exceed Windows'
     * 260-character path limit; that fails loudly when the directory is
     * created, rather than silently, so it is left to the user to shorten.</p>
     */
    private static final int MAX_NAME_PART = 48;

    /** Hex digits of the full-name hash appended to a truncated name. */
    private static final int HASH_SUFFIX = 8;

    static String safeName(String text) {
        String clean = text == null ? "" : text.trim();
        clean = clean.replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]", "_");
        clean = clean.replaceAll("\\s+", "_");
        clean = clean.replaceAll("_+", "_");
        clean = clean.replaceAll("^[._]+|[._]+$", "");
        if (clean.length() > MAX_NAME_PART) {
            // Plain truncation is not safe here. Callers use this both to
            // de-duplicate names and to build the path, and they disambiguate
            // collisions by appending "_2" — which a plain cut would remove
            // again, so the de-duplication loop would never terminate and two
            // different long names would land in the same directory. The hash
            // of the full name keeps distinct inputs distinct and makes each
            // "_2" attempt produce a different result.
            // Spend a budget measured in chars, but only ever advance by whole
            // code points: the cap has to hold in the same unit the filesystem
            // counts, while cutting mid-surrogate would leave an unpaired half
            // in the path.
            // Locale.ROOT so the digits are plain ASCII whatever the machine's
            // locale: this ends up in a filename.
            String hash = String.format(
                    Locale.ROOT, "%08x", Integer.valueOf(clean.hashCode()));
            int budget = MAX_NAME_PART - HASH_SUFFIX - 1;
            int cut = 0;
            while (cut < clean.length()) {
                int width = Character.charCount(clean.codePointAt(cut));
                if (cut + width > budget) break;
                cut += width;
            }
            clean = clean.substring(0, cut).replaceAll("[._]+$", "") + "_" + hash;
        }
        return clean.length() == 0 ? "analysis" : clean;
    }

    private static String channelFileName(int index, String channel) {
        // The C<n>_ prefix keeps channels distinct even when two long names
        // truncate to the same text.
        return "C" + (index + 1) + "_" + safeName(channel);
    }

    static final class OutputDirectories {
        final File root;
        final File objects;
        final File multi;
        final File maps;
        final File folder;

        OutputDirectories(File root, File objects, File multi,
                          File maps, File folder) {
            this.root = root;
            this.objects = objects;
            this.multi = multi;
            this.maps = maps;
            this.folder = folder;
        }
    }
}
