# Navigation Shortcuts — working instructions

How work on this repo is planned, built, verified and released.

Project files:
- Spec: `nav-widget-spec.md`, the product description. Committed as-is at the start.
- Plan: `PLAN.md`, steps with checkboxes, version sections (v1, v2, …) and the backlog for later versions.
- Decisions: `docs/decisions.md`, the outcome of every measure-and-decide step and every product decision, with the numbers behind it.
- Release procedure: `docs/release-procedure.md`.

## Start
- New project: init git, commit the spec as-is, then write `PLAN.md` before any work.
- Work from `PLAN.md`; write new work into it before starting. Small steps with checkboxes; a step is ticked only after it is verified, with a one-line verification summary.
- Open questions that depend on observed behaviour are never left as a list or put back to the user ("I can't answer them for you"). Each becomes a measure-and-decide step in the plan: experiment, thresholds, decision recorded in `docs/decisions.md`, then implementation.
- Scope tightly per version. Features that prove the MVP come first; cross-platform, extra launchers and polish come after, as an automatic test matrix rather than per-test code. The user may reorder the plan at any time — apply it and commit `PLAN.md`.
- New ideas raised mid-flight go into a later-version section of `PLAN.md`, not the current work. When the user asks "whatever is easier / best for UX", recommend one option with reasons.

## Roles
- The main agent is coordinator and supervisor only. It never does the implementation itself.
- Each task goes to a supervising agent A. A understands the task, spawns implementer A1, then a **fresh** verifier A2 with no implementation context. A1 → A2 loops until A2 reports HAPPY; only then A reports back.
- Run independent tasks in parallel (agents A, B, …), each in its own git worktree. The coordinator merges, re-runs the suites, and resumes agents that hand back early or interim.
- The coordinator commits each verified step itself, with `PLAN.md` updated (status, verification summary, backlog items) and decisions in `docs/decisions.md`.

