'use strict';

var async = require('async');
var syncbase = require('syncbase');

var bm = require('./benchmark');
var CollectionDispatcher = require('./collection_dispatcher');
var MemCollection = require('./mem_collection');
var SyncbaseDispatcher = require('./syncbase_dispatcher');

// Copied from meteor/todos/server/bootstrap.js.
var data = [
  {name: 'Meteor Principles',
   contents: [
     ['Data on the Wire', 'Simplicity', 'Better UX', 'Fun'],
     ['One Language', 'Simplicity', 'Fun'],
     ['Database Everywhere', 'Simplicity'],
     ['Latency Compensation', 'Better UX'],
     ['Full Stack Reactivity', 'Better UX', 'Fun'],
     ['Embrace the Ecosystem', 'Fun'],
     ['Simplicity Equals Productivity', 'Simplicity', 'Fun']
   ]
  },
  {name: 'Languages',
   contents: [
     ['Lisp', 'GC'],
     ['C', 'Linked'],
     ['C++', 'Objects', 'Linked'],
     ['Python', 'GC', 'Objects'],
     ['Ruby', 'GC', 'Objects'],
     ['JavaScript', 'GC', 'Objects'],
     ['Scala', 'GC', 'Objects'],
     ['Erlang', 'GC'],
     ['6502 Assembly', 'Linked']
   ]
  },
  {name: 'Favorite Scientists',
   contents: [
     ['Ada Lovelace', 'Computer Science'],
     ['Grace Hopper', 'Computer Science'],
     ['Marie Curie', 'Physics', 'Chemistry'],
     ['Carl Friedrich Gauss', 'Math', 'Physics'],
     ['Nikola Tesla', 'Physics'],
     ['Claude Shannon', 'Math', 'Computer Science']
   ]
  }
];

function initData(disp, cb) {
  var timestamp = Date.now();
  async.each(data, function(list, cb) {
    disp.addList({name: list.name}, function(err, listId) {
      if (err) return cb(err);
      async.eachSeries(list.contents, function(info, cb) {
        timestamp += 1;  // ensure unique timestamp
        disp.addTodo(listId, {
          text: info[0],
          tags: info.slice(1),
          done: false,
          timestamp: timestamp
        }, cb);
      }, cb);
    });
  }, cb);
}

// Returns a new Vanadium context object with a timeout.
function wt(ctx, timeout) {
  return ctx.withTimeout(timeout || 5000);
}

function appExists(ctx, service, name, cb) {
  service.listApps(ctx, function(err, names) {
    if (err) return cb(err);
    return cb(null, names.indexOf(name) >= 0);
  });
}

exports.initSyncbaseDispatcher = function(rt, name, benchmark, cb) {
  cb = bm.logLatency('initSyncbaseDispatcher', cb);
  var service = syncbase.newService(name);
  // TODO(sadovsky): Instead of appExists, simply check for ErrExist in the
  // app.create response.
  var ctx = rt.getContext();
  appExists(wt(ctx), service, 'todos', function(err, exists) {
    if (err) return cb(err);
    var app = service.app('todos'), db = app.noSqlDatabase('db');
    var disp = new SyncbaseDispatcher(rt, db);
    if (exists) {
      if (benchmark) {
        return bm.runBenchmark(ctx, db, cb);
      }
      console.log('app exists; assuming everything has been initialized');
      return cb(null, disp);
    }
    console.log('app does not exist; initializing everything');
    app.create(wt(ctx), {}, function(err) {
      if (err) return cb(err);
      db.create(wt(ctx), {}, function(err) {
        if (err) return cb(err);
        db.createTable(wt(ctx), 'tb', {}, function(err) {
          if (err) return cb(err);
          if (benchmark) {
            return bm.runBenchmark(ctx, db, cb);
          }
          console.log('hierarchy created; writing rows');
          initData(disp, function(err) {
            if (err) return cb(err);
            cb(null, disp);
          });
        });
      });
    });
  });
};

exports.initCollectionDispatcher = function(cb) {
  cb = bm.logLatency('initCollectionDispatcher', cb);
  var lists = new MemCollection('lists'), todos = new MemCollection('todos');
  var disp = new CollectionDispatcher(lists, todos);
  initData(disp, function(err) {
    if (err) return cb(err);
    cb(null, disp);
  });
};
