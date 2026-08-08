/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package volcoloc;

import sc.fiji.oc3d.core.macro.MacroOptions;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Strict parser for ImageJ macro option strings.
 */
public final class VolColocMacroOptionsParser {

    private VolColocMacroOptionsParser() {
    }

    public static VolColocMacroOptions parse(String text) {
        VolColocMacroOptions options = new VolColocMacroOptions();
        Set<String> seen = new HashSet<String>();
        for (String token : tokenize(text == null ? "" : text)) {
            int equals = token.indexOf('=');
            if (equals >= 0) {
                String key = token.substring(0, equals).trim().toLowerCase(Locale.ROOT);
                if (!seen.add(key)) {
                    throw new IllegalArgumentException("Duplicate macro option: " + key);
                }
                applyKeyValue(options, key,
                        decodeValue(token.substring(equals + 1).trim()));
            } else {
                applyFlag(options, token.toLowerCase(Locale.ROOT));
            }
        }
        options.validate();
        return options;
    }

    /**
     * The chassis tokeniser. This plugin's own version was the strict one —
     * refusing unclosed brackets, stray closing brackets and line breaks in
     * values, where the shared tokeniser silently mis-parsed all three — so
     * those checks were promoted into {@code oc3d-core} as
     * {@code strictTokens} rather than given up on adoption.
     *
     * <p>The option <em>model</em> stays here: five image slots, per-channel
     * thresholds and bounding-box thresholds are this plugin's vocabulary, not
     * the family's.
     */
    static List<String> tokenize(String text) {
        return MacroOptions.strictTokens(text);
    }

    private static void applyKeyValue(VolColocMacroOptions options,
                                      String key, String value) {
        if ("mode".equals(key)) {
            String normalized = value.toLowerCase(Locale.ROOT);
            if ("labels".equals(normalized) || "images".equals(normalized)) {
                options.setMode(VolColocMacroOptions.InputMode.LABELS);
            } else if ("rois".equals(normalized) || "roi_sets".equals(normalized)) {
                options.setMode(VolColocMacroOptions.InputMode.ROIS);
            } else if ("batch".equals(normalized) || "folder".equals(normalized)) {
                options.setMode(VolColocMacroOptions.InputMode.BATCH);
            } else {
                throw new IllegalArgumentException(
                        "mode must be labels, rois, or batch.");
            }
            return;
        }
        if ("reference".equals(key)) {
            options.setReferenceTitle(value);
            return;
        }
        if ("reference_path".equals(key)) {
            options.setReferencePath(value);
            return;
        }
        if ("save_dir".equals(key)) {
            options.setSaveDir(value);
            return;
        }
        if ("min_overlap_percent".equals(key) || "min_overlap".equals(key)) {
            options.setMinimumOverlapPercent(parseDouble(key, value));
            return;
        }
        if ("batch_folder".equals(key) || "folder".equals(key)) {
            options.setBatchFolder(value);
            return;
        }
        if ("regex".equals(key) || "label_regex".equals(key)) {
            options.setBatchRegex(value);
            return;
        }
        if ("varying_group".equals(key)) {
            options.setVaryingGroup(parseInteger(key, value));
            return;
        }
        if ("batch_thresholds".equals(key)) {
            options.setBatchThresholds(value);
            return;
        }
        if ("batch_bb_thresholds".equals(key)) {
            options.setBatchBoundingBoxThresholds(value);
            return;
        }

        int index = slotIndex(key, "image", "_path");
        if (index >= 0) {
            options.setImagePath(index, value);
            return;
        }
        index = slotIndex(key, "image", "");
        if (index >= 0) {
            options.setImageTitle(index, value);
            return;
        }
        index = slotIndex(key, "roi", "");
        if (index >= 0) {
            options.setRoiPath(index, value);
            return;
        }
        index = slotIndex(key, "name", "");
        if (index >= 0) {
            options.setChannelName(index, value);
            return;
        }
        index = slotIndex(key, "threshold", "");
        if (index >= 0) {
            options.setThresholdPercent(index, parseDouble(key, value));
            return;
        }
        index = slotIndex(key, "bb_threshold", "");
        if (index >= 0) {
            options.setBoundingBoxThresholdPercent(
                    index, parseDouble(key, value));
            return;
        }
        throw new IllegalArgumentException(
                "Unknown Volumetric Colocalization macro option: " + key);
    }

