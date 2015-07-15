'use strict';

var _ = require('lodash');
var moment = require('moment');
var React = require('react');

exports.h = function(selector, props, children) {
  if (_.isPlainObject(props)) {
    console.assert(!props.id && !props.className);
  } else {
    children = props;
    props = {};
  }
  var parts = selector.split('.');
  var x = parts[0].split('#'), tagName = x[0], id = x[1];
  var className = parts.slice(1).join(' ');
  console.assert(tagName);
  props = _.assign({}, props, {
    id: id || undefined,
    className: className || undefined
  });
  return React.createElement(tagName, props, children);
};

// Returns a string timestamp, useful for logging.
var timestamp = exports.timestamp = function(t) {
  t = t || Date.now();
  return moment(t).format('HH:mm:ss.SSS');
};

var LOGGERS = [console.log.bind(console)];

exports.addLogger = function(logger) {
  LOGGERS.push(logger);
};

exports.log = function() {
  var args = [timestamp()].concat(Array.prototype.slice.call(arguments));
  _.forEach(LOGGERS, function(logger) {
    logger.apply(null, args);
  });
};