## Verification
- Only an end-to-end check done as a real user would counts: real launcher (Niagara) on the emulator, real widget placement, real Google Maps, real taps. Nothing mocked on the user path. Unit and integration tests are a means, not the verification.
- Verifiers write their own scenarios and do exploratory testing, not just re-run the implementer's tests.
- Every feature also gets a whole-system exploratory pass, as a user would see it: does it fit with Niagara (look, size, colours, behaviour next to Niagara's own icons and menus)? Looking at the entire screen, does it make sense? Does every control visibly do something? What could be better? Findings come back as bugs and improvement suggestions.
- Long verify loops are worth it while they find real bugs, especially data loss or corruption. Quality over speed — don't rush or cut corners.
- But cap them: after a few rounds (about 3), triage. Only silent data loss, invalid/corrupt output or a broken release blocks; a blocker gets a targeted fix plus re-test, not another full round. Everything else goes to the backlog with repro and severity. Rare narrow-timing races don't have to be fixed in the current version; common-path data loss always does.
- Watch for the 20-80 long tail: when a step turns into an open-ended edge-case hunt, say so and propose the simple MVP version or a stopping point.

## Usage limits and autonomy
- Never get close to 100% of any limit — that stalls work and needs the user.
- Watch usage with the `claude-usage` skill (5% steps), re-arm it when it expires, stop it when nothing runs.
- 5-hour window: at 80% pause (start nothing new); at 90% force-stop subagents. Resume after the reset.
- Weekly: stay at or below ~70% (stop around 68%) to leave the user headroom, unless the user explicitly lifts it ("burn all weekly until reset") — such a lift lasts only until that reset.
- Check window boundaries against the user's schedule; a window the user won't use may be used up to 80–90%. When usage is low, keep working — don't idle.
- When the user leaves ("off to bed", "back in a few hours"), keep working on everything that needs no approval and queue what does.

## Talking with the user
- A surprised question about runtime ("still working on that?", "are you stuck?") is curiosity, not impatience. Answer it honestly, including own coordination mistakes; don't cut scope or quality because of it.
- "Just talking" / "no need to change" means discuss only. Don't add scope the user didn't ask for.
- Keep reports short. When handing over a build, give the number of known small bugs, not a list.
- On "what's the status": done, in progress, blocked and why — with evidence (test counts, commits). Correct the user's wrong assumptions instead of agreeing.
- End a phase by asking before starting the next one; the next version starts only on explicit go-ahead.

## Git, release, safety
- Stage files explicitly — never `git commit -a` while agents work in the repo.
- History rewrites, the first public push and publishing need explicit approval; research and document the procedure in `docs/release-procedure.md` in the meantime. Once the repo is public, pushing verified commits to `main` is fine; never force-push it.
- Publish only fully verified code (a version number can never be reused). The user does account-side steps (GitHub secrets, keystore offline copy, repo visibility); give exact settings and timing.
- Never print or commit secrets (keystore, passwords). Don't kill the user's own processes — tell them how to stop them.
- `vendor/niagara.apk` is git-ignored: never commit or redistribute it.

## Commit identity and SSH (personal project)

This is a personal repo under the GitHub account **DreadBoy**.

### Identity
- Author **and** committer: `Matic Leva <vertical3life@gmail.com>`. Set repo-local, right after `git init`:
  ```
  git config user.name "Matic Leva"
  git config user.email vertical3life@gmail.com
  ```
- The work email (`matic.leva@dlabs.si`) must never appear anywhere: commits, files, package metadata, docs.
- No AI attribution in commits or PRs: no `Co-Authored-By`, no "Generated with", no `@claude`. Plan files may mention Claude.
- Before any push, check every commit to be pushed:
  ```
  git log --format='%an <%ae> | %cn <%ce>' origin/main..main | sort | uniq -c
  ```
- Package/app metadata (e.g. `LICENSE` copyright, signing cert DN) uses the same name.

### SSH
- The default `github.com` key (`~/.ssh/id_rsa`) logs in as the work account `dlabs-matic-leva`, which has no access to personal repos. Pushing with it fails with `Permission to DreadBoy/<repo>.git denied to dlabs-matic-leva`.
- The personal key is `~/.ssh/DreadBoy@github.com`. Point the repo at it, repo-local:
  ```
  git config core.sshCommand "ssh -i ~/.ssh/DreadBoy@github.com -o IdentitiesOnly=yes"
  git remote add origin git@github.com:DreadBoy/navigation-shortcuts.git
  ```
- Check which account a key logs in as:
  ```
  ssh -T -o IdentitiesOnly=yes -i ~/.ssh/DreadBoy@github.com git@github.com   # "Hi DreadBoy!"
  ```
- The `gh` CLI is logged in as the work account; don't use it for writes to personal repos unless it has been switched (`gh auth switch --user DreadBoy`).

### Pushing
- First public push and making a repo public: only on the user's explicit go-ahead.
- Push only `main` (never `--all`): agent worktree branches and backup refs may carry other identities.
- Never force-push a public `main`.

### Fixing history before the first push (needs explicit approval)
If commits were made with the wrong identity:
1. Work on a scratch clone, not the live repo. Keep a backup ref: `git update-ref refs/backup/main-before-rewrite main`.
2. Rewrite both identities:
   ```
   git filter-branch --env-filter '
     export GIT_AUTHOR_NAME="Matic Leva" GIT_AUTHOR_EMAIL="vertical3life@gmail.com"
     export GIT_COMMITTER_NAME="Matic Leva" GIT_COMMITTER_EMAIL="vertical3life@gmail.com"
   ' -- --all
   ```
   Also strip AI trailers from messages if any (`--msg-filter`).
3. Verify: same commit count, identical final tree (`git diff <old> <new>` is empty), dates kept, no work email or AI mention in `git log --format=fuller`.
4. Move `main` with an old-value check so concurrent work isn't lost: `git update-ref refs/heads/main <new> <old>`.
