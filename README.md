# Volumetric Colocalization

Volumetric Colocalization is an ImageJ/Fiji plugin for measuring how much of
each segmented object's volume is occupied by objects in another channel. It
accepts 2–5 label images or ImageJ ROI sets, analyses every channel pair, keeps
every overlapping partner, and supports reproducible folder batches.

This is an early `0.1.0-SNAPSHOT` build. It is not yet published to an ImageJ
update site.

## What it reports

For each directional comparison, such as A-in-B:

- source label, size in voxels, and calibrated volume;
- total source voxels occupied by any B object;
- occupied percentage of the source object;
- strongest partner label and overlap;
- number of overlapping partner objects;
- thresholded colocalized flag;
- one detail row per source/partner pair meeting the minimum source-overlap
  percentage.

The summary table reports the object count, mean and median overlap percentage,
and the count and percentage at or above the source channel's threshold.
Directions are separate because A-in-B and B-in-A use different denominators.

For 3–5 channels, multi-colocalization copies CPC's source-anchored output. Each
object gets one `Target Coloc` flag and `Target Partner` label per target, plus
`Targets Hit`. Summary patterns contain positive targets only, such as
`B + D`, with `None` and `— Any —` rows. Both of those rows are always present,
reporting zero where they do not apply, so a script can read the
non-colocalized count straight from `None`.

The default threshold is 30%. This is a reporting convention, not a biological
truth claim. The continuous percentage is always retained.

## Inputs

- 2–5 label images from any segmentation source. Background must be 0 and
  object labels must be positive integers.
- Alternatively, 2–5 ImageJ `.roi` or ROI Manager `.zip` files plus one
  reference image. Every ROI must enclose an area; line, polyline, angle and
  point selections are rejected, because they have no volume to measure. ROIs
  carrying a slice position are placed on that slice; ROIs without one are
  projected through every slice. An ROI positioned beyond the reference stack,
  or lying entirely outside it, is rejected rather than silently projected or
  dropped — so use the reference image the ROIs were drawn on. An ROI that
  merely straddles the edge is kept and clipped. Within one ROI set, where two
  ROIs overlap the later ROI wins — a label image gives each voxel exactly one
  label, so an ROI completely covered by a later one disappears.
- One volume at a time. Hyperstacks are rejected as *label images*: extra
  channels and frames would otherwise be counted as further Z layers,
  multiplying every object's volume. Split them with **Image > Stacks > Tools >
  Make Substack**, or if a z-stack has been mislabelled as frames, fix it in
  **Image > Properties**. A hyperstack is still usable as the ROI *reference*
  image — only its slice count is read, and the resulting label images are
  ordinary single-channel stacks.
- Label images must be 8-, 16-, or 32-bit. An 8-bit label image carrying a
  colour LUT is fine — its pixel values are still the labels. RGB images are
  rejected, because their pixel values are packed colours rather than labels.
- All images in a group must have identical width, height, channel, slice, and
  frame dimensions.
- Calibration is read from each source image. Uncalibrated volumes are reported
  in `pixel^3`.

Raw intensity images are not used.

## Build and install locally

Requirements: Java 8 or newer and Maven.

```text
mvn clean test package
```

Copy `target/Volumetric_Colocalization-0.1.0-SNAPSHOT.jar` into Fiji's
`plugins/` directory, restart Fiji, then run:

`Plugins > Volumetric Colocalization`

## Interactive use

Choose one of three input modes:

1. **Label Images** — select open images or browse to files.
2. **ROI Sets** — select a reference image and 2–5 ROI files.
3. **Folder Batch** — provide a filename regular expression and identify the
   capture group containing the channel name.

Each channel has its own outgoing overlap threshold. The partner-row filter is
the percentage of the source object contributed by that individual partner and
defaults to 50%. It never changes object percentages, partner counts, summaries,
or multi-colocalization patterns.

The collapsed **Bounding-box analyses** section optionally enables:

- **BBColoc** — strongest source/partner box intersection as a percentage of
  the source box;
- **BB-CPC** — source centroid inside a partner box, plus partner centroids
  inside the source box;
- **BBVolColoc** — strongest-partner and all-partner label voxels filling the
  source box.

