/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package volcoloc.equivalence;

import sc.fiji.volcoloc.core.OverlapParameters;
import volcoloc.VolColocParameters;

/**
 * One input both engines must refuse, described once and built twice.
 *
 * <p>The messages are the plugin's contract with the user, so the harness
 * asserts the text and not merely that something was thrown.
 */
abstract class Rejection {

    final String name;

    Rejection(String name) {
        this.name = name;
    }

    abstract VolColocParameters pluginParameters();

    abstract OverlapParameters coreParameters();
}
