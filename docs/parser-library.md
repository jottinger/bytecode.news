# Parser Library Guide

This guide documents the lightweight command parser in `lib-core`:
`com.enigmastation.streampack.core.parser`.

It is intentionally small and opt-in. Operations can keep raw-string parsing where flexibility is
more important, and adopt parser patterns where structure and validation matter.

## Why This Exists

Many operations need the same primitives:

- optional trigger handling (`!`)
- whitespace normalization outside quotes
- quoted argument support
- command + argument validation
- usable error states (missing args vs invalid arg vs too many args)

The parser library centralizes those behaviors.

## Components

- `CommandLexer`
  - converts raw input into `LexedInput(raw, triggered, tokens)`
  - strips leading `!` from token stream while preserving `triggered=true`
  - keeps quoted text as one token
- `CommandPattern`
  - defines command literal prefix + typed positional args
- `CommandArgType<T>`
  - validates and converts one token to a typed value
- built-in arg types:
  - `StringArgType`
  - `UsernameArgType`
  - `ChoiceArgType`
  - `PositiveIntArgType`
  - `IntRangeArgType`
- `CommandPatternMatcher`
  - matches a raw input against one or more patterns
  - returns `CommandMatchResult`

## Result Contract

`CommandPatternMatcher.match(...)` returns:

- `CommandMatchResult.Match`
- `CommandMatchResult.MissingArguments`
- `CommandMatchResult.InvalidArgument`
- `CommandMatchResult.TooManyArguments`
- `null` (no literal prefix matched)

This allows operations to decide whether to return user-facing guidance, or fall through.

## Typical Usage In An Operation

Use with `TranslatingOperation<T>`:

```kotlin
data class ExampleRequest(val username: String, val content: String)

private val matcher =
    CommandPatternMatcher(
        listOf(
            CommandPattern(
                name = "do_that",
                literals = listOf("do", "that"),
                args = listOf(
                    CommandArgSpec("username", UsernameArgType),
                    CommandArgSpec("content", StringArgType),
                ),
            )
        )
    )

override fun translate(payload: String, message: Message<*>): ExampleRequest? {
    return when (val result = matcher.match(payload)) {
        is CommandMatchResult.Match ->
            ExampleRequest(
                username = result.captures["username"] as String,
                content = result.captures["content"] as String,
            )
        else -> null
    }
}
```

## Choice Validation Example

`ChoiceArgType` is useful when valid options are dynamic.

`operation-cal` uses this pattern to validate `today [list|calendar]` and
`tomorrow [list|calendar]` against currently registered calendar names.

## Design Notes

- This parser is not a full BNF/LL parser.
- It is meant for command-shaped input and clean whitespace handling.
- Operations remain decoupled; parser patterns are local to each operation.
- Raw parsing remains valid for operations that need maximal flexibility.
