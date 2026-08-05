# Changelog

## 0.1.0 - Unreleased

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
- Documented BBVolColoc's bounding-box-volume cost, ROI overlap and slice
  rules, and the accepted image types.
