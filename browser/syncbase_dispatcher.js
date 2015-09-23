// Copyright 2015 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

// Syncbase-based implementation of Dispatcher.
//
// Schema design doc (a bit outdated):
// https://docs.google.com/document/d/1GtBk75QmjSorUW6T6BATCoiS_LTqOrGksgqjqJ1Hiow/edit#
//
// TODO(sadovsky): Support arbitrary item reordering using a Dewey-Decimal-like
// order-tracking scheme, with suffix randomization to prevent conflicts.
//
// NOTE: For now, when an item is deleted, any sub-items that were added
// concurrently (on some other device) are orphaned. Eventually, we'll GC
// orphaned records; for now, we don't bother. This orphaning-based approach
// enables us to use simple last-one-wins conflict resolution for all records
// stored in Syncbase.
// NOTE: For now, we actually always orphan tags when deleting todo entries.
//
// TODO(sadovsky): Orphaning degrades performance, because scan responses (e.g.
// scan to get all todos for a given list) include orphaned records.

'use strict';

var _ = require('lodash');
var async = require('async');
var inherits = require('inherits');
var syncbase = require('syncbase');
var nosql = syncbase.nosql;
var vanadium = require('vanadium');
var verror = vanadium.verror, vtrace = vanadium.vtrace;

var Dispatcher = require('./dispatcher');
var util = require('./util');
var wn = util.wn, wt = util.wt;

inherits(SyncbaseDispatcher, Dispatcher);
module.exports = SyncbaseDispatcher;

function SyncbaseDispatcher(rt, db) {
  var that = this;
  Dispatcher.call(this);
  this.rt_ = rt;
  this.ctx_ = rt.getContext();
  this.db_ = db;
  this.tb_ = db.table('tb');

  // Initialize Syncbase watch
  var errorCb = function(err) {
    if (err) {
      this.emit('watchError', err);
    }
  }
  this.db_.getResumeMarker(that.ctx_, function(err, marker) {
    if (err) { cb(err); }
    var watchStream = that.db_.watch(that.ctx_, that.tb_.name, '', marker, errorCb);

    // TODO(aghassemi): Ideally the app would update its in-memory data
    // structures directly from the watch stream, but since SyncGroup changes
    // do not yet show up on the watch stream, it can't.
    watchStream.on('data', function(change) {
      // TODO(aghassemi): fromSync should be change.fromSync but due to
      // issue v.io/i/685 we set it to true for now.
      var fromSync = true;
      that.emit('change', fromSync);
    });

    watchStream.on('error', errorCb);
  });
}

////////////////////////////////////////
// Helpers

function noop() {}

var SEP = '.';  // separator for key parts

function join() {
  // TODO(sadovsky): Switch to using naming.join() once Syncbase allows slashes
  // in row keys. Also, restrict which chars are allowed in tag names.
  var args = Array.prototype.slice.call(arguments);
  return args.join(SEP);
}

function newListKey() {
  return util.uid();
}

function newTodoKey(listId) {
  return join(listId, 'todos', util.uid());
}

function tagKey(todoId, tag) {
  return join(todoId, 'tags', tag);
}

function keyToListId(key) {
  var parts = key.split(SEP);
  // Assume key is for list, todo, or tag.
  if (parts.length === 1 || parts.length === 3 || parts.length === 5) {
    return parts[0];
  }
  throw new Error('bad key: ' + key);
}

// TODO(sadovsky): Maybe switch from JSON to VOM/VDL.
function marshal(x) {
  return JSON.stringify(x);
}
function unmarshal(x) {
  return JSON.parse(x);
}

var SILENT = new vanadium.context.ContextKey();

// Defines a SyncbaseDispatcher method. If the first argument to fn is not a
// context, creates a new context with a timeout.
function define(name, fn) {
  SyncbaseDispatcher.prototype[name] = function() {
    var args = Array.prototype.slice.call(arguments);
    var ctx = args[0];
    if (ctx instanceof vanadium.context.Context) {
      args.shift();
    } else {
      ctx = wt(this.ctx_);
    }
    args = [wn(ctx, name)].concat(args);
    // The callback argument is optional, and clients don't always pass one.
    if (typeof args[args.length - 1] !== 'function') {
      args.push(noop);
    }
    if (!ctx.value(SILENT)) {
      // Build args string for logging the invocation. Drop ctx and cb, convert
      // to JSON, drop square brackets.
      var cb = args[args.length - 1];
      var argsStr = JSON.stringify(args.slice(1, -1)).slice(1, -1);
      args[args.length - 1] = util.logFn(name + '(' + argsStr + ')', cb);
    }
    return fn.apply(this, args);
  };
}

