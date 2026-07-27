#!/bin/sh
set -eu

for identifier in "${PRODUCT_POSTGRES_USER}" "${PRODUCT_POSTGRES_DB}"; do
  case "${identifier}" in
    ""|*[!A-Za-z0-9_]*)
      echo "Product database identifiers may contain only letters, digits, and underscores." >&2
      exit 1
      ;;
  esac
done

export PGPASSWORD="${POSTGRES_PASSWORD}"

until pg_isready \
    -h "${POSTGRES_HOST}" \
    -p "${POSTGRES_PORT}" \
    -U "${POSTGRES_USER}" \
    -d "${POSTGRES_DB}" >/dev/null 2>&1; do
  sleep 2
done

role_exists="$(psql \
  -h "${POSTGRES_HOST}" \
  -p "${POSTGRES_PORT}" \
  -U "${POSTGRES_USER}" \
  -d "${POSTGRES_DB}" \
  -Atc "SELECT 1 FROM pg_roles WHERE rolname = '${PRODUCT_POSTGRES_USER}'")"
escaped_password="$(printf '%s' "${PRODUCT_POSTGRES_PASSWORD}" | sed "s/'/''/g")"

if [ "${role_exists}" != "1" ]; then
  psql \
    -h "${POSTGRES_HOST}" \
    -p "${POSTGRES_PORT}" \
    -U "${POSTGRES_USER}" \
    -d "${POSTGRES_DB}" \
    -v ON_ERROR_STOP=1 \
    -c "CREATE ROLE \"${PRODUCT_POSTGRES_USER}\" LOGIN PASSWORD '${escaped_password}'" \
    >/dev/null
else
  psql \
    -h "${POSTGRES_HOST}" \
    -p "${POSTGRES_PORT}" \
    -U "${POSTGRES_USER}" \
    -d "${POSTGRES_DB}" \
    -v ON_ERROR_STOP=1 \
    -c "ALTER ROLE \"${PRODUCT_POSTGRES_USER}\" WITH LOGIN PASSWORD '${escaped_password}'" \
    >/dev/null
fi

database_exists="$(psql \
  -h "${POSTGRES_HOST}" \
  -p "${POSTGRES_PORT}" \
  -U "${POSTGRES_USER}" \
  -d "${POSTGRES_DB}" \
  -Atc "SELECT 1 FROM pg_database WHERE datname = '${PRODUCT_POSTGRES_DB}'")"

if [ "${database_exists}" != "1" ]; then
  createdb \
    -h "${POSTGRES_HOST}" \
    -p "${POSTGRES_PORT}" \
    -U "${POSTGRES_USER}" \
    -O "${PRODUCT_POSTGRES_USER}" \
    "${PRODUCT_POSTGRES_DB}"
fi

echo "Product database is ready."
