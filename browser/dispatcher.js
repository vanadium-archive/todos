// Note, our Dispatcher combines the following React Flux concepts: Actions,
// Dispatcher, and Stores.

'use strict';

module.exports = Dispatcher;

function Dispatcher(lists, todos) {
  this.lists_ = lists;
  this.todos_ = todos;
}

function noop() {}

// Note, we pass noop as the callback everywhere since our app handles all
// updates by watching for changes.
// TODO(sadovsky): Pass a callback and handle errors.
Dispatcher.prototype = {
  addList: function(name) {
    return this.lists_.insert({name: name}, noop);
  },
  editListName: function(listId, name) {
    this.lists_.update(listId, {$set: {name: name}}, noop);
  },
  addTodo: function(listId, text, tags) {
    return this.todos_.insert({
      listId: listId,
      text: text,
      done: false,
      timestamp: (new Date()).getTime(),
      tags: tags
    }, noop);
  },
  removeTodo: function(todoId) {
    this.todos_.remove(todoId, noop);
  },
  editTodoText: function(todoId, text) {
    this.todos_.update(todoId, {$set: {text: text}}, noop);
  },
  markTodoDone: function(todoId, done) {
    this.todos_.update(todoId, {$set: {done: done}}, noop);
  },
  addTag: function(todoId, tag) {
    this.todos_.update(todoId, {$addToSet: {tags: tag}}, noop);
  },
  removeTag: function(todoId, tag) {
    this.todos_.update(todoId, {$pull: {tags: tag}}, noop);
  }
};
