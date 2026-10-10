# Life Stages: handoff

A Fabric mod for Minecraft Java **26.3**. Players live a whole life (Newborn to Elderly), raise children (other players or NPC babies), are looked after by an NPC Mom, grow old, and choose how to come back. This file is everything a new session needs to keep going.

- **Project folder:** `C:\dev\lifestages`. Work here, not in `C:\dev\wptournaments` (that is an unrelated project the session happens to start in).
- **Design doc (Claude Docs):** https://claude.ai/code/artifact/ef358590-8e4f-4490-b749-e35651303602. The table under "Changes from playtesting" gets one row per change, every round.
- **Texture gallery (artifact):** https://claude.ai/artifact/CxEPJzCLLQZLT6ks4VNtLJ (built by `tools/textures/build_gallery.py`, republished to the same URL).
- **Memory note:** `C:\Users\micha\.claude\projects\C--dev-wptournaments\memory\life-stages-mod.md` (decisions per round).
- **Git:** branch `main`, pushed to https://github.com/michaelrod77/lifestages. This shell has no git identity set; commit with `git -c user.name="Michael Rodriguez" -c user.email="3519552+michaelrod77@users.noreply.github.com" commit ...`. **After every update: commit, rebuild the jar, and push.** The built jar `build/libs/lifestages-0.1.0.jar` is tracked on purpose so the other test machines can `git pull` and copy it into their mods folder.

## Working with the user

- They play-test in their CurseForge modpack and send numbered lists of bugs and requests, sometimes with reference images.
- Reply in plain language: what changed, as they'd notice it in game. No class names or code in the summary.
- After each round:
  1. Run both game tests.
  2. Copy the jar to the modpack.
  3. Update the README and add rows to the design doc's playtesting table.
  4. Commit (with the rebuilt jar) and push to GitHub.
- Say what you did not see working (UI changes made after the last test run, for example).
- Ask before closing their apps. Memory on this PC is tight: if a test run fails to start, check for running `javaw` processes and free memory, then ask them to close Minecraft and CurseForge.
- The PC sometimes goes to sleep during long runs. The log time jumps by hours and the background task times out. That is not a mod bug; just rerun.

## Build, test, install

All commands run from `C:\dev\lifestages` in Git Bash. Gradle needs Java 25, so set it first:

```bash
export JAVA_HOME="/c/Program Files/Microsoft/jdk-25.0.4.101-hotspot"
```

Compile everything (fast check):

```bash
./gradlew compileJava compileClientJava compileGametestJava --console=plain -q
```

Run the automated tests (about 4-5 minutes; run in the background with a 40-minute timeout):

```bash
./gradlew runClientGameTest --console=plain > gt.log 2>&1
```

Build the jar:

```bash
./gradlew build -x test --console=plain -q
```

Install it in the user's modpack (Minecraft must be closed or restarted):

```bash
cp build/libs/lifestages-0.1.0.jar "$USERPROFILE/curseforge/minecraft/Instances/26.3/mods/"
```

- **Tests:** `src/gametest/java/dev/lifestages/test/`.
  - `LifeStagesClientTest` plays a whole singleplayer life (~20 numbered sections, from Newborn and Mom through NPC babies, the carrier and house planning).
  - `LifeStagesMultiplayerTest` runs a real dedicated server.
  - Screenshots go to `build/run/clientGameTest/screenshots/`. Look at them for any visual change.
  - Failures show as `AssertionError` with a line number in the log.
- **Test world:** superflat (ground at y = -61), daylight cycle off. To test drops or pits, build raised mounds; you can't dig.
- **Speeding up time in tests:** `LifeConfig.get().lifeLengthMultiplier` speeds up *everything*, the player included. Set it back to 1.0 after.
- **Frozen mobs:** a mob with `setNoAi(true)` doesn't run its own logic (no growing, no needs). Freeze only for screenshots.
- **Cry cooldown:** test cries must be more than 2 s apart.
- **Mom-driven steps can take a while:** she may finish a chore first. Use generous `waitFor` timeouts (60-90 s).

## Generated assets (never hand-edit the outputs)

- `python tools/textures/generate_textures.py` writes all PNGs into `src/main/resources/assets/lifestages/textures/`.
  - `pixel.py`: canvas, hue-shifted ramps.
  - `items.py`: items, blocks, particles, GUI.
  - `skins.py`: player overlay layers (swaddle, onesie, diaper, elderly hair, **baby carrier**).
  - `mom_family.py`, `outfits.py`: the six Moms, built from the user's reference skin.
  - `nursery.py`: changing table, music box, rattle, trash can.
  - `presents.py`: present blocks and the 4-slot GUI.
  - `baby_skins.py`: NPC baby, kid and teen outfits, and the spawn egg.
  - Style: HD 32x32 items, 128x128 skins, modern pastel look, no noisy dithering.
