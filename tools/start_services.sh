#!/bin/bash

# Expects credentials in tmp/creds, generated as follows:
#
# make build
# ./bin/principal seekblessings --v23.credentials tmp/creds

set -euo pipefail

trap kill_child_processes INT TERM EXIT

silence() {
  "$@" &> /dev/null || true
}

# Copied from chat example app.
kill_child_processes() {
  # Attempt to stop child processes using the TERM signal.
  if [[ -n "$(jobs -p -r)" ]]; then
    silence pkill -P $$
    sleep 1
    # Kill any remaining child processes using the KILL signal.
    if [[ -n "$(jobs -p -r)" ]]; then
      silence sudo -u "${SUDO_USER}" pkill -9 -P $$
    fi
  fi
}

main() {
  local -r TMP=tmp
  local -r PORT=${PORT-4000}
  local -r MOUNTTABLED_ADDR=":$((PORT+1))"
  local -r SYNCBASED_ADDR=":$((PORT+2))"

  mkdir -p $TMP

  # TODO(sadovsky): Run mounttabled and syncbased each with its own blessing
  # extension.
  ./bin/mounttabled \
    --v23.tcp.address=${MOUNTTABLED_ADDR} \
    --v23.credentials=${TMP}/creds &

  ./bin/syncbased \
    --v=5 \
    --alsologtostderr=false \
    --root-dir=${TMP}/syncbase_${PORT} \
    --name=syncbase \
    --v23.namespace.root=/${MOUNTTABLED_ADDR} \
    --v23.tcp.address=${SYNCBASED_ADDR} \
    --v23.credentials=${TMP}/creds \
    --v23.permissions.literal='{"Admin":{"In":["..."]},"Write":{"In":["..."]},"Read":{"In":["..."]},"Resolve":{"In":["..."]},"Debug":{"In":["..."]}}'

  tail -f /dev/null  # wait forever
}

main "$@"
