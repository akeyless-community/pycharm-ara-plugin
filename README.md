# Akeyless ARA Plugin for JetBrains IDEs (PyCharm)

Native JetBrains plugin that brings
[Akeyless Agentic Runtime Authority (ARA)](https://docs.akeyless.io/docs/agentic-runtime-authority)
to **PyCharm**, IntelliJ IDEA, WebStorm, and other IntelliJ-based IDEs.

The IDE orchestrates. Akeyless holds the credentials. **Secret values never enter the UI or logs.**

This matches the tools and auth model of the
[Claude Desktop connector](https://github.com/akeyless-community/claude-akeyless-connector),
implemented in Kotlin — **no Akeyless CLI or Node required** for core use.

> Related (different product): the [Akeyless MCP plugin](https://github.com/akeyless-community/akeyless-mcp-plugin)
> browses secrets via the CLI MCP server. This ARA plugin executes `list-secrets` / `query-db` /
> `service-execute` through the Gateway with JIT credentials.

## Features

- **list-secrets** — discover ARA-enabled dynamic, rotated, and custom-MCP secrets, and SSH certificate issuers
- **query-db** — run database queries via Gateway JIT credentials
- **service-execute** — AWS, GCP, Azure, Kubernetes, GitHub, or custom MCP actions, and remote commands on SSH, Windows, and LDAP servers
- **list-sub-tools** — discover sub-tools for a service secret or remote item
- **Settings UI** — Gateway URL + auth method (Access Key / JWT stored in PasswordSafe)
- **SAML / OIDC** — browser login on first use

## Remote commands (SSH, Windows, LDAP)

The **Service Execute** tab also runs one non-interactive command on a remote host, with the credentials of the item you name. The Gateway decides how to run it from the item's target type:

| Item | `target_type` in list-secrets | Runs over |
|---|---|---|
| Rotated secret on an SSH target | `ssh` | SSH |
| Rotated secret on a Windows target | `windows` | WinRM |
| Rotated secret on a linked target | `linked` | SSH or WinRM, on one of the linked hosts |
| RDP dynamic secret | `rdp` | SSH or WinRM, as the temporary user |
| LDAP (Active Directory) rotated or dynamic secret | `ldap` | WinRM over HTTPS, as the domain user |
| SSH certificate issuer (`secret_type: ssh-cert-issuer`) | `ssh` | SSH, with a short-lived certificate signed for the call |

The payload is either a plain command line (`uptime`, `Get-Service`) or a sub-tool call for anything else:

```json
{"tool":"run","arguments":{"command":"systemctl status nginx","host":"web1.corp","cwd":"/var/log"}}
```

- `run` takes `command`, plus optional `host` (required when the item has more than one host; the error lists them), `cwd`, `shell` (`powershell` or `cmd`, WinRM only), `host_key_fingerprint` (`SHA256:<base64>`, SSH only), and `remote_user` (SSH certificate issuers only).
- `upload` (`remote_path`, base64 `content`, at most 512 KB) and `download` (`remote_path`) move one file.
- A run returns `stdout`, `stderr`, `exit_code`, `truncated`, and, when sent with `cwd` or run in PowerShell, the `cwd` the command ended in. Pass that `cwd` back on the next call, so a series of commands behaves like one shell session.
- The **List Sub-Tools** tab on a remote item returns this catalog without connecting to the host.

The same ARA gates apply as for databases: `ara_allow_access`, input rules (the Gateway ships default rules per target type), AI validation, output rules, and an ARA session record. Remote commands need Gateway 5.6.0 or later; an older Gateway answers `unsupported secret type`. A remote call can take up to 3.5 minutes on the Gateway.

## Requirements

- JetBrains IDE 2024.3 or later (PyCharm, IntelliJ IDEA, etc.)
- JDK 17 or higher to **build** from source
- Akeyless Gateway with ARA configured
- Network access to your Gateway

## Installation

### From source (Marketplace-style ZIP)

```bash
git clone https://github.com/akeyless-community/pycharm-ara-plugin.git
cd pycharm-ara-plugin
./gradlew buildPlugin
```

Then in the IDE:

1. **Settings → Plugins → ⚙ → Install Plugin from Disk…**
2. Select the ZIP from `build/distributions/`
3. Restart when prompted
4. Ensure the plugin is **enabled**

### Development sandbox

```bash
./gradlew runIde
```

## Configuration

**Settings → Tools → Akeyless ARA**  
(or **Tools → Akeyless ARA Settings…**)

| Field | Notes |
|---|---|
| Gateway URL | e.g. `https://gw.example.com:8000/api/v2` |
| Authentication Method | `access_key` (default), `saml`, `oidc`, `universal_identity`, `jwt` |
| Access ID | Required for access_key, saml, oidc, jwt |
| Access Key | Required for access_key — stored in PasswordSafe |
| UID Token File | Required for universal_identity (default `~/.akeyless/uid_rotator/uid-token`) |
| JWT | Required for jwt — stored in PasswordSafe |
| Agent ID | ARA audit id (default `pycharm`) |
| Default secret path | Optional |

Cloud IAM (`aws_iam` / `azure_ad` / `gcp`) is deferred to a later release.

### SAML / OIDC

The first authenticated call opens your browser. Complete IdP login; the plugin receives the callback on `127.0.0.1`.

## Usage

1. Open **Tools → Show Akeyless ARA** (or **View → Tool Windows → Akeyless ARA**)
2. Click **Refresh secrets**
3. Use **Query DB**, **Service Execute**, or **List Sub-Tools** tabs

### OAuth-backed secrets

If a call returns an authorization URL, open it, then paste **auth-code** (state is pre-filled) and retry.

## Architecture

```mermaid
sequenceDiagram
    participant User as User
    participant UI as Akeyless ARA tool window
    participant Ara as AraClient (Kotlin)
    participant API as Akeyless API /api/v2
    participant GW as Gateway config port
    participant Target as DB / Cloud

    User->>UI: list-secrets / query-db / service-execute
    UI->>Ara: call with settings
    Ara->>API: auth + list-items
    API-->>Ara: paths + types
    Ara->>GW: POST /config/target_query
    GW->>Target: JIT credentials
    Target-->>GW: results
    GW-->>Ara: JSON (no credentials)
    Ara-->>UI: results only
```

## Build & test

```bash
./gradlew check
./gradlew buildPlugin
```

## Publishing to JetBrains Marketplace

See [PUBLISHING.md](PUBLISHING.md).

## Project structure

```
pycharm-ara-plugin/
├── src/main/kotlin/com/akeyless/ara/
│   ├── client/          # ARA HTTP client (auth, list, query, execute)
│   ├── settings/        # Settings + PasswordSafe
│   ├── ui/              # Tool window
│   ├── actions/         # Tools menu actions
│   └── icons/
├── src/main/resources/META-INF/
│   ├── plugin.xml
│   └── pluginIcon.*
├── src/test/kotlin/
├── PUBLISHING.md
└── build.gradle.kts
```

## Related

- [Claude ARA connector](https://github.com/akeyless-community/claude-akeyless-connector)
- [Cursor ARA connector](https://github.com/akeyless-community/cursor-akeyless-connector)
- [Akeyless MCP JetBrains plugin](https://github.com/akeyless-community/akeyless-mcp-plugin) (secrets browser)

## License

MIT — see [LICENSE](LICENSE).

## Support

- Plugin issues: https://github.com/akeyless-community/pycharm-ara-plugin/issues
- Akeyless docs: https://docs.akeyless.io/docs/agentic-runtime-authority
