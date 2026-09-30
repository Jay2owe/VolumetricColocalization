# Changelog

## 0.1.1 - 2026-09-30

Faster, with every measured value unchanged bit for bit, plus three fixes to
the dialogs and the folder batch found by driving them in a real Fiji.

### Performance

- Embeds `volcoloc-core` 0.2.0 (was 0.1.0). The engine now reads each slice
  once from the stack's pixel array, counts labels below 65,536 in a plain
  array, and adds pair overlaps as runs of neighbouring voxels instead of one
  hash-map lookup per voxel per channel pair.
- Measured end to end, as a user runs it: open three label TIFFs, analyse
  every pair in both directions with all tables and the three bounding-box
  analyses, and write every CSV. On a synthetic 3-channel
  1024 x 1024 x 40 stack with 8,000 ellipsoidal objects per channel, the
  median of 5 runs went from 6.7 s with 0.1.0 to 4.1 s with 0.1.1: **1.6x
  faster**. Opening the images and writing the tables do not speed up, so
  the gain is smaller than the core's own 2.6x.
- No output changed: all 24 CSVs of that run (110,299 rows) are
  byte-identical between 0.1.0 and 0.1.1, and the 299 golden dumps pass
  unchanged.

### Fixes

- **Escape** now closes the mode, label-image, ROI-set and folder-batch
  dialogs as Cancel, as it does in any ImageJ dialog. It did nothing before.
- **Macro Recorder**: a dialog run recorded its full
  `run("Volumetric Colocalization", "...")` line and then a second, bare
  `run("Volumetric Colocalization");` that reopens the dialog when the macro
  is replayed. Only the full line is recorded now, and a cancelled dialog
  records nothing.
- **Stopping a folder batch**: Escape was ignored and every group ran. It now
  stops the batch before the next group. Groups already analysed keep their
  saved outputs and appear in the batch tables; the report and the status
  bar say the batch was stopped, and `VolColocBatchResult.isCancelled()`
  reports it to Java callers. An Escape pressed before a batch starts is
  ignored. While a batch runs, the status bar names the group being analysed
  (group N of M) with a progress bar.

## 0.1.0 - 2026-08-08

### Architecture — split into an embeddable engine and a thin plugin

Done before first publication, so this plugin never ships the duplicated
chassis it was built from. No measured value changed: the extraction is gated
by 299 golden dumps captured from the pre-migration build and compared
bit-for-bit, doubles included, on every later change.

- Extracted the analysis engine into `volcoloc-core` and deleted this plugin's
  copy — `VolColocAnalysis`, `BoundingBoxAnalysis` and `PrimitiveMaps`, 960
  lines. 3D Objects Counter+ and the Colocalization Suite can now embed the
  same engine without a user installing this plugin.
- `VolColocResult` is now a table adapter: it holds the engine's result model
  and adds this plugin's ImageJ `ResultsTable` builders. A consumer with its
  own table layout uses the model and skips the tables.
- Adopted `oc3d-core` for ROI ingest, the toggle widget and macro tokenising;
  deleted this plugin's `LabelUtils` and `ToggleSwitch`.
- Adopted the same `oc3d-core` regex-group discovery used by CPC and deleted
  VolColoc's private folder walker and grouping implementation. The plugin
  keeps its 2–5 channel policy and output schema while sharing traversal,
  cycle protection, output exclusion, optional-group handling, and ordering.
- Both modules are shaded into the jar, relocated under `volcoloc.internal`.
  Still one jar, still installable on a bare Fiji with no prerequisites.
- Promoted this plugin's stricter rules into the shared chassis rather than
  losing them to it — see `oc3d-core`. Its ROI ingest previously smeared an
  ROI positioned beyond the reference stack across the whole volume, and its
  macro tokeniser silently mis-parsed unclosed brackets. Both now refuse.

### Behaviour

- Added directional object-volume overlap for 2–5 label images.
- Retained every overlapping partner with a configurable detail-row filter.
- Added per-channel thresholds, pair summaries, and source-anchored
  multi-colocalization patterns.
- Added label-image, ROI-set, macro, Java API, and folder-batch workflows.
- Added CPC-style Swing dialogs and the auto-save output tree.
- Matched CPC's batch entry flow: a **Batch...** footer action opens a dedicated
  batch dialog with an inline, computation-free group preview, then runs the
  accepted batch off the UI thread.
