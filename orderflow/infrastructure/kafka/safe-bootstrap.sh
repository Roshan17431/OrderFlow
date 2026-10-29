#!/usr/bin/env bash
# Proposed OrderFlow single-node KRaft guard for the official apache/kafka:4.3.2 image.
# Preserve metadata/storage format 3.9-IV0; broker feature finalization is forbidden.
# Keep the image's /__cacert_entrypoint.sh; override Cmd to invoke this script.
set -euo pipefail

fail() { printf 'Kafka storage guard: %s\n' "$*" >&2; exit 78; }
readonly orderflow_data_dir=/var/lib/kafka/data
readonly orderflow_meta="$orderflow_data_dir/meta.properties"
readonly orderflow_release_version=3.9-IV0

[[ ${CLUSTER_ID:-} =~ ^[A-Za-z0-9_-]{22}$ ]] || fail 'an explicit 22-character Kafka cluster ID is required'
[[ ${KAFKA_NODE_ID:-} == 1 ]] || fail 'node.id must remain 1'
[[ ${KAFKA_PROCESS_ROLES:-} == broker,controller ]] || fail 'combined broker/controller roles must remain unchanged'
[[ ${KAFKA_LOG_DIRS:-} == "$orderflow_data_dir" ]] || fail 'only the existing /var/lib/kafka/data log directory is permitted'
[[ ${KAFKA_CONTROLLER_QUORUM_VOTERS:-} == 1@kafka:29093 ]] || fail 'the existing static voter configuration is required'
[[ ${KAFKA_CONTROLLER_LISTENER_NAMES:-} == CONTROLLER ]] || fail 'controller listener name must remain CONTROLLER'
[[ ${KAFKA_LISTENERS:-} == CONTROLLER://:29093,INTERNAL://:29092 ]] || fail 'only the existing private cloud listeners are permitted'
[[ ${KAFKA_LISTENER_SECURITY_PROTOCOL_MAP:-} == CONTROLLER:PLAINTEXT,INTERNAL:PLAINTEXT ]] || fail 'listener protocol map must remain unchanged'
[[ ${KAFKA_INTER_BROKER_LISTENER_NAME:-} == INTERNAL ]] || fail 'inter-broker listener must remain INTERNAL'
[[ ${KAFKA_ADVERTISED_LISTENERS:-} == INTERNAL://kafka:29092 ]] || fail 'advertised listener must remain private'
[[ -z ${KAFKA_METADATA_LOG_DIR+x} ]] || fail 'a separate metadata log directory is forbidden'
[[ -z ${KAFKA_LOG_DIR+x} ]] || fail 'a second legacy log directory setting is forbidden'
[[ -z ${KAFKA_METADATA_VERSION+x} ]] || fail 'metadata.version is a persisted feature, not a server-property pin'
[[ ${KAFKA_UNSTABLE_FEATURE_VERSIONS_ENABLE:-false} == false ]] || fail 'unstable feature versions must remain disabled'
[[ ! -e /mnt/shared/config/server.properties ]] || fail 'mounted server.properties could override protected storage settings'
[[ -d "$orderflow_data_dir" && ! -L "$orderflow_data_dir" ]] || fail 'the expected real data directory must already be mounted'

property() {
    # Kafka-generated meta.properties uses simple unescaped key=value identity fields.
    awk -F= -v wanted="$1" '
        $1 == wanted { count++; value=substr($0, index($0,"=")+1) }
        END { if (count != 1) exit 1; print value }
    ' "$orderflow_meta"
}
verify_identity() {
    [[ -f "$orderflow_meta" && ! -L "$orderflow_meta" ]] || fail 'meta.properties must be a regular file'
    # Accept Kafka's generated identity syntax only. Java Properties also allows
    # whitespace, colon separators and escapes, which could otherwise conceal a
    # second identity from the simple key=value reader above.
    awk '
        /^[[:space:]]*(#|!|$)/ { next }
        !/^(version|cluster[.]id|node[.]id|directory[.]id)=[A-Za-z0-9_-]+$/ { exit 1 }
    ' "$orderflow_meta" || fail 'meta.properties contains noncanonical identity fields'
    [[ $(property version) == 1 ]] || fail 'only KRaft meta.properties version 1 is permitted'
    [[ $(property cluster.id) == "$CLUSTER_ID" ]] || fail 'stored cluster ID does not match the configured cluster ID'
    [[ $(property node.id) == "$KAFKA_NODE_ID" ]] || fail 'stored node ID does not match the configured node ID'
    local directory_id
    directory_id=$(property directory.id) || fail 'exactly one stored directory ID is required'
    [[ $directory_id =~ ^[A-Za-z0-9_-]{22}$ ]] || fail 'stored directory ID is invalid'
}

# Use vendor defaults/validation without calling vendor setup/format yet.
. /etc/kafka/docker/configureDefaults
. /etc/kafka/docker/configure

if [[ -e "$orderflow_meta" || -L "$orderflow_meta" ]]; then
    verify_identity
    printf 'Kafka storage guard: existing KRaft identity verified; no format command executed.\n'
else
    [[ ${ORDERFLOW_KAFKA_INITIALIZE_EMPTY:-false} == true ]] || fail 'empty first-install initialization requires ORDERFLOW_KAFKA_INITIALIZE_EMPTY=true'
    orderflow_first_entry=$(find "$orderflow_data_dir" -mindepth 1 -maxdepth 1 -print -quit) || fail 'data directory cannot be inspected'
    [[ -z "$orderflow_first_entry" ]] || fail 'data directory is nonempty but meta.properties is missing; refusing to format'
    # A minimal formatter-only config uses exactly the same identity/storage/listeners.
    # The vendor wrapper later creates the full runtime config from the existing env.
    orderflow_format_config=$(mktemp /tmp/orderflow-kafka-format.XXXXXX)
    trap 'rm -f "$orderflow_format_config"' EXIT
    cat > "$orderflow_format_config" <<EOF
node.id=$KAFKA_NODE_ID
process.roles=$KAFKA_PROCESS_ROLES
listeners=$KAFKA_LISTENERS
advertised.listeners=$KAFKA_ADVERTISED_LISTENERS
listener.security.protocol.map=$KAFKA_LISTENER_SECURITY_PROTOCOL_MAP
inter.broker.listener.name=$KAFKA_INTER_BROKER_LISTENER_NAME
controller.listener.names=$KAFKA_CONTROLLER_LISTENER_NAMES
controller.quorum.voters=$KAFKA_CONTROLLER_QUORUM_VOTERS
log.dirs=$KAFKA_LOG_DIRS
EOF
    /opt/kafka/bin/kafka-storage.sh format \
        "--cluster-id=$CLUSTER_ID" \
        --config "$orderflow_format_config" \
        --release-version "$orderflow_release_version" \
        --feature kraft.version=0
    verify_identity
    rm -f "$orderflow_format_config"
    trap - EXIT
    printf 'Kafka storage guard: empty volume initialized at metadata 3.9-IV0, static kraft.version=0.\n'
fi

# All approved storage paths are now formatted with the original identities.
# Vendor launch prepares the full config; its default format attempt must fail as
# already formatted, which launch explicitly accepts. There are no other log dirs.
# No kafka-features upgrade/downgrade/finalization is ever invoked here.
exec /etc/kafka/docker/launch