- `cd tools/data && python gen_data.py` writes models, blockstates, loot tables, recipes, advancements and tags.
- Shell heredocs break on apostrophes. Write patch scripts with the Write tool into the scratchpad and run them with Python.
- `bash tools/dev/mc.sh find <regex> | sig <class> | src <class>` looks up Minecraft and Fabric classes. A full `genSources` runs out of memory on this PC.

## Minecraft 26.3 / Fabric gotchas (learned the hard way)

- **Names are Mojang's, unobfuscated.** Loom 1.18 uses the `net.fabricmc.fabric-loom` plugin and `implementation`, not `modImplementation`.
- **Renamed APIs:**
  - `Identifier` (not ResourceLocation)
  - `GuiGraphicsExtractor` with `extractBackground` / `extractRenderState`
  - `mc.gui.screen()`, `mc.setScreenAndShow(...)`
  - `sendOverlayMessage`
  - `spawnAtLocation`
  - `EntityTypes.ZOMBIE` (types live in `EntityTypes`)
- **Fabric `ItemEvents.USE` / `USE_ON` must return `null` for "no opinion".** Returning `PASS` replaces the item's own use; that once broke fishing rods and bows for everyone. The older callbacks (`UseBlockCallback`, `UseEntityCallback`, `AttackEntityCallback`) still use `PASS`.
- **Loot tables:** an entry takes a single `"modifier": {"type": ...}` (not `"functions"`), and a pool takes `"condition": {"type": ...}`. The old keys are silently ignored.
- **Key codes are HID usage ids** (G = 10, O = 18, Z = 29). Vanilla 26.3 binds **G = Quick Actions** and **O = Friends**. Our emote wheel is on **Z** (`key.lifestages.emotes`) and "ask to be family" on **U** (`key.lifestages.family`). A shared key fires both actions and vanilla's screen wins. Ids were renamed so old saved bindings don't carry over.
- **Data attachments are copied after `AFTER_RESPAWN`,** so set respawn state in `AFTER_DEATH`.
- **Riding:** `startRiding(vehicle, force = true, ...)` skips `canAddPassenger`, so carrier capacity is enforced in `Carrying.canHold`. Players can be ridden thanks to `EntityMixin`.
- **A player's own client is never told about that player's riders.** Passenger updates go only to entities' *trackers*, and a player doesn't track themselves. `EntityMixin` sends `ClientboundSetPassengersPacket` to a carrying `ServerPlayer` on add/remove passenger. Without it, the carrier's screen showed the babies standing where they were spawned.
- **Rendering:** Moms and NPC babies use `HumanoidRenderState` (an avatar render state gets redrawn by the player renderer). Model parts are reset every frame by `Model.setupAnim → resetPose`.
- **Mob AI:** `TemptGoal` needs `Attributes.TEMPT_RANGE` on the mob. `Mob.targetSelector` is reached through `MobAccessor`.
- **Mom in water:** bobbing at the surface makes `isInWater()` flicker, so "in the water" logic waits for `onGround()`.
- **Changes from earlier rounds:**
  - Mom-built houses have a blueprint version (`HouseBuilder.VERSION`, now 3); older houses get remodeled.
  - Stage lengths are `Stage.minMinutes`, now the original values: a full life is about 2 hours with goals done.

## Code map (`src/main/java/dev/lifestages`)

