// Syncbase-based implementation of Dispatcher.
//
// Schema design doc (a bit outdated):
// https://docs.google.com/document/d/1GtBk75QmjSorUW6T6BATCoiS_LTqOrGksgqjqJ1Hiow/edit#
//
// TODO(sadovsky): Currently, list and todo order are not preserved. We should
// make the app always order these lexicographically, or better yet, use a
// Dewey-Decimal-like scheme (with randomization).
//
// NOTE: For now, when an item is deleted, any sub-items that were added
// concurrently (on some other device) are orphaned. Eventually, we'll GC
// orphaned records; for now, we don't bother. This orphaning-based approach
// enables us to use simple last-one-wins conflict resolution for all records
// stored in Syncbase.
//
// TODO(sadovsky): Orphaning degrades performance, because scan responses (e.g.
// scan to get all lists) include orphaned records. If we switch from scans to
// queries, performance should improve since all row filtering will happen
// server side.

'use strict';

var _ = require('lodash');
var async = require('async');
var inherits = require('inherits');
var randomBytes = require('randombytes');
var syncbase = require('syncbase');
var nosql = syncbase.nosql;
var vanadium = require('vanadium');
var vtrace = vanadium.vtrace;

var bm = require('./benchmark');
var Dispatcher = require('./dispatcher');

inherits(SyncbaseDispatcher, Dispatcher);
module.exports = SyncbaseDispatcher;

function SyncbaseDispatcher(rt, db) {
  Dispatcher.call(this);
  this.rt_ = rt;
  this.ctx_ = rt.getContext();
  this.db_ = db;
  this.tb_ = db.table('tb');
}

////////////////////////////////////////
// Helpers

function noop() {}

var SEP = '.';  // separator for key parts

function join() {
  // TODO(sadovsky): Switch to using naming.join() once Syncbase allows slashes
  // in row keys.
  var args = Array.prototype.slice.call(arguments);
  return args.join(SEP);
}

function uuid(len) {
  len = len || 16;
  return randomBytes(Math.ceil(len / 2)).toString('hex').substr(0, len);
}

function newListKey() {
  return uuid();
}

function newTodoKey(listId) {
  return join(listId, 'todos', uuid());
}

function tagKey(todoId, tag) {
  return join(todoId, 'tags', tag);
}

function marshal(x) {
  return JSON.stringify(x);
}

function unmarshal(x) {
  return JSON.parse(x);
}

////////////////////////////////////////
// Vanadium helpers

// Returns a new Vanadium context object with a timeout.
function wt(ctx, timeout) {
  return ctx.withTimeout(timeout || 5000);
}

// Returns a new Vanadium context object with the given name.
function wn(ctx, name) {
  return vtrace.withNewSpan(ctx, name);
}

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
    // Drop ctx and cb, convert to JSON, drop square brackets.
    var cb = args[args.length - 1];
    var argsStr = JSON.stringify(args.slice(1, -1)).slice(1, -1);
    args[args.length - 1] = bm.logFn(name + '(' + argsStr + ')', cb);
    return fn.apply(this, args);
  };
}

////////////////////////////////////////
// SyncbaseDispatcher impl

// TODO(sadovsky): Switch to storing VDL values (instead of JSON) and use a
// query to get all values of a particular type.
define('getLists', function(ctx, cb) {
  var that = this;
  // NOTE(sadovsky): Storing lists in a separate keyspace from todos and tags
  // could make this scan faster. But given the size of the data (tiny), it
  // shouldn't make much difference.
  async.parallel([
    function(cb) {
      that.getRows_(ctx, null, function(err, rows) {
        if (err) return cb(err);
        var lists = [];
        _.forEach(rows, function(row) {
          if (row.key.indexOf(SEP) >= 0) {
            return;
          }
          lists.push(_.assign({}, unmarshal(row.value), {_id: row.key}));
        });
        return cb(null, lists);
      });
    },
    // Returns a list of SG objects (from getSyncGroup), one per SG.
    // TODO(sadovsky): Would be nice if Syncbase could provide more info in a
    // single RPC.
    function(cb) {
      bm.logFn('getSyncGroupNames', cb);
      // FIXME: Remove this hack once db.getSyncGroupNames, sg.getSpec, and
      // sg.getMembers are implemented.
      /* jshint -W027 */
      return process.nextTick(function() {
        cb(null, []);
      });
      that.db_.getSyncGroupNames(ctx, function(err, sgNames) {
        if (err) return cb(err);
        async.map(sgNames, function(sgName, cb) {
          that.getSyncGroup(ctx, that.sgNameToListId_(sgName), cb);
        }, cb);
      });
    }
  ], function(err, results) {
    if (err) return cb(err);
    var lists = results[0], sgs = results[1];
    // Left join: add 'sg' field to each list for which there's an SG.
    _.forEach(lists, function(list) {
      var listId = list._id;
      _.forEach(sgs, function(sg) {
        console.assert(sg.spec.Prefixes.length === 1);
        if (listId === sg.spec.Prefixes[0]) {
          list.sg = sg;
        }
      });
    });
    return cb(null, lists);
  });
});

