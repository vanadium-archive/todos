'use strict';

var EventEmitter = require('events').EventEmitter;
var inherits = require('util').inherits;

inherits(Collection, EventEmitter);
module.exports = Collection;

function Collection() {
  EventEmitter.call(this);
}

// Collection interface, with most methods stubbed out.
Collection.prototype.find = function(q, opts, cb) {
  throw new Error('not implemented');
};

Collection.prototype.insert = function(v, cb) {
  throw new Error('not implemented');
};

Collection.prototype.remove = function(q, cb) {
  throw new Error('not implemented');
};

Collection.prototype.update = function(q, opts, cb) {
  throw new Error('not implemented');
};

Collection.prototype.findOne = function(q, opts, cb) {
  this.find(q, opts, function(err, all) {
    if (err) return cb(err);
    if (all.length > 0) {
      return cb(null, all[0]);
    }
    return cb();
  });
};
