# Publishing to JetBrains Marketplace

This guide helps you publish the **Akeyless Agentic Runtime Authority** plugin to the official JetBrains Marketplace.

| Field | Value |
|---|---|
| Plugin ID | `com.akeyless.ara` |
| Display name | Akeyless Agentic Runtime Authority |
| Suggested category | **Security** (or Tools) |
| Repository | https://github.com/akeyless-community/pycharm-ara-plugin |

## Prerequisites

1. **JetBrains Account**: https://account.jetbrains.com/
2. **Plugin Developer Account**: https://plugins.jetbrains.com/author/me
3. **Plugin Signing Certificate** (optional but recommended): https://plugins.jetbrains.com/docs/intellij/plugin-signing.html

## Step 1: Prepare Plugin Metadata

Ensure `src/main/resources/META-INF/plugin.xml` has:

- Plugin ID: `com.akeyless.ara`
- Name: **Akeyless Agentic Runtime Authority**
- Version matching `gradle.properties`
- Vendor: Akeyless
- Comprehensive `<description>` and `<change-notes>` for the release

Icons (required for Marketplace):

- `src/main/resources/META-INF/pluginIcon.svg` and/or `pluginIcon.png` (40×40)
- `pluginIcon@2x.png` (80×80) for high-DPI

## Step 2: Release checklist (each Marketplace version)

1. **Version** — set the same value in:
   - `version` in `gradle.properties`
   - `<version>` / change-notes via `intellijPlatform.pluginConfiguration` / `plugin.xml`
2. **Compatibility** — `sinceBuild = "243"` (IDE 2024.3+). Prefer **no** `until-build` so newer IDE branches (e.g. PyCharm 2026.2 / `262.*`) stay compatible.
3. **JDK for Gradle** — compilation uses **JDK 17** via the toolchain. If `./gradlew` fails on a very new host JDK, set `org.gradle.java.home` in `gradle.properties` to JDK 17 or 21.
4. **Secrets** — copy `publish.env.example` → `publish.env` (gitignored). Never commit tokens.

`build.gradle.kts` already wires `signPlugin` and `publishPlugin` from environment variables.

## Step 3: Build and test

```bash
./gradlew clean check buildPlugin

# Optional sandbox IDE with the plugin loaded
./gradlew runIde

ls -lh build/distributions/
```

Install the ZIP from disk on PyCharm / IntelliJ and verify:

- Plugin is **enabled**
- **Tools → Show Akeyless ARA** opens the tool window
- **Settings → Tools → Akeyless ARA** opens configuration
- Access Key / JWT are **not** stored in plaintext settings XML

## Step 4: Marketplace listing

1. Go to https://plugins.jetbrains.com/author/me
2. **Add new plugin** (first time) or open the existing listing
3. Fill in:
   - **Plugin name**: Akeyless Agentic Runtime Authority
   - **Plugin ID**: `com.akeyless.ara` (must match `plugin.xml`)
   - **Category**: Security
   - **Tags**: akeyless, secrets, security, ara, agentic-runtime-authority, pycharm
   - **Repository URL**: https://github.com/akeyless-community/pycharm-ara-plugin
   - **Support URL**: https://github.com/akeyless-community/pycharm-ara-plugin/issues

## Step 5: Upload

### Option A: Manual upload

1. `./gradlew clean buildPlugin`
2. Marketplace → **Upload new version**
3. Upload `build/distributions/pycharm-ara-plugin-*.zip`
4. Paste release notes from change-notes
5. Submit for review

### Option B: Automated publish

```bash
cp publish.env.example publish.env
# edit publish.env — set PUBLISH_TOKEN (and optional signing vars)
set -a && source publish.env && set +a
./gradlew clean check buildPlugin signPlugin publishPlugin
```

Token: https://plugins.jetbrains.com/author/me/token

## Review process

Typical Marketplace review is 1–3 business days. Common feedback:

- Missing / weak documentation
- Credentials stored insecurely (we use PasswordSafe)
- Compatibility / verifier failures
- Missing icons

## Post-publication

For each release:

1. Bump `version` in `gradle.properties`
2. Update change-notes
3. `./gradlew clean check buildPlugin publishPlugin`
4. Tag the GitHub release with the same version

## Checklist before publishing

- [ ] `./gradlew check` and `buildPlugin` succeed
- [ ] Tested with **Install Plugin from Disk** on PyCharm 2024.3+ / 2026.x
- [ ] Plugin enabled after install; tool window and settings visible
- [ ] No secrets in `akeyless-ara.xml` (PasswordSafe only)
- [ ] README and PUBLISHING.md accurate
- [ ] Icons present
- [ ] Vendor / description / change-notes complete
- [ ] Version bumped
- [ ] No tokens or certificates committed

## Resources

- https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html
- https://plugins.jetbrains.com/docs/marketplace/marketplace-plugin-requirements.html
- https://plugins.jetbrains.com/docs/intellij/plugin-signing.html