    private static void applyFlag(VolColocMacroOptions options, String flag) {
        if ("labels".equals(flag)) {
            options.setMode(VolColocMacroOptions.InputMode.LABELS);
        } else if ("rois".equals(flag) || "roi_sets".equals(flag)) {
            options.setMode(VolColocMacroOptions.InputMode.ROIS);
        } else if ("batch".equals(flag)) {
            options.setMode(VolColocMacroOptions.InputMode.BATCH);
        } else if ("bidirectional".equals(flag)) {
            options.setBidirectional(true);
        } else if ("unidirectional".equals(flag)
                || "no_bidirectional".equals(flag)) {
            options.setBidirectional(false);
        } else if ("objects".equals(flag) || "per_object".equals(flag)) {
            options.setPerObjectTables(true);
        } else if ("hide_objects".equals(flag) || "no_objects".equals(flag)) {
            options.setPerObjectTables(false);
        } else if ("summary".equals(flag)) {
            options.setSummaryTable(true);
        } else if ("hide_summary".equals(flag) || "no_summary".equals(flag)) {
            options.setSummaryTable(false);
        } else if ("partners".equals(flag) || "partner_details".equals(flag)) {
            options.setPartnerDetails(true);
        } else if ("hide_partners".equals(flag) || "no_partners".equals(flag)) {
            options.setPartnerDetails(false);
        } else if ("multi".equals(flag) || "multi_colocalization".equals(flag)) {
            options.setMultiColocalization(true);
        } else if ("hide_multi".equals(flag) || "no_multi".equals(flag)) {
            options.setMultiColocalization(false);
        } else if ("bb_overlap".equals(flag)
                || "bounding_box_overlap".equals(flag)) {
            options.setBoundingBoxOverlap(true);
        } else if ("bb_cpc".equals(flag)
                || "bounding_box_cpc".equals(flag)) {
            options.setBoundingBoxCpc(true);
        } else if ("bb_volume_fill".equals(flag)
                || "bb_volcoloc".equals(flag)) {
            options.setBoundingBoxVolumeFill(true);
        } else if ("auto_save".equals(flag) || "autosave".equals(flag)) {
            options.setAutoSave(true);
        } else if ("hide_display".equals(flag) || "no_display".equals(flag)) {
            options.setHideDisplay(true);
        } else if ("recursive".equals(flag)) {
            options.setRecursive(true);
        } else if ("non_recursive".equals(flag) || "no_recursive".equals(flag)) {
            options.setRecursive(false);
        } else {
            throw new IllegalArgumentException(
                    "Unknown Volumetric Colocalization macro flag: " + flag);
        }
    }

    private static int slotIndex(String key, String prefix, String suffix) {
        if (!key.startsWith(prefix) || !key.endsWith(suffix)) return -1;
        String middle = key.substring(
                prefix.length(), key.length() - suffix.length());
        if (middle.length() != 1) return -1;
        char value = middle.charAt(0);
        return value >= '1' && value <= '5' ? value - '1' : -1;
    }

    private static int parseInteger(String key, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer.");
        }
    }

    private static double parseDouble(String key, String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be a number.");
        }
    }

    private static String decodeValue(String raw) {
        if (raw.length() >= 2 && raw.charAt(0) == '['
                && raw.charAt(raw.length() - 1) == ']') {
            String inner = raw.substring(1, raw.length() - 1);
            if (inner.indexOf('"') >= 0
                    || inner.indexOf('\n') >= 0 || inner.indexOf('\r') >= 0) {
                throw new IllegalArgumentException(
                        "Bracketed macro values contain unsupported characters.");
            }
            return inner;
        }
        if (raw.indexOf('"') >= 0
                || raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(
                    "Macro values contain unsupported characters.");
        }
        return raw;
    }
}
