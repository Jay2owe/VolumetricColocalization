# Migration plan: Volumetric Colocalization → oc3d-core + volcoloc-core

**Goal.** Split this plugin into an embeddable engine (`volcoloc-core`) and a
thin plugin, adopting `oc3d-core` for the chassis — **before first publication**,
so it never ships its duplicated copy of CPC's chassis.

**Pattern:** `../../PLUGIN_CORE_PATTERN.md`
**Specs:** `../../oc3d-core/README.md`, `../../volcoloc-core/README.md`
**Test contract:** `../../oc3d-core/EQUIVALENCE_HARNESS.md`

**Ship gate: nothing is published until the harness passes.**

**Sequencing:** after the CPC migration, which promotes the chassis into
`oc3d-core` and proves the boundary.

---

## Position — the cheapest extraction in the family

`0.1.0-SNAPSHOT`, **not yet published to an update site**. No existing users
whose numbers must not move; no update-site transition to manage.

And the duplication is entirely avoidable rather than sunk. This plugin was built
by copying CPC's chassis — its own README says the multi-channel output "copies
CPC's source-anchored output". Every file below exists twice in the tree today:

| Concern | CPC | Here |
|---|---|---|
| Label/ROI ingest | `cpc/LabelUtils.java` | `volcoloc/LabelUtils.java` |
| Label facade | `CPCLabelImages.java` | `VolColocLabelImages.java` |
| Macro options | `CPCMacroOptions.java` | `VolColocMacroOptions.java` |
| Macro parser | `CPCMacroOptionsParser.java` | `VolColocMacroOptionsParser.java` |
| Batch runner | `CPCBatchRunner.java` | `VolColocBatchRunner.java` |
| Batch params/result | `CPCBatchParameters/Result` | `VolColocBatchParameters/Result` |
| Params/result | `CPCParameters/CPCResult` | `VolColocParameters/VolColocResult` |
| Toggle widget | `cpc/ui/ToggleSwitch.java` | `volcoloc/ui/ToggleSwitch.java` |
| Dialog | `cpc/ui/CPCDialog.java` | `volcoloc/ui/VolColocDialog.java` |

Unlike the 3D Objects Counter - StarDist case, where the copy was written and
released, here it can simply never ship.

---

## Stage 0 — Harness and goldens

The existing suite is a good base — extend, do not replace:
`VolColocIOTest`, `VolColocLabelImagesTest`, `VolColocRoiFileTest`,
`VolColocMacroOptionsParserTest`, `BoundingBoxParallelismTest`,
`PrimitiveMapsTest`, `VolColocBatchRunnerTest`, `VolColocNameLengthTest`.

- Harness at `src/test/java/volcoloc/equivalence/`.
- Corpus: 2–5 channel label sets; 8-, 16-, 32-bit label images; 8-bit with a
  colour LUT; anisotropic calibration.
- ROI corpus covering every documented rule: ROIs with and without a slice
  position; ROIs straddling the stack edge (kept and clipped); overlapping ROIs
  where the later wins; ROIs entirely outside the reference stack (rejected);
  line/polyline/angle/point selections (rejected); RGB (rejected); hyperstack as
  label image (rejected) but hyperstack as ROI reference (accepted).
- Threshold sweep at 0%, 30% (default), 100%.
- Goldens to `golden/<git-sha>/`, immutable.

**Tier 1** (bit-identical): occupied voxel counts; occupied percentages;
strongest-partner labels; overlapping-partner counts; thresholded flags;
`Targets Hit`; combination-pattern counts **including `None` and `— Any —`**;
summary counts, means, medians and percentages.

Every documented rejection must still reject, **with the same message** — the
messages are the plugin's contract with the user.

**Exit gate:** harness green twice with byte-identical output.

---

## Stage 1 — Adopt `oc3d-core` chassis

- Delete `volcoloc/LabelUtils.java`, `VolColocLabelImages.java`, the macro
  parser, batch classes and `ToggleSwitch`; consume the `oc3d-core` versions.
