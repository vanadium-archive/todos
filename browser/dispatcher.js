// Defines the Dispatcher interface. Loosely inspired by React Flux.

'use strict';

var EventEmitter = require('events').EventEmitter;
var inherits = require('util').inherits;

inherits(Dispatcher, EventEmitter);
module.exports = Dispatcher;

// Dispatcher interface. Dispatchers emit 'change' events.
function Dispatcher() {
  EventEmitter.call(this);
}

// Returns lists with _id.
Dispatcher.prototype.getLists = function(cb) {
  throw new Error('not implemented');
};

// Returns todos with _id and tags.
Dispatcher.prototype.getTodos = function(listId, cb) {
  throw new Error('not implemented');
};

// The given list must not have _id.
Dispatcher.prototype.addList = function(list, cb) {
  throw new Error('not implemented');
};

Dispatcher.prototype.editListName = function(listId, name, cb) {
  throw new Error('not implemented');
};

// The given todo must not have _id, but may have tags.
Dispatcher.prototype.addTodo = function(listId, todo, cb) {
  throw new Error('not implemented');
};

Dispatcher.prototype.removeTodo = function(todoId, cb) {
  throw new Error('not implemented');
};

Dispatcher.prototype.editTodoText = function(todoId, text, cb) {
  throw new Error('not implemented');
};

Dispatcher.prototype.markTodoDone = function(todoId, done, cb) {
  throw new Error('not implemented');
};

Dispatcher.prototype.addTag = function(todoId, tag, cb) {
  throw new Error('not implemented');
};

Dispatcher.prototype.removeTag = function(todoId, tag, cb) {
  throw new Error('not implemented');
};
