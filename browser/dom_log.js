'use strict';

/* global Mousetrap */

var util = require('./util');

var logEl;

function append(className, msg) {
  var msgEl = document.createElement('div');
  msgEl.className = className;
  msgEl.innerText = msg;
  logEl.appendChild(msgEl);
  logEl.scrollTop = logEl.scrollHeight;  // scroll to bottom
}

exports.init = function() {
  logEl = document.querySelector('#log');

  // TODO(sadovsky): Override other console methods as well, e.g. 'error'.
  var consoleLog = console.log.bind(console);
  console.log = function() {
    var args = [util.timestamp()].concat(Array.prototype.slice.call(arguments));
    consoleLog.apply(null, args);
    append('msg', args.join(' '));
  };

  window.onerror = function(errorMsg, url, lineNumber) {
    var args = [util.timestamp(), errorMsg];
    append('msg error', args.join(' '));
    // Show log if it's not already visible, so that the developer sees the
    // error.
    logEl.classList.add('visible');
  };

  Mousetrap.bind(['ctrl+l', 'meta+l'], function() {
    logEl.classList.toggle('visible');
  });
};
