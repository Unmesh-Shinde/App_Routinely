# Text input validation policy

## Defaults

- Human-authored titles and labels: 80 characters maximum.
- Descriptions and notes: 300 characters maximum.
- Trim leading/trailing whitespace; preserve internal spaces and Unicode letters (including accented and non-Latin scripts).
- Require at least one letter in a non-empty semantic text value. This rejects digits-only and punctuation-only values while allowing useful mixed values such as `5K Run`, `B12 breakfast`, and `3-day plan`.
- Allow these punctuation marks in titles/labels: apostrophe (`'`, `’`), hyphen (`-`, `–`), comma, period, parentheses, slash, ampersand, plus, and colon. They may appear as needed but not consecutively. Do not impose arbitrary per-character quotas; the length cap limits total punctuation. Reject emoji, controls, markup-like symbols, and other punctuation in these fields.
- Descriptions/notes use the same allowlist and at-least-one-letter rule. If a future notes field needs free-form symbols (for example code or URLs), it should opt into a separate policy rather than weakening names globally.

## Number policy

- Numbers are allowed in titles/labels only when the value also contains a letter. Digits may appear at the beginning, middle, or end; do not limit how many, since values such as `5K Run`, `Vitamin B12`, and `Workout 2` are legitimate.
- Number-only values are rejected in semantic text fields. Pure numeric measurements remain valid in their dedicated number fields and are validated by their own range/precision rules.
- Numeric entry fields should accept ASCII digits; decimal fields may accept one decimal separator and a leading minus only where the domain allows it. Reject pasted units, exponent notation, `NaN`, and infinity unless a specific field explicitly supports them. Validate again on save; keyboard type alone is not validation.

## Negative-case checklist

| Input | Expected result |
| --- | --- |
| blank / whitespace-only required title | reject as required |
| `123`, `...`, `!!!`, or `😀` as a title | reject |
| `5K Run`, `B12 meal`, `Run 2` | accept |
| `Mom's lunch`, `Push-up`, `Mobility / Yoga`, `Fish & rice`, `Warm-up (easy)` | accept |
| adjacent punctuation such as `Run--fast` | reject |
| title with 81 characters / description with 301 | prevent typing/paste beyond limit |
| accented or non-Latin letters | accept |
| embedded newline/tab/control characters | reject |
| numeric field with letters, multiple decimals, out-of-range value | reject at save with a field-level error |

Apply semantic title/description rules consistently to meal and workout entry first, then migrate other human-authored text fields (reminder titles/notes, profile name, signup name, plan/template names) explicitly. Keep passwords/PINs, search queries, URLs, and numeric measurements on purpose-specific policies; a single filter across every `EditText` would break these inputs.
