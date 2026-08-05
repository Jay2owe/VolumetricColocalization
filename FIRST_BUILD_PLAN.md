# 01 — Volumetric Colocalization

**Name settled 2026-07-30.** Plain descriptive name, no acronym — the 3D Objects Counter+ approach.
Maximum searchability, zero explanation cost, impossible to misremember.

## Naming and identity

| Item | Value |
|---|---|
| Display name | Volumetric Colocalization |
| Menu entry | `Plugins > Volumetric Colocalization` |
| Macro command | `run("Volumetric Colocalization", "...")` |
| GitHub repo | `github.com/Jay2owe/VolumetricColocalization` |
| Update site | `https://sites.imagej.net/Volumetric-Colocalization/` |
| Maven groupId | `io.github.jay2owe` (matching FLASH) |
| Maven artifactId | `Volumetric_Colocalization` |
| Java package | `volcoloc` (short, matching CPC's `cpc`) |
| Entry class | `Volumetric_Colocalization.java` (trailing underscore, as `CPC_.java`) |
| Built jar | `Volumetric_Colocalization-<version>.jar` |
| Licence | BSD 3-Clause, matching CPC |

**Spelling: `-z-` throughout.** The family standardises on *colocalization*, matching CPC's
existing README, Fiji, ImageJ and Coloc 2. This applies to menu entries, macro options, column
headers, output folder names and documentation. Package and class names follow the same spelling.

Citation line:

> Malcolm, J. (2026). Volumetric Colocalization (v1.0.0) [Software].
> GitHub. https://github.com/Jay2owe/VolumetricColocalization

Methods-section form:

> Volumetric overlap was quantified using the Volumetric Colocalization plugin (v1.0.0).

Sits in the family as: **CPC** (centroid coincidence) · **3D Objects Counter+** (detection and
shape) · **Volumetric Colocalization** (volume overlap).

## Goal

For every segmented object, report what percentage of its volume is occupied by objects from each
partner channel — with per-channel thresholds, all partners retained (not just the best), N-way
multi-colocalization patterns, and full batch processing.

CPC answers "is the centre of A inside B?". This answers "how much of A is inside B?". Same inputs,
same users, complementary result. It is the single most obvious follow-up question a CPC user has.

## Case strength: 1 of 7 — very strong

Volumetric overlap exists in the ecosystem (3D Objects Counter, JACoP, DiAna, mcib3d) but every
implementation has at least one of these gaps:

- **No batch processing.** One image at a time, manual re-entry of settings.
- **No thresholds.** You get a raw overlap percentage with no principled cutoff, so "colocalized"
  counts must be derived by hand in Excel, differently in every lab.
- **No multi-colocalization.** Strictly pairwise. With 3–5 channels you cannot ask "which objects
  overlap GFAP *and* Iba1 but not NeuN".
- **Best-partner only.** Overlap is collapsed to the single strongest partner, discarding the rest.
  FLASH's own engine does this too.

Closing all four in one plugin that accepts any label image is a genuinely useful, easily explained
contribution — and it inherits CPC's audience directly.

## Inputs needed

| Input | Required | Notes |
|---|---|---|
| 2–5 label images | yes | any source; open images or file paths |
| ROI `.zip` sets + reference image | alternative | via the CPC `LabelUtils` path |
| Voxel calibration | recommended | read from the image; needed to report volumes in µm³ |
| Raw intensity images | no | not used by this plugin |

No raw images means the whole plugin stays on the label-image contract — the simplest possible
input story, identical to CPC's.

## Outputs

- **Per-object table per pair**: source label, volume, overlap voxels with partner, overlap % of
  source, best partner label, partner count, thresholded colocalized flag.
- **All-partner detail** (new): one row per (source object, partner object) pair where that partner
  occupies at least 50% of the source object by default (configurable), so meaningful many-to-many
  relationships survive into the CSV.
- **Summary table**: per pair, object counts, mean/median overlap %, count and % above threshold,
  in both directions.
- **Multi-colocalization summary** (new): combination patterns across 3–5 channels with counts, in
  the style of CPC's multi-target output.
- Auto-save tree mirroring CPC's, under `Volumetric Colocalization/`: `Objects/`, `Multi/`,
  `Maps/`, `Folder/`, each with a `README.txt`.

## Functionality to match (the CPC standard)

All ten points of the CPC standard in `00_PORTFOLIO_OVERVIEW.md`. Specific to this plugin:

- Per-channel threshold field, defaulting to 30% (FLASH's default), settable per source channel.
- Bidirectional by default — A-in-B and B-in-A give different answers because the denominator
  differs, and both must be reported.
- Batch preview must show the parsed groups before any computation runs.
- Macro options for every threshold, so a whole study is reproducible from one macro line.

## Reference style from CPC

Copy the repository shape wholesale from `Experiments/CPC`. Concretely:

| Take from | Use as |
|---|---|
| `cpc/LabelUtils.java` | ROI-zip loading and ROI→label fill — verbatim |
| `cpc/ui/ToggleSwitch.java` | boolean control — verbatim |
| `cpc/CPCLabelImages.java` | public ROI→label API — rename only |
| `cpc/CPCParameters.java` + `CPCResult.java` + `CPC.java` | builder / result / facade triad |
| `cpc/CPCBatch*.java` | batch dialog, params, runner, result |
| `cpc/CPCMacroOptions*.java` | macro model and parser |
| `cpc/ui/CPCDialog.java` | Input / Analysis / Output three-section dialog |
| `cpc/CPC_.java` | `PlugIn` entry and macro-vs-interactive routing |
| `pom.xml`, `mvnw`, `LICENSE`, `CITATION.cff`, `PUBLISHING_AUDIT.md` | repo furniture |

Only `CPCAnalysis.java` (951 lines) has no counterpart — the engine is written fresh.

Class naming follows CPC's shape: `VolColocParameters`, `VolColocResult`, `VolColoc` (facade),
`VolColocAnalysis` (engine), `VolColocBatch*`, `VolColocMacroOptions*`, `ui/VolColocDialog`.

## Source material in FLASH

These are **internal FLASH classes, not standalone plugins**. Copy, do not move (FLASH must keep
working during extraction).

| FLASH source | What it gives |
|---|---|
| `analyses/ThreeDObjectAnalysis.java` L5804–5876 `computeColocFromLabelImages()` | the core engine: single-pass voxel scan, per-label sizes, `(A,B)` overlap counts packed into a `long` key, max overlap % per object |
| `analyses/ThreeDObjectAnalysis.java` L5530 `appendColocColumns()` | column layout and naming (`Colocalisation with <partner>`, `VolColoc<thr>_<partner>`) — **respell to `-z-` when porting** |
| `analyses/ThreeDObjectAnalysis.java` L6140 `getColocThreshold()` | per-channel threshold plumbing |
| `objects/CpcUtils.java` (259 lines) | `ObjectInfo` — label, centroid, voxel count, inclusive bounding box, intersection volume |
| `objects/BoundingBoxColoc.java` (88 lines) | `fillPercent` and box geometry for the optional bounding-box variants |
| `objects/LabelIndex.java` (48 lines) | label lookup helper |

Note: FLASH's version uses `gnu.trove` primitive maps to avoid autoboxing on dense stacks. Trove is
present in Fiji, but adding a dependency breaks the `ij`-only rule — write a small long-keyed
open-addressing map instead (~80 lines), or accept `HashMap<Long,Integer>` for v0.1.0 and optimise
once real stack sizes are measured.

## New beyond FLASH

1. **Keep all partners**, not just the maximum. FLASH collapses to best-partner; this is the
   headline gap in every existing tool.
2. **Multi-colocalization patterns** across 3–5 channels.
3. **Batch mode** — FLASH has it only inside the pipeline, bound to `channel_config.json`.
4. **Jaccard / IoU and Dice** per object pair — symmetric indices that sidestep the
   which-direction-am-I-measuring problem entirely. Cheap to add once overlap counts exist.
5. **Randomized null model** — shuffle object positions within the mask preserving count and size,
   report expected overlap %, enrichment ratio, permutation *p*.
6. **Containment taxonomy** — classify each pair as A⊂B, B⊂A, partial, or disjoint.

Items 1–3 are v0.1.0. Items 4–6 are v0.2.0 and are what make it publishable rather than merely
useful.

## Dependencies

`ij` only, if the Trove replacement is written. Target: same zero-friction install as CPC.

## Pros

- Closest possible adjacency to CPC's existing audience — minimal explanation cost.
- Engine is the best-understood code in FLASH and already has test coverage nearby.
- Pairs naturally with CPC in a methods section: "we report both centroid coincidence and
  volumetric overlap", which drives citations to both plugins.
- No raw-image input, so the contract stays as simple as CPC's.
- The four gaps it closes are concrete and immediately demonstrable in a figure.
- Plain name means no one has to be told what it does.

## Cons

- Most crowded space of the family — reviewers will ask "why not JACoP/DiAna?". The answer has to
  be batch + thresholds + multi-coloc + null models, stated plainly and shown.
- Generic name is harder to cite crisply than an acronym and builds less of a recognisable family
  identity — accepted trade-off for searchability.
- Voxel scan is O(voxels × pairs); large 3D stacks need the primitive-map optimisation early.
- Thresholding is scientifically contentious — the 30% default must be presented as a reporting
  convention, never a truth claim, and the continuous percentage must always be saved alongside.
- Overlaps in purpose with plugin 06; the boundary must be that 06 *includes* this engine rather
  than reimplementing it.

## First build (v0.1.0) scope

In: label + ROI input, 2–5 channels, all pairwise, bidirectional, per-channel thresholds,
per-object and summary tables, percentage-filtered all-partner detail rows, CPC-style multi-coloc
patterns, optional BBColoc/BB-CPC/BBVolColoc behind collapsed controls, batch with preview,
auto-save tree, macro options, Java API, JUnit tests.

Out: null models, Jaccard/Dice, containment taxonomy, overlap maps.

## Name collision check — CLEARED 2026-07-30

| Namespace | Checked against | Result |
|---|---|---|
| ImageJ update-site list | local clone of `imagej/list-of-update-sites` at commit `58b1ff6` (2026-07-20), 329 sites | **free** — no site named "Volumetric Colocalization"; only one coloc-named site exists (`Colocalization by Cross Correlation`, id `Amccall`) |
| Update-site URL | `https://sites.imagej.net/Volumetric-Colocalization/` | **free** — HTTP 404 |
| ImageJ wiki page | `https://imagej.net/plugins/volumetric-colocalization` | **free** — HTTP 404 |
| GitHub repo name | `gh search repos` for `VolumetricColocalization` and `volumetric colocalization` | **free** — no results; `Jay2owe/VolumetricColocalization` available |
| Published plugins | web search for `"Volumetric Colocalization" ImageJ Fiji plugin` | **free** — no plugin carries this name |

Nearest names, none colliding: *Volumetric Tissue Exploration and Analysis* (VTEA, update site
`ICBM-IUPUI`) is 3D tissue cytometry, unrelated. *Colocalization by Cross Correlation*
(`Amccall`) is an intensity CCF method, unrelated.

Publishing path is already in place: `Jay2owe` holds forks of both `imagej/list-of-update-sites`
and `imagej/imagej.github.io`, so the update-site PR and wiki-page PR follow the same route used
for CPC and 3D Objects Counter+.

### Nearest competitors — verified, not assumed

Checked because these are what reviewers will name:

- **Colocalization Object Counter** (Anders-Lunde) — despite the name, it is **centroid/point-based
  and semi-automatic**, not volumetric: "objects are defined by a single point" with XYZ
  designation, up to 8 categories giving 256 combination patterns. It competes with **CPC**, not
  with this plugin. No volumetric overlap %, no per-channel thresholds, no folder batch.
- **Coloc 2**, **EzColocalization**, **Colocalization Colormap** — pixel-intensity methods
  (Pearson/Manders/Costes/Li), a different measurement class entirely.
- **JACoP**, **DiAna**, **3D Objects Counter**, **mcib3d** — the real volumetric comparators. The
  differentiator stands: batch, per-channel thresholds, all-partners-retained, multi-coloc
  patterns.

Notably, the multi-channel combination-pattern idea is already established in the field (Anders-Lunde
does 256 categories from 8), so it is a *parity* feature, not a differentiator. The genuine
differentiators are **batch + thresholds + all-partners-retained**, and later the null model.

## Settled

- **Bounding-box families: include all three**, default off behind a collapsed optional section.
- **Partner-detail filter: percentage of source, default 50%.** It affects detail rows only, not
  total overlap, partner counts, threshold flags, summaries or multi-colocalization.
- **Multi-colocalization: copy CPC.** Every image is a source; per-object tables use target
  `Coloc`/`Partner` columns and `Targets Hit`; summaries use positive-only joined patterns,
  `None`, and `— Any —`.
- **CPC cross-link: yes, both ways.** Text drafted in `CPC_CROSSLINK_DRAFT.md`, to be applied when
  v1.0.0 is actually published — not before, since it would otherwise link to a nonexistent plugin
  from a README real users read.
- **Name collision check: cleared** — see the section above.
