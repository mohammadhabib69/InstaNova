#!/bin/bash
# Prompt J — Supabase SQL helper.
#
# The project's direct connection host (db.<ref>.supabase.co) is IPv6-only in
# this project, so psql goes through the transaction/session pooler instead.
# Region confirmed by probing: ap-northeast-1.
#
# Credentials are read from the environment so nothing is committed:
#   PGPASSWORD   database password
# Usage: ./scripts/p4_db.sh "select 1"
set -euo pipefail

export PATH=/opt/homebrew/opt/libpq/bin:$PATH

REF="${SUPABASE_REF:-poqdemxiuoocbzkwbyhy}"
REGION="${SUPABASE_REGION:-ap-northeast-1}"

exec psql \
  "host=aws-0-${REGION}.pooler.supabase.com port=5432 user=postgres.${REF} dbname=postgres sslmode=require" \
  -v ON_ERROR_STOP=1 \
  "$@"