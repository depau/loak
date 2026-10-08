# Contributing to Lo'ak

## What's welcome

All contributions are welcome — including LLM-assisted ones, typo fixes and refactoring. This
is a self-maintained fork of Navic; the upstream anti-LLM contribution policy does not apply.

## Environment

You will need:

* JDK 21 — required (`jvmTarget = 21`), no other version
* Android Studio (via [JetBrains Toolbox](https://www.jetbrains.com/toolbox-app/))
* A high-end development box: **>16 GB of RAM** and ~50 GB of free storage. The build is heavy.
* [Xcode](https://developer.apple.com/xcode/) if developing for iOS. **Apple silicon required** —
  Compose Multiplatform no longer compiles on Intel (x86_64) hosts since 1.11.1.

Test mainly on Android; use iOS only for iOS-specific changes (Kotlin Native is slow and heavy).

## Conventions

* **Pre-commit** is set up. Install it with `brew install pre-commit && pre-commit install`;
  it runs formatting and secret detection (`gitleaks`) on every commit. CI enforces it on PRs too.
* **Format your code** — `indent_style = tab`, 4-space indent, 100 col, LF (see `.editorconfig`).
* **Conventional commits** — `feat:`, `fix:`, `chore:`, `refactor:`, ... (matches existing history).
* **Keep PRs small and focused** — create separate PRs for unrelated changes so maintainers can
  cherry-pick specific ones.
* **Screenshots required for UI changes** — verify on different themes and form factors first.
* **User-facing strings** go in `loak/src/commonMain/composeResources/values/strings.xml`.
  Translations are managed on Weblate — don't hand-edit the `values-*` locale dirs.

This repo has no automated test suite; the sanity gates are the compile/assemble tasks and manual
UI verification.

## Questions or assistance

Ask in the [Discord](https://discord.gg/TBcnNX66PH) server.
