# Change Policy for Human and AI Contributors

This project is edited by multiple tools and AI coding assistants. The changelog is the shared memory between them.

## Required before editing

1. Read `CHANGELOG.md`.
2. Read the relevant existing code and tests.
3. Check `git status` and preserve changes that were already present.

## Required after editing

Any person or AI system that changes source code, tests, build configuration, manifests, resources, backend code, scripts, or project configuration must update `CHANGELOG.md` in the same change.

The entry must be newest first and contain:

- Date in `YYYY-MM-DD` format.
- `By:` identifying the human, model, tool, or integration. Examples: `Codex (GPT-5)`, `Claude Code (Claude)`, `Gemini in Android Studio`, `GitHub Copilot`, or a person's name.
- A concise summary of what changed and why.
- Verification performed, including tests, build commands, or `Not run` with a reason.

Do not rewrite or remove earlier entries. If several agents work on the project on the same day, add separate entries so ownership remains clear. If an existing entry is inaccurate, append a correction rather than silently changing history.

## Enforcement

Run `powershell -ExecutionPolicy Bypass -File tools/verify-changelog.ps1` before handing off a code change. The script checks that tracked project changes are accompanied by a new changelog entry containing a date and `By:` line.

This policy is intentionally tool-agnostic. `AGENTS.md`, `CLAUDE.md`, `GEMINI.md`, and `.github/copilot-instructions.md` point here so commonly used assistants discover the same rule. A model can still ignore a repository file, so teams should also run the validator in their review or CI process.
