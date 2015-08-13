// Copyright 2015 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

// Collection-based implementation of Dispatcher.

'use strict';

var _ = require('lodash');
var inherits = require('inherits');

var Collection = require('./collection');
var Dispatcher = require('./dispatcher');

inherits(CollectionDispatcher, Dispatcher);
module.exports = CollectionDispatcher;

function noop() {}

function CollectionDispatcher(lists, todos) {
  Dispatcher.call(this);
  console.assert(lists instanceof Collection);
  console.assert(todos instanceof Collection);
  this.lists_ = lists;
  this.todos_ = todos;
}

CollectionDispatcher.prototype.getLists = function(cb) {
  this.lists_.find({}, {sort: {name: 1}}, cb);
};

CollectionDispatcher.prototype.getTodos = function(listId, cb) {
  this.todos_.find({listId: listId}, {sort: {timestamp: 1}}, cb);
};

CollectionDispatcher.prototype.addList = function(list, cb) {
  this.lists_.insert(list, this.maybeEmit_(cb));
};

CollectionDispatcher.prototype.editListName = function(listId, name, cb) {
  this.lists_.update(listId, {$set: {name: name}}, this.maybeEmit_(cb));
};

CollectionDispatcher.prototype.addTodo = function(listId, todo, cb) {
  todo = _.assign({}, todo, {listId: listId});
  this.todos_.insert(todo, this.maybeEmit_(cb));
};

CollectionDispatcher.prototype.removeTodo = function(todoId, cb) {
  this.todos_.remove(todoId, this.maybeEmit_(cb));
};

CollectionDispatcher.prototype.editTodoText = function(todoId, text, cb) {
  this.todos_.update(todoId, {$set: {text: text}}, this.maybeEmit_(cb));
};

CollectionDispatcher.prototype.markTodoDone = function(todoId, done, cb) {
  this.todos_.update(todoId, {$set: {done: done}}, this.maybeEmit_(cb));
};

CollectionDispatcher.prototype.addTag = function(todoId, tag, cb) {
  this.todos_.update(todoId, {$addToSet: {tags: tag}}, this.maybeEmit_(cb));
};

CollectionDispatcher.prototype.removeTag = function(todoId, tag, cb) {
  this.todos_.update(todoId, {$pull: {tags: tag}}, this.maybeEmit_(cb));
};

CollectionDispatcher.prototype.maybeEmit_ = function(cb) {
  var that = this;
  cb = cb || noop;
  return function(err) {
    cb.apply(null, arguments);
    if (err) return;
    that.emit('change');
  };
};
