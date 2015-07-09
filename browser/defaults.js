'use strict';

var async = require('async');
var syncbase = require('syncbase');

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

exports.initData = function(disp, cb) {
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
  }, cb);
};

function newCtx(rt, timeout) {
  timeout = timeout || 5000;
  return rt.getContext().withTimeout(timeout);
}

function appExists(rt, service, name, cb) {
  service.listApps(newCtx(rt), function(err, names) {
    if (err) return cb(err);
    return cb(null, names.indexOf(name) >= 0);
  });
}

exports.initSyncbaseDispatcher = function(rt, name, cb) {
  var service = syncbase.newService(name);
  // TODO(sadovsky): Instead of appExists, simply check for ErrExist in the
  // app.create response.
  appExists(rt, service, 'todos', function(err, exists) {
    if (err) return cb(err);
    var app = service.app('todos'), db = app.noSqlDatabase('db');
    var disp = new SyncbaseDispatcher(rt, db);
    if (exists) {
      console.log('app exists; assuming everything has been initialized');
      return cb(null, disp);
    }
    console.log('app does not exist; initializing everything');
    app.create(newCtx(rt), {}, function(err) {
      if (err) return cb(err);
      db.create(newCtx(rt), {}, function(err) {
        if (err) return cb(err);
        db.createTable(newCtx(rt), 'tb', {}, function(err) {
          if (err) return cb(err);
          return cb(null, disp);
        });
      });
    });
  });
};

exports.initCollectionDispatcher = function(cb) {
  var lists = new MemCollection('lists'), todos = new MemCollection('todos');
  var disp = new CollectionDispatcher(lists, todos);
  process.nextTick(function() {
    cb(null, disp);
  });
};
