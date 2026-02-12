# Karma Operations

Karma is a community reputation system where users increment or decrement scores for subjects.
Scores decay over time - recent activity counts more than old activity.
Records older than one year are purged automatically.

## Set Karma

Adjusts karma for a subject by +1 or -1.
This operation is **unaddressed** - it fires on ambient conversation without requiring a signal character or bot mention.

**Syntax:**
```
subject++       increment karma for subject
subject--       decrement karma for subject
```

**Priority:** 40 (runs early, before most addressed operations).

**Behavior:**

- The subject is everything before the final `++` or `--`.
  Multi-word subjects work: `spring boot++` increments karma for "spring boot".
- Trailing text after the predicate is discarded: `kotlin++ is great` increments "kotlin".
- IRC nick-completion suffixes (`:`, `,`, `;`) are stripped: `jreicher: ++` increments "jreicher".
- Subjects longer than 150 characters are ignored.
- Arrow sequences (`-->`, `<--`) are normalized to avoid false matches.
- The `c++` language name is handled correctly: `c++--` decrements "c++", `c+++` increments "c+".

**Self-karma protection:**
If the sender increments their own karma, the increment is silently flipped to a decrement.
Self-decrements are allowed without modification.
The response includes a notice: "You can't increment your own karma! Your karma is now -1."

**Immune subjects:**
Subjects listed in `streampack.karma.immune-subjects` (configured via the `KARMA_IMMUNE_SUBJECTS` environment variable) are silently ignored.
This prevents the bot itself from accumulating karma.

**Response examples:**
```
foo now has karma of 3.
bar now has karma of -1.
bar has neutral karma.
You can't increment your own karma! Your karma is now -1.
```

## Get Karma

Queries the current decayed karma score for a subject.
This operation is **addressed** - it requires the signal character or bot mention.

**Syntax:**
```
karma <subject>
```

**Priority:** 50 (default).

**Behavior:**

- Returns the current decayed score for the subject.
- If the querier asks about themselves, the response is personalized ("you have karma of 3" instead of "foo has karma of 3").
- If no karma records exist for the subject, returns "has no karma data."
- A score of exactly 0 (after decay) displays as "neutral karma."

**Response examples:**
```
kotlin has karma of 12.
kotlin, you have karma of 12.
unknown_thing has no karma data.
balanced has neutral karma.
```

## Karma Ranking

Displays the top or bottom karma holders.
This operation is **addressed**.

**Syntax:**
```
top karma           show top 5 subjects by karma (default)
top <N> karma       show top N subjects
top karma <N>       show top N subjects (alternate syntax)
bottom karma        show bottom 5 subjects by karma (default)
bottom <N> karma    show bottom N subjects
```

**Priority:** 50 (default).

**Behavior:**

- Default count is 5, maximum is 10.
  Counts outside this range are clamped.
- "Top" returns only subjects with positive karma, sorted highest first.
- "Bottom" returns only subjects with negative karma, sorted lowest first.
- Subjects with exactly zero karma are excluded from both rankings.
- The output is built entry by entry, stopping when the next entry would push the response past 400 characters.
  This keeps the response within a single IRC message.

**Response examples:**
```
Top karma: kotlin(42), spring(30), java(15)
Bottom karma: xml(-8), soap(-5), ant(-2)
No karma data yet.
```

## Scoring Model

Karma uses time-decayed scoring.
Each day's karma changes are stored as a single aggregated record per subject.
The current score is computed as:

```
score = sum(delta * e^(-0.002 * age_in_days))
```

This means:
- Today's karma counts at full value.
- A karma change from 6 months ago (~180 days) retains about 70% of its value.
- A karma change from a year ago (~365 days) retains about 48% of its value.
- Records older than one year are purged entirely.

The decay ensures that karma reflects recent community sentiment rather than ancient history.

## Configuration

| Property | Env Variable | Default | Description |
|----------|-------------|---------|-------------|
| `streampack.karma.immune-subjects` | `KARMA_IMMUNE_SUBJECTS` | (none) | Comma-separated list of subjects that cannot receive karma |
