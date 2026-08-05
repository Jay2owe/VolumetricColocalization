/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package volcoloc.ui;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Toolkit;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * CPC-style Swing dialog with section headers, toggles, and path browsers.
 */
public class VolColocDialog {

    private static final Color BACKGROUND = new Color(245, 245, 245);
    private static final Color HEADER = new Color(55, 71, 79);
    private static final Color LABEL = new Color(33, 33, 33);
    private static final Color HELP = new Color(117, 117, 117);

    private final JDialog dialog;
    private final JPanel content;
    private JPanel activeTarget;
    private final List<ToggleSwitch> toggles = new ArrayList<ToggleSwitch>();
    private final List<JTextField> textFields = new ArrayList<JTextField>();
    private final List<JComboBox<String>> choices =
            new ArrayList<JComboBox<String>>();
    private final List<JTextField> numericFields = new ArrayList<JTextField>();
    private boolean canceled = true;
    private int toggleIndex;
    private int textIndex;
    private int choiceIndex;
    private int numberIndex;

    public VolColocDialog(String title) {
        dialog = new JDialog((Frame) null, title, true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(15, 20, 10, 20));
        content.setBackground(BACKGROUND);
        activeTarget = content;

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BACKGROUND);
        dialog.getContentPane().setLayout(new BorderLayout());
        dialog.getContentPane().add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        buttons.setBackground(BACKGROUND);
        JButton cancel = new JButton("Cancel");
        JButton ok = new JButton("OK");
        cancel.setPreferredSize(new Dimension(80, 28));
        ok.setPreferredSize(new Dimension(80, 28));
        cancel.addActionListener(event -> {
            canceled = true;
            dialog.dispose();
        });
        ok.addActionListener(event -> {
            canceled = false;
            dialog.dispose();
        });
        buttons.add(cancel);
        buttons.add(ok);
        dialog.getContentPane().add(buttons, BorderLayout.SOUTH);
    }

    public void addHeader(String text) {
        activeTarget.add(Box.createVerticalStrut(10));
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        label.setForeground(HEADER);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        activeTarget.add(label);
        activeTarget.add(Box.createVerticalStrut(4));
        JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        separator.setAlignmentX(Component.LEFT_ALIGNMENT);
        activeTarget.add(separator);
        activeTarget.add(Box.createVerticalStrut(6));
    }

    /**
     * Begin a section whose controls are hidden until the user expands it.
     * Call {@link #endGroup()} after adding the section's controls.
     */
    public JPanel beginCollapsibleSection(String title, boolean expanded) {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setOpaque(false);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JButton header = new JButton();
        header.setHorizontalAlignment(SwingConstants.LEFT);
        header.setBorderPainted(false);
        header.setContentAreaFilled(false);
        header.setFocusPainted(false);
        header.setForeground(HEADER);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(0, 12, 4, 0));
        body.setVisible(expanded);
        updateCollapsibleHeader(header, title, expanded);
        header.addActionListener(event -> {
            boolean show = !body.isVisible();
            body.setVisible(show);
            updateCollapsibleHeader(header, title, show);
            repack();
        });
        wrapper.add(header);
        wrapper.add(body);
        content.add(Box.createVerticalStrut(6));
        content.add(wrapper);
        activeTarget = body;
        return body;
    }

    public void endGroup() {
        activeTarget = content;
    }

    public ToggleSwitch addToggle(String text, boolean defaultValue) {
        ToggleSwitch toggle = new ToggleSwitch(defaultValue);
        toggles.add(toggle);
        JPanel row = row();
        row.add(label(text));
        row.add(Box.createHorizontalGlue());
        row.add(toggle);
        addRow(row);
        return toggle;
    }

    public JLabel addHelpText(String text) {
        JLabel help = new JLabel(
                "<html><body style='width:320px;'>" + text + "</body></html>");
        help.setFont(help.getFont().deriveFont(Font.ITALIC, 10f));
        help.setForeground(HELP);
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        help.setBorder(new EmptyBorder(0, 24, 2, 0));
        activeTarget.add(help);
        activeTarget.add(Box.createVerticalStrut(2));
        return help;
    }

    public JTextField addStringField(
            String text, String defaultValue, int columns) {
        JPanel row = row();
        row.add(label(text));
        row.add(Box.createHorizontalGlue());
        JTextField field = new JTextField(defaultValue, columns);
        field.setMaximumSize(new Dimension(Math.max(120, columns * 9), 24));
        row.add(field);
        textFields.add(field);
        addRow(row);
        return field;
    }

    public JComboBox<String> addChoice(
            String text, String[] items, String defaultItem) {
        JPanel row = row();
        row.add(label(text));
        row.add(Box.createHorizontalGlue());
        JComboBox<String> choice = new JComboBox<String>(items);
        if (defaultItem != null) choice.setSelectedItem(defaultItem);
        choice.setMaximumSize(new Dimension(280, 24));
        row.add(choice);
        choices.add(choice);
        addRow(row);
        return choice;
    }

    public JTextField addFileField(String text, String defaultPath) {
        return addFileField(text, defaultPath, null);
    }

    public JTextField addFileField(
            String text, String defaultPath, FileNameExtensionFilter filter) {
        JPanel row = row();
        row.add(label(text));
        row.add(Box.createHorizontalGlue());
        JTextField field = new JTextField(defaultPath, 18);
        field.setMaximumSize(new Dimension(230, 24));
        row.add(field);
        row.add(Box.createHorizontalStrut(4));
        JButton browse = new JButton("...");
        browse.setPreferredSize(new Dimension(28, 24));
        browse.setMaximumSize(new Dimension(28, 24));
        browse.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser();
            if (filter != null) chooser.setFileFilter(filter);
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                field.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        row.add(browse);
        textFields.add(field);
        addRow(row);
        return field;
    }

    public JTextField addDirectoryField(String text, String defaultPath) {
        JPanel row = row();
        row.add(label(text));
        row.add(Box.createHorizontalGlue());
        JTextField field = new JTextField(defaultPath, 18);
        field.setMaximumSize(new Dimension(230, 24));
        row.add(field);
        row.add(Box.createHorizontalStrut(4));
        JButton browse = new JButton("...");
        browse.setPreferredSize(new Dimension(28, 24));
        browse.setMaximumSize(new Dimension(28, 24));
        browse.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (defaultPath != null && defaultPath.length() > 0) {
                chooser.setCurrentDirectory(new File(defaultPath));
            }
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                field.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        row.add(browse);
        textFields.add(field);
        addRow(row);
        return field;
    }

    public JTextField addNumericField(
            String text, double defaultValue, int decimals) {
        JPanel row = row();
        row.add(label(text));
        row.add(Box.createHorizontalGlue());
        String value = decimals == 0
                ? String.valueOf((int) defaultValue)
                : String.valueOf(defaultValue);
        JTextField field = new JTextField(value, 8);
        field.setMaximumSize(new Dimension(96, 24));
        row.add(field);
        numericFields.add(field);
        addRow(row);
        return field;
    }

    public boolean showDialog() {
        repack();
        dialog.setVisible(true);
        return !canceled;
    }

    public boolean wasCanceled() {
        return canceled;
    }

    public boolean getNextBoolean() {
        return toggles.get(toggleIndex++).isSelected();
    }

    public String getNextString() {
        return textFields.get(textIndex++).getText();
    }

    public String getNextChoice() {
        return (String) choices.get(choiceIndex++).getSelectedItem();
    }

    public double getNextNumber() {
        String text = numericFields.get(numberIndex++).getText().trim();
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException exception) {
            return Double.NaN;
        }
    }

    private JPanel row() {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        row.setBorder(new EmptyBorder(0, 4, 0, 4));
        return row;
    }

    private JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));
        label.setForeground(LABEL);
        return label;
    }

    private void addRow(JPanel row) {
        activeTarget.add(row);
        activeTarget.add(Box.createVerticalStrut(4));
    }

    private void repack() {
        dialog.pack();
        Dimension preferred = dialog.getPreferredSize();
        int maximumHeight = (int) (
                Toolkit.getDefaultToolkit().getScreenSize().height * 0.8);
        if (preferred.height > maximumHeight) {
            dialog.setSize(preferred.width + 30, maximumHeight);
        }
        dialog.setLocationRelativeTo(null);
    }

    private static void updateCollapsibleHeader(
            JButton button, String title, boolean expanded) {
        button.setText((expanded ? "\u25bc " : "\u25b6 ") + title);
    }
}
