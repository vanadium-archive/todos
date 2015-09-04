// Copyright 2015 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

'use strict';

var _ = require('lodash');
var moment = require('moment');
var randomBytes = require('randombytes');
var React = require('react');
var vtrace = require('vanadium').vtrace;

// If true, run in "demo mode":
// - Start from a blank slate (no predefined lists)
// - Default to syncbase dispatcher
// - Include "join list" input box, share list codes instead of urls
exports.DEMO = true;

exports.h = function(selector, props, children) {
  if (_.isPlainObject(props)) {
    console.assert(!props.id && !props.className);
  } else {
    children = props;
    props = {};
  }
  var parts = selector.split('.');
  var x = parts[0].split('#'), tagName = x[0], id = x[1];
  var className = parts.slice(1).join(' ');
  console.assert(tagName);
  props = _.assign({}, props, {
    id: id || undefined,
    className: className || undefined
  });
  return React.createElement(tagName, props, children);
};

// Returns a string timestamp, useful for logging.
exports.timestamp = function(t) {
  t = t || Date.now();
  return moment(t).format('HH:mm:ss.SSS');
};

// Returns a unique string identifier of the given length.
exports.uid = function(len) {
  len = len || 16;
  return randomBytes(Math.ceil(len / 2)).toString('hex').substr(0, len);
};

// Converts from binary to hex-encoded string and vice versa.
exports.strToHex = function(s) {
  return new Buffer(s, 'binary').toString('hex');
};
exports.hexToStr = function(s) {
  return new Buffer(s, 'hex').toString('binary');
};

function logStart(name) {
  console.log(name + ' start');
  return Date.now();
}

function logStop(name, start, err) {
  var dt = Date.now() - start;
  console.log(name + (err ? ' FAILED after ' : ' took ') + dt + 'ms');
  if (err) console.error(err);
}

// Wraps the given callback to log start time, stop time, and delta time of a
// function invocation.
function logFn(name, cb) {
  var start = logStart(name);
  return function(err) {
    logStop(name, start, err);
    cb.apply(null, arguments);
  };
}
exports.logFn = logFn;

// Returns a new Vanadium context object with the given name.
exports.wn = function(ctx, name) {
  return vtrace.withNewSpan(ctx, name);
};

// Returns a new Vanadium context object with a timeout.
function wt(ctx, timeout) {
  return ctx.withTimeout(timeout || 5000);
}
exports.wt = wt;

// Creates <app>/<database>/<table> hierarchy in Syncbase.
// Note, for errors we still return the db handle since some errors (e.g.
// verror.ExistError) are non-fatal.
exports.createHierarchy = function(ctx, service, appName, dbName, tbName, cb) {
  var app = service.app(appName), db = app.noSqlDatabase(dbName);
  var appLog = 'create app "' + appName + '"';
  app.create(wt(ctx), {}, logFn(appLog, function(err) {
    if (err) return cb(err, db);
    var dbLog = 'create database "' + dbName + '"';
    db.create(wt(ctx), {}, logFn(dbLog, function(err) {
      if (err) return cb(err, db);
      var tbLog = 'create table "' + tbName + '"';
      var tb = db.table(tbName);
      tb.create(wt(ctx), {}, logFn(tbLog, function(err) {
        if (err) return cb(err, db);
        cb(null, db);
      }));
    }));
  }));
};
