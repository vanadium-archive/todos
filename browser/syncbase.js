// Syncbase wrapper that implements the Collection API.

// TODO(sadovsky): Implement.

'use strict';

var inherits = require('util').inherits;

var Collection = require('./collection');

inherits(Syncbase, Collection);
module.exports = Syncbase;

// TODO(sadovsky): Watch store for change events. (Necessary if we want to
// immediately display synced data.)

function Syncbase(db, tableName) {
  Collection.call(this);
  this.table_ = db.table(tableName);
}

Syncbase.prototype.find = function(q, opts, cb) {
  throw new Error('not implemented');
};

Syncbase.prototype.insert = function(v, cb) {
  throw new Error('not implemented');
};

Syncbase.prototype.remove = function(q, cb) {
  throw new Error('not implemented');
};

Syncbase.prototype.update = function(q, opts, cb) {
  throw new Error('not implemented');
};
