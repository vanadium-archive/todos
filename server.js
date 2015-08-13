// Copyright 2015 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

'use strict';

var express = require('express');
var pathlib = require('path');

var app = express();

function pathTo(path) {
  return pathlib.join(__dirname, path);
}

app.use('/public', express.static(pathTo('public')));
app.use('/third_party', express.static(pathTo('third_party')));

app.get('*', function(req, res) {
  res.sendFile(pathTo('public/index.html'));
});

var server = app.listen(process.env.PORT || 4000, function() {
  var hostname = require('my-local-ip')();
  console.log('Serving http://%s:%d', hostname, server.address().port);
});