////////////////////////////////////////
// SyncbaseDispatcher impl

// Returns list objects without 'sg' fields.
define('getListsOnly_', function(ctx, cb) {
  this.getRowsQuery_(ctx, 'k not like "%' + SEP + '%"', function(err, rows) {
    if (err) return cb(err);
    var lists = _.map(rows, function(row) {
      console.assert(!row.key.includes(SEP));
      return _.assign(unmarshal(row.value), {_id: row.key});
    });
    return cb(null, lists);
  });
});

// Returns a list of objects describing SyncGroups.
// TODO(sadovsky): Would be nice if this could be done with a single RPC.
define('getSyncGroups_', function(ctx, cb) {
  var that = this;
  this.db_.getSyncGroupNames(ctx, function(err, sgNames) {
    if (err) return cb(err);
    async.map(sgNames, function(sgName, cb) {
      that.getSyncGroup_(ctx, sgName, cb);
    }, cb);
  });
});

// Returns list objects with 'sg' fields.
define('getLists', function(ctx, cb) {
  var that = this;
  async.parallel([
    function(cb) {
      that.getListsOnly_(ctx, cb);
    },
    function(cb) {
      that.getSyncGroups_(ctx, cb);
    }
  ], function(err, results) {
    if (err) return cb(err);
    var lists = results[0], sgs = results[1];
    // Left join: add 'sg' field to each list for which there's an SG.
    _.forEach(lists, function(list) {
      var listId = list._id;
      _.forEach(sgs, function(sg) {
        console.assert(sg.spec.prefixes.length === 1);
        if (listId === sg.spec.prefixes[0].slice(3)) {  // drop 'tb:' prefix
          list.sg = sg;
        }
      });
    });
    return cb(null, lists);
  });
});

define('getTodos', function(ctx, listId, cb) {
  this.getRows_(ctx, join(listId, 'todos'), function(err, rows) {
    if (err) return cb(err);
    var todos = [];
    var todo = {};
    _.forEach(rows, function(row) {
      var parts = row.key.split(SEP);
      console.assert(parts.length >= 3 && parts[0] === listId);
      if (parts.length === 3) {  // todo entry
        todo = _.assign(unmarshal(row.value), {_id: row.key, tags: []});
        todos.push(todo);
      } else if (parts.length === 5) {  // tag for todo entry
        var tagName = parts[4];
        if (tagKey(todo._id, tagName) !== row.key) {
          // Orphaned tag (from a deleted todo entry); skip.
          // TODO(ivanpi): Garbage collect orphaned tags.
          return;
        }
        todo.tags.push(tagName);
      } else {
        throw new Error('bad key: ' + row.key);
      }
    });
    return cb(null, todos);
  });
});

define('addList', function(ctx, list, cb) {
  console.assert(!list._id);
  var listId = newListKey();
  var v = marshal(list);
  this.tb_.put(ctx, listId, v, function(err) {
    if (err) return cb(err);
    return cb(null, listId);
  });
});

define('editListName', function(ctx, listId, name, cb) {
  this.update_(ctx, listId, function(list) {
    return _.assign(list, {name: name});
  }, cb);
});

define('addTodo', function(ctx, listId, todo, cb) {
  console.assert(!todo._id);
  var that = this;
  var tags = todo.tags;
  delete todo.tags;
  var todoId = newTodoKey(listId);
  var v = marshal(todo);
  // Write todo and tags in a batch.
  var opts = new nosql.BatchOptions();
  nosql.runInBatch(ctx, this.db_, opts, function(db, cb) {
    // NOTE: Dealing with tables is awkward given that batches and syncgroups
    // are database-level. Maybe we should just get rid of tables. Doing so
    // would solve other problems as well, e.g. the API inconsistency for
    // creating databases vs. tables.
    var tb = db.table('tb');
    tb.put(wn(ctx, 'put:' + todoId), todoId, v, function(err) {
      if (err) return cb(err);
      async.each(tags, function(tag, cb) {
        that.addTagImpl_(ctx, tb, todoId, tag, cb);
      }, cb);
    });
  }, cb);
});

