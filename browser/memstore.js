// TODO(sadovsky): Use minimongo?

'use strict';

var _ = require('lodash');
var inherits = require('util').inherits;

var Collection = require('./collection');

inherits(Memstore, Collection);
module.exports = Memstore;

function Memstore(name) {
  Collection.call(this);
  this.name_ = name;
  this.vals_ = [];
}

Memstore.prototype.find = function(q, opts, cb) {
  var that = this;
  q = this.normalize_(q);
  var res = _.filter(this.vals_, function(v) {
    return that.matches_(v, q);
  });
  if (opts.sort) {
    // TODO(sadovsky): Eliminate simplifying assumptions.
    var keys = _.keys(opts.sort);
    console.assert(keys.length === 1);
    var key = keys[0];
    console.assert(opts.sort[key] === 1);
    res = _.sortBy(res, key);
  }
  return cb(null, _.cloneDeep(res));
};

Memstore.prototype.insert = function(v, cb) {
  console.assert(!_.has(v, '_id'));
  v = _.assign({}, v, {_id: this.vals_.length});
  this.vals_.push(v);
  this.emit('change');
  return cb(null, v._id);
};

Memstore.prototype.remove = function(q, cb) {
  var that = this;
  q = this.normalize_(q);
  this.vals_ = _.filter(this.vals_, function(v) {
    return !that.matches_(v, q);
  });
  this.emit('change');
  return cb();
};

Memstore.prototype.update = function(q, opts, cb) {
  var that = this;
  q = this.normalize_(q);
  var vals = _.filter(this.vals_, function(v) {
    return that.matches_(v, q);
  });

  // TODO(sadovsky): Eliminate simplifying assumptions.
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

  this.emit('change');
  return cb();
};

Memstore.prototype.normalize_ = function(q) {
  if (_.isObject(q)) {
    return q;
  }
  return {_id: q};
};

Memstore.prototype.matches_ = function(v, q) {
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
