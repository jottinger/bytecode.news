# Poetry Operations

Poetry operations demonstrate the loopback mechanism in Nevet's operation chain.
Normally, operation output goes to the egress channel for delivery to protocol adapters, and that's the end of the line - the bot never processes its own output.
Loopback changes that: an operation can flag its result for re-injection into the inbound chain, where other operations can act on it.

Poetry proves the concept with a two-stage pipeline:
a poem generator emits a poem, the loopback sends it back through the chain, and a separate analysis operation evaluates it.
Both operations are independently invokable by humans too.

These operations require the AI subsystem to be enabled.
See [AI Configuration](#ai-configuration) below.

## Generate a Poem

Creates a poem about a topic and loops the result back for analysis.
This operation is **addressed**.

**Syntax:**
```
poem <topic>
```

**Priority:** 65.

**Behavior:**

- Parses `"poem <topic>"` from the message text (case-insensitive).
- Sends the topic to the AI model with a system prompt requesting a short poem.
- Formats the output as a single line with verses separated by slashes, prefixed with `"a poem:"`.
- Returns the result with `loopback = true`, so it re-enters the operation chain as an addressed message.

**Response example:**
```
a poem: roses are red,/violets are blue,/some poems rhyme,/and some don't.
```

Because loopback is enabled, this response is delivered to the channel AND sent back through the operation chain, where the analysis operation picks it up.

## Analyze a Poem

Analyzes poem text for meter, rhyme scheme, and poetic form.
This operation is **addressed**.

**Syntax:**

No explicit command - it triggers on the `"a poem:"` prefix that poem generation produces.
It can also be triggered manually:
```
a poem: shall I compare thee to a summer's day?/thou art more lovely and more temperate
```

Or by sending a typed `PoemAnalysisRequest` payload directly (for programmatic use).

**Priority:** 20 (high priority so it catches loopback messages before the factoid catch-all).

**Behavior:**

- Detects messages starting with `"a poem:"` (case-insensitive).
- Strips the prefix and converts slash-separated verses back to newlines for proper analysis.
- Sends the reconstructed poem text to the AI model with a system prompt requesting concise meter, rhyme scheme, and form identification.
- Returns the analysis as a plain result with no loopback (terminal - the chain ends here).

**Response example:**
```
Analysis: This poem uses AABB rhyme scheme with iambic tetrameter.
```

## The Loopback Flow

Here is what happens end-to-end when a user says `poem roses`:

1. **PoemOperation** matches `"poem roses"`, sends `"roses"` to the AI, gets a multi-line poem back.
2. PoemOperation formats the poem as `"a poem: roses are red,/violets are blue,/..."` and returns `Success(formatted, loopback = true)`.
3. **OperationService** sends the result to the egress channel (the user sees the poem).
4. Because `loopback = true`, OperationService re-injects the formatted text as a new addressed message with an incremented hop count.
5. **PoemAnalysisOperation** (priority 20, runs early) sees `"a poem: ..."`, converts slashes back to newlines, sends the poem to the AI for analysis.
6. PoemAnalysisOperation returns the analysis as a normal `Success` (no loopback), which goes to egress.
7. The user sees the analysis in the same channel.

The hop count guard (default max 3) prevents infinite loopback chains.

## AI Configuration

Poetry operations depend on `lib-ai`, which provides a provider-agnostic `AiService` wrapper around Spring AI's `ChatModel` interface.
The concrete AI provider is configured in the `app` module.

### Getting an Anthropic API Key

1. Go to [console.anthropic.com](https://console.anthropic.com/) and create an account (or sign in).
2. Navigate to **API Keys** in the left sidebar.
3. Click **Create Key**, give it a name, and copy the key.

### Configuring the Key

Add these to your `.env` file in the project root:

```
AI_ENABLED=true
ANTHROPIC_API_KEY=sk-ant-api03-your-key-here
```

`AI_ENABLED` controls whether the AI subsystem activates at all.
`ANTHROPIC_API_KEY` provides the credentials for the Anthropic provider.
Both must be set for poetry operations to work.
If `AI_ENABLED=true` but the key is blank, a warning is logged and AI services remain disabled.

### Running the Live Tests

The live tests actually call the Anthropic API and cost real money (small amounts - a few cents per run).
They are gated behind a system property:

```bash
./mvnw test -pl operation-poetry -am -Dlive.tests=true
```

Make sure your `.env` file has both `AI_ENABLED=true` and a valid `ANTHROPIC_API_KEY` before running.

### Configuration Reference

| Property | Env Variable | Default | Description |
|----------|-------------|---------|-------------|
| `streampack.ai.enabled` | `AI_ENABLED` | `false` | Master switch for AI services |
| `streampack.ai.api-key` | `ANTHROPIC_API_KEY` | (empty) | Anthropic API key |
| `streampack.ai.model` | - | `claude-sonnet-4-5-20250929` | Model ID to use |
| `streampack.ai.max-tokens` | - | `1024` | Maximum tokens per response |

### Swapping Providers

The `lib-ai` module depends only on `spring-ai-model` (the abstract `ChatModel` interface).
It has no knowledge of Anthropic or any other provider.
The Anthropic-specific configuration lives in `app/src/main/kotlin/.../config/AnthropicConfiguration.kt`.

To use a different provider (OpenAI, Ollama, etc.):
1. Replace the `spring-ai-anthropic` dependency in `app/pom.xml` with the desired provider.
2. Replace `AnthropicConfiguration` with a configuration class that creates the appropriate `ChatModel` bean.
3. Update `.env` with the new provider's credentials.

No changes to `lib-ai` or `operation-poetry` are needed.
