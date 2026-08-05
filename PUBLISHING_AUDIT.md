# Publishing Audit

Status: development build; not ready to publish.

The release itself — wiki page, update site, `sites.yml` PR, and the order they
have to happen in — is drafted in `docs/RELEASE_STAGE4.md`. Nothing there has
been submitted. The one item on the list below that no amount of automated
testing can close is **interactive Fiji acceptance testing**; it needs a person
at a Fiji install.

## Passed in the current tree

- Maven coordinates and Java package match the build plan.
- BSD 3-Clause licence metadata and source headers are present.
- `plugins.config` points to `volcoloc.Volumetric_Colocalization`.
- The only compile dependency is ImageJ `ij`.
- Label, ROI, macro, batch, save-tree, and core-engine tests pass.
- The Maven wrapper runs `clean verify`.
- The JAR contains the menu configuration, entry class, Swing dialog, and
  public Java facade.
- Bounding-box families are optional and hidden in collapsed controls.
- Partner-detail filtering is source-percentage based and defaults to 50%.
- Multi-target output follows CPC's table and pattern semantics.
- README documents thresholds, directional semantics, macros, batch grouping,
  outputs, and the v0.1.0 boundary.

## Required before a public release

- Run representative large 3D stacks and record time and peak memory.
- Perform interactive Fiji acceptance testing on label, ROI, and batch modes.
- Replace `0.1.0-SNAPSHOT` with the release version and date the changelog and
  citation metadata.
- Create the public repository and ImageJ update site only after release
  checks pass.
- Add update-site publishing automation and credentials as repository secrets.
- Apply the drafted CPC cross-links only after both the GitHub release and
  update-site JAR exist.
