// Code for benchmarking.

'use strict';

var async = require('async');
var syncbase = require('syncbase');
var nosql = syncbase.nosql;

var util = require('./util');

exports.logLatency = logLatency;
exports.runBenchmark = runBenchmark;

var LOG_EVERYTHING = false;

function logStart(name) {
  util.log(name + ' start');
  return Date.now();
}

function logStop(name, start) {
  util.log(name + ' took ' + (Date.now() - start) + 'ms');
}

function logLatency(name, cb) {
  var start = logStart(name);
  return function() {
    logStop(name, start);
    cb.apply(null, arguments);
  };
}

// Does n parallel puts with a common prefix, then returns the prefix.
// TODO(sadovsky): According to Shyam, since these puts are being done in
// parallel, it may be the case that each one is setting up its own VC (doing
// auth handshake, fetching discharge, etc.), rather than all puts sharing a
// single VC. Ali or Suharsh should know the state of VC sharing. Possible
// workaround would be to do something that forces VC creation before starting
// the parallel puts. (OTOH, we always run service.listApps before the puts,
// which should create the VC.)
function doPuts(ctx, tb, n, cb) {
  cb = logLatency('doPuts', cb);
  var prefix = util.timestamp() + '.';
  async.times(100, function(n, cb) {
    // TODO(sadovsky): Remove this once we loosen Syncbase's naming rules.
    prefix = prefix.replace(/:/g, '.');
    var key = prefix + n;
    var value = '';
    if (LOG_EVERYTHING) util.log('put: ' + key);
    tb.put(ctx, key, value, function(err) {
      if (LOG_EVERYTHING) util.log('put done: ' + key);
      cb(err);
    });
  }, function(err) {
    return cb(err, prefix);
  });
}

// Scans (and logs) all records with the given prefix.
function doScan(ctx, tb, prefix, cb) {
  cb = logLatency('doScan(' + prefix + ')', cb);
  var bytes = 0, streamErr = null;
  tb.scan(ctx, nosql.rowrange.prefix(prefix), function(err) {
    err = err || streamErr;
    if (err) return cb(err);
    util.log('scanned ' + bytes + ' bytes');
    cb();
  }).on('data', function(row) {
    bytes += row.key.length + row.value.length;
    if (LOG_EVERYTHING) util.log('scan: ' + JSON.stringify(row));
  }).on('error', function(err) {
    streamErr = streamErr || err.error;
  });
}

// Assumes table 'tb' exists.
function runBenchmark(ctx, db, cb) {
  cb = logLatency('runBenchmark', cb);
  var tb = db.table('tb');
  doPuts(ctx, tb, 100, function(err, prefix) {
    if (err) return cb(err);
    doScan(ctx, tb, prefix, cb);
  });
}