- Changed partner-detail filtering to source-overlap percentage, default 50%.
- Matched CPC multi-target per-object columns, positive-only patterns, `None`,
  and `— Any —` totals.
- Added optional BBColoc, BB-CPC, and BBVolColoc behind collapsed controls.
- Retained and displayed aggregate batch tables when auto-save is disabled.
- Made batch summary output respect the Summary table option.
- Escaped ambiguous multi-pattern channel names and rounded folder-level
  multi-pattern percentages to two decimals.
- Strengthened alignment checks for hyperstack channel, slice, and frame
  dimensions.
- Made the Auto-save toggle authoritative when a save directory is present.
- Made the public batch builder opt in to file output and prevented colliding
  parsed group names from overwriting one another.
- Made the interactive batch Auto-save default off and protected channel and
  recursive-folder CSV paths from sanitization collisions.
- Kept folder-level multi-pattern aggregates separate when batch groups have
  different target-channel sets.
- Propagated CSV write failures, reserved aggregate filenames, skipped
  nonparticipating optional regex groups, closed ROI inputs on failure, and
  guarded recursive batches against directory cycles.
- Made per-group batch write failures abort the run before failed groups enter
  aggregate tables.
- Preserved ROI labels above 65,535 with 32-bit label images and rejected
  floating-point labels beyond Java's integer range.
- Fixed ROIs positioned beyond the reference stack being silently projected
  onto every slice, which inflated their volume and every percentage derived
  from it. They are now rejected with the slice counts named.
- Rejected RGB inputs, which previously passed validation and turned packed
  colour values into fabricated object labels. Indexed-colour label images are
  still accepted; their pixel values really are the labels.
- Rejected line, polyline, angle and point ROIs, which were previously filled
  to their bounding box or vertex polygon — a traced 20-pixel diagonal became a
  400-pixel solid block, and every percentage computed against it was wrong.
- Rejected ROIs lying entirely outside the reference image; they were
  previously dropped without a row, shifting object counts and every summary
  denominator. ROIs straddling the edge are still clipped and kept.
- Rejected hyperstack inputs, whose extra channels and frames were counted as
  further Z layers: a 2-channel 3-slice image reported an object's volume as 6
  voxels rather than 3, and a 4-frame series pooled the time course into one
  object.
- Bounded output filenames. Five channels named after their source files
  produced a 386-character filename, past the 255-character limit on one path
  component, so the save failed and — because saving ran before display — the
  completed analysis was discarded with it. Names are now capped and results
  are displayed before saving. A capped name carries a hash of the full name,
  so two long names that agree past the cap still get separate files and the
  batch de-duplication loop still terminates.
- Reported the `None` multi-colocalization pattern even when every object is
  colocalized, so the row a script reads the non-colocalized count from is
  always present.
- Named the offending ROI in ROI rejection messages, and stopped reporting a
  zero-area selection as lying outside the reference image.
- Rejected ROIs that label no pixels — a polygon with collinear vertices has
  ordinary bounds but an empty mask, so it silently disappeared from every
  table and shifted each summary denominator.
- Placed the `None` pattern immediately before `— Any —` so the last two rows
  of a multi-colocalization summary are the same for every image, and gave the
  folder-level aggregate the same per-source grouping and row order.
- Recorded the macro mode in a locale-independent form; under a Turkish-locale
  Fiji the recorded `mode=rois` came back as `mode=roıs` and would not replay.
- Fixed CSV writing splitting column headings on tabs, which corrupted the
  heading list and aborted the save when a channel name contained a tab.
- Wrote all CSVs and `README.txt` files as UTF-8 instead of the JVM default
  charset, so the `— Any —` pattern and `µm^3` unit survive on Fiji's bundled
  Java 8; quoted embedded quotes as well as commas.
- Gave tables with no rows a header row instead of writing a zero-byte CSV.
- Rejected null threshold entries and empty images with a message rather than
  a NullPointerException or an empty result.
- Built each direction once and reused it for multi-colocalization instead of
  recomputing all N(N-1) ordered pairs.
- Exposed the source channel's volume unit on `DirectionResult`.
- Added an immutable batch golden master captured at commit
  `4b8cde235e97ab8b1522bbeddabd8c62ea9f8f0e`; discovery, result tables, saved
  files, rejection messages, degenerate inputs, and every output switch are
  Tier 1 exact through chassis adoption.
- Documented BBVolColoc's bounding-box-volume cost, ROI overlap and slice
  rules, and the accepted image types.
