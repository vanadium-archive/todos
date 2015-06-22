'use strict';

var async = require('async');
var syncbase = require('syncbase');

var Memstore = require('./memstore');
var Syncbase = require('./syncbase');

var SYNCBASE_NAME = 'test/syncbased';

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

function initData(lists, todos, cb) {
  var timestamp = Date.now();
  async.each(data, function(list, cb) {
    lists.insert({name: list.name}, function(err, listId) {
      if (err) return cb(err);
      async.each(list.contents, function(info, cb) {
        timestamp += 1;  // ensure unique timestamp
        todos.insert({
          listId: listId,
          text: info[0],
          done: false,
          timestamp: timestamp,
          tags: info.slice(1)
        }, cb);
      }, cb);
    });
  }, cb);
}

function appExists(ctx, service, name, cb) {
  service.listApps(ctx, function(err, names) {
    if (err) return cb(err);
    return cb(null, names.indexOf(name) >= 0);
  });
}

exports.initCollections = function(ctx, engine, cb) {
  function doInitData(lists, todos, cb) {
    initData(lists, todos, function(err) {
      if (err) return cb(err);
      return cb(null, {
        lists: lists,
        todos: todos
      });
    });
  }

  switch (engine) {
  case 'syncbase':
    var service = syncbase.newService(SYNCBASE_NAME);
    appExists(ctx, service, 'todos', function(err, exists) {
      if (err) return cb(err);
      var app = service.app('todos'), db = app.noSqlDatabase('db');
      var lists = new Syncbase(db, 'lists');
      var todos = new Syncbase(db, 'todos');
      if (exists) {
        console.log('app exists; assuming everything has been initialized');
        return cb(null, {
          lists: lists,
          todos: todos
        });
      }
      console.log('app does not exist; initializing everything');
      app.create(ctx, {}, function(err) {
        console.log('app.create done');
        // TODO(sadovsky): This fails with "No usable servers found". Chat with
        // Nick to determine optimal setup for development and debugging.
        if (err) return cb(err);
        var db = app.noSqlDatabase('db');
        db.create(ctx, {}, function(err) {
          if (err) return cb(err);
          async.each(['lists', 'todos'], function(name, cb) {
            db.createTable(ctx, name, {}, cb);
          }, function(err) {
            if (err) return cb(err);
            var lists = new Syncbase(db, 'lists');
            var todos = new Syncbase(db, 'todos');
            doInitData(lists, todos, cb);
          });
        });
      });
    });
    break;
  case 'memstore':
    var lists = new Memstore('lists');
    var todos = new Memstore('todos');
    doInitData(lists, todos, cb);
    break;
  default:
    throw new Error('unknown engine: ' + engine);
  }
};
