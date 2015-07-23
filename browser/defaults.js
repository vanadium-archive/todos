'use strict';

var async = require('async');
var syncbase = require('syncbase');
var vanadium = require('vanadium');
var verror = vanadium.verror;

var CollectionDispatcher = require('./collection_dispatcher');
var MemCollection = require('./mem_collection');
var SyncbaseDispatcher = require('./syncbase_dispatcher');
var util = require('./util');

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
  cb = util.logFn('initData', cb);
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

exports.initSyncbaseDispatcher = function(rt, name, cb) {
  cb = util.logFn('initSyncbaseDispatcher', cb);
  var ctx = rt.getContext();
  var s = syncbase.newService(name);
  util.createHierarchy(ctx, s, 'todos', 'db', 'tb', function(err, db) {
    if (err && !(err instanceof verror.ExistError)) {
      return cb(err);
    }
    var disp = new SyncbaseDispatcher(rt, db);
    if (err) {  // verror.ExistError
      console.log('skipping initData');
      return cb(null, disp);
    }
    initData(disp, function(err) {
      if (err) return cb(err);
      cb(null, disp);
    });
  });
};

exports.initCollectionDispatcher = function(cb) {
  cb = util.logFn('initCollectionDispatcher', cb);
  var lists = new MemCollection('lists'), todos = new MemCollection('todos');
  var disp = new CollectionDispatcher(lists, todos);
  initData(disp, function(err) {
    if (err) return cb(err);
    cb(null, disp);
  });
};
