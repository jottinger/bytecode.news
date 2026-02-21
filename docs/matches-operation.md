# 21 Matches Operation

A simple take-turns game that serves as the first consumer of the per-provenance state store.
Players remove 1-3 matches from a pile of 21 each turn; whoever is forced to take the last match loses.
The bot uses the mathematically unbeatable `(4 - N)` strategy, so it always wins.

The real purpose of this operation is proving the state lifecycle: create on game start, update on each turn, delete on game over or concede.

## Module

`operation-21`, package `com.enigmastation.streampack.matches.operation`.

`TypedOperation<String>`, operation group `21-matches`, **addressed**.
No autoconfiguration class - uses `@Component` for discovery.

## Commands

All commands are prefixed with `21`.

### Start a Game

```
21 matches
```

Creates a new game with 21 matches.
Returns an error if a game is already active in the current provenance (channel or DM).

### Take Matches

```
21 take <1-3>
```

Player takes 1, 2, or 3 matches from the pile.
The bot immediately responds by taking `4 - N` matches, keeping the pile on the 21 - 17 - 13 - 9 - 5 - 1 track.
When the pile reaches 1, the bot declares victory and clears the game state.

Errors on: no active game, number out of range, non-numeric input, or trying to take more than remaining.

### Concede

```
21 concede
```

Forfeits the current game and clears the state.
Returns a graceful message if no game is active.

### Unknown Commands

Any other text after `21 ` returns a usage hint.

## State Management

Game state is stored via `ProvenanceStateService` with key `"21-matches"` and data `{ "remaining": N }`.
State is scoped to the provenance URI, so independent games can run simultaneously in different channels or DMs.

Lifecycle:
- **Created** when `21 matches` starts a new game.
- **Updated** after each `21 take` turn.
- **Deleted** when the bot wins or the player concedes.

## Flavor Text

Each bot turn includes randomly selected flavor text to keep things interesting:

- **Reaction** - a comment on the player's move (e.g., "Excellent choice!", "How very predictable.", "The plot thickens.")
- **Consideration** - a preamble to the bot's take (e.g., "After careful consideration", "With surgical precision", "After pretending to think about it")
- **Victory line** - declared when the bot wins (e.g., "The math was never in your favor. The last match is yours.")
- **Concede line** - when the player forfeits (e.g., "A graceful surrender. The board is cleared.")

The tone is playful and slightly conniving, never insulting.

## Example Session

```
<user>    !21 matches
<bot>     A new game of 21 Matches begins! There are 21 matches on the table.
          Take 1, 2, or 3 matches each turn. Whoever is forced to take the
          last match loses. Use '21 take <1-3>' to play.
<user>    !21 take 3
<bot>     Playing it safe, I see. After careful consideration, I take 1.
          There are 17 matches remaining.
<user>    !21 take 2
<bot>     I see what you're doing there. Without a hint of doubt, I take 2.
          There are 13 matches remaining.
<user>    !21 take 1
<bot>     Oh, I was hoping you'd do that. Decisively, I take 3.
          There are 9 matches remaining.
<user>    !21 take 1
<bot>     Interesting move. Having consulted my vast intellect, I take 3.
          There are 5 matches remaining.
<user>    !21 take 2
<bot>     The plot thickens. Reluctantly, but inevitably, I take 2.
          The final match awaits you. I believe that makes me the winner.
```

## Why the Bot Always Wins

From 21, the bot maintains the invariant that after each full round (player + bot), exactly 4 matches are removed.
The sequence 21 - 17 - 13 - 9 - 5 - 1 is forced regardless of what the player takes.
Since 21 mod 4 = 1, the first player is always in the losing position.
There is no player-wins code path; if one existed, the code would be wrong.

## Testing

```bash
./mvnw test -pl operation-21
```

Integration tests cover: game start, duplicate start, valid/invalid takes, boundary errors, take with no game, concede with/without game, full playthrough to bot victory, and unknown subcommands.
