/*
 * Synthetic-only Kafka codec probe. Run with the Kafka/client codec JARs extracted
 * from the image being measured, never with an independently resolved classpath.
 * kafka_codec_probe.py supplies the unchanged cloud filesystem/security settings.
 */
import com.github.luben.zstd.Zstd;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.xxhash.XXHashFactory;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.utils.AppInfoParser;
import org.xerial.snappy.Snappy;

public class KafkaCodecProbe {
    static final String MARKER = "ORDERFLOW_CODEC_RESULT=";
    static final Duration WAIT = Duration.ofSeconds(20);
    static final Set<String> CODECS = Set.of("none", "gzip", "lz4", "snappy", "zstd");

    public static void main(String[] args) throws Exception {
        var result = runtime();
        String operation = args.length == 0 ? "native" : args[0];
        result.put("operation", operation);
        try {
            if (operation.equals("native")) {
                result.put("lz4Factory", LZ4Factory.fastestInstance().toString());
                result.put("xxhashFactory", XXHashFactory.fastestInstance().toString());
                result.put("lz4JavaSafe", observe(() -> lz4RoundTrip(LZ4Factory.safeInstance())));
                result.put("lz4Fastest", observe(() -> lz4RoundTrip(LZ4Factory.fastestInstance())));
                result.put("lz4Native", observe(() -> lz4RoundTrip(LZ4Factory.nativeInstance())));
                result.put("snappyNative", observe(() -> {
                    byte[] input = value("snappy", 0).getBytes(StandardCharsets.UTF_8);
                    check(Arrays.equals(input, Snappy.uncompress(Snappy.compress(input))), "Snappy round trip");
                }));
                result.put("zstdNative", observe(() -> {
                    byte[] input = value("zstd", 0).getBytes(StandardCharsets.UTF_8);
                    check(Arrays.equals(input, Zstd.decompress(Zstd.compress(input), input.length)), "Zstd round trip");
                }));
            } else if (operation.equals("snapshot")) {
                check(args.length == 4, "Arguments: snapshot bootstrap topic group");
                safeTopic(args[2]);
                safeGroup(args[3]);
                snapshot(args[1], args[2], args[3], result);
            } else {
                check(args.length >= 5 && args.length <= 7,
                        "produce bootstrap topic codec count | append bootstrap topic codec count startOffset | consume[-commit] bootstrap topic codec totalCount [group [targetNextOffset]]");
                String bootstrap = args[1], topic = args[2], codec = args[3];
                int count = Integer.parseInt(args[4]);
                safeTopic(topic);
                check(CODECS.contains(codec) && count > 0 && count <= 1000, "Invalid codec/count");
                result.put("topic", topic);
                result.put("codec", codec);
                result.put("expectedRecords", count);
                if (codec.equals("lz4")) {
                    result.put("lz4Factory", LZ4Factory.fastestInstance().toString());
                    result.put("xxhashFactory", XXHashFactory.fastestInstance().toString());
                }
                if (operation.equals("produce")) {
                    check(args.length == 5, "produce creates a new isolated one-partition topic");
                    produce(bootstrap, topic, codec, count, 0, true, result);
                } else if (operation.equals("append")) {
                    check(args.length == 6, "append requires expected start offset");
                    produce(bootstrap, topic, codec, count, Integer.parseInt(args[5]), false, result);
                } else if (operation.equals("consume") || operation.equals("consume-commit")) {
                    String group = args.length > 5 ? args[5] : topic + ".consumer";
                    int target = args.length > 6 ? Integer.parseInt(args[6]) : count;
                    safeGroup(group);
                    check(target >= 0 && target <= count, "Invalid target next offset");
                    consume(bootstrap, topic, codec, count, group, target, result);
                }
                else throw new IllegalArgumentException("Unsupported operation " + operation);
            }
            result.put("status", "PASS");
        } catch (Throwable error) {
            result.put("status", "FAIL");
            result.put("error", failure(error));
            error.printStackTrace(System.err);
        }
        System.out.println(MARKER + json(result));
        if (result.get("status").equals("FAIL")) System.exit(2);
    }

