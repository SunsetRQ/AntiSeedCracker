# Contributing to AntiSeedCracker

## Reporting bugs

Use the **Bug Report** issue template. Include your server software, version, Java version, full config.yml, and the complete startup log section — not just one line.

## Suggesting features

Use the **Feature Request** template. Describe the specific bypass vector or seed-leaking attack you want to address, not just the general idea.

## Questions

Post in [Discussions](../../discussions) rather than opening an issue. Issues are for confirmed bugs and accepted feature work.

## Pull requests

1. Fork the repository and create a branch: `git checkout -b feat/my-change`
2. Build and verify locally:
   ```sh
   # Requires JDK 25
   mvn clean package
   ```
3. Ensure zero compilation warnings (`-Xlint:deprecation` passes clean)
4. Keep the same code style as the existing sources:
   - No comments in source files
   - No unused imports
   - Thread safety: async tasks go through `FoliaSchedulerUtil`; any block mutations run on the chunk's region thread
   - All world-modifying operations must be idempotent and guarded by a Persistent Data Container marker
5. Open a PR against `main` with a clear description of what bypass vector is addressed or what bug is fixed

## Versioning

| Change | Version bump |
|---|---|
| New protection layer | Minor: 3.x.0 |
| Bug fix | Patch: 3.1.x |
| Breaking config change | Major: x.0.0 |