- **`life/`:** the core systems.
  - `Stage` (the 11 stages) and `LifeData` (per-player synced attachment; codecs grouped at 16 fields or fewer). `Life` handles access.
  - `LifeManager` (the server tick: aging, naps, old age).
  - `Choices` (Adult/Baby/Elderly/Ghost, Mom pick, `lookingForBaby` defaults to on). A baby connection places a `StarterChest` (up to 4).
  - `Feeding` (only newborns are milk-only).
  - `Carrying` (the baby carrier; two newborns or infants at once).
  - `Family`, `Emotes`, `Bodily`, `Diapers`, `Cribs`, `Birthdays` (presents; Adult gets 2 Baby Spawn Eggs), `Growth`, `Ghosts`, `WillChest`.
  - `Moms` (assign, spawn, house planning that avoids other players' houses).
  - `HouseBuilder` (the 13x7 cottage blueprint with a nursery; site choice keeps 4+ blocks from other houses).
  - `DropFixer` (fills or fences drops near the house).
- **`entity/`:**
  - `MomEntity`: the NPC Mom AI. Order inside `customServerAiStep`: self-safety → teen check → parent near → threats → cry → changing table → nap → feeding and diapers → nap start → tidy → fix drops → playtime → carry/home.
  - `BabyEntity`: NPC child from egg to Young Adult; needs, care-based growth, sugar rush, rescue instead of death.
  - `ThrownDiaper`.
- **`block/`:** crib, changing table, music box (Twinkle Twinkle), trash can, presents (4-slot containers, `BabySafe`), teddy bear (`BabySafe`), Will Chest, gravestone, mess. `Nursery` is the per-dimension registry of tables, trash cans and playing music boxes.
- **`registry/`:** `Mod*` classes for items, blocks, entities, menus, sounds, particles, effects, components, advancements and damage.
- **`net/`:** `Payloads` (`Choose` has `momLook`; `Act` actions include `SEARCH_PARENTS`) and `Net`.
- **Client (`src/client/java/dev/lifestages/client`):**
  - `Hud`: stage, growth bar, naps and sugar at top center; the NPC baby panel at top left; care tips under the crosshair.
  - `Keys`.
  - Screens: choice (with "Search for parents"), Mom picker, emote wheel, journal, present.
  - Renderers: Mom, NPC baby, player overlay (including the carrier).
  - `PlayerModelMixin`: baby proportions.

## State of the last round (round 17)

| Change | Status |
|---|---|
| The Adult birthday present holds two Baby Spawn Eggs (slots 1-2), the other two slots empty | Done, tested |
| Hatching an egg opens "Meet your baby" (`BabyDesignScreen`): edit the suggested name (max 24 characters, Random button) and pick one of four looks; Done or closing the screen applies it (`NameBaby` payload, parent only) | Done, tested (screenshot `08d0_meet_your_baby`) |

## State of the previous round (round 16)

| Change | Status |
|---|---|
| Right-clicking a crib with a baby in it picks the baby up (the crib's rails catch the click before the baby) | Done, tested (NPC baby) |
| Starter chest includes a changing table. A diaper changed on the changing table stays clean twice as long (6 feeds or 12 minutes instead of 3 or 6) | Done, tested |
| NPC babies can't hop out of cribs: newborns and infants can't jump, toddlers hop about half a block (the rail is 3/4 of a block) | Done, tested (jump strength values) |
| Sugar crash: when a rush wears off, NPC babies are very tired, very slow (-65%) and cranky, and only nap when carried or in a crib; player children get Slowness II and Nausea. Ends when they sleep, 2 minutes at most | Done, tested |

## State of the previous round (round 15)

| Change | Status |
|---|---|
| Carried babies hide their name tag in first person | Done, not screenshot-checked (the name is absent) |
| Journal button is "Looking for new parent". The next teenager or grown-up who picks that child up becomes their parent, and anyone old enough can always pick a child up | Done, self-adopt guard tested; two-player pickup not covered by the automated test |

## State of the previous round (round 14)

| Change | Status |
|---|---|
| Growth bar shows 1, 2, or 3 arrows while goals are speeding the stage up, and none at the normal pace | Done, tested |

## State of the previous round (round 13)

| Change | Status |
|---|---|
| Your own baby: empty-hand right-click picks them up (sneak is only for a child who isn't yours) | Done, tested |
| Right-click a changing table while carrying a baby to put them on it | Done, tested |
| Adults no longer start with nursery items. Each baby connection places a chest: 1 crib, 32 diapers, 1 rattle, 64 milk bottles, 1 trash can, 1 music box. Up to 4 chests | Done, tested |

## State of the previous round (round 12)

| Change | Status |
|---|---|
| New Adults have "looking for a baby" on; a parent stays listed until they have the maximum number of children (4) | Done, tested |
| Baby carrier: babies ride on the chest facing out, drawn as a carrier on the grown-up (and on Mom). Carriers keep both hands (use, attack, place, break); carried babies can't use blocks, attack or break. Carrier speed is still -10%. Carried NPC babies look ahead, not up at the carrier | Done, screenshot test (`15_baby_carrier_twins`) |
| Two newborns/infants per carrier, otherwise one child; V puts NPC babies down too | Done, tested |
| Choice screen: "Search for parents" when nobody is listed; the list also refreshes live every ~2 s | Done, not covered by an automated test (needs two players) |
| Houses: a new Mom's house keeps clear of other players' houses (online players' saved origins, plus avoiding sites with other buildings) | Done, tested via `HouseBuilder.plan` |
| How to look after a baby: tips under the crosshair when looking at a baby, a status line at the top while carrying, instructions in chat when becoming a parent | Done; carrier line seen in a screenshot, look-at tips not screenshot-checked |
| Bug found while testing: the carrying player's own screen didn't show babies in their carrier | Fixed (rider sync, see gotchas) |

## Open ideas and known gaps

- Multiplayer has been tested by the user over LAN (2 PCs plus a Mac). The Mac couldn't connect, most likely the macOS "Local Network" permission; it wasn't resolved in-session.
- Carrying a *player* baby into a crib isn't implemented (NPC babies only). Player babies get into cribs themselves.
- Newborn NPC babies are drawn upright (no crawling pose).
- The carrier texture is a jacket-layer overlay. A 3D carrier model would look better.
- CurseForge publishing waits until the user approves the finished mod. License is still `All-Rights-Reserved` pending their choice.
- A dedicated server setup guide (beyond the README) was offered but not written.
