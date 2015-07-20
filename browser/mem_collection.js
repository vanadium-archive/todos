// In-memory implementation of Collection.
// TODO(sadovsky): Replace with nedb NPM module.

'use strict';

var _ = require('lodash');
var inherits = require('inherits');

var Collection = require('./collection');

inherits(MemCollection, Collection);
module.exports = MemCollection;

function MemCollection(name) {
  Collection.call(this);
  this.name_ = name;
  this.vals_ = [];
}

function noop() {}

MemCollection.prototype.find = function(q, opts, cb) {
  var that = this;
  cb = cb || noop;
  q = this.normalize_(q);
  var res = _.filter(this.vals_, function(v) {
    return that.matches_(v, q);
  });
  if (opts.sort) {
    // Note, we make various simplifying assumptions.
    var keys = _.keys(opts.sort);
    console.assert(keys.length === 1);
    var key = keys[0];
    console.assert(opts.sort[key] === 1);
    res = _.sortBy(res, key);
  }
  process.nextTick(function() {
    cb(null, _.cloneDeep(res));
  });
};

MemCollection.prototype.insert = function(v, cb) {
  var that = this;
  cb = cb || noop;
  console.assert(!v._id);
  v = _.assign({}, v, {_id: String(this.vals_.length)});
  this.vals_.push(v);
  process.nextTick(function() {
    that.emit('change');
    cb(null, v._id);
  });
};

MemCollection.prototype.remove = function(q, cb) {
  var that = this;
  cb = cb || noop;
  q = this.normalize_(q);
  this.vals_ = _.filter(this.vals_, function(v) {
    return !that.matches_(v, q);
  });
  process.nextTick(function() {
    that.emit('change');
    cb();
  });
};

MemCollection.prototype.update = function(q, opts, cb) {
  var that = this;
  cb = cb || noop;
  q = this.normalize_(q);
  var vals = _.filter(this.vals_, function(v) {
    return that.matches_(v, q);
  });

  // Note, we make various simplifying assumptions.
  var keys = _.keys(opts);
  console.assert(keys.length === 1);
  var key = keys[0];
  console.assert(_.contains(['$addToSet', '$pull', '$set'], key));
  var opt = opts[key];
  var fields = _.keys(opt);
  console.assert(fields.length === 1);
  var field = fields[0];

  _.each(vals, function(val) {
    switch (key) {
    case '$addToSet':
      val[field] = _.union(val[field], [opt[field]]);
      break;
    case '$pull':
      val[field] = _.without(val[field], opt[field]);
      break;
    case '$set':
      val[field] = opt[field];
      break;
    }
  });

  process.nextTick(function() {
    that.emit('change');
    cb();
  });
};

MemCollection.prototype.normalize_ = function(q) {
  if (_.isObject(q)) {
    return q;
  }
  return {_id: q};
};

MemCollection.prototype.matches_ = function(v, q) {
  var keys = _.keys(q);
  for (var i = 0; i < keys.length; i++) {
    var key = keys[i];
    if (_.isArray(v[key]) && !_.isArray(q[key])) {
      if (!_.contains(v[key], q[key])) {
        return false;
      }
    } else {
      if (q[key] !== v[key]) {
        return false;
      }
    }
  }
  return true;
};
