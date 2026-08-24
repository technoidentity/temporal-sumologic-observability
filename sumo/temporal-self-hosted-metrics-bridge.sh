#!/usr/bin/env bash
# Continuously scrape a self-hosted Temporal Server /metrics endpoint and forward
# the dashboard-relevant metric families to a Sumo Logic HTTP Logs & Metrics source
# in Prometheus format.
#
# Use this for EC2/VM/Docker self-hosted deployments that are NOT on Kubernetes.
# (On Kubernetes, prefer the Sumo Kubernetes Collection annotation scrape in
#  temporal-self-hosted-collection-values.example.yaml instead of this bridge.)
#
# Required env:
#   SUMO_METRICS_URL   Sumo HTTP Logs & Metrics source URL. KEEP SECRET; never commit.
# Optional env:
#   METRICS_ENDPOINT   Temporal server Prometheus endpoint. Default http://localhost:6051/metrics
#   INTERVAL_SECONDS   Scrape/forward interval. Default 60
#
# Notes:
# - --compressed is required: the endpoint is large (100k+ lines on busy multi-namespace
#   servers) and an uncompressed pull over a WAN can truncate mid-stream.
# - Only the families the self-hosted dashboard uses are forwarded, and histogram
#   *_bucket series are dropped, to keep ingestion cardinality/cost low. Latency panels
#   use *_sum / *_count, which are kept.
set -euo pipefail

: "${SUMO_METRICS_URL:?set SUMO_METRICS_URL (Sumo HTTP source; keep it out of git)}"
ENDPOINT="${METRICS_ENDPOINT:-http://localhost:6051/metrics}"
INTERVAL="${INTERVAL_SECONDS:-60}"

FAMILIES='service_requests|service_errors|service_pending_requests|service_latency_sum|service_latency_count|client_errors|action|workflow_success|workflow_failed|schedule_to_start_timeout|start_to_close_timeout|approximate_backlog_count|approximate_backlog_age_seconds|no_poller_tasks|poll_success|poll_success_sync|poll_timeouts|persistence_requests|persistence_errors|persistence_errors_resource_exhausted|persistence_latency_sum|persistence_latency_count|cache_size|cache_usage|restarts|num_goroutines|memory_heap'

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT

echo "$(date -u +%FT%TZ) bridge start: $ENDPOINT -> Sumo every ${INTERVAL}s"
while true; do
  if curl -fsS --compressed --max-time "$INTERVAL" "$ENDPOINT" 2>/dev/null \
       | grep -E "^(${FAMILIES})(\{| )" > "$tmp"; then
    if curl -fsS -X POST -H "Content-Type: application/vnd.sumologic.prometheus" \
         --data-binary @"$tmp" "$SUMO_METRICS_URL" >/dev/null 2>&1; then
      echo "$(date -u +%FT%TZ) pushed $(wc -l < "$tmp") series"
    else
      echo "$(date -u +%FT%TZ) push failed"
    fi
  else
    echo "$(date -u +%FT%TZ) scrape failed from $ENDPOINT"
  fi
  sleep "$INTERVAL"
done
