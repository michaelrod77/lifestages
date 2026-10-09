# Life Stages

A Fabric mod for Minecraft Java 26.3. Every player lives a whole life: start as a baby or an adult, grow through 11 stages by eating, drinking milk and sleeping, raise children, grow old, and choose how to come back. The bar under your stage shows how far along you are. While those activities are speeding the stage up, one, two, or three arrows sit on the bar. They leave when the boost does.

## Requirements

- Minecraft Java Edition 26.3
- Fabric Loader 0.19.5 or newer
- Fabric API 0.161.0+26.3 or newer
- Java 25 (only needed for a dedicated server; the Minecraft Launcher brings its own Java for players)

## Installing

### Players

1. Install the Fabric Loader for 26.3 with the [Fabric installer](https://fabricmc.net/use/installer/).
2. Put `fabric-api-<version>.jar` and `lifestages-<version>.jar` in `.minecraft/mods/`.
3. Start the game with the Fabric profile and join the server.

### Dedicated server

1. Install Java 25.
2. Download the Fabric server launcher for 26.3 from the [Fabric server page](https://fabricmc.net/use/server/) and start it once.
3. Put the same two jars in the server's `mods/` folder and start it again.
4. Optional: edit `config/lifestages.json`, then run `/lifestages reload`.

Every player needs the mod; the server refuses players without it.

## Controls

| Key | Action |
| --- | --- |
| Z (hold) | Emote wheel: cry, fart, be cute, poop (Newborn to Preschooler). Release in the middle to repeat the last emote, so tapping Z cries again. |
| J | Growth Journal: stage, age, goals, family, settings |
| U | Ask the player you're looking at to be family |
| Right-click (empty hand) | Pick up your own baby or child, or a child who turned on "Looking for new parent" (that pickup makes you their parent). Sneak + right-click picks up any other child. Two newborns or infants fit at once, older children one at a time. Your hands stay free; the baby can't reach anything. Right-click a changing table while carrying them to put them on it, or a crib to lay an NPC baby down. |
| V | Put down the baby in your carrier (or, as a toddler, ask to get down) |
| Jump x6 | Wriggle free when carried (Toddler and older) |
| B / K / R | Ghost: Boo, flicker a lamp, be reborn |

## Starting a life

On first join you choose Adult (you start as a Young Adult, age 18, listed as looking for a baby so new babies can pick you as their parent) or Baby. Each time a baby connects with you, a starter chest appears beside you with a crib, 32 diapers, a rattle, 64 milk bottles, a trash can and a music box. You can look after up to 4 babies, so you get up to 4 of those chests. A Baby can pick a parent from the grown-ups looking for a baby ("Search for parents" checks again if nobody is listed yet), and picks which of the six Moms will look after them, or "Surprise me". Each player's Mom builds her house clear of everyone else's. A child who already has a Mom can open the Journal (J) and turn on "Looking for new parent". The next teenager or grown-up who picks them up becomes their parent, and Mom steps back while that parent is nearby. Anyone old enough can always pick a child up.

## Raising an NPC baby

When you reach Adult, your birthday present holds four Baby Spawn Eggs. Use one on the ground to have a baby (use as many as you like). Your baby is curious: it crawls (later toddles) around its home, off to animals, flowers and anything you drop. The top-left of your screen shows each nearby baby's food, rest and diaper.

- Feed it: a newborn only drinks milk; after that it eats just about anything. Anything sugary gives a sugar rush, and it runs off, sometimes toward water, drops or monsters.
- Change it with a clean diaper when it's dirty.
- Let it sleep: a tired baby naps where it is, or right-click with an empty hand to pick it up and right-click a crib to lay it down (cribs rest it twice as fast). Right-click a changing table while carrying them to put them on it.
- Cuddle someone else's baby (empty hand) or play with a rattle or teddy bear to stop the crying. Your own baby is picked up instead.
- Keep it safe: monsters go after babies, and babies can fall or drown. A baby is never lost: if it would have been badly hurt, it's rushed home and you hear about the close call.

It grows up through every stage to a Young Adult, each stage as long as a player's. Care sets the pace: fed, clean (while in diapers), rested and loved (cuddles, play, carrying) each count; all four make it grow twice as fast, poor care slows it, and neglect stops it until you put things right. The panel shows the care level and a bar to the next stage. Preschoolers are out of diapers; children stay up all day and go home to sleep at night; teenagers are too big to carry. At Young Adult they're all grown up: no more needs, and they give you a thank-you present.

## Birthdays

Every stage-up gives a wrapped present. Place it and right-click to open it like a small chest with four slots; the gift is inside (the first one holds a Teddy Bear). Babies can place, open and pick up presents and their Teddy Bear. Break a present and it drops with its contents, so you can wrap things up and give them to someone else.

## Nursery

The NPC Mom's cottage has a nursery with a crib, a changing table, a trash can, a barrel of diapers and milk bottles, and a music box. Crying makes her come running: she picks you up and changes, feeds or naps you, in that order. She uses any changing table within 40 blocks. The music box plays a lullaby; naps near it go faster. Right-click a trash can with a dirty diaper to throw it away (sneak to throw away anything else), or throw a diaper into it. Mom takes dirty diapers to the trash can within 40 blocks, including any she finds lying around. Mom keeps you safe: she picks you up near monsters, fire, lava, water or powder snow, fights a lone zombie with you in her arms but runs from creepers and archers, pats out flames, gets you out of deep water, and at night brings you inside and shuts the door. In her hands the frying pan finishes any mob in one bonk. By day she fills in (or fences off) drops of more than 3 blocks within 10 blocks of the house. She tells you why when she won't put you down. In free time she takes you to the yard on a calm day, or to the nursery, and plays with your teddy bear or a rattle.

Recipes: Changing Table (3 white wool over planks-chest-planks over 3 planks), Music Box (gold nugget over planks-note block-planks), Rattle (pink dye and a stick, diagonal), Trash Can (cauldron, iron trapdoor and lime dye, any shape).

## Commands

- `/family invite|accept|decline|leave <player>|list`
- `/lifestages info [player]`
- Operators: `/lifestages stage <player> <stage>`, `/lifestages age <player> add <minutes>`, `/lifestages goals <player> complete`, `/lifestages mom summon|dismiss <player>`, `/lifestages reload`

## Building

```
gradlew build
```

The jar is written to `build/libs/`. Textures are generated by `tools/textures/generate_textures.py`; JSON data by `tools/data/gen_data.py`. `gradlew runClientGameTest` plays through a life automatically and saves screenshots to `run/screenshots/`.
