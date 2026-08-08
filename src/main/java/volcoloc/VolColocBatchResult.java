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

/**
 * Summary returned by the headless batch API.
 */
public final class VolColocBatchResult {

    private final int totalGroups;
    private final int runnableGroups;
    private final int processedGroups;
    private final int skippedGroups;
    private final int errorGroups;
    private final File outputDirectory;
    private final ResultsTable summaryTable;
    private final ResultsTable folderSummaryTable;
    private final ResultsTable multiSummaryTable;
    private final ResultsTable folderMultiSummaryTable;
    private final ResultsTable boundingBoxSummaryTable;
    private final ResultsTable folderBoundingBoxSummaryTable;

    VolColocBatchResult(int totalGroups, int runnableGroups,
                        int processedGroups, int skippedGroups,
                        int errorGroups, File outputDirectory,
                        ResultsTable summaryTable,
                        ResultsTable folderSummaryTable,
                        ResultsTable multiSummaryTable,
                        ResultsTable folderMultiSummaryTable,
                        ResultsTable boundingBoxSummaryTable,
                        ResultsTable folderBoundingBoxSummaryTable) {
        this.totalGroups = totalGroups;
        this.runnableGroups = runnableGroups;
        this.processedGroups = processedGroups;
        this.skippedGroups = skippedGroups;
        this.errorGroups = errorGroups;
        this.outputDirectory = outputDirectory;
        this.summaryTable = summaryTable;
        this.folderSummaryTable = folderSummaryTable;
        this.multiSummaryTable = multiSummaryTable;
        this.folderMultiSummaryTable = folderMultiSummaryTable;
        this.boundingBoxSummaryTable = boundingBoxSummaryTable;
        this.folderBoundingBoxSummaryTable = folderBoundingBoxSummaryTable;
    }

    public int getTotalGroups() {
        return totalGroups;
    }

    public int getRunnableGroups() {
        return runnableGroups;
    }

    public int getProcessedGroups() {
        return processedGroups;
    }

    public int getSkippedGroups() {
        return skippedGroups;
    }

    public int getErrorGroups() {
        return errorGroups;
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public ResultsTable getSummaryTable() {
        return summaryTable;
    }

    public ResultsTable getFolderSummaryTable() {
        return folderSummaryTable;
    }

    public ResultsTable getMultiSummaryTable() {
        return multiSummaryTable;
    }

    public ResultsTable getFolderMultiSummaryTable() {
        return folderMultiSummaryTable;
    }

    public ResultsTable getBoundingBoxSummaryTable() {
        return boundingBoxSummaryTable;
    }

    public ResultsTable getFolderBoundingBoxSummaryTable() {
        return folderBoundingBoxSummaryTable;
    }

    public boolean hasErrors() {
        return errorGroups > 0;
    }
}
