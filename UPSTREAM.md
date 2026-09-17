# Upstream provenance

RuoYi Robot imports and preserves the following upstream projects under the MIT license.

| Component | Source | Pinned commit | Import date |
| --- | --- | --- | --- |
| Backend | Java 17 backend foundation; original repository recorded in Git history | `f3b6a0a853f1d48f5ecc510038e3c9eab47ed4e2` | 2026-09-10 |
| Admin frontend | Vue 3 admin foundation; original repository recorded in Git history | `aab14fb0e74720dd09e964ae066f8bbde9f9012e` | 2026-09-10 |

The complete trees were imported from commit-addressed GitHub source archives after filtered Git transfers stalled. The archive root names include and verify the pinned commits above. The upstream MIT `LICENSE` file is preserved. Imported sources now live in `robot-platform-dependencies`, `robot-platform-framework`, `robot-platform-module-system`, `robot-platform-module-infra`, `sql`, `script`, and the full frontend tree in `robot-platform-ui-admin`.

The original upstream repository URLs and source names remain available in the unmodified Git history:

```bash
git show b886a5d:UPSTREAM.md
```

Java packages and runtime class references have migrated to `com.robot.platform`. The server module is `robot-platform-server`, its application entry point is `com.robot.platform.server.RobotPlatformApplication`, and its admin package is `robot-platform-ui-admin`. Existing physical database table and sequence names remain unchanged. This is an independent community project and is not an official RuoYi project.
