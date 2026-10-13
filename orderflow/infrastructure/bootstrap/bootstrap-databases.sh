#!/bin/sh
# Runs in postgres:16-alpine, against a private server. No password on argv.
set -eu
export PGPASSWORD="${POSTGRES_PASSWORD:?postgres administrator password required}"
export PGCONNECT_TIMEOUT=5
export PGHOST="${POSTGRES_HOST:-postgres}" PGPORT="${POSTGRES_PORT:-5432}"
export PGUSER="${POSTGRES_USER:?postgres administrator role required}" PGDATABASE="${POSTGRES_DB:-postgres}"
for prefix in AUTH PRODUCT INVENTORY ORDER PAYMENT DELIVERY NOTIFICATION OPERATIONS; do
  eval "database=\${${prefix}_POSTGRES_DB:?required} role=\${${prefix}_POSTGRES_USER:?required} password=\${${prefix}_POSTGRES_PASSWORD:?required}"
  case "$database:$role" in *[!A-Za-z0-9_:]*|:*|*:) echo 'Invalid database/role identifier' >&2; exit 1;; esac
  [ "$role" != "$PGUSER" ] && [ "$database" != "$PGDATABASE" ] || { echo 'App owner must be separate from admin' >&2; exit 1; }
done
# Fail before mutations if owners or database names are duplicated.
roles=''; databases=''
for prefix in AUTH PRODUCT INVENTORY ORDER PAYMENT DELIVERY NOTIFICATION OPERATIONS; do
  eval "database=\${${prefix}_POSTGRES_DB} role=\${${prefix}_POSTGRES_USER}"
  case " $roles " in *" $role "*) echo 'Duplicate owner' >&2; exit 1;; esac
  case " $databases " in *" $database "*) echo 'Duplicate database' >&2; exit 1;; esac
  roles="$roles $role"; databases="$databases $database"
done
attempt=0
until pg_isready >/dev/null 2>&1; do
  attempt=$((attempt + 1)); [ "$attempt" -lt 30 ] || { echo 'Postgres readiness timed out' >&2; exit 1; }
  sleep 2
done
for prefix in AUTH PRODUCT INVENTORY ORDER PAYMENT DELIVERY NOTIFICATION OPERATIONS; do
  eval "database=\${${prefix}_POSTGRES_DB} role=\${${prefix}_POSTGRES_USER} password=\${${prefix}_POSTGRES_PASSWORD}"
  # SQL generated on stdin by psql quoted variables. Never interpolate passwords.
  export ORDERFLOW_DATABASE_PASSWORD="$password"
  psql -X -q -v ON_ERROR_STOP=1 -v db="$database" -v owner="$role" <<'SQL'
-- Existing databases with a different owner require explicit migration; never take ownership silently.
SELECT EXISTS (SELECT FROM pg_database d JOIN pg_roles r ON d.datdba=r.oid WHERE d.datname=:'db' AND r.rolname<>:'owner') AS wrong_owner \gset
\if :wrong_owner
\echo 'Existing database owner mismatch; operator migration required'
\quit 1
\endif
\getenv pass ORDERFLOW_DATABASE_PASSWORD
SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD %L', :'owner', :'pass')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'owner') \gexec
SELECT format('ALTER ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD %L', :'owner', :'pass') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'db', :'owner')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname=:'db') \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'db') \gexec
SQL
  PGDATABASE="$database" psql -X -q -v ON_ERROR_STOP=1 -v owner="$role" <<'SQL'
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
SELECT format('ALTER SCHEMA public OWNER TO %I', :'owner') \gexec
SQL
  printf '%s database ready\n' "$prefix"
done
