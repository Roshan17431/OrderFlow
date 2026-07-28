#!/bin/sh
set -eu

validate_identifier() {
  label="$1"
  identifier="$2"
  case "${identifier}" in
    ""|*[!A-Za-z0-9_]*)
      echo "${label} database identifiers may contain only letters, digits, and underscores." >&2
      exit 1
      ;;
  esac
}

ensure_database() {
  label="$1"
  database="$2"
  role="$3"
  password="$4"

  validate_identifier "${label}" "${database}"
  validate_identifier "${label}" "${role}"

  role_exists="$(psql \
    -h "${POSTGRES_HOST}" \
    -p "${POSTGRES_PORT}" \
    -U "${POSTGRES_USER}" \
    -d "${POSTGRES_DB}" \
    -Atc "SELECT 1 FROM pg_roles WHERE rolname = '${role}'")"
  escaped_password="$(printf '%s' "${password}" | sed "s/'/''/g")"

  if [ "${role_exists}" != "1" ]; then
    psql \
      -h "${POSTGRES_HOST}" \
      -p "${POSTGRES_PORT}" \
      -U "${POSTGRES_USER}" \
      -d "${POSTGRES_DB}" \
      -v ON_ERROR_STOP=1 \
      -c "CREATE ROLE \"${role}\" LOGIN PASSWORD '${escaped_password}'" \
      >/dev/null
  else
    psql \
      -h "${POSTGRES_HOST}" \
      -p "${POSTGRES_PORT}" \
      -U "${POSTGRES_USER}" \
      -d "${POSTGRES_DB}" \
      -v ON_ERROR_STOP=1 \
      -c "ALTER ROLE \"${role}\" WITH LOGIN PASSWORD '${escaped_password}'" \
      >/dev/null
  fi

  database_exists="$(psql \
    -h "${POSTGRES_HOST}" \
    -p "${POSTGRES_PORT}" \
    -U "${POSTGRES_USER}" \
    -d "${POSTGRES_DB}" \
    -Atc "SELECT 1 FROM pg_database WHERE datname = '${database}'")"

  if [ "${database_exists}" != "1" ]; then
    createdb \
      -h "${POSTGRES_HOST}" \
      -p "${POSTGRES_PORT}" \
      -U "${POSTGRES_USER}" \
      -O "${role}" \
      "${database}"
  else
    psql \
      -h "${POSTGRES_HOST}" \
      -p "${POSTGRES_PORT}" \
      -U "${POSTGRES_USER}" \
      -d "${POSTGRES_DB}" \
      -v ON_ERROR_STOP=1 \
      -c "ALTER DATABASE \"${database}\" OWNER TO \"${role}\"" \
      >/dev/null
  fi

  echo "${label} database is ready."
}

export PGPASSWORD="${POSTGRES_PASSWORD}"

until pg_isready \
    -h "${POSTGRES_HOST}" \
    -p "${POSTGRES_PORT}" \
    -U "${POSTGRES_USER}" \
    -d "${POSTGRES_DB}" >/dev/null 2>&1; do
  sleep 2
done

ensure_database \
  "Product" \
  "${PRODUCT_POSTGRES_DB}" \
  "${PRODUCT_POSTGRES_USER}" \
  "${PRODUCT_POSTGRES_PASSWORD}"
ensure_database \
  "Inventory" \
  "${INVENTORY_POSTGRES_DB}" \
  "${INVENTORY_POSTGRES_USER}" \
  "${INVENTORY_POSTGRES_PASSWORD}"
ensure_database \
  "Order" \
  "${ORDER_POSTGRES_DB}" \
  "${ORDER_POSTGRES_USER}" \
  "${ORDER_POSTGRES_PASSWORD}"

echo "All service databases are ready."
