# Projectiles Trajectory Preview Plugin

Paper plugin port of [ProjectilesTrajectoryPreview](https://github.com/maDU59/ProjectilesTrajectoryPreview). It renders projectile paths, impact outlines, and impact fills for players on the server without requiring a client mod.

This plugin uses packet-only TextDisplay entities for rendering. PacketEvents detects player movement/look packets without polling, while trajectory calculation and TextDisplay updates remain safely synchronized with the server main thread.

## Demo

<!-- markdownlint-disable MD033 -->
<video src="video.mp4" controls width="720"></video>
<!-- markdownlint-enable MD033 -->

[Watch the demo video](video.mp4)

## Features

- Server-side projectile trajectory preview for common vanilla projectiles.
- Packet-only TextDisplay rendering; no real display entities are spawned into the world.
- Fixed-size TextDisplay pools for trajectory lines, target outlines, and target highlight faces.
- Smooth transformation updates for existing TextDisplay packets.
- Impact target outline and translucent fill.
- Target-dependent colors for entities, with fixed color support.
- Optional offhand preview.
- Temporary item-drop trajectory preview from client drop packets.
- Per-projectile enable/disable toggles.
- `/ptp` command for status, toggle, and config reload.

## Requirements

- Java 21
- Paper `1.21.11`
- PacketEvents installed as a server plugin

`PacketEvents` is declared as a plugin dependency. EntityLib and TextDisplayShapes API are shaded into this plugin jar.

## Build

```powershell
mvn clean package -DskipTests
```

The shaded plugin jar is produced at:

```text
target/projectilestrajectorypreviewplugin-1.0.0.jar
```

## Installation

1. Install PacketEvents on the server.
2. Put the built `projectilestrajectorypreviewplugin-1.0.0.jar` into the server `plugins` folder.
3. Start the server once to generate `plugins/ProjectilesTrajectoryPreviewPlugin/config.yml`.
4. Edit the config if needed.
5. Run `/ptp reload` or restart the server.

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/ptp status` | `ptp.use` | Show current enabled/offhand/outline status. |
| `/ptp toggle` | `ptp.use` | Toggle the global `enabled` setting and save config. |
| `/ptp reload` | `ptp.use` | Reload `config.yml` and clear active previews. |

## Configuration

Default config:

```yaml
enabled: true
enable-offhand: true
show-trajectory: enabled
show-outline: enabled
show-highlight: enabled
show-uncertainty: disabled
show-drop-preview: true
drop-preview-duration-ms: 450
update-interval-ms: 50
fallback-update-ticks: 0
line-count: 48
line-thickness: 0.025
line-render-mode: single-135
line-entity-budget: 128
trajectory-style: solid
trajectory-color: depends-on-target
trajectory-alpha: 210
outline-color: '#FFEB50'
outline-alpha: 190
highlight-color: '#FFEB50'
highlight-alpha: 55
uncertainty-color: '#F2F7FF'
uncertainty-alpha: 42
uncertainty-base-radius: 0.08
uncertainty-spread-per-block: 0.01725
uncertainty-max-radius: 1.25

projectiles:
  bow: true
  crossbow: true
  trident: true
  ender-pearl: true
  snowball: true
  egg: true
  wind-charge: true
  potion: true
  experience-bottle: true
  fishing-rod: true
```

### Display modes

`show-trajectory`, `show-outline`, `show-highlight`, and `show-uncertainty` accept:

- `enabled` - always show when a trajectory exists.
- `target-is-entity` - only show when the trajectory hits an entity.
- `disabled` - never show.

Older boolean-style values are also accepted internally: `true` maps to `enabled`, and `false` maps to `disabled`.

### Trajectory styles

`trajectory-style` accepts:

- `solid`
- `dashed`
- `dotted`

Dashed and dotted styles shorten each TextDisplay line segment while keeping the fixed-size packet pool.

### Line render modes

`line-render-mode` accepts:

- `standard` - one TextDisplay plane per logical line.
- `single-135` - one TextDisplay plane per logical line, rolled 135 degrees for better visibility at the same entity cost as `standard`.
- `double-sided` - front and back planes for each logical line.
- `crossed-double-sided` - two overlapping planes rolled 45 and 135 degrees, each with front and back faces.

`crossed-double-sided` is the most visible option, but it uses four TextDisplay entities per logical line. `line-entity-budget` caps the trajectory, outline, and highlight display pool per player; if the requested `line-count` would exceed that budget, the plugin lowers the effective trajectory line count automatically.

### Colors

Color settings accept either a hex color or `depends-on-target`.

Examples:

```yaml
trajectory-color: depends-on-target
outline-color: '#FFEB50'
highlight-color: '#FFEB50'
```

When `depends-on-target` is used, entity targets are colored by broad type:

| Target | Color |
| --- | --- |
| Player | Blue |
| Passive entity | Green |
| Hostile entity | Red |
| Other mob | Purple |
| Other living entity | Cyan |
| Other entity | Magenta |
| Block or no target | White |

Opacity is controlled separately by the matching `*-alpha` value from `0` to `255`.

### Uncertainty Area

`show-uncertainty` displays a pale white range marker at the predicted impact point. Its radius is estimated as `uncertainty-base-radius + travel-distance * uncertainty-spread-per-block`, capped by `uncertainty-max-radius`. This represents vanilla projectile inaccuracy/divergence; it is a visual guide, not an exact random seed prediction.

## Supported projectiles

- Bow
- Crossbow, including charged firework rockets and multishot
- Trident, excluding Riptide throws
- Snowball
- Egg
- Ender pearl
- Wind charge
- Splash and lingering potions
- Experience bottle
- Fishing rod
- Dropped items, shown briefly when the client sends a drop packet

## Server-side notes

This is a server plugin, not a client mod. Players do not need to install anything client-side.

Because the server cannot know every client-only state, there are a few intentional differences from the Fabric client mod:

- Third-person detached camera state is unknown to the server, so hand offset is always applied.
- Drop preview is event-like and short-lived; it is triggered by the drop packet rather than by continuously reading the client's keybind state.
- Fishing rod physics is currently approximated with server-side data.
- Actual Minecraft projectile randomness can still make the final impact differ slightly from the preview.

## Development Notes

- PacketEvents handles look/movement/drop packet detection.
- Packet-triggered updates are throttled and coalesced per player before running trajectory physics on the main thread.
- Set `fallback-update-ticks` above `0` only when movement packet hooks are not firing in a specific server setup.
- EntityLib sends packet-only TextDisplay entities to the viewer.
- TextDisplayShapes math utilities build the line and parallelogram transformations.
- The plugin keeps fixed pools of TextDisplays per player and updates transformations instead of spawning/removing displays every packet.

## Credits

This plugin is a Paper port inspired by and partially adapted from the original Fabric client mod [ProjectilesTrajectoryPreview](https://github.com/maDU59/ProjectilesTrajectoryPreview) by maDU59.

The projectile physics parameters, trajectory simulation structure, and several parity decisions were implemented with reference to the original mod's MIT-licensed source code.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).

Portions based on [ProjectilesTrajectoryPreview](https://github.com/maDU59/ProjectilesTrajectoryPreview) retain the original project's MIT license notice.
