/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package volcoloc;

import ij.ImagePlus;
import sc.fiji.volcoloc.core.DirectionalPairRunner;

import java.util.List;

/**
 * Public Java API. It opens no dialogs and displays no result windows.
 *
 * <p>The measurement itself lives in {@code volcoloc-core}, shaded into this
 * jar. Validation, channel-name normalisation and the arithmetic all moved
 * there with it, so this class is the plugin's stable entry point over an
 * engine other plugins can embed. Callers see no difference: the same inputs
 * produce the same numbers, asserted by
 * {@code volcoloc.equivalence.GoldenEquivalenceTest} against output captured
 * before the extraction.
 */
public final class VolColoc {

    private VolColoc() {
    }

    public static VolColocResult run(ImagePlus imageA, ImagePlus imageB) {
        return run(VolColocParameters.builder(imageA, imageB).build());
    }

    public static VolColocResult run(List<ImagePlus> images) {
        return run(VolColocParameters.builder(images).build());
    }

    /**
     * @throws IllegalArgumentException if the images are not a usable set of
     *         label images, or a threshold is out of range. The core throws;
     *         presenting it is the caller's business.
     */
    public static VolColocResult run(VolColocParameters parameters) {
        if (parameters == null) {
            // The core says the same thing, but it cannot read a null's
            // presentation flags, so this one is answered here.
            throw new IllegalArgumentException(
                    "Volumetric Colocalization parameters must not be null.");
        }
        return new VolColocResult(
                parameters,
                DirectionalPairRunner.run(parameters.toCoreParameters()));
    }
}
