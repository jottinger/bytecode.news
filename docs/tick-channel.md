# Tick Channel

The tick channel is a 1-second heartbeat that any component can listen to.
It provides the system with a sense of time - without it, everything is purely reactive: messages in, responses out, nothing in between.

Timed behavior lives here.
Polling services check whether their interval has elapsed.
Future features like countdown games, delayed message delivery, or periodic announcements will use the same mechanism.

## Why a Channel

The tick is a Spring Integration `PublishSubscribeChannel`, not a direct callback loop.
This is deliberate - it preserves the upgrade path to AMQP in a horizontally-scaled deployment.
If we later run multiple Nevet instances behind a load balancer, the tick channel can be backed by a message broker, and listeners on each instance will receive ticks without code changes.

## How It Works

`TickService` fires once per second, publishing `Instant.now()` to the `tickChannel`.
Every bean implementing `TickListener` receives the tick.
What they do with it is their business.

```
TickService (@Scheduled, 1s)
    |
    v
tickChannel (PublishSubscribeChannel)
    |
    +---> RssFeedPollingService.onTick(now)   -- "has 5 minutes passed? poll feeds"
    +---> GitHubPollingService.onTick(now)    -- "has 60 minutes passed? poll repos"
    +---> (future) CountdownOperation.onTick  -- "decrement timer, announce if zero"
    +---> (future) TellDeliveryService.onTick -- "any pending messages past their delay?"
```

## Implementing a TickListener

Implement the interface and let Spring discover it as a bean:

```kotlin
@Service
class MyPollingService(
    private val myProperties: MyProperties,
) : TickListener {
    private var lastPollTime: Instant = Instant.EPOCH

    override fun onTick(now: Instant) {
        if (Duration.between(lastPollTime, now) >= myProperties.pollInterval) {
            lastPollTime = now
            doWork()
        }
    }

    fun doWork() {
        // your periodic logic here
    }
}
```

Key points:

- **The listener decides its own interval.** The tick is always 1 second. If you want to act every 5 minutes, track your last action time and compare.
- **Initialize `lastPollTime` to `Instant.EPOCH`** so the first tick triggers immediately rather than waiting for the full interval.
- **Keep `onTick` fast.** If your work is slow (network calls, database queries), it blocks the tick channel for other listeners. The current implementation runs listeners synchronously on the publishing thread. If this becomes a bottleneck, we can switch to an executor-backed channel later.
- **No `@Scheduled` needed.** The tick infrastructure handles scheduling. Your service is just a listener.

## Property Gate

The tick scheduler is controlled by `streampack.tick.scheduler.enabled`:

- **Defaults to `true`** - in production, ticks fire automatically.
- **Set to `false` in tests** - the `lib-test` module's `application.yml` disables it so tests are not bombarded with background ticks.

The `tickChannel` bean itself is always present regardless of the property.
Only the `TickService` (which publishes to the channel) and the `TickSchedulingConfiguration` (which enables `@EnableScheduling`) are gated.

## Testing with Ticks

Since the scheduler is off in tests, you publish ticks manually:

```kotlin
@Autowired @Qualifier("tickChannel") lateinit var tickChannel: MessageChannel

@Test
fun `polling triggers after interval elapses`() {
    // Simulate time passing
    val now = Instant.now()
    tickChannel.send(MessageBuilder.withPayload(now).build())

    // Assert your listener acted
}
```

This gives tests full control over when ticks fire and with what timestamps.
No flaky timing-dependent assertions, no `Thread.sleep()`, no waiting for background threads.

## Components

| Class | Purpose |
|-------|---------|
| `TickListener` | Interface with `onTick(now: Instant)` |
| `TickListenerWiring` | Discovers `TickListener` beans, subscribes them to `tickChannel` |
| `TickSchedulingConfiguration` | Enables `@EnableScheduling`, gated on property |
| `TickService` | Publishes `Instant.now()` every second, gated on property |

All four live in `lib-core` under `com.enigmastation.streampack.core.integration`.
The channel bean itself is defined in `EventChannelConfiguration` alongside `ingressChannel` and `egressChannel`.

## Migration from @Scheduled

RSS and GitHub polling previously used `@Scheduled` with their own `@EnableScheduling` configuration classes.
They now implement `TickListener` instead.

What changed:
- `RssFeedPollingService` and `GitHubPollingService` implement `TickListener`
- Each tracks `lastPollTime` and compares against its configured interval on each tick
- The `@Scheduled` annotation was removed from `pollAllFeeds()` / `pollAllRepos()`
- `RssSchedulingConfiguration` and `GitHubSchedulingConfiguration` were deleted

The poll interval is still read from properties (`streampack.rss.poll-interval`, `streampack.github.poll-interval`).
The only behavioral change is that polling is now driven by the tick channel rather than Spring's internal scheduler.
