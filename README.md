# Voxizen

> **Denizen addon** for [Simple Voice Chat](https://github.com/henkelmax/simple-voice-chat) —
> exposes voice groups, audio sources, microphone packets, and speech recognition to Denizen scripts.

[![Requires Denizen](https://img.shields.io/badge/Requires-Denizen-blueviolet)](https://denizenscript.com)
[![Requires Simple Voice Chat](https://img.shields.io/badge/Requires-Simple%20Voice%20Chat-blue)](https://github.com/henkelmax/simple-voice-chat)

---

## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Commands](#commands)
  - [voicegroup](#voicegroup)
  - [voicesource](#voicesource)
  - [audio](#audio)
  - [volumecategory](#volumecategory)
  - [vosk](#vosk)
- [Events](#events)
- [Tags](#tags)
  - [PlayerTag extensions](#playertag-extensions)
  - [ServerTag extensions](#servertag-extensions)
  - [VoiceGroupTag](#voicegrouptag)
  - [VoiceSourceTag](#voicesourcetag)
- [Mechanisms](#mechanisms)
- [Quick-start examples](#quick-start-examples)
- [License](#license)

---

## Features

| Category | What you get |
|---|---|
| **Voice groups** | Create, delete, and manage Simple Voice Chat groups from scripts. Supports `normal`, `open`, and `isolated` types, optional passwords, and persistent groups. |
| **Audio sources** | Persistent locational, entity, and static audio channels. Pipe WAV files, Opus frame lists, or raw microphone packets into them. |
| **Volume categories** | Register custom volume categories so players can adjust specific audio sources in the SVC GUI independently. |
| **Microphone access** | Receive, inspect, modify, and cancel every microphone packet before it reaches other players. |
| **Speech recognition** | Built-in [Vosk](https://alphacephei.com/vosk/) integration — transcribe buffered Opus frames to text asynchronously. |
| **Player state** | Read and write every voice-chat property of a player: connected, installed, disabled, current group. |
| **Full event coverage** | Cancellable events for connects, disconnects, state changes, group create/remove, group join/leave, microphone, and voice distance. |

---

## Requirements

- **Paper** (or a compatible fork) 1.20+
- **[Denizen](https://denizenscript.com)** — latest build
- **[Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat)** — server-side mod/plugin

---

## Installation

1. Drop `Voxizen.jar` into your `plugins/` folder.
2. Restart the server.
3. Voxizen registers itself automatically as a Denizen addon — no extra configuration needed.

---

## Commands

All commands are Denizen script commands, not Minecraft chat commands.
Use them inside `.dsc` script files.

---

### voicegroup

```
voicegroup [create/delete] [id:<id>] (name:<name>) (type:isolated/{normal}/open) (password:<password>) (persistent:<boolean>)
```

Creates or deletes a managed Simple Voice Chat group.

| Argument | Required | Default | Description |
|---|---|---|---|
| `id` | ✅ | — | Unique string identifier for this group. |
| `name` | create only | — | Display name shown in the SVC GUI. |
| `type` | ❌ | `normal` | `normal` · `open` · `isolated` (see below). |
| `password` | ❌ | none | Optional password for the group. |
| `persistent` | ❌ | `false` | If `true`, the group survives after all members leave. |

**Group types**

| Type | Behaviour |
|---|---|
| `normal` | Members hear each other **and** nearby players not in any group. |
| `open` | Members hear each other, nearby players hear them too. |
| `isolated` | Members **only** hear each other. |

**Save entries**

| Entry | Returns |
|---|---|
| `<entry[name].voicegroup>` | `VoiceGroupTag` of the created group. |
| `<entry[name].id>` | The string id. |

**Examples**

```yaml
# Create a persistent isolated staff channel
- voicegroup create id:staff name:Staff type:isolated persistent:true save:result
- narrate "Group: <entry[result].voicegroup>"

# Create a password-protected VIP group
- voicegroup create id:vip name:VIP password:secret123

# Delete a group
- voicegroup delete id:vip
```

---

### voicesource

```
voicesource [create/delete] [id:<id>] (source:<entity>/<location>) (targets:<player>|...) (distance:<#>) (category:<category>)
```

Creates or deletes a persistent audio channel.
Use the `audio` command to send sound to it.

| Argument | Required | Default | Description |
|---|---|---|---|
| `id` | ✅ | — | Unique string identifier for this source. |
| `source` | ❌ | — | `LocationTag` → locational channel. `EntityTag` → entity channel. Omit → static channel. |
| `targets` | ❌ | all in range | `ListTag` of players who can hear this source. |
| `distance` | ❌ | `16` | Hearing radius in blocks (locational / entity channels). |
| `category` | ❌ | none | Volume category id (must be created with `volumecategory` first). |

**Channel types**

| Type | How it works |
|---|---|
| Locational | Positional audio at a fixed world location. |
| Entity | Follows an entity around the world. |
| Static | Explicit target list, no position. |

**Save entries**

| Entry | Returns |
|---|---|
| `<entry[name].voicesource>` | `VoiceSourceTag` of the created source. |
| `<entry[name].id>` | The string id. |

**Examples**

```yaml
# Open broadcast — everyone in 20 blocks hears it
- voicesource create id:stage source:<player.location> distance:20 save:r
- narrate "Created: <entry[r].voicesource>"

# Restricted broadcast — only listed players hear it
- voicesource create id:radio source:<player.location> distance:32 targets:<[audience]> category:music

# Entity-following source (e.g. a villager NPC talking)
- voicesource create id:npc_voice source:<[npc_entity]> distance:10

# Static channel (assign targets later via mechanism)
- voicesource create id:staff_audio

# Delete
- voicesource delete id:stage
```

---

### audio

```
audio [play/stop] (bytes:<base64>|...) [source:<sourceTag>]
```

Sends audio to a persistent voice source or stops it.

**`bytes` accepts three forms:**

| Form | Behaviour |
|---|---|
| `BinaryTag` (raw WAV bytes) | Automatically converted to Opus frames asynchronously. |
| `ListTag` of Base64 Opus frames | Sent at 20 ms intervals. |
| Single Base64 Opus frame | Sent immediately — ideal for microphone relay. |

This command is **Holdable** (`~audio`). Use `~` when playing WAV files or frame lists so the script waits for playback to finish.

**Examples**

```yaml
# Play a WAV file
- ~fileread path:data/music.wav save:f
- ~audio play bytes:<entry[f].data> source:<voicesource[stage]>

# Relay microphone in real time (no ~ needed — single frame)
on player microphone:
  - audio play bytes:<context.bytes> source:<voicesource[relay]>

# Stop and flush
- audio stop source:<voicesource[stage]>
```

---

### volumecategory

```
volumecategory [create/delete] [id:<id>] (name:<name>) (icon:<binary/base64>) (description:<text>) (name_translation_key:<key>) (description_translation_key:<key>)
```

Registers a custom volume category for the SVC GUI.

| Argument | Required | Description |
|---|---|---|
| `id` | ✅ | 1–16 chars, only lowercase `a-z` and `_`. |
| `name` | create only | Display name in the GUI. |
| `icon` | ❌ | `BinaryTag` or Base64 image. Automatically scaled to 16×16. |
| `description` | ❌ | Tooltip text. |
| `name_translation_key` | ❌ | i18n key for the name. |
| `description_translation_key` | ❌ | i18n key for the description. |

**Examples**

```yaml
# Simple category
- volumecategory create id:music name:Music description:<element[Background music]>

# With a custom icon loaded from file
- ~fileread path:data/music_icon.png save:icon
- volumecategory create id:ambient name:Ambient icon:<entry[icon].data>

# Delete
- volumecategory delete id:music
```

---

### vosk

```
vosk [bytes:<base64>|...]
```

Transcribes Base64-encoded Opus audio data using the Vosk speech recognition engine.
**Holdable** — always use `~vosk`.

**Save entries**

| Entry | Returns |
|---|---|
| `<entry[name].text>` | Transcribed text as `ElementTag`. Empty string if nothing recognised. |

**Example**

```yaml
# Collect frames with the microphone event, transcribe at end of phrase
on player microphone:
  - if <context.bytes.is_truthy>:
      - flag <player> audio:|:<context.bytes>
  - else:
      - ~vosk bytes:<player.flag[audio]||<list>> save:result
      - flag <player> audio:!
      - if <entry[result].text.is_truthy>:
          - narrate "You said: <entry[result].text>"
```

---

## Events

All events are cancellable unless noted otherwise.

### player connects to voicechat

Fires when a player connects to Simple Voice Chat.

```yaml
on player connects to voicechat:
  - narrate targets:<player> "Voice chat connected."
```

---

### player disconnects from voicechat

Fires when a player disconnects from Simple Voice Chat.

```yaml
on player disconnects from voicechat:
  - narrate targets:<player> "Voice chat disconnected."
```

---

### player changes voice state

Fires when a player connects, disconnects, joins/leaves a group, or disables voice chat.

| Context | Returns |
|---|---|
| `<context.is_disabled>` | `ElementTag(Boolean)` — player disabled voice chat on their end. |
| `<context.is_disconnected>` | `ElementTag(Boolean)` — player is not connected. |

---

### player microphone

Fires for every microphone packet sent by a player.

| Context | Returns |
|---|---|
| `<context.bytes>` | `ElementTag` — Base64-encoded Opus audio data. |
| `<context.is_whispering>` | `ElementTag(Boolean)` — whether the player is whispering. |

**Determination:** `ElementTag` (Base64) to replace the Opus bytes before they reach other players.

```yaml
on player microphone:
  - if <player.flag[vc_muted]||false>:
      - determine cancelled
```

---

### player voice distance calculated

Fires when the voice distance for a microphone packet is being processed.

| Context | Returns |
|---|---|
| `<context.distance>` | `ElementTag(Decimal)` — current voice distance. |
| `<context.is_whispering>` | `ElementTag(Boolean)` |

**Determination:** `ElementTag(Decimal)` to override the distance.

```yaml
on player voice distance calculated:
  - if <context.is_whispering>:
      - determine 4.0
```

---

### player joins voice group

Fires when a player joins any SVC group.

**Switch:** `id:<id>` — only fire for a specific managed group id.

| Context | Returns |
|---|---|
| `<context.group>` | `VoiceGroupTag` of the group being joined. |

**Determination:** `VoiceGroupTag` to redirect the player into a different group.

```yaml
on player joins voice group id:vip:
  - if <player.has_permission[voxizen.vip].not>:
      - determine <voicegroup[staff]>
```

---

### player leaves voice group

Fires when a player leaves any SVC group.

**Switch:** `id:<id>`

| Context | Returns |
|---|---|
| `<context.group>` | `VoiceGroupTag` of the group being left. |

---

### voice group created

Fires when any SVC group is created (including via `voicegroup` command).

**Switch:** `id:<id>`

| Context | Returns |
|---|---|
| `<context.group>` | `VoiceGroupTag` of the created group. |
| `<context.creator>` | `PlayerTag` of the creator, or `null` if created via command. |

---

### voice group removed

Fires when any SVC group is removed (including via `voicegroup` command).

**Switch:** `id:<id>`

| Context | Returns |
|---|---|
| `<context.group>` | `VoiceGroupTag` of the removed group. |

```yaml
# Prevent a managed group from being deleted
on voice group removed id:staff:
  - determine cancelled
```

---

## Tags

### PlayerTag extensions

| Tag | Returns | Description |
|---|---|---|
| `<PlayerTag.voice_group>` | `VoiceGroupTag` | Current voice group, or null if not in one. |
| `<PlayerTag.in_voice_group>` | `ElementTag(Boolean)` | Whether the player is in a group. |
| `<PlayerTag.voice_connected>` | `ElementTag(Boolean)` | Whether the player is actively connected. |
| `<PlayerTag.voice_installed>` | `ElementTag(Boolean)` | Whether the player has the SVC mod installed. |
| `<PlayerTag.voice_disabled>` | `ElementTag(Boolean)` | Whether the player disabled voice chat client-side. |

---

### ServerTag extensions

| Tag | Returns | Description |
|---|---|---|
| `<server.voice_groups>` | `ListTag(VoiceGroupTag)` | All groups on the server, including player-created ones. |
| `<server.voice_sources>` | `ListTag(ElementTag)` | IDs of all active managed audio sources. |
| `<server.volume_categories>` | `ListTag(ElementTag)` | IDs of all registered volume categories. |

---

### VoiceGroupTag

**Prefix:** `voicegroup`  
**Format:** `voicegroup@<id>`

```yaml
- define group <voicegroup[staff]>
```

| Tag | Returns | Description |
|---|---|---|
| `<VoiceGroupTag.id>` | `ElementTag` | The string id (managed) or UUID (unmanaged). |
| `<VoiceGroupTag.name>` | `ElementTag` | Display name. |
| `<VoiceGroupTag.type>` | `ElementTag` | `normal`, `open`, or `isolated`. |
| `<VoiceGroupTag.persistent>` | `ElementTag(Boolean)` | Whether the group is persistent. |
| `<VoiceGroupTag.password>` | `ElementTag` | Password, or null if none. |

---

### VoiceSourceTag

**Prefix:** `voicesource`  
**Format:** `voicesource@<id>`

```yaml
- define src <voicesource[radio]>
```

| Tag | Returns | Description |
|---|---|---|
| `<VoiceSourceTag.id>` | `ElementTag` | The string id of this source. |
| `<VoiceSourceTag.type>` | `ElementTag` | `locational`, `entity`, or `static`. |
| `<VoiceSourceTag.source>` | `LocationTag` / `EntityTag` | Underlying source object. Null for static channels. |
| `<VoiceSourceTag.distance>` | `ElementTag(Decimal)` | Hearing radius. Null for static channels. |
| `<VoiceSourceTag.targets>` | `ListTag(PlayerTag)` | Currently targeted online players. |
| `<VoiceSourceTag.category>` | `ElementTag` | Volume category id, or null if none set. |

---

## Mechanisms

### PlayerTag mechanisms

| Mechanism | Input | Description |
|---|---|---|
| `voice_group` | `ElementTag` (VoiceGroupTag id or `null`) | Move the player into a group, or remove them from their current one. |
| `voice_connected` | `ElementTag(Boolean)` | Set the player's connected state. Resets on reconnect. |
| `voice_disabled` | `ElementTag(Boolean)` | Set the player's disabled state. |

```yaml
# Put a player in the staff group
- adjust <player> voice_group:<voicegroup[staff]>

# Remove from any group
- adjust <player> voice_group:null

# Mute a player
- adjust <player> voice_disabled:true
```

---

### VoiceSourceTag mechanisms

| Mechanism | Input | Description |
|---|---|---|
| `targets` | `ListTag(PlayerTag)` | Replace the full target list. Rebuilds filter on locational/entity channels; adds/removes on static channels. |
| `distance` | `ElementTag(Decimal)` | Set the hearing radius (locational / entity only). |
| `category` | `ElementTag` | Set the volume category. |

```yaml
# Restrict a source to online staff
- adjust <voicesource[broadcast]> targets:<server.online_players.filter[has_permission[voxizen.staff]]>

# Widen a source's range
- adjust <voicesource[stage]> distance:48

# Change category
- adjust <voicesource[stage]> category:music
```

---

## Quick-start examples

### Radio station

```yaml
# Setup
- voicesource create id:radio source:<player.location> distance:32 category:music

# Play a file
- ~fileread path:data/radio.wav save:f
- ~audio play bytes:<entry[f].data> source:<voicesource[radio]>

# Stop
- audio stop source:<voicesource[radio]>
- voicesource delete id:radio
```

---

### Real-time microphone relay

```yaml
on player microphone:
  - if <server.voice_sources.contains[relay].not>:
      - stop
  - audio play bytes:<context.bytes> source:<voicesource[relay]>
```

---

### Staff voice channel (persistent, isolated)

```yaml
on server start:
  - voicegroup create id:staff name:Staff type:isolated persistent:true

on player joins:
  - if <player.has_permission[voxizen.staff]>:
      - adjust <player> voice_group:<voicegroup[staff]>

on player quits:
  - adjust <player> voice_group:null

# Prevent accidental deletion
on voice group removed id:staff:
  - determine cancelled
```

---

### Speech-to-text (Vosk)

```yaml
on player microphone:
  - if <context.bytes.is_truthy>:
      - flag <player> vc_frames:|:<context.bytes>
  - else:
      - define frames <player.flag[vc_frames]||<list>>
      - flag <player> vc_frames:!
      - if <[frames].is_empty>:
          - stop
      - ~vosk bytes:<[frames]> save:r
      - if <entry[r].text.is_truthy>:
          - narrate targets:<player> "You said: <entry[r].text>"
```

---

### Custom volume category with icon

```yaml
- ~fileread path:plugins/Voxizen/icons/music.png save:icon
- volumecategory create id:music name:Music icon:<entry[icon].data> description:<element[Background music volume]>

# Assign it to a source
- voicesource create id:stage source:<player.location> distance:20 category:music
```

---