- **Diff before deleting.** This repo's `LabelUtils` is stricter than CPC's — it
  rejects line/polyline/angle/point selections, rejects ROIs beyond the reference
  stack rather than silently projecting, and rejects hyperstacks as label images
  (extra channels and frames would otherwise count as further Z layers and
  multiply every object's volume). **These rules are better and must survive into
  core, not be lost to it.** If core lacks one, that is a gap in CPC; fix core,
  do not weaken this plugin.

**Exit gate:** full harness green, especially the ROI rejection corpus.

---

## Stage 2 — Extract `volcoloc-core`

- Extract the overlap computation, directional pair runner and multi-target
  summary from `VolColocAnalysis`.
- **Preserve the reporting convention:** 30% is a default threshold, not a truth
  claim, and the continuous percentage is always retained. This is a correctness
  property, not a setting.
- **Directional denominators.** A-in-B and B-in-A use different denominators and
  are reported separately. Already correct here; it must stay correct through
  extraction, and the directional cases are explicit Tier 1 corpus entries.
- **The core returns a result model, not an ImageJ `ResultsTable`.** Table
  construction stays in the plugin, so 3D Objects Counter+ can append overlap
  columns to its own table.
- No Swing, no `GenericDialog`, no `IJ.error` in the core.
- **Decide `BoundingBoxAnalysis` and `PrimitiveMaps` by reading them.** If they
  generalize to any label-pair problem they belong in `oc3d-core`; if specific to
  volume overlap they stay in `volcoloc-core`. Record the reason. Do not move
  speculatively.
- Reconcile `VolColocIO` against `oc3d-core/io` rather than assuming either wins.

**Exit gate:** full harness green. Cross-check — CPC and this plugin must now
produce identical **channel, pair and object counts** for the same inputs, since
both use the shared chassis and ingest. Only the measurement columns differ. Any
divergence is an extraction bug.

---

## Stage 3 — Shade and package

- Relocate `sc.fiji.oc3d.core` → `volcoloc.internal.core` and
  `sc.fiji.volcoloc.core` → `volcoloc.internal.volcoloc`.
- Do not relocate `volcoloc.VolColoc` or the public API.
- Grep for `Class.forName` and reflection first.

**Exit gate:** jar installs on a bare Fiji with no update sites but its own.

---

## Stage 4 — First release

This is a 0.1.0 launch, not a migration release. Per the distribution rule:

- imagej.net wiki page (prerequisite for update-site listing).
- Update site, then a `sites.yml` PR to `imagej/list-of-update-sites` so it is
  **listed**, not unlisted.
- `CITATION.cff` and a Zenodo DOI; cross-cite CPC.
- Licence: **BSD-3-Clause** — correct as-is, no GPL dependencies.
- README install section: one jar, one update site, no prerequisites.
- Naming and spelling follow the portfolio standard: **`-z-` colocalization**,
  family-wide.

**Land the core adoption green on the harness before starting the release work.**
Do not debug an extraction and a first launch at the same time.

---

## What this unlocks

3D Objects Counter+ shades in `volcoloc-core` and gains optional overlap columns
— occupied percentage, strongest partner, partner count — appended to its
existing per-object table, off by default.

The user updates one plugin. They install neither Volumetric Colocalization nor
anything called a core.

`06 - Colocalization Suite` is the other consumer, and with `cpc-core` alongside
it can offer both tests from one dialog — a thin adapter at the consumer side,
not a shared abstraction forced on both engines.

---

## Risks

| Risk | Mitigation |
|---|---|
| CPC's chassis is weaker than this one | Stage 1 diffs both; the stricter rule wins and goes into core |
| `BoundingBoxAnalysis` moved without understanding | Read first, decide with a written reason |
| Directional denominators confused | Explicit Tier 1 corpus entries per direction |
| 30% threshold hardens into a truth claim | Stated as a reporting convention in the core spec; continuous value always retained |
| `None` / `— Any —` rows change | Tier 1; documented as script-readable |
| Rejection messages drift | Harness asserts message text, not just that it rejected |
| Extraction and first launch debugged together | Stage 4 explicitly gated behind a green harness |