define('removeTodo', function(ctx, todoId, cb) {
  // TODO(ivanpi): Also delete corresponding tags.
  this.tb_.delete(ctx, todoId, cb);
});

define('editTodoText', function(ctx, todoId, text, cb) {
  this.update_(ctx, todoId, function(todo) {
    return _.assign(todo, {text: text});
  }, cb);
});

define('markTodoDone', function(ctx, todoId, done, cb) {
  this.update_(ctx, todoId, function(todo) {
    return _.assign(todo, {done: done});
  }, cb);
});

define('addTag', function(ctx, todoId, tag, cb) {
  this.addTagImpl_(ctx, this.tb_, todoId, tag, cb);
});

define('removeTag', function(ctx, todoId, tag, cb) {
  this.tb_.delete(ctx, tagKey(todoId, tag), cb);
});

////////////////////////////////////////
// SyncGroup methods

// TODO(sadovsky): It's not clear from the Syncbase API how SG names should be
// constructed, and it's also weird that db.SyncGroup(name) expects an absolute
// name. We should probably allow clients to specify DB-relative SG names.

// Currently, SG names must be of the form <syncbaseName>/%%sync/<suffix>.
// We use <app>/<db>/<table>/<listId> for the suffix part.

SyncbaseDispatcher.prototype.sgNameToListId = function(sgName) {
  return sgName.replace(new RegExp('.*/%%sync/todos/db/'), '');
};

SyncbaseDispatcher.prototype.listIdToSgName = function(listId) {
  // TODO(sadovsky): fullName doesn't include the mount table name, i.e. the
  // part corresponding to namespaceRoots. Our workaround is to specify a
  // fully-qualified Syncbase name.
  var prefix = this.tb_.fullName.replace('/todos/db/tb', '/%%sync/todos/db');
  return prefix + '/' + listId;
};

// Returns an object describing the SyncGroup with the given name.
// Currently, this object will have two fields: 'name' and 'spec'.
define('getSyncGroup_', function(ctx, sgName, cb) {
  var sg = this.db_.syncGroup(sgName);
  async.parallel([
    function(cb) {
      sg.getSpec(ctx, function(err, spec, version) {
        if (err) return cb(err);
        cb(null, spec);
      });
    },
    function(cb) {
      // TODO(sadovsky): For now, in the UI we just want to show who's on the
      // ACL for a given list, so we don't bother with getMembers. On top of
      // that, currently getMembers returns a map of random Syncbase instance
      // ids to SyncGroupMemberInfo structs, neither of which tell us anything
      // useful.
      if (true) {
        process.nextTick(cb);
      } else {
        sg.getMembers(ctx, cb);
      }
    }
  ], function(err, results) {
    if (err) return cb(err);
    cb(null, {
      name: sgName,
      spec: results[0]
    });
  });
});

// TODO(sadovsky): Copied from test-syncgroup.js. I have no idea whether this
// value is appropriate.
var MEMBER_INFO = new nosql.SyncGroupMemberInfo({
  syncPriority: 8
});

define('createSyncGroup', function(ctx, sgName, blessings, mtName, cb) {
  var sg = this.db_.syncGroup(sgName);
  var spec = new nosql.SyncGroupSpec({
    // TODO(sadovsky): Maybe make perms more restrictive.
    perms: new Map([
      ['Admin',   {'in': blessings}],
      ['Read',    {'in': blessings}],
      ['Write',   {'in': blessings}],
      ['Resolve', {'in': blessings}],
      ['Debug',   {'in': blessings}]
    ]),
    // TODO(sadovsky): Update this once we switch to {table, prefix} tuples.
    prefixes: ['tb:' + this.sgNameToListId(sgName)],
    mountTables: [vanadium.naming.join(mtName, 'rendezvous')]
  });
  sg.create(ctx, spec, MEMBER_INFO, this.maybeEmit_(cb));
});

