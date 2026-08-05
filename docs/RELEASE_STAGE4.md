# Stage 4 — first release: drafted, nothing published

Written 2026-08-05, after migration stages 1–3 landed green. **Every item here is
a draft.** No repository was created, no update site was touched, no pull request
was opened. The three artefacts this produces are:

| Artefact | Draft lives at |
|---|---|
| imagej.net wiki page | `../../../ImageJ-Wiki-Submission/imagej.github.io/_pages/plugins/volumetric-colocalization.md` |
| `sites.yml` entry + PR | `../../../ImageJ-Wiki-Submission/list-of-update-sites/sites-yml-snippets.md` |
| Update-site setup and upload | this file, § C |

The wiki page and `sites.yml` snippet go in the shared submission bundle because
that is where the CPC, FLASH, Macro Builder and 3D Objects Counter+ drafts already
live, and the bundle mirrors the exact target paths in the upstream repositories.

---

## Verified starting state, 2026-08-05

Checked rather than assumed, with a known-good control alongside each check:

| Thing | State |
|---|---|
| `github.com/Jay2owe/VolumetricColocalization` | **does not exist** (`gh repo view` → could not resolve) |
| `sites.imagej.net/Volumetric-Colocalization/db.xml.gz` | **404** (control: CPC's site → 200) |
| `imagej.net/plugins/volumetric-colocalization` | **404** (controls: CPC, 3DOC+, FLASH pages → 200) |
| `sites.yml` entry | absent; no open or merged PR for it |

So this is a genuine first launch on all three fronts, with nothing to reconcile
against an existing published state.

Prior art to follow, all merged: wiki PRs #419 (CPC) and #428 (FLASH) on
`imagej/imagej.github.io`; list PRs #203, #207, #208, #213 on
`imagej/list-of-update-sites`. A fork of the list repository already exists at
`Jay2owe/list-of-update-sites`, checked out locally at
`../../../list-of-update-sites` with `upstream` configured.

---

## Decisions taken while drafting

**Update-site ID: `Volumetric-Colocalization`.** Matches the URL already written
into `CPC_CROSSLINK_DRAFT.md`, so nothing has to change if that cross-link is
applied later. Hyphenated like `Macro-Builder` rather than run together like
`3DObjectsCounterPlus`.

**Wiki slug: `volumetric-colocalization`**, giving
`https://imagej.net/plugins/volumetric-colocalization`.

**Release version: `0.1.0`.** The migration plan calls Stage 4 "a 0.1.0 launch",
`CITATION.cff` already says `0.1.0`, and 3D Objects Counter+ launched publicly at
`0.1.0`. `CPC_CROSSLINK_DRAFT.md` says "hold until v1.0.0 is actually published" —
that is loose wording from before the plan settled, and its own stated trigger is
the update-site jar plus a GitHub release, not a version number. **Fix that line
in the cross-link draft rather than inflating the version to match it.**

**Licence: BSD-3-Clause**, unchanged, and correct — the only compile dependency
is `net.imagej:ij`, and both shaded cores are BSD-3-Clause too. Wiki front matter
points `license-url` at the repository `LICENSE` file, the form 3D Objects
Counter+ uses, rather than `/licensing/public-domain` which CPC's page inherited
from a template and which is wrong for a BSD project.

**Spelling: `-z-` colocalization throughout**, per the portfolio standard in the
migration plan. British spellings are kept for `Centre-Particle Coincidence` and
`colour`, matching the family's existing pages.

---

## Order of operations, and why it is this order

1. **Release build and acceptance testing** — § B
2. **GitHub repository and release** — § A depends on nothing, but the wiki page
   links it, so it must exist before the wiki PR
3. **Update site created and serving the jar** — § C
4. **Wiki page PR** — § D
5. **`sites.yml` PR** — § E

The last three are ordered by what each one would break if it went first:

- The `sites.yml` description links `imagej.net/plugins/volumetric-colocalization`,
  which 404s until the wiki PR merges. This is the same form the pending CPC
  description update moves to.
- The list repository's README asks contributors to have an ImageJ page, so the
  wiki page is a soft prerequisite for the listing anyway.
- Listing an update site that does not yet serve a jar puts a dead entry into the
  update-site manager for every Fiji user. The repository's CI would not catch
  this — its `cram` test only checks the base updater redirects and the four core
  sites, not each listed site's existence — so nothing but sequencing prevents it.

---

## § A — GitHub repository

Needs the user's account. The repository does not exist yet.

- Name: `VolumetricColocalization`, already written into `README.md`,
  `CITATION.cff` and `pom.xml`.
- Public, BSD-3-Clause, description matching the wiki page's `description` field.
- Push the existing local history. It is six commits and tells the migration story
  honestly: pre-migration state green at 104 tests, goldens and the A/B gate, core
  adoption, shading, chassis adoption, documentation.
- Tag `v0.1.0` at the release commit and create a GitHub Release with the jar
  attached. Zenodo picks releases up from tags, not from pushes.

---

## § B — release build and the checks that must pass first

### Blocking, and needs a person

`PUBLISHING_AUDIT.md` lists interactive Fiji acceptance testing on label, ROI and
batch modes as required before a public release. **That has not been done, and no
test suite substitutes for it.** The 472 automated tests across the three modules
prove the arithmetic and the ingest rules; they do not prove that the dialog lays
out correctly, that the file choosers behave, or that the auto-save tree lands
where a user expects on a real machine.

The other audit items are now satisfied: representative timings are measured and
recorded in `README.md` § Algorithm, and the artifact checks below are green.

### Version bump

Drop `-SNAPSHOT` from `pom.xml`, date `CHANGELOG.md`, confirm `CITATION.cff`.

**Also settle the two core modules.** The plugin currently shades
`volcoloc-core:0.1.0-SNAPSHOT` and `oc3d-core:0.1.0-SNAPSHOT`. A release jar built
against SNAPSHOT dependencies cannot be reproduced later, because those
coordinates are mutable by definition. The cores are never published as jars — the
pattern forbids it — but they can and should carry fixed versions and git tags, so
`v0.1.0` of this plugin names exactly which engine it shaded. Tag both cores
`v0.1.0`, set their poms to `0.1.0`, and depend on that.

### Artifact checks — run and green on the current build

```
target/Volumetric_Colocalization-0.1.0-SNAPSHOT.jar
```

| Check | Result |
|---|---|
| total entries | 74 |
| `plugins.config` present | yes |
| entry class `volcoloc/Volumetric_Colocalization.class` | present |
| unrelocated `sc/fiji/` classes | **0** |
| bundled `ij/` classes | **0** |
| relocated engine, `volcoloc/internal/volcoloc/` | 33 classes |
| relocated chassis, `volcoloc/internal/core/` | 6 classes — `LabelUtils`, `RoiLabelImages`, `LabelImages`, `MacroOptions`, `ToggleSwitch` |

Six chassis classes rather than the whole of `oc3d-core` is `minimizeJar` doing its
job: only what is actually reached is carried.

### The artifact-glob trap

`mvn package` leaves four jars in `target/`:

```
Volumetric_Colocalization-0.1.0-SNAPSHOT.jar            <- the one to upload
Volumetric_Colocalization-0.1.0-SNAPSHOT-sources.jar    <- matches a naive glob
Volumetric_Colocalization-0.1.0-SNAPSHOT-tests.jar      <- matches a naive glob
original-Volumetric_Colocalization-0.1.0-SNAPSHOT.jar   <- pre-shade, does not match
```

The obvious glob `target/Volumetric_Colocalization-*.jar` matches **three** of
them. The uploader must see exactly one artifact, so the glob has to exclude
`-sources` and `-tests` explicitly, or run `mvn package` with those attachments
off. Check the dry-run log names one file before going near a live upload.

---

## § C — the update site

### Setup

The account already exists — four sites are live under it (`Center-Particle-Coincidence`,
`3DObjectsCounterPlus`, `Macro-Builder`, `FLASH`), so this is the same route taken
four times before, not a new registration.

- Site name: `Volumetric-Colocalization`
- URL: `https://sites.imagej.net/Volumetric-Colocalization/`
- Destination inside Fiji: `plugins/`
- Credentials as repository secrets `IMAGEJ_UPLOAD_USER` and
  `IMAGEJ_UPLOAD_PASSWORD`. Never committed, never printed.

### Generate the upload workflow

```bash
python ~/.claude/skills/imagej-update-site-release/scripts/generate_update_site_workflow.py \
  --site Volumetric-Colocalization \
  --artifact-glob "target/Volumetric_Colocalization-*.jar" \
  --install-dir plugins \
  --replace-glob "plugins/Volumetric_Colocalization-*.jar" \
  --project-name volumetric-colocalization \
  --out .github/workflows/fiji-update-site-upload.yml
```

Then **narrow the generated `--artifact-glob` handling** per the trap above before
running anything.

**The generated workflow also needs the core checkout steps.** It runs
`mvn clean test` and `mvn package` on a fresh runner, which cannot resolve
`oc3d-core` or `volcoloc-core` — they are deliberately on no Maven repository.
Copy the three checkout steps and the two `mvn install` steps from
`.github/workflows/build.yml`, and pin `OC3D_CORE_REF` and `VOLCOLOC_CORE_REF`
to release tags rather than `master`, so the uploaded jar names exactly which
engine it shaded.

The generated workflow is manual (`workflow_dispatch`), defaults to
`dry_run=true`, and requires the typed confirmation `UPLOAD Volumetric-Colocalization`
for a live upload.

### Dry run, then ask

```bash
gh workflow run fiji-update-site-upload.yml --ref main -f dry_run=true -f clear_stale_lock=false
gh run watch RUN_ID --exit-status
```

The log must name exactly one file — `Would upload 'plugins/Volumetric_Colocalization-0.1.0.jar'` —
and end with `Dry run complete. No files were uploaded.` A dry run that reports
`Nothing to upload` means the staging is wrong, not that the jar is current.

**A live upload happens only on the user's explicit say-so, in the moment.** It is
a production release: it is what every Fiji user with this site enabled will
receive.

```bash
gh workflow run fiji-update-site-upload.yml --ref main \
  -f dry_run=false -f clear_stale_lock=false \
  -f confirm_live_upload="UPLOAD Volumetric-Colocalization"
```

### Verify afterwards

```bash
python ~/.claude/skills/imagej-update-site-release/scripts/inspect_update_site.py \
  --site Volumetric-Colocalization \
  --expect-file "plugins/Volumetric_Colocalization-0.1.0.jar" \
  --require-no-lock
```

Then download the hosted jar and re-run the § B artifact checks against it. Exit
code 0 is not evidence the site changed; the hosted `db.xml.gz` is. If the hosted
jar's hash differs from the tested one, say so — same source commit and same bytes
are different claims.

---

## § D — wiki page PR

Page draft:
`../../../ImageJ-Wiki-Submission/imagej.github.io/_pages/plugins/volumetric-colocalization.md`

**Before submitting, set `release-date`.** The front matter currently reads
`release-date: TBD-SET-AT-RELEASE`, deliberately loud so it cannot be submitted by
accident.

The page was written from source, not from the README alone: dialog group and
control labels come from `Volumetric_Colocalization.java`, result window titles
from its `display` method, and column names from `VolColocResult.java`. It carries
no private paths, no `## Links` block duplicating the front matter, and no
exclusivity claims — checked with the skill's text scan.

```bash
cd <clone of imagej/imagej.github.io>
git checkout -b add-volumetric-colocalization-plugin-page
cp "<bundle>/imagej.github.io/_pages/plugins/volumetric-colocalization.md" _pages/plugins/
git add _pages/plugins/volumetric-colocalization.md
git commit -m "Add Volumetric Colocalization plugin page"
git push -u origin add-volumetric-colocalization-plugin-page
```

PR title: `Add Volumetric Colocalization plugin page`

PR body:

```markdown
## Summary
- add the Volumetric Colocalization plugin page
- document installation from the Volumetric Colocalization update site
- cover the directional volume-overlap measure, inputs, dialog workflow, optional
  bounding-box analyses, outputs, batch processing, and macro use for v0.1.0

## Verification
- confirmed `plugins/Volumetric_Colocalization-0.1.0.jar` is current on
  `https://sites.imagej.net/Volumetric-Colocalization/`
- verified dialog labels, result window titles, and table column names against
  the v0.1.0 source rather than the README
- validated front matter and checked the page for private paths, duplicated
  metadata links, and unsupported claims
```

---

## § E — `sites.yml` PR

Entry and insertion point: `../../../ImageJ-Wiki-Submission/list-of-update-sites/sites-yml-snippets.md`,
final section. It goes between `Void Whizzard` and
`Volumetric Tissue Exploration and Analysis`.

### Validation — already run, on a scratch copy

The live checkout at `../../../list-of-update-sites` was **not modified**. The entry
was inserted into a copy in the scratch directory and put through the
repository's own checks from `.github/build.sh`:

| Check | Result |
|---|---|
| `yamllint -d relaxed -d "{rules: {key-duplicates: {}}}" sites.yml` | pass |
| duplicate `id:` check | no duplicates |
| alphabetical order, `LC_ALL=C sort --ignore-case` | ordered correctly |
| `python generate-legacy-pages.py` | pass; the entry renders into `sites.html`, `sites.xml`, `sites_insecure.xml` |

`cram tests` was not re-run: it checks the base updater redirects and the four
core sites, and is unaffected by adding an entry.

These will need re-running on the real branch at submission time, since `main`
will have moved.

```bash
cd ../../../list-of-update-sites
git fetch upstream && git checkout -b add-volumetric-colocalization-update-site upstream/main
# insert the entry, then:
git add sites.yml
git commit -m "Add Volumetric Colocalization update site"
git push -u origin add-volumetric-colocalization-update-site
```

PR title: `Add Volumetric Colocalization update site`

PR body:

```markdown
## Summary

- add the Volumetric Colocalization update site at https://sites.imagej.net/Volumetric-Colocalization/
- link the ImageJ plugin page and maintainer
- describe the directional object-volume overlap measure, supported inputs, and outputs

## Validation

- python -m yamllint -d relaxed -d {rules: {key-duplicates: {}}} sites.yml
- duplicate ID check from .github/build.sh
- alphabetical order check from .github/build.sh
- python generate-legacy-pages.py
- python -m cram --shell C:\Program Files\Git\bin\bash.exe tests
- git diff --check
```

Commit only `sites.yml`. Delete the generated `actual`, `expected`, `sites.html`,
`sites.xml` and `sites_insecure.xml` before committing.

---

## § F — after the release

- **Zenodo DOI.** Enable the repository in Zenodo, then publish the `v0.1.0`
  GitHub Release; Zenodo mints the DOI from the tag. Add the DOI to
  `CITATION.cff` and to the wiki page's citation block, and cross-cite CPC.
- **Apply the CPC cross-links.** `CPC_CROSSLINK_DRAFT.md` holds both halves,
  already written. Its trigger is now met once the update site serves the jar and
  the release exists. Apply both halves in one session so the two READMEs never
  disagree. Correct its "hold until v1.0.0" line while there.
- **CPC's `sites.yml` description link update**, noted in the bundle as
  outstanding, is now actionable — CPC's wiki page is live. Independent of this
  release, but it is the same file and the same reviewer.
- **Update `PUBLISHING_AUDIT.md`** from "not ready to publish" to the published
  state, keeping the record of what was checked.

---

## What is blocking, and on whom

| Blocker | Needs |
|---|---|
| Interactive Fiji acceptance testing on label, ROI and batch modes | a person at a Fiji install — the one genuine gate |
| GitHub repository creation and push | the user's account |
| Update-site creation and upload credentials | the user's ImageJ account; secrets set on the repository |
| Live update-site upload | explicit approval in the moment; it is a production release |
| Zenodo DOI | the user's Zenodo account, after the GitHub Release |
| Release version `0.1.0` confirmed, and the cores tagged so the jar is reproducible | a decision, recommended above |

Everything else in this file is written and validated as far as it can be without
publishing anything.
