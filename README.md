# playmc-litemc-mc-plugin

The server's own Paper plugin: the commands that are specific to PlayMC and
have no plugin worth pulling in for them. Code, not deployment.

| Path | Purpose |
|---|---|
| `pom.xml` | build - paper-api only, nothing is shaded in |
| `.github/workflows/release.yml` | builds a tagged version and attaches the jar to a release |
| `src/main/resources/plugin.yml` | command and permission declarations |
| `src/main/resources/config.yml` | rules, welcome and `/grant` text, editable without a rebuild |
| `src/main/java/com/playmc/litemc/` | `LiteMcPlugin`, `Messages`, the two commands and the join listener |

## Why a plugin and not `commands.yml`

A `commands.yml` alias has no permission node of its own. It dispatches its
lines with the *sender's* permissions, so `/rules` was only usable by someone
who already held `minecraft.command.tellraw`, and `/grant` by someone holding
`luckperms.user.parent.set` - a node broad enough to make anyone an admin.
Opening either one up meant handing out the underlying command.

A plugin declares its own nodes, so `litemc.rules` and `litemc.grant` are
granted to exactly the groups that should have them, and `/grant` runs
LuckPerms from the console instead of as the sender.

## Commands

| Command | Permission | Default | Behaviour |
|---|---|---|---|
| `/rules` | `litemc.rules` | everyone | Prints `rules.lines` from `config.yml`. |
| `/grant <player>` | `litemc.grant` | op | Puts an online player in the `grant.group` LuckPerms group. |
| | `litemc.grant.self` | op | Bypasses the guard against promoting yourself. |

## The welcome message

Players who join without the `grant.group` role are greeted after
`welcome.delay-ticks` with `welcome.lines`, which point them at `/rules` and
tell them to ask for `/grant`. Group membership decides who sees it, not
whether they have played before, so it keeps appearing while it is still the
right advice and stops by itself once someone grants them. Nothing is stored
per player. Set `welcome.enabled: false` to turn it off.

## Notes

`/grant` still goes through LuckPerms - it dispatches `grant.command` from the
console - so LuckPerms' own action log (`/lp log recent`) records every
promotion alongside this plugin's `INFO` line. Because LuckPerms applies the
change asynchronously, membership is re-checked `grant.verify-delay-ticks`
later and a `WARNING` is logged if it never landed.

## Build

Needs JDK 25, matching the `java25` image and paper-api, which is compiled for
25. There is no local JDK in this checkout, so nothing here has been compiled
yet.

```
mvn -B package
```

produces `target/LiteMC-<version>.jar`.

The pom version is `${revision}`, defaulting to a `-SNAPSHOT` for local
builds. Releases override it with `-Drevision`, so the pom is never edited to
cut one and `main` never carries a release version.

## Releasing

The tag is the version. Push `vMAJOR.MINOR.PATCH` and `Release: plugin` builds
it and creates the matching GitHub release with the jar attached:

```
git tag v0.1.0 && git push origin v0.1.0
```

A tag in any other shape fails the job rather than producing a mislabelled
jar, and a suffixed tag such as `v0.2.0-rc1` is published as a pre-release.
The build also asserts that the tag reached the filtered `plugin.yml`, so the
version the server reports can never drift from the release it came from.

## Deploying it

`playmc-litemc-config/plugins.yml` needs `url` and `sha256` for every enabled
plugin, and the release notes print that entry ready to paste - `sha256`
already filled in:

```yaml
  - id: litemc
    name: LiteMC
    enabled: true
    version: "0.1.0"
    file: LiteMC-0.1.0.jar
    url: https://github.com/playmc-adm/playmc-litemc-mc-plugin/releases/download/v0.1.0/LiteMC-0.1.0.jar
    sha256: "..."
    config:
      - src: plugins/LiteMC/config.yml
        dest: LiteMC/config.yml
```

Then run **Deploy: plugins** in `playmc-litemc-provision`.

Two things have to happen on the config side before the commands work:

- Delete the `grant` and `rules` aliases from `playmc-litemc-config/commands.yml`.
  Server aliases are registered *after* plugins are enabled and overwrite the
  bare command name, so they would silently shadow this plugin.
- Add `litemc.grant` to the `player` group so Players can promote Visitors.
  `litemc.rules` defaults to everyone and needs no entry.