    static Properties common(String bootstrap) {
        var props = new Properties();
        props.put("bootstrap.servers", bootstrap);
        props.put("request.timeout.ms", "5000");
        props.put("default.api.timeout.ms", "20000");
        props.put("client.id", "orderflow-synthetic-codec-probe");
        return props;
    }

    static void produce(String bootstrap, String topic, String codec, int count, int startOffset,
            boolean createTopic, Map<String, Object> result)
            throws Exception {
        try (var admin = AdminClient.create(common(bootstrap))) {
            check(startOffset >= 0, "Negative start offset");
            if (createTopic) {
                // Refuse a reused topic rather than altering any existing records.
                admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1)
                        .configs(Map.of("compression.type", "producer"))))
                        .all().get(20, TimeUnit.SECONDS);
            }
            var partition = new TopicPartition(topic, 0);
            long actualEnd = admin.listOffsets(Map.of(partition, OffsetSpec.latest()))
                    .all().get(20, TimeUnit.SECONDS).get(partition).offset();
            check(actualEnd == startOffset, "Refusing append at unexpected end offset " + actualEnd);
            var resource = new ConfigResource(ConfigResource.Type.TOPIC, topic);
            result.put("brokerTopicCompression", admin.describeConfigs(List.of(resource))
                    .all().get(20, TimeUnit.SECONDS).get(resource).get("compression.type").value());
        }
        var props = common(bootstrap);
        props.put("acks", "all");
        props.put("enable.idempotence", "true");
        props.put("max.in.flight.requests.per.connection", "5");
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", StringSerializer.class.getName());
        props.put("delivery.timeout.ms", "15000");
        props.put("max.block.ms", "15000");
        props.put("linger.ms", "100");
        props.put("batch.size", "262144");
        props.put("buffer.memory", "4194304");
        // The none test deliberately exercises the actual client default.
        if (!codec.equals("none")) props.put("compression.type", codec);
        result.put("compressionExplicitlySet", props.containsKey("compression.type"));
        result.put("resolvedProducerCompression", new ProducerConfig(props).getString("compression.type"));
        result.put("startOffset", startOffset);
        KafkaProducer<String, String> producer = new KafkaProducer<>(props);
        try {
            var sends = new ArrayList<Future<RecordMetadata>>();
            for (int i = startOffset; i < startOffset + count; i++) {
                sends.add(producer.send(new ProducerRecord<>(topic, 0, key(i), value(codec, i))));
            }
            var offsets = new ArrayList<Long>();
            for (var send : sends) offsets.add(send.get(20, TimeUnit.SECONDS).offset());
            check(offsets.size() == count && offsets.get(0) == startOffset
                            && offsets.get(count - 1) == startOffset + count - 1,
                    "Unexpected persisted producer offsets");
            result.put("acknowledgedOffsets", offsets);
        } finally {
            producer.close(Duration.ofSeconds(2));
        }
    }

    static Properties consumerProps(String bootstrap, String group) {
        var props = common(bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        props.put(ConsumerConfig.GROUP_PROTOCOL_CONFIG, "classic");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return props;
    }

    static void consume(String bootstrap, String topic, String codec, int count, String group,
            int target, Map<String, Object> result)
            throws Exception {
        result.put("group", group);
        result.put("groupProtocol", "classic");
        result.put("targetNextOffset", target);
        var partition = new TopicPartition(topic, 0);
        var props = consumerProps(bootstrap, group);
        var consumer = new KafkaConsumer<String, String>(props);
        try {
            Long before = committed(bootstrap, group, partition);
            result.put("committedOffsetBefore", before);
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + WAIT.toNanos();
            long expected = before == null ? 0 : before;
            check(expected <= target, "Committed offset exceeds target; no resets allowed");
            int consumed = 0;
            while (expected < target && System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(300))) {
                    check(record.partition() == 0 && record.offset() == expected, "Unexpected record offset/replayed record");
                    check(key((int) expected).equals(record.key()), "Synthetic record key mismatch");
                    check(value(codec, (int) expected).equals(record.value()), "Synthetic record payload mismatch");
                    consumed++;
                    expected++;
                    // Mirrors record acknowledgement without any business listeners.
                    consumer.commitSync(Map.of(partition, new OffsetAndMetadata(record.offset() + 1)), WAIT);
                    result.put("consumedRecords", consumed);
                    if (expected == target) break;
                }
            }
            check(expected == target, "Did not reach target next offset " + target);
        } finally {
            consumer.close(Duration.ofSeconds(2));
            try { result.put("committedOffsetAfter", committed(bootstrap, group, partition)); }
            catch (Throwable offsetError) { result.put("committedOffsetReadError", failure(offsetError)); }
        }
        check(result.get("committedOffsetAfter") instanceof Number
                && ((Number) result.get("committedOffsetAfter")).longValue() == target,
                "Broker committed offset mismatch");
        var resumed = new KafkaConsumer<String, String>(props);
        try {
            long[] initialPosition = {-1};
            resumed.subscribe(List.of(topic), new ConsumerRebalanceListener() {
                public void onPartitionsRevoked(Collection<TopicPartition> partitions) {}
                public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                    if (partitions.contains(partition)) initialPosition[0] = resumed.position(partition, WAIT);
                }
            });
            long deadline = System.nanoTime() + WAIT.toNanos();
            long pendingExpected = target;
            while (resumed.assignment().isEmpty() && System.nanoTime() < deadline) {
                for (var record : resumed.poll(Duration.ofMillis(300))) {
                    check(record.partition() == 0 && record.offset() == pendingExpected
                                    && pendingExpected < count,
                            "Committed records replayed on restart");
                    check(key((int) pendingExpected).equals(record.key())
                            && value(codec, (int) pendingExpected).equals(record.value()),
                            "Pending record payload mismatch after restart");
                    pendingExpected++;
                }
            }
            check(resumed.assignment().contains(partition), "Consumer restart assignment timed out");
            check(initialPosition[0] == target, "Restart initial position differs from committed target");
            result.put("resumedInitialPosition", initialPosition[0]);
            // A partial target deliberately leaves records pending; position may
            // advance due to polling, but the broker commit must remain target.
            if (target == count) {
                check(resumed.position(partition, WAIT) == target, "Restart did not resume at committed offset");
                check(resumed.poll(Duration.ofSeconds(1)).isEmpty(), "Duplicate records on consumer restart");
            }
            result.put("resumedPosition", resumed.position(partition, WAIT));
            result.put("restartNoCommittedReplay", true);
            if (target == count) result.put("restartNoReplay", true);
            result.put("pendingRecordsObservedWithoutCommit", pendingExpected - target);
            result.put("committedOffsetAfterRestart", committed(bootstrap, group, partition));
            check(((Number) result.get("committedOffsetAfterRestart")).longValue() == target,
                    "Restart changed committed offset without acknowledgement");
        } finally {
            resumed.close(Duration.ofSeconds(2));
        }
    }

    static Long committed(String bootstrap, String group, TopicPartition partition) throws Exception {
        try (var admin = AdminClient.create(common(bootstrap))) {
            var offsets = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata()
                    .get(20, TimeUnit.SECONDS);
            var offset = offsets.get(partition);
            return offset == null ? null : offset.offset();
        }
    }

    static void snapshot(String bootstrap, String topic, String group, Map<String, Object> result)
            throws Exception {
        result.put("topic", topic);
        result.put("groupId", group);
        try (var admin = AdminClient.create(common(bootstrap))) {
            result.put("clusterId", admin.describeCluster().clusterId().get(20, TimeUnit.SECONDS));
            var description = admin.describeTopics(List.of(topic)).allTopicNames()
                    .get(20, TimeUnit.SECONDS).get(topic);
            result.put("topicId", description.topicId().toString());
            result.put("partitionCount", description.partitions().size());
            var earliestRequest = new LinkedHashMap<TopicPartition, OffsetSpec>();
            var latestRequest = new LinkedHashMap<TopicPartition, OffsetSpec>();
            for (var partition : description.partitions()) {
                var key = new TopicPartition(topic, partition.partition());
                earliestRequest.put(key, OffsetSpec.earliest());
                latestRequest.put(key, OffsetSpec.latest());
            }
            var earliest = admin.listOffsets(earliestRequest).all().get(20, TimeUnit.SECONDS);
            var latest = admin.listOffsets(latestRequest).all().get(20, TimeUnit.SECONDS);
            var offsets = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata()
                    .get(20, TimeUnit.SECONDS);
            var partitions = new ArrayList<Map<String, Object>>();
            for (var partition : description.partitions()) {
                var key = new TopicPartition(topic, partition.partition());
                var entry = new LinkedHashMap<String, Object>();
                entry.put("partition", partition.partition());
                entry.put("leader", partition.leader().id());
                entry.put("replicas", partition.replicas().stream().map(node -> node.id()).toList());
                entry.put("isr", partition.isr().stream().map(node -> node.id()).toList());
                entry.put("logStart", earliest.get(key).offset());
                entry.put("logEnd", latest.get(key).offset());
                entry.put("committedOffset", offsets.containsKey(key) ? offsets.get(key).offset() : null);
                partitions.add(entry);
            }
            result.put("partitions", partitions);
            var resource = new ConfigResource(ConfigResource.Type.TOPIC, topic);
            var config = admin.describeConfigs(List.of(resource)).all().get(20, TimeUnit.SECONDS).get(resource);
            var stableConfigs = new LinkedHashMap<String, Object>();
            for (String name : List.of("compression.type", "cleanup.policy", "retention.ms", "retention.bytes",
                    "segment.bytes", "min.insync.replicas", "max.message.bytes", "message.timestamp.type")) {
                var entry = config.get(name);
                stableConfigs.put(name, entry == null ? null : entry.value());
            }
            result.put("topicConfigs", stableConfigs);
            var allConfigs = new LinkedHashMap<String, Object>();
            config.entries().stream().sorted(java.util.Comparator.comparing(entry -> entry.name()))
                    .forEach(entry -> allConfigs.put(entry.name(), entry.isSensitive() ? null : entry.value()));
            result.put("allTopicConfigs", allConfigs);
            var features = admin.describeFeatures().featureMetadata().get(20, TimeUnit.SECONDS);
            var finalized = new LinkedHashMap<String, Object>();
            features.finalizedFeatures().forEach((name, range) -> finalized.put(name,
                    Map.of("min", range.minVersionLevel(), "max", range.maxVersionLevel())));
            result.put("finalizedFeatures", finalized);
            result.put("finalizedFeaturesEpoch", features.finalizedFeaturesEpoch().orElse(null));
            try {
                var groupDescription = admin.describeConsumerGroups(List.of(group)).all()
                        .get(20, TimeUnit.SECONDS).get(group);
                result.put("groupState", groupDescription.groupState().toString());
                result.put("groupType", groupDescription.type().toString());
                result.put("groupAssignor", groupDescription.partitionAssignor());
                result.put("groupMemberCount", groupDescription.members().size());
            } catch (Throwable groupError) {
                // An absent group before its first commit is itself an observation.
                result.put("groupDescriptionError", failure(groupError));
            }
        }
    }

    static void safeTopic(String topic) {
        check(topic.matches("orderflow-codec-probe-[a-z0-9.-]{1,180}"), "Refusing non-probe topic");
    }

    static void safeGroup(String group) {
        check(group.matches("orderflow-codec-probe-[a-z0-9.-]{1,200}"), "Refusing non-probe group");
    }

    static String key(int i) { return "codec-key-" + i; }
    static String value(String codec, int i) {
        return "orderflow-synthetic-codec-probe|" + codec + "|" + i + "|" + "payload-0123456789abcdef".repeat(256);
    }

    static void lz4RoundTrip(LZ4Factory factory) {
        byte[] input = value("lz4", 0).getBytes(StandardCharsets.UTF_8);
        var compressor = factory.fastCompressor();
        byte[] compressed = new byte[compressor.maxCompressedLength(input.length)];
        int length = compressor.compress(input, 0, input.length, compressed, 0, compressed.length);
        byte[] output = new byte[input.length];
        int restored = factory.safeDecompressor().decompress(compressed, 0, length, output, 0, output.length);
        check(restored == input.length && Arrays.equals(input, output), "LZ4 round trip");
    }

    interface CheckedAction { void run() throws Throwable; }
    static Map<String, Object> observe(CheckedAction action) {
        var observation = new LinkedHashMap<String, Object>();
        try { action.run(); observation.put("status", "PASS"); }
        catch (Throwable error) { observation.put("status", "FAIL"); observation.put("error", failure(error)); }
        return observation;
    }

    static List<Map<String, Object>> failure(Throwable error) {
        var chain = new ArrayList<Map<String, Object>>();
        for (int i = 0; error != null && i < 12; i++, error = error.getCause()) {
            var entry = new LinkedHashMap<String, Object>();
            entry.put("class", error.getClass().getName());
            entry.put("message", error.getMessage());
            chain.add(entry);
        }
        return chain;
    }

    static Map<String, Object> runtime() throws Exception {
        var result = new LinkedHashMap<String, Object>();
        result.put("javaRuntime", System.getProperty("java.runtime.version"));
        result.put("javaVendor", System.getProperty("java.vendor"));
        result.put("osArch", System.getProperty("os.arch"));
        result.put("user", System.getProperty("user.name"));
        result.put("kafkaVersion", AppInfoParser.getVersion());
        result.put("kafkaCommitId", AppInfoParser.getCommitId());
        result.put("defaultProducerCompression", ProducerConfig.configDef().defaultValues().get("compression.type"));
        var jars = new LinkedHashMap<String, Object>();
        for (var type : List.of(KafkaProducer.class, LZ4Factory.class, Snappy.class, Zstd.class)) {
            Path path = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
            jars.put(path.getFileName().toString(), hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
        }
        result.put("jarSha256", jars);
        result.put("tmpMount", Files.readAllLines(Path.of("/proc/self/mountinfo")).stream()
                .filter(line -> line.split(" ")[4].equals("/tmp")).toList());
        result.put("processSecurity", Files.readAllLines(Path.of("/proc/self/status")).stream()
                .filter(line -> line.startsWith("Uid:") || line.startsWith("Gid:")
                        || line.startsWith("NoNewPrivs:") || line.startsWith("Seccomp:")).toList());
        return result;
    }

    static String hex(byte[] bytes) { return java.util.HexFormat.of().formatHex(bytes); }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) return "{" + String.join(",", map.entrySet().stream()
                .map(entry -> json(entry.getKey().toString()) + ":" + json(entry.getValue())).toList()) + "}";
        if (value instanceof Collection<?> collection) return "[" + String.join(",", collection.stream()
                .map(KafkaCodecProbe::json).toList()) + "]";
        String text = value.toString();
        var quoted = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            if (c == '\\' || c == '"') quoted.append('\\').append(c);
            else if (c < 32) quoted.append(String.format("\\u%04x", (int) c));
            else quoted.append(c);
        }
        return quoted.append('"').toString();
    }
}