define('joinSyncGroup', function(ctx, sgName, cb) {
  var sg = this.db_.syncGroup(sgName);
  sg.join(ctx, MEMBER_INFO, this.maybeEmit_(cb));
});

////////////////////////////////////////
// vtrace methods

// DO NOT USE THIS. vtrace RPCs are extremely slow in JavaScript because VOM
// decoding is slow for trace records, which are deeply nested. E.g. 100 puts
// can take 20+ seconds with vtrace vs. 2 seconds without.
// EDIT: It might not be quite that bad - the "20+ seconds" cited above might
// also include the latency added by having the Chrome dev console open.
SyncbaseDispatcher.prototype.resetTraceRecords = function() {
  this.ctx_ = vtrace.withNewStore(this.rt_.getContext());
  vtrace.getStore(this.ctx_).setCollectRegexp('.*');
};

SyncbaseDispatcher.prototype.getTraceRecords = function() {
  return vtrace.getStore(this.ctx_).traceRecords();
};

SyncbaseDispatcher.prototype.logTraceRecords = function() {
  console.log(vtrace.formatTraces(this.getTraceRecords()));
};

////////////////////////////////////////
// Internal helpers

// TODO(aghassemi): Remove this once changes to SyncGroups are included
// in the watch stream.
SyncbaseDispatcher.prototype.maybeEmit_ = function(cb) {
  var that = this;
  cb = cb || noop;
  return function(err) {
    cb.apply(null, arguments);
    if (err) return;
    that.emit('change');
  };
};

// Writes the given tag into the given table.
define('addTagImpl_', function(ctx, tb, todoId, tag, cb) {
  // TODO(sadovsky): Syncbase currently disallows most characters in keys, so
  // as a quick hack we drop all unsavory characters before storing tags.
  tag = tag.replace(/[^a-zA-Z0-9_.-]/g, '');  // taken from Syncbase key check
  tag = tag.split(SEP).join('');  // also eliminate separator
  if (tag === '') {
    return process.nextTick(cb);
  }
  tb.put(ctx, tagKey(todoId, tag), null, cb);
});

// Returns all rows in the table with the given key prefix.
define('getRows_', function(ctx, prefix, cb) {
  var rows = [], streamErr = null;
  var range = nosql.rowrange.prefix(prefix || '');
  this.tb_.scan(ctx, range, function(err) {
    if (err) return cb(err);
    if (streamErr) return cb(streamErr);
    cb(null, rows);
  }).on('data', function(row) {
    rows.push(row);
  }).on('error', function(err) {
    streamErr = streamErr || err.error;
  });
});

// Returns all rows in the table matching the where clause.
define('getRowsQuery_', function(ctx, where, cb) {
  var rows = [], streamErr = null;
  var q = 'select k, v from ' + this.tb_.name + ' where ' + where;
  this.db_.exec(ctx, q, function(err) {
    if (err) return cb(err);
    if (streamErr) return cb(streamErr);
    rows = rows.slice(1);  // remove header
    cb(null, rows);
  }).on('data', function(row) {
    rows.push({key: row[0], value: row[1]});
  }).on('error', function(err) {
    streamErr = streamErr || err.error;
  });
});

// Performs a read-modify-write on key, applying updateFn to the value.
// Takes care of value marshalling and unmarshalling.
// TODO(sadovsky): Atomic read-modify-write requires 4 RPCs. MongoDB-style API
// would bring it down to 1.
define('update_', function(ctx, key, updateFn, cb) {
  var opts = new nosql.BatchOptions();
  nosql.runInBatch(ctx, this.db_, opts, function(db, cb) {
    var tb = db.table('tb');
    tb.get(wn(ctx, 'get:' + key), key, function(err, value) {
      if (err) {
        if (err instanceof verror.NoExistError) {
          // Concurrent delete, likely from a remote peer. Pretend this update
          // never happened.
          // TODO(sadovsky): Maybe make it so client transactions take priority
          // over sync transactions, so that app developers don't have to worry
          // about this concurrency scenario.
          return cb();
        }
        return cb(err);
      }
      var newValue = marshal(updateFn(unmarshal(value)));
      tb.put(wn(ctx, 'put:' + key), key, newValue, cb);
    });
  }, cb);
});
