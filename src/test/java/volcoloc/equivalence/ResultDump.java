/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package volcoloc.equivalence;

import sc.fiji.volcoloc.core.OverlapResult;
import volcoloc.VolColocResult;

/**
 * Renders a result to a stable text form, so a whole run can be compared with
 * one string equality rather than by hand-written field-by-field assertions
 * that quietly go stale when a field is added.
 *
 * <p>Every Tier 1 field named in the module's ship gate appears here.
 *
 * <p>Doubles are written as raw bit patterns, not as decimal text. A decimal
 * rendering can hide a difference in the last bits, and the gate is
 * bit-identity, not closeness. It also makes {@code NaN} and {@code -0.0}
 * comparable, which the bounding-box families produce for unselected measures.
 *
 * <p>This once carried two parallel implementations, one per engine, so the
 * extraction could be proved without a shared bug cancelling out on both sides.
 * That comparison ran green over 299 cases and is recorded in the git history;
 * now that the plugin delegates to the core there is only one engine to dump,
 * and the standing gate is {@link GoldenEquivalenceTest} against output
 * captured before the change.
 */
final class ResultDump {

    private ResultDump() {
    }

    private static void bits(StringBuilder out, String name, double value) {
        out.append(name).append('=')
                .append(Long.toHexString(Double.doubleToRawLongBits(value)));
    }

    static String of(VolColocResult result) {
        return of(result.getOverlapResult());
    }

    static String of(OverlapResult result) {
        StringBuilder out = new StringBuilder();
        out.append("channels=").append(result.getChannelNames()).append('\n');

        for (OverlapResult.DirectionResult direction : result.getDirectionResults()) {
            out.append("direction ").append(direction.getSourceIndex())
                    .append("->").append(direction.getTargetIndex())
                    .append(" src=").append(direction.getSourceChannel())
                    .append(" tgt=").append(direction.getTargetChannel())
                    .append(" unit=").append(direction.getVolumeUnit())
                    .append(' ');
            bits(out, "threshold", direction.getThresholdPercent());
            out.append('\n');

            for (OverlapResult.ObjectResult row : direction.getObjects()) {
                out.append("  object label=").append(row.getSourceLabel())
                        .append(" voxels=").append(row.getSourceVoxels())
                        .append(" unit=").append(row.getVolumeUnit())
                        .append(" overlapVoxels=").append(row.getOverlapVoxels())
                        .append(" bestPartner=").append(row.getBestPartnerLabel())
                        .append(" bestPartnerVoxels=").append(row.getBestPartnerOverlapVoxels())
                        .append(" partners=").append(row.getPartnerCount())
                        .append(" colocalized=").append(row.isColocalized())
                        .append(' ');
                bits(out, "volume", row.getSourceVolume());
                out.append(' ');
                bits(out, "overlapPercent", row.getOverlapPercent());
                out.append('\n');
            }

            for (OverlapResult.PartnerDetail row : direction.getPartnerDetails()) {
                out.append("  detail source=").append(row.getSourceLabel())
                        .append(" partner=").append(row.getPartnerLabel())
                        .append(" voxels=").append(row.getOverlapVoxels())
                        .append(' ');
                bits(out, "sourcePercent", row.getSourceOverlapPercent());
                out.append(' ');
                bits(out, "partnerPercent", row.getPartnerOverlapPercent());
                out.append('\n');
            }

            OverlapResult.Summary summary = direction.getSummary();
            out.append("  summary count=").append(summary.getObjectCount())
                    .append(" colocalizedCount=").append(summary.getColocalizedCount())
                    .append(' ');
            bits(out, "mean", summary.getMeanOverlapPercent());
            out.append(' ');
            bits(out, "median", summary.getMedianOverlapPercent());
            out.append(' ');
            bits(out, "colocalizedPercent", summary.getColocalizedPercent());
            out.append('\n');
        }

        for (OverlapResult.MultiChannelResult multi : result.getMultiChannelResults()) {
            out.append("multi source=").append(multi.getSourceIndex())
                    .append(' ').append(multi.getSourceChannel()).append('\n');
            for (OverlapResult.MultiObjectResult row : multi.getObjects()) {
                out.append("  multiObject label=").append(row.getSourceLabel())
                        .append(" targetsHit=").append(row.getTargetsHit())
                        .append(" pattern=").append(row.getPattern()).append('\n');
                for (OverlapResult.TargetStatus status : row.getTargets()) {
                    out.append("    target index=").append(status.getTargetIndex())
                            .append(" channel=").append(status.getTargetChannel())
                            .append(" partner=").append(status.getPartnerLabel())
                            .append(" colocalized=").append(status.isColocalized())
                            .append(' ');
                    bits(out, "percent", status.getOverlapPercent());
                    out.append('\n');
                }
            }
            for (OverlapResult.PatternSummary row : multi.getPatterns()) {
                out.append("  pattern count=").append(row.getObjectCount())
                        .append(' ');
                bits(out, "percent", row.getObjectPercent());
                out.append(" name=").append(row.getPattern()).append('\n');
            }
        }

        for (OverlapResult.BoundingBoxDirectionResult direction
                : result.getBoundingBoxDirectionResults()) {
            out.append("bbox ").append(direction.getSourceIndex())
                    .append("->").append(direction.getTargetIndex())
                    .append(" src=").append(direction.getSourceChannel())
                    .append(" tgt=").append(direction.getTargetChannel())
                    .append(" overlap=").append(direction.isIncludeBoundingBoxOverlap())
                    .append(" cpc=").append(direction.isIncludeBoundingBoxCpc())
                    .append(" fill=").append(direction.isIncludeBoundingBoxVolumeFill())
                    .append(' ');
            bits(out, "threshold", direction.getThresholdPercent());
            out.append('\n');
            for (OverlapResult.BoundingBoxObjectResult row : direction.getObjects()) {
                out.append("  bboxObject label=").append(row.getSourceLabel())
                        .append(" boxVolume=").append(row.getBoxVolume())
                        .append(" overlapPartner=").append(row.getBoundingBoxOverlapPartnerLabel())
                        .append(" overlapColoc=").append(row.isBoundingBoxOverlapColocalized())
                        .append(" cpcColoc=").append(row.isBoundingBoxCpcColocalized())
                        .append(" cpcPartner=").append(row.getBoundingBoxCpcPartnerLabel())
                        .append(" cpcContains=").append(row.getBoundingBoxCpcContainsCount())
                        .append(" fillPartner=").append(row.getBoundingBoxVolumePartnerLabel())
                        .append(" fillColoc=").append(row.isBoundingBoxVolumeColocalized())
                        .append(' ');
                bits(out, "overlapPercent", row.getBoundingBoxOverlapPercent());
                out.append(' ');
                bits(out, "fillBest", row.getBoundingBoxVolumeBestPercent());
                out.append(' ');
                bits(out, "fillTotal", row.getBoundingBoxVolumeTotalPercent());
                out.append('\n');
            }
        }
        return out.toString();
    }
}
