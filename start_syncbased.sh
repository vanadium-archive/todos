#!/bin/bash
# Copyright 2015 The Vanadium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style
# license that can be found in the LICENSE file.

# Expects credentials in /tmp/creds, generated as follows:
# make build
# ./bin/principal seekblessings --v23.credentials tmp/creds

./bin/syncbased --root-dir=tmp/sbroot --v23.tcp.address=localhost:8200 --v23.credentials=tmp/creds --v=3 --alsologtostderr=true --v23.permissions.literal='{"Admin":{"In":["..."]},"Write":{"In":["..."]},"Read":{"In":["..."]},"Resolve":{"In":["..."]},"Debug":{"In":["..."]}}'
