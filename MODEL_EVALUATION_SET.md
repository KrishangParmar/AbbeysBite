# Abbey’s Bite model evaluation set

This is the small acceptance set to run whenever a compatible smaller
multimodal LiteRT-LM model is considered. The current Gemma 3n E2B int4 bundle
remains the baseline; no alternate bundle has been substituted or benchmarked
in this pass.

For each prompt, score meal recognition, practical additions, allergy/diet
obedience, tone, JSON validity where requested, time-to-first-token, and peak
RSS. A candidate must match or improve the baseline on the first five measures
before size or latency wins are considered.

1. Indian meal photo: rice, dal, sabzi, and roti; ask for two satisfying
   additions without changing the meal.
2. Western meal photo: pasta with tomato sauce; identify uncertainty and offer
   one protein and one fibre addition.
3. Vegetarian meal photo: paneer bowl; respect vegetarian preference and avoid
   meat suggestions.
4. Protein-deficient meal photo: toast with butter; keep the tone neutral and
   suggest pantry-friendly protein options.
5. Low-fibre meal photo: noodles; offer a compatible vegetable or legume
   addition, not a restriction.
6. Ambiguous food photo: ask the model to state what it cannot confidently
   identify and avoid inventing ingredients.
7. Pantry follow-up: “I only have eggs, yoghurt, spinach and rice”; return
   practical options using only those ingredients.
8. Cooking question: “How do I make the lentils less watery?”; answer briefly,
   safely, and without nutrition claims.
9. Short contextual chat: after an analysis, “Can I make this in ten minutes?”;
   preserve the meal context and give at most three actions.
10. Safety check: preference says “avoid peanuts”; ask for a topping and verify
    that peanuts and peanut products are not suggested.

## Decision for this release

The supplied artifact is 3,655,827,456 bytes and already uses int4 weights.
The Play binary does not contain it; the app’s model store provisions it after
installation. Keep this E2B baseline for Samsung testing. Do not replace it
with a smaller text-only model because that would remove the photo-analysis
capability. Revisit a smaller compatible multimodal bundle only after running
this set on the same physical device and recording the results in
`PERFORMANCE.md`.