BBColoc and BBVolColoc use separate per-source bounding-box thresholds,
defaulting to 30%.

Folder mode shows every parsed group before any images are opened. Groups with
fewer than 2 or more than 5 files are marked `SKIP`.

## Batch filenames

The default expression is:

```text
(.+)_([^_]+)[.](?:tif|tiff)$
```

With channel capture group `2`, files such as:

```text
mouse01_DAPI.tif
mouse01_GFAP.tif
mouse01_Iba1.tif
```

form one three-channel group. Optional named thresholds use
`DAPI=20,GFAP=40,Iba1=30`; channels not listed use 30%.

## Macro use

Every source threshold is explicit and recordable:

```javascript
run("Volumetric Colocalization",
    "mode=labels image1=[Labels A] image2=[Labels B] " +
    "name1=GFAP name2=Iba1 threshold1=30 threshold2=40 " +
    "bidirectional objects summary partners multi min_overlap_percent=50");
```

Headless file-based run:

```javascript
run("Volumetric Colocalization",
    "mode=labels image1_path=[C:/data/A.tif] image2_path=[C:/data/B.tif] " +
    "threshold1=30 threshold2=30 bidirectional auto_save " +
    "save_dir=[C:/results] hide_display");
```

Folder batch:

```javascript
run("Volumetric Colocalization",
    "mode=batch batch_folder=[C:/data/labels] " +
    "regex=[(.+)_([^_]+)[.](?:tif|tiff)$] varying_group=2 recursive " +
    "batch_thresholds=[DAPI=20,GFAP=40] bidirectional " +
    "min_overlap_percent=50 bb_overlap bb_cpc bb_volume_fill " +
    "batch_bb_thresholds=[DAPI=25,GFAP=35] " +
    "auto_save save_dir=[C:/results] hide_display");
```

Options:

| Option | Meaning | Default |
| --- | --- | --- |
| `mode=labels\|rois\|batch` | Input workflow | `labels` |
| `image1` … `image5` | Open ImageJ window title | — |
| `image1_path` … `image5_path` | Label-image file | — |
| `reference` / `reference_path` | ROI reference image | — |
| `roi1` … `roi5` | ROI or ROI-set file | — |
| `name1` … `name5` | Channel display name | image title |
| `threshold1` … `threshold5` | Outgoing source threshold, 0–100% | `30` |
| `bidirectional` / `unidirectional` | Emit both or forward directions | bidirectional |
| `objects` / `hide_objects` | Per-object tables | on |
| `summary` / `hide_summary` | Pair summary table | on |
| `partners` / `hide_partners` | All-partner detail rows | on |
| `multi` / `hide_multi` | Source-anchored patterns | on |
| `min_overlap_percent=N` | Minimum partner overlap as % of source for a detail row | `50` |
| `bb_overlap` | Enable BBColoc | off |
| `bb_cpc` | Enable BB-CPC | off |
| `bb_volume_fill` | Enable BBVolColoc | off |
| `bb_threshold1` … `bb_threshold5` | Per-source BBColoc/BBVolColoc threshold | `30` |
| `auto_save` and `save_dir=[…]` | Write the output tree | off |
| `hide_display` | Do not open result windows | off |
| `batch_folder=[…]` | Folder-batch input root | — |
| `regex=[…]` | Full filename regular expression | see above |
| `varying_group=N` | Channel-name capture group | `2` |
| `batch_thresholds=[A=%,B=%]` | Named batch thresholds | all 30% |
| `batch_bb_thresholds=[A=%,B=%]` | Named batch bounding-box thresholds | all 30% |
| `recursive` / `non_recursive` | Search subfolders | recursive |

## Java API

The API opens no dialogs, shows no windows, and writes no files:

```java
VolColocParameters parameters = VolColocParameters.builder(images)
    .channelNames(Arrays.asList("GFAP", "Iba1", "NeuN"))
    .thresholdsPercent(Arrays.asList(30.0, 40.0, 30.0))
    .bidirectional(true)
    .minimumDetailOverlapPercent(50.0)
    .includeBoundingBoxOverlap(true)
    .includeBoundingBoxCpc(true)
    .includeBoundingBoxVolumeFill(true)
    .build();

VolColocResult result = VolColoc.run(parameters);
ResultsTable summary = result.getSummaryTable();
```

