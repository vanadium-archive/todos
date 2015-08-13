// Defines the Collection interface, a subset of the MongoDB collection API.

'use strict';

var EventEmitter = require('events').EventEmitter;
var inherits = require('inherits');

inherits(Collection, EventEmitter);
module.exports = Collection;

// Collection interface. Collections emit 'change' events.
function Collection() {
  EventEmitter.call(this);
}

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