define('getTodos', function(ctx, listId, cb) {
  this.getRows_(ctx, listId, function(err, rows) {
    if (err) return cb(err);
    var todos = [];
    var todo = {};
    _.forEach(rows, function(row) {
      var parts = row.key.split(SEP);
      if (parts.length < 2 || parts[0] !== listId) {
        return;
      } else if (parts.length === 3) {  // next todo
        if (todo._id) {
          todos.push(todo);
        }
        todo = _.assign({}, unmarshal(row.value), {_id: row.key});
      } else if (parts.length === 5) {  // tag for current todo
        if (!todo.tags) {
          todo.tags = [];
        }
        todo.tags.push(parts[4]);  // push tag name
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
  this.tb_.put(ctx, listId, v, this.maybeEmit_(function(err) {
    if (err) return cb(err);
    return cb(null, listId);
  }));
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
  }, this.maybeEmit_(cb));
});

define('removeTodo', function(ctx, todoId, cb) {
  this.tb_.row(todoId).delete(ctx, this.maybeEmit_(cb));
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
  this.addTagImpl_(ctx, this.tb_, todoId, tag, this.maybeEmit_(cb));
});

define('removeTag', function(ctx, todoId, tag, cb) {
  // NOTE: Table.delete is awkward (it takes a range), so instead we use
  // Row.delete. It would be nice for Table.delete to operate on a single row
  // and have a separate Table.deleteRowRange.
  this.tb_.row(tagKey(todoId, tag)).delete(ctx, this.maybeEmit_(cb));
});

////////////////////////////////////////
// SyncGroup methods

// TODO(sadovsky): It's not clear from the Syncbase API how SG names should be
// constructed, and it's also weird that db.SyncGroup(name) expects an absolute
// name. We should probably allow clients to specify DB-relative SG names.

// Currently, SG names must be of the form <syncbaseName>/$sync/<suffix>.
// We use <app>/<db>/<table>/<listId> for the suffix part

SyncbaseDispatcher.prototype.sgNameToListId_ = function(sgName) {
  return sgName.replace(new RegExp('.*/$sync/todos/db/tb/'), '');
};

SyncbaseDispatcher.prototype.listIdToSgName_ = function(listId) {
  var prefix = this.tb_.fullName.replace('/todos/db/tb/',
                                         '/$sync/todos/db/tb/');
  return prefix + listId;
};

// Returns spec and members for the given list.
define('getSyncGroup', function(ctx, listId, cb) {
  var sg = this.db_.syncGroup(this.listIdToSgName_(listId));
  async.parallel([
    function(cb) {
      sg.getSpec(ctx, cb);
    },
    function(cb) {
      sg.getMembers(ctx, cb);
    }
  ], function(err, results) {
    if (err) return cb(err);
    // FIXME: Convert 'members' to email addresses.
    return {
      spec: results[0],
      members: _.keys(results[1])
    };
  });
});

// TODO(sadovsky): Copied from test-syncgroup.js. I have no idea whether this
// value is appropriate.
var MEMBER_INFO = new nosql.SyncGroupMemberInfo({
  syncPriority: 8
});

define('createSyncGroup', function(ctx, listId, cb) {
  var sg = this.db_.syncGroup(this.listIdToSgName_(listId));
  var spec = new nosql.SyncGroupSpec({
    // TODO(sadovsky): Make perms more restrictive.
    perms: new Map([
      ['Admin',   {'In': ['...']}],
      ['Read',    {'In': ['...']}],
      ['Write',   {'In': ['...']}],
      ['Resolve', {'In': ['...']}],
      ['Debug',   {'In': ['...']}]
    ]),
    // TODO(sadovsky): Update this once we switch to {table, prefix} tuples.
    prefixes: ['tb:' + listId]
  });
  sg.create(ctx, spec, MEMBER_INFO, this.maybeEmit_(cb));
});

define('joinSyncGroup', function(ctx, listId, cb) {
  var sg = this.db_.syncGroup(this.listIdToSgName_(listId));
  sg.join(ctx, MEMBER_INFO, this.maybeEmit_(cb));
});

////////////////////////////////////////
// vtrace methods

// DO NOT USE THIS. vtrace RPCs are extremely slow in JavaScript because VOM
// decoding is slow for trace records, which are deeply nested. E.g. 100 puts
// can take 20+ seconds with vtrace vs. 2 seconds without.
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

// TODO(sadovsky): Watch for changes on Syncbase itself so that we can detect
// when data arrives via sync, and drop this method.
SyncbaseDispatcher.prototype.maybeEmit_ = function(cb) {
  var that = this;
  cb = cb || noop;
  return function(err) {
    cb.apply(null, arguments);
    if (!err) that.emit('change');
  };
};

// Writes the given tag into the given table.
define('addTagImpl_', function(ctx, tb, todoId, tag, cb) {
  // TODO(sadovsky): Syncbase currently disallows whitespace in keys, so as a
  // quick hack we drop all whitespace before storing tags.
  tag = tag.replace(/\s+/g, '');
  tb.put(ctx, tagKey(todoId, tag), null, cb);
});

// Returns all rows in the table.
define('getRows_', function(ctx, listId, cb) {
  var rows = [], streamErr = null;
  var range = nosql.rowrange.prefix(listId || '');
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

// Performs a read-modify-write on key, applying updateFn to the value.
// Takes care of value marshalling and unmarshalling.
// TODO(sadovsky): Atomic read-modify-write requires 4 RPCs. MongoDB-style API
// would bring it down to 1.
define('update_', function(ctx, key, updateFn, cb) {
  var opts = new nosql.BatchOptions();
  nosql.runInBatch(ctx, this.db_, opts, function(db, cb) {
    var tb = db.table('tb');
    tb.get(wn(ctx, 'get:' + key), key, function(err, value) {
      if (err) return cb(err);
      var newValue = marshal(updateFn(unmarshal(value)));
      tb.put(wn(ctx, 'put:' + key), key, newValue, cb);
    });
  }, this.maybeEmit_(cb));
});
