// Code for benchmarking.

'use strict';

var async = require('async');
var syncbase = require('syncbase');
var nosql = syncbase.nosql;

var util = require('./util');
var wt = util.wt;

var LOG_EVERYTHING = false;

// Does n parallel puts with a common prefix, then returns the prefix.
function doPuts(ctx, tb, n, cb) {
  cb = util.logFn('doPuts', cb);
  var prefix = util.timestamp() + '.';
  async.times(100, function(n, cb) {
    // TODO(sadovsky): Remove this once we loosen Syncbase's naming rules.
    prefix = prefix.replace(/:/g, '.');
    var key = prefix + n;
    var value = '';
    if (LOG_EVERYTHING) console.log('put: ' + key);
    tb.put(ctx, key, value, function(err) {
      if (LOG_EVERYTHING) console.log('put done: ' + key);
      cb(err);
    });
  }, function(err) {
    return cb(err, prefix);
  });
}

// Scans all records with the given prefix.
function doScan(ctx, tb, prefix, cb) {
  cb = util.logFn('doScan(' + prefix + ')', cb);
  var bytes = 0, streamErr = null;
  tb.scan(ctx, nosql.rowrange.prefix(prefix), function(err) {
    err = err || streamErr;
    if (err) return cb(err);
    console.log('scanned ' + bytes + ' bytes');
    cb();
  }).on('data', function(row) {
    bytes += row.key.length + row.value.length;
    if (LOG_EVERYTHING) console.log('scan: ' + JSON.stringify(row));
  }).on('error', function(err) {
    streamErr = streamErr || err.error;
  });
}

// Creates a fresh hierarchy, then runs doPuts followed by doScan.
exports.runBenchmark = function(rt, name, cb) {
  cb = util.logFn('runBenchmark', cb);
  var ctx = rt.getContext();
  var s = syncbase.newService(name);
  var appName = 'bm.' + util.uid();
  util.createHierarchy(ctx, s, appName, 'db', 'tb', function(err, db) {
    if (err) return cb(err);
    var tb = db.table('tb');
    doPuts(wt(ctx), tb, 100, function(err, prefix) {
      if (err) return cb(err);
      doScan(wt(ctx), tb, prefix, cb);
    });
  });
};
