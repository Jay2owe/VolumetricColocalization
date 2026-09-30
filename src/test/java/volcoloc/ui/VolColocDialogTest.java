/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package volcoloc.ui;

import org.junit.Test;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

public class VolColocDialogTest {

    private static Action escapeAction(JRootPane root) {
        Object key = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
        assertNotNull("Escape is bound anywhere in the window", key);
        Action action = root.getActionMap().get(key);
        assertNotNull(action);
        return action;
    }

    @Test
    public void escapeRunsTheCancelAction() {
        JRootPane root = new JRootPane();
        AtomicBoolean cancelled = new AtomicBoolean(false);

        VolColocDialog.bindEscape(root, () -> cancelled.set(true));
        escapeAction(root).actionPerformed(
                new ActionEvent(root, ActionEvent.ACTION_PERFORMED, "escape"));

        assertTrue(cancelled.get());
    }

    @Test
    public void escapeClosesTheDialogAsCancel() {
        assumeFalse(GraphicsEnvironment.isHeadless());
        VolColocDialog dialog = new VolColocDialog("Escape test");
        dialog.addToggle("Option", true);
        dialog.window().pack();
        assertTrue(dialog.window().isDisplayable());

        escapeAction(dialog.window().getRootPane()).actionPerformed(
                new ActionEvent(dialog.window(), ActionEvent.ACTION_PERFORMED, "escape"));

        assertTrue(dialog.wasCanceled());
        assertFalse(dialog.window().isDisplayable());
    }
}
