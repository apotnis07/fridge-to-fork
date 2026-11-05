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
Hence we make changes to its configuration by adding the JavaTimeModule() [JavaTimeModule is a Jackson add-on module that teaches Jackson how to handle Java’s date and time types (Instant, LocalDateTime, Duration, …).] and disabling the SerializationFeature.WRITE_DATES_AS_TIMESTAMPS that would 