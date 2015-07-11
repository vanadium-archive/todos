#!/usr/bin/env bash

set -euo pipefail

readonly DIR=${1}
mkdir -p ${DIR}

get() {
  local -r NAME=${1}
  local -r URL=${2}
  curl -L -o ${DIR}/${NAME} ${URL}
}

get async.min.js https://raw.githubusercontent.com/caolan/async/master/dist/async.min.js
get lodash.min.js https://raw.githubusercontent.com/lodash/lodash/3.10.0/lodash.min.js
get moment.min.js https://raw.githubusercontent.com/moment/moment/2.10.3/min/moment.min.js
get react.min.js https://fb.me/react-0.13.3.min.js
get react-with-addons.js https://fb.me/react-with-addons-0.13.3.js
