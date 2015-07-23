'use strict';

var async = require('async');
var syncbase = require('syncbase');
var vanadium = require('vanadium');
var verror = vanadium.verror;

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
  cb = bm.logFn('initData', cb);
  var timestamp = Date.now();
  async.each(data, function(list, cb) {
    disp.addList({name: list.name}, function(err, listId) {
      if (err) return cb(err);
      async.each(list.contents, function(info, cb) {
        timestamp += 1;  // ensure unique timestamp
        disp.addTodo(listId, {
          text: info[0],
          tags: info.slice(1),
          done: false,
          timestamp: timestamp
        }, cb);
      }, cb);
    });
  }, function(err) {
    // NOTE(sadovsky): Based on console logs, it looks like browser async.each
    // doesn't use process.nextTick for its final callback!
    process.nextTick(function() {
      return cb(err);
    });
  });
}

// Returns a new Vanadium context object with a timeout.
function wt(ctx, timeout) {
  return ctx.withTimeout(timeout || 5000);
}

exports.initSyncbaseDispatcher = function(rt, name, benchmark, cb) {
  cb = bm.logFn('initSyncbaseDispatcher', cb);
  var ctx = rt.getContext();
  var service = syncbase.newService(name);
  var app = service.app('todos'), db = app.noSqlDatabase('db');
  var disp = new SyncbaseDispatcher(rt, db);
  app.create(wt(ctx), {}, function(err) {
    if (err) {
      if (err instanceof verror.ExistError) {
        if (benchmark) {
          return bm.runBenchmark(ctx, db, cb);
        }
        console.log('app exists; assuming database has been initialized');
        return cb(null, disp);
      }
      return cb(err);
    }
    console.log('app did not exist; initializing database');
    db.create(wt(ctx), {}, function(err) {
      if (err) return cb(err);
      db.createTable(wt(ctx), 'tb', {}, function(err) {
        if (err) return cb(err);
        if (benchmark) {
          // TODO(sadovsky): Restructure things so that we still call initData
          // even if on the first page load we ran the benchmark. Maybe do this
          // by having the benchmark use a completely different app and/or
          // database.
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
};

exports.initCollectionDispatcher = function(cb) {
  cb = bm.logFn('initCollectionDispatcher', cb);
  var lists = new MemCollection('lists'), todos = new MemCollection('todos');
  var disp = new CollectionDispatcher(lists, todos);
  initData(disp, function(err) {
    if (err) return cb(err);
    cb(null, disp);
  });
};
