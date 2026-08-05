# CPC ↔ Volumetric Colocalization cross-link — drafted, NOT yet applied

**Decision: yes, cross-link both ways.** CPC's users are precisely this plugin's audience, and the
two measures are complementary rather than competing, so pointing at each other is honest and is
the cheapest adoption channel available.

**Hold until v1.0.0 is actually published.** Applying this now would put a link to a nonexistent
plugin and a dead update-site URL into a published README that real users read. Trigger for
applying: the jar is on `https://sites.imagej.net/Volumetric-Colocalization/` and the GitHub
release exists.

Apply both halves in the same session so the two READMEs never disagree.

---

## Half 1 — add to CPC's README

File: `Experiments/CPC/README.md`

### 1a. One line in the intro paragraph

After the existing sentence ending "…so segmentation and colocalization are fully decoupled.",
append:

> For volume-overlap colocalization on the same inputs, see
> [Volumetric Colocalization](https://github.com/Jay2owe/VolumetricColocalization).

### 1b. New section, placed immediately after `## Algorithm` and before `## Auto-save Output Structure`

```markdown
---

## Related Plugins

CPC answers one question: **is the centre of this object inside a partner object?** The answer is
binary, threshold-free, and insensitive to how much the objects actually overlap.

That is deliberately narrow. Two companion plugins answer the adjacent questions on the same
inputs — any label image or ROI set, no shared configuration:

| Plugin | Question it answers |
| --- | --- |
| [Volumetric Colocalization](https://github.com/Jay2owe/VolumetricColocalization) | How much of this object's volume overlaps a partner? A graded percentage with per-channel thresholds. |
| [3D Objects Counter+](https://github.com/Jay2owe/3DObjectsCounterPlus) | What objects are there, and what shape are they? Detection and morphological filtering upstream of either method. |

CPC and Volumetric Colocalization **can disagree, and neither result is wrong** — they measure
different things. An object can have high volumetric overlap but fail CPC when the overlap is at
the edges rather than where the centroid sits; it can pass CPC but overlap only slightly when the
centroid lands just inside a large partner. Reporting both is usually more informative than
choosing one, and both are directional, so A→B and B→A should be reported separately.
```

---

## Half 2 — add to Volumetric Colocalization's README

Mirror image, so neither plugin looks like an afterthought of the other. Place after that
README's `## Algorithm` section.

```markdown
---

## Related Plugins

Volumetric Colocalization measures **how much** of an object overlaps a partner — a graded
percentage, thresholded to a colocalized flag.

| Plugin | Question it answers |
| --- | --- |
| [CPC — Centre-Particle Coincidence](https://github.com/Jay2owe/CPC) | Is the *centre* of this object inside a partner? A binary containment test with no threshold. |
| [3D Objects Counter+](https://github.com/Jay2owe/3DObjectsCounterPlus) | What objects are there, and what shape are they? |

Volumetric overlap gives you a spectrum; CPC gives you a category. Use volumetric when the degree
of overlap matters or objects are irregularly shaped, where a single centroid can sit outside the
object it represents. Use CPC when you need a clean count of contained objects, or when
threshold choice would otherwise drive the result. They can disagree; neither is wrong.
```

---

## Source for the explanatory wording

The contrast above is condensed from the existing FLASH document
`FLASH/docs/methods/CPC_vs_Volumetric_colocalisation_explained.md`, which already works through
both methods with a worked GFAP/NeuN example and an interpretation guide.

That document is a strong candidate to become a **shared wiki page** on imagej.net once both
plugins are published — it is better material than either README can carry, and a neutral page
both plugins link to avoids duplicating the explanation in two places that will drift apart.
Note it uses `-s-` spelling throughout and would need respelling to match the family standard.

## Also worth doing at the same time

- Add Volumetric Colocalization to CPC's update-site description? **No** — update-site descriptions
  in `sites.yml` should describe their own site only.
- Cite CPC in Volumetric Colocalization's paper/preprint methods, and vice versa where relevant.
- Check that the wiki pages for both plugins link to each other under "See also".
