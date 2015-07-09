// Syncbase-based implementation of Dispatcher.
//
// Schema design doc (a bit outdated):
// https://docs.google.com/document/d/1GtBk75QmjSorUW6T6BATCoiS_LTqOrGksgqjqJ1Hiow/edit#
//
// NOTE: Currently, list and todo order are not preserved. We should make the
// app always order these lexicographically.

'use strict';

var _ = require('lodash');
var async = require('async');
var inherits = require('inherits');
var randomBytes = require('randombytes');

var syncbase = require('syncbase');
var nosql = syncbase.nosql;

var Dispatcher = require('./dispatcher');

inherits(SyncbaseDispatcher, Dispatcher);
module.exports = SyncbaseDispatcher;

function SyncbaseDispatcher(rt, db) {
  Dispatcher.call(this);
  this.rt_ = rt;
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
// SyncbaseDispatcher impl

// TODO(sadovsky): Switch to storing VDL values (instead of JSON) and use a
// query to get all values of a particular type.
SyncbaseDispatcher.prototype.getLists = function(cb) {
  this.getRows_(function(err, rows) {
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
};

SyncbaseDispatcher.prototype.getTodos = function(listId, cb) {
  // TODO(sadovsky): Specify listId as prefix to getRows_.
  this.getRows_(function(err, rows) {
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
};

SyncbaseDispatcher.prototype.addList = function(list, cb) {
  console.assert(!list._id);
  var listId = newListKey();
  var v = marshal(list);
  this.tb_.put(this.newCtx_(), listId, v, this.maybeEmit_(function(err) {
    if (err) return cb(err);
    return cb(null, listId);
  }));
};

SyncbaseDispatcher.prototype.editListName = function(listId, name, cb) {
  this.update_(listId, function(list) {
    return _.assign(list, {name: name});
  }, cb);
};

SyncbaseDispatcher.prototype.addTodo = function(listId, todo, cb) {
  var that = this;
  console.assert(!todo._id);
  var tags = todo.tags;
  delete todo.tags;
  var todoId = newTodoKey(listId);
  var v = marshal(todo);
  // Write todo and tags in a batch.
  var opts = new nosql.BatchOptions();
  nosql.runInBatch(this.newCtx_(), this.db_, opts, function(db, cb) {
    // NOTE: Dealing with tables is awkward given that batches and syncgroups
    // are database-level. Maybe we should just get rid of tables. Doing so
    // would solve other problems as well, e.g. the API inconsistency for
    // creating databases vs. tables.
    var tb = db.table('tb');
    tb.put(that.newCtx_(), todoId, v, function(err) {
      if (err) return cb(err);
      async.each(tags, function(tag, cb) {
        that.addTagInternal_(tb, todoId, tag, cb);
      }, cb);
    });
  }, this.maybeEmit_(cb));
};

SyncbaseDispatcher.prototype.removeTodo = function(todoId, cb) {
  this.tb_.row(todoId).delete(this.newCtx_(), this.maybeEmit_(cb));
};

SyncbaseDispatcher.prototype.editTodoText = function(todoId, text, cb) {
  this.update_(todoId, function(todo) {
    return _.assign(todo, {text: text});
  }, cb);
};

SyncbaseDispatcher.prototype.markTodoDone = function(todoId, done, cb) {
  this.update_(todoId, function(todo) {
    return _.assign(todo, {done: done});
  }, cb);
};

SyncbaseDispatcher.prototype.addTag = function(todoId, tag, cb) {
  this.addTagInternal_(this.tb_, todoId, tag, this.maybeEmit_(cb));
};

SyncbaseDispatcher.prototype.removeTag = function(todoId, tag, cb) {
  // NOTE: Table.delete is awkward (it takes a range), so instead we use
  // Row.delete. It would be nice for Table.delete to operate on a single row
  // and have a separate Table.deleteRowRange.
  this.tb_.row(tagKey(todoId, tag)).delete(this.newCtx_(), this.maybeEmit_(cb));
};

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

// Returns a new Vanadium context object with a timeout.
SyncbaseDispatcher.prototype.newCtx_ = function(timeout) {
  timeout = timeout || 5000;
  return this.rt_.getContext().withTimeout(timeout);
};

// Writes the given tag into the given table.
SyncbaseDispatcher.prototype.addTagInternal_ = function(tb, todoId, tag, cb) {
  // NOTE: Syncbase currently disallows whitespace in keys, so as a quick hack
  // we drop all whitespace before storing tags.
  tag = tag.replace(/\s+/g, '');
  tb.put(this.newCtx_(), tagKey(todoId, tag), null, cb);
};

// Returns all rows in the table.
SyncbaseDispatcher.prototype.getRows_ = function(cb) {
  var rows = [], streamErr = null;
  var range = nosql.rowrange.prefix('');
  this.tb_.scan(this.newCtx_(), range, function(err) {
    if (err) return cb(err);
    if (streamErr) return cb(streamErr);
    cb(null, rows);
  }).on('data', function(row) {
    rows.push(row);
  }).on('error', function(err) {
    streamErr = streamErr || err.error;
  });
};

// Performs a read-modify-write on key, applying updateFn to the value.
// Takes care of value marshalling and unmarshalling.
SyncbaseDispatcher.prototype.update_ = function(key, updateFn, cb) {
  var that = this;
  var opts = new nosql.BatchOptions();
  nosql.runInBatch(this.newCtx_(), this.db_, opts, function(db, cb) {
    var tb = db.table('tb');
    tb.get(that.newCtx_(), key, function(err, value) {
      if (err) return cb(err);
      var newValue = marshal(updateFn(unmarshal(value)));
      tb.put(that.newCtx_(), key, newValue, cb);
    });
  }, this.maybeEmit_(cb));
};
