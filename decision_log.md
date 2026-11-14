## Record vs Class
Events are immutable so they are the perfect use case for a record.

## userId only in key or in key and value
userId should be in both key and value as we may decide to change the key at some point (group by) and this might lead to losing the userId data

## key -- userId or recipeId?
When key is userId the recipes are stored in a single partition for a user as per their key (user_id) this also maintains the order of operations, first saved then deleted.

If recipeId is made key, then there wont be a way to make out which recipe was stored before the other. It would still retain chronological information about itself but not in the bigger picture.

- Search events have no recipeId. SearchPerformed isn’t about a recipe, so there’s nothing to key it by. userId works for both topics, so you use one consistent key.

- It saves work in your Streams app. You’ll count searches per user per minute. If messages are already keyed by userId, a user’s events are already in one partition and can be counted there. If they were keyed by something else, regrouping by userId makes Kafka Streams re-shuffle the data through an extra internal topic, called a repartition topic, before it can count. That’s another good interview point.




# Learning Log
With a plain new ObjectMapper() serializing an Instant either fails with an error prints a large number instead of a string in the ISO-8601 format.
Hence we make changes to its configuration by adding the JavaTimeModule() [JavaTimeModule is a Jackson add-on module that teaches Jackson how to handle Java’s date and time types (Instant, LocalDateTime, Duration, …).] and disabling the SerializationFeature.WRITE_DATES_AS_TIMESTAMPS would make it an int but we want a string (ISO-8601 format) so we disable it

Flow of Serialization:
RecipeSaved.of(...) creates a Java Object, which is not a JSON yet -> mapper.writeValueAsString(event) Serializes that object into a JSON string like so {"eventType":"RecipeSaved","schemaVersion":1,"userId":"customer-1","recipeId":"88c11bc9-1905-406b-a3c8-1e6f33ba3658","ingredients":["potatoes","milk","butter","garlic"],"timestamp":"2026-11-05T06:11:12.689155Z"}



Example with RF=3 and min.insync.replicas=2:

- 3 replicas in sync: acks=all waits for all 3, not 2.
- One broker fails, 2 in sync: it waits for those 2, and the write is still accepted.
- Two fail, 1 in sync: the write is rejected with NotEnoughReplicasException. Kafka refuses to accept data that only one broker holds.

acks=all waits for the replicas that are in the ISR right now, not for every replica that exists. In-sync is what counts, not the total number of copies.

If an acknowledgement is lost, the producer retries. With idempotence enabled, each message carries the producer’s ID and a per-partition sequence number, so the broker recognises the retry as a duplicate and drops it while still acknowledging it. Without it, the retry would be written twice.

This only covers the producer’s own retries within one session. If the app sends twice or restarts and resends, duplicates are still possible. End to end Remi is at-least-once, so consumers must tolerate duplicates.

By default, spring-kafka’s JsonSerializer adds a header to each message containing the full Java class name, e.g. com.example.fridge_to_fork.event.RecipeSaved. Consumers can use it to pick the class to deserialize into.
But, if a class is renamed or moved the consumer can no longer deserealize using the header. Hence, we set spring.kafka.producer.properties.spring.json.add.type.headers=false and use the eventType in the json.


## Todo 6: the event publisher
What it does

It’s one Spring service, e.g. EventPublisher, with a method like publish(event). Given any of your three events, it:

- chooses the topic based on the event type,
- uses the event’s userId as the Kafka key,
- sends it with KafkaTemplate without blocking,
- logs where it was written (topic, partition, offset), or the error if the send failed.

Why it’s needed
- Your events currently go nowhere. You have the broker, the topics, the event records and the producer config, but nothing connects them. This is the class that actually sends events.
- It keeps Kafka out of your business code. Without it, RecipeService and SuggestController would each need KafkaTemplate, topic names, key choices and error handling. With it, they make one call and don’t need to know Kafka exists.
- It’s the single place for everything to do with sending. The feature flag (todo 7), failure handling (todo 10) and the publish metrics (milestone 3) are all added here, once, instead of in every caller.

### Why a separate class for Publisher:

- RecipeService and SuggestController shouldn’t depend on Kafka. If you replaced Kafka with something else later, only this class would change.
- One place for the key choice, the topic routing, error logging, and later the feature flag and metrics.
- It’s easy to replace with a mock in tests.


We are not doing kafka connect (taking rows from external db and writing into topics) because when we search for a recipe nothing is being written to the db so there wont be a row in the db that would indicate search

Searches aren’t persisted, so CDC can’t capture them. For recipes, CDC would publish table rows rather than domain events and add Connect plus Debezium infrastructure. App-level publishing after commit is simpler, with the outbox + CDC as the stronger option if guaranteed delivery became necessary.


That’s a special rule in SLF4J, the logging API Spring Boot uses: if the last argument is an exception and no {} placeholder is left for it, it’s logged as the exception, with its full stack trace printed below the message.