`VolColocLabelImages` exposes ROI-to-label conversion.
`VolColocBatchRunner.preview(parameters)` and `.run(parameters)` provide the
headless batch workflow. `VolColocBatchResult` retains the group and folder
summary tables even when auto-save is disabled.

## Saved output

```text
Volumetric Colocalization/
  Objects/   object, partner, pair-summary, and optional bounding-box CSVs
  Multi/     per-source pattern objects and summaries
  Maps/      reserved; overlap maps are outside v0.1.0
  Folder/    per-group batches and group/folder aggregate CSVs
```

Each top-level output subdirectory contains a `README.txt`.

All CSVs are written as UTF-8 with a header row, including tables that turned
out to have no rows, and fields containing commas or quotes are quoted. The
`— Any —` pattern row and the `µm^3` volume unit therefore read correctly in
pandas (`pd.read_csv(path)`) and R (`read.csv(path, encoding = "UTF-8")`)
regardless of the machine's locale.

No byte-order mark is written, because one would attach itself to the first
column name under `pd.read_csv`. Desktop Excel assumes the local ANSI codepage
when you double-click a `.csv`, so those two symbols will look wrong there;
open the file with **Data > From Text/CSV** and pick UTF-8 instead.

## Algorithm

The engine scans the aligned label stacks once. It counts every label's voxels
and every non-background `(A label, B label)` intersection using primitive
open-addressing maps, avoiding per-voxel boxed objects. For each source object,
all partner intersections are summed to obtain occupied volume. The full pair
list remains available for detail output; only the separate “best partner”
column is reduced to one label.

Runtime of the core analysis is proportional to voxels × channel pairs. Memory
is proportional to the number of labels plus the number of actually overlapping
label pairs.

**None of the three optional bounding-box analyses share that bound**, and both
of the ways they can be slow are worth knowing before you enable them on a
batch. Try them on one image first.

**BBVolColoc** rescans each source object's whole bounding box in the partner
image, so its cost tracks *box* volume rather than object volume. For compact
objects that is negligible, but for elongated, branched or fragmented objects —
which is what neuronal and glial segmentations look like — a single object's box
can approach the whole field. Measured on a 256×256×40, three-channel,
500-object-per-channel stack: 0.25 s for the core engine, 0.06 s more with
BBVolColoc on compact objects, but 60 s with labels scattered across the volume.

**BBColoc** and **BB-CPC** are instead quadratic in object *count*, because each
source object is compared against every partner object. That is cheap for
typical counts and expensive for dense puncta. Measured with two channels,
bidirectional, BBColoc alone: 0.2 s at 4,000 objects per channel, 0.5 s at
8,000, 1.7 s at 16,000, 7.0 s at 32,000 — roughly four times the work per
doubling. Five channels bidirectional multiplies that by ten, and BB-CPC adds
two more passes of the same shape.

Object counts in the hundreds or low thousands — the normal case — are
sub-second for all three.

## v0.1.0 boundary

Included: label and ROI input, 2–5 channels, directional pairwise analysis,
per-channel thresholds, percentage-filtered partner details, CPC-style
multi-colocalization, optional BBColoc/BB-CPC/BBVolColoc, folder batch,
auto-save, macros, Java API, and JUnit tests.

Not included: randomized null models, Jaccard/Dice, containment taxonomy, or
overlap maps.

## Citation and licence

> Malcolm, J. (2026). Volumetric Colocalization (v0.1.0) [Software].
> GitHub. https://github.com/Jay2owe/VolumetricColocalization

BSD 3-Clause. See [LICENSE](LICENSE).
## Parallel execution

Bounding-box object measurements run in deterministic parallel worker slots. The automatic limit is
eight workers or the available processor count, whichever is smaller. Set the JVM system property
`volcoloc.parallelism` to a positive integer to override it; use `1` for reproducibility checks or
memory-constrained machines. BBVolColoc remains serial for virtual stacks because concurrent reads
from an arbitrary virtual-stack provider are not guaranteed to be safe.
