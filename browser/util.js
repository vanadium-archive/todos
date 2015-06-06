'use strict';

var _ = require('lodash');
var React = require('react');

exports.h = function(selector, props, children) {
  if (_.isPlainObject(props)) {
    console.assert(!props.id && !props.className);
  } else {
    children = props;
    props = {};
  }
  var parts = selector.split('.');
  var x = parts[0].split('#'), type = x[0], id = x[1];
  var className = parts.slice(1).join(' ');
  console.assert(type);
  props = _.assign({}, props, {
    id: id || undefined,
    className: className || undefined
  });
  return React.createElement(type, props, children);
};
