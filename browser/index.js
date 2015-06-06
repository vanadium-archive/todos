// TODO(sadovsky): Maybe update to the new Meteor Todos UI.
// https://github.com/meteor/simple-todos

'use strict';

/* jshint newcap: false */

var _ = require('lodash');
var React = require('react');

var Dispatcher = require('./dispatcher');
var h = require('./util').h;

////////////////////////////////////////
// Global state

var defaults = require('./defaults');
var cLists = defaults.lists;
var cTodos = defaults.todos;

var d = new Dispatcher(cLists, cTodos);

////////////////////////////////////////
// Helpers

function noop() {}

function activateInput(input) {
  input.focus();
  input.select();
}

function okCancelEvents(callbacks) {
  var ok = callbacks.ok || noop;
  var cancel = callbacks.cancel || noop;
  function done(ev) {
    var value = ev.target.value;
    if (value) {
      ok(value, ev);
    } else {
      cancel(ev);
    }
  }
  return {
    onKeyDown: function(ev) {
      if (ev.which === 27) {  // esc
        cancel(ev);
      }
    },
    onKeyUp: function(ev) {
      if (ev.which === 13) {  // enter
        done(ev);
      }
    },
    onBlur: function(ev) {
      done(ev);
    }
  };
}

////////////////////////////////////////
// Components

var TagFilter = React.createFactory(React.createClass({
  displayName: 'TagFilter',
  render: function() {
    var that = this;
    var tagFilter = this.props.tagFilter;
    var tagInfos = [], totalCount = 0;
    _.each(this.props.todos, function(todo) {
      _.each(todo.tags, function(tag) {
        var tagInfo = _.find(tagInfos, function(x) {
          return x.tag === tag;
        });
        if (!tagInfo) {
          tagInfos.push({tag: tag, count: 1, selected: tagFilter === tag});
        } else {
          tagInfo.count++;
        }
      });
      totalCount++;
    });
    tagInfos = _.sortBy(tagInfos, 'tag');
    // Add "All items" tag.
    tagInfos.unshift({
      tag: null,
      count: totalCount,
      selected: tagFilter === null
    });
    return h('div#tag-filter.tag-list', [
      h('div.label', 'Show:')
    ].concat(_.map(tagInfos, function(tagInfo) {
      var count = h('span.count', '(' + tagInfo.count + ')');
      return h('div.tag' + (tagInfo.selected ? '.selected' : ''), {
        onMouseDown: function() {
          var newTagFilter = tagFilter === tagInfo.tag ? null : tagInfo.tag;
          that.props.setTagFilter(newTagFilter);
        }
      }, [tagInfo.tag === null ? 'All items' : tagInfo.tag, ' ', count]);
    })));
  }
}));

var Tags = React.createFactory(React.createClass({
  displayName: 'Tags',
  getInitialState: function() {
    return {
      addingTag: false
    };
  },
  componentDidUpdate: function() {
    if (this.state.addingTag) {
      activateInput(this.getDOMNode().querySelector('#edittag-input'));
    }
  },
  render: function() {
    var that = this;
    var children = [];
    _.each(this.props.tags, function(tag) {
      // Note, we must specify the "key" prop so that React doesn't reuse the
      // opacity=0 node after a tag is removed.
      children.push(h('div.tag.removable_tag', {key: tag}, [
        h('div.name', tag),
        h('div.remove', {
          onClick: function(ev) {
            ev.target.parentNode.style.opacity = 0;
            // Wait for CSS animation to finish.
            window.setTimeout(function() {
              d.removeTag(that.props.todoId, tag);
            }, 300);
          }
        })
      ]));
    });
    if (this.state.addingTag) {
      children.push(h('div.tag.edittag', h('input#edittag-input', _.assign({
        type: 'text',
        defaultValue: ''
      }, okCancelEvents({
        ok: function(value) {
          d.addTag(that.props.todoId, value);
          that.setState({addingTag: false});
        },
        cancel: function() {
          that.setState({addingTag: false});
        }
      })))));
    } else {
      children.push(h('div.tag.addtag', {
        onClick: function() {
          that.setState({addingTag: true});
        }
      }, '+tag'));
    }
    return h('div.item-tags', children);
  }
}));

var Todo = React.createFactory(React.createClass({
  displayName: 'Todo',
  getInitialState: function() {
    return {
      editingText: false
    };
  },
  componentDidUpdate: function() {
    if (this.state.editingText) {
      activateInput(this.getDOMNode().querySelector('#todo-input'));
    }
  },
  render: function() {
    var that = this;
    var todo = this.props.todo, children = [];
    if (this.state.editingText) {
      children.push(h('div.edit', h('input#todo-input', _.assign({
        type: 'text',
        defaultValue: todo.text
      }, okCancelEvents({
        ok: function(value) {
          d.editTodoText(todo._id, value);
          that.setState({editingText: false});
        },
        cancel: function() {
          that.setState({editingText: false});
        }
      })))));
    } else {
      children.push(h('div.destroy', {
        onClick: function() {
          d.removeTodo(todo._id);
        }
      }));
      children.push(h('div.display', [
        h('input.check', {
          type: 'checkbox',
          checked: todo.done,
          onClick: function() {
            d.markTodoDone(todo._id, !todo.done);
          }
        }),
        h('div.todo-text', {
          onDoubleClick: function() {
            that.setState({editingText: true});
          }
        }, todo.text)
      ]));
    }
    children.push(Tags({todoId: todo._id, tags: todo.tags}));
    return h('li.todo' + (todo.done ? '.done' : ''), children);
  }
}));

var Todos = React.createFactory(React.createClass({
  displayName: 'Todos',
  render: function() {
    var that = this;
    if (this.props.listId === null) {
      return null;
    }
    var children = [];
    if (this.props.todos === null) {
      children.push('Loading...');
    } else {
      var tagFilter = this.props.tagFilter, items = [];
      _.each(this.props.todos, function(todo) {
        if (tagFilter === null || _.contains(todo.tags, tagFilter)) {
          items.push(Todo({todo: todo}));
        }
      });
      children.push(h('div#new-todo-box', h('input#new-todo', _.assign({
        type: 'text',
        placeholder: 'New item'
      }, okCancelEvents({
        ok: function(value, ev) {
          var tags = tagFilter ? [tagFilter] : [];
          d.addTodo(that.props.listId, value, tags);
          ev.target.value = '';
        }
      })))));
      children.push(h('ul#item-list', items));
    }
    return h('div#items-view', children);
  }
}));

var List = React.createFactory(React.createClass({
  displayName: 'List',
  getInitialState: function() {
    return {
      editingName: false
    };
  },
  componentDidUpdate: function() {
    if (this.state.editingName) {
      activateInput(this.getDOMNode().querySelector('#list-name-input'));
    }
  },
  render: function() {
    var that = this;
    var list = this.props.list, child;
    // http://facebook.github.io/react/docs/forms.html#controlled-components
    if (this.state.editingName) {
      child = h('div.edit', h('input#list-name-input', _.assign({
        type: 'text',
        defaultValue: list.name
      }, okCancelEvents({
        ok: function(value) {
          d.editListName(list._id, value);
          that.setState({editingName: false});
        },
        cancel: function() {
          that.setState({editingName: false});
        }
      }))));
    } else {
      child = h('div.display', h('a.list-name' + (list.name ? '' : '.empty'), {
        href: '/lists/' + list._id
      }, list.name));
    }
    return h('div.list' + (list.selected ? '.selected' : ''), {
      onMouseDown: function() {
        that.props.setListId(list._id);
      },
      onClick: function(ev) {
        ev.preventDefault();  // prevent page refresh
      },
      onDoubleClick: function() {
        that.setState({editingName: true});
      }
    }, child);
  }
}));

var Lists = React.createFactory(React.createClass({
  displayName: 'Lists',
  render: function() {
    var that = this;
    var children = [h('h3', 'Todo Lists')];
    if (this.props.lists === null) {
      children.push(h('div#lists', 'Loading...'));
    } else {
      var lists = [];
      _.each(this.props.lists, function(list) {
        list.selected = that.props.listId === list._id;
        lists.push(List({
          list: list,
          setListId: that.props.setListId
        }));
      });
      children.push(h('div#lists', lists));
      children.push(h('div#createList', h('input#new-list', _.assign({
        type: 'text',
        placeholder: 'New list'
      }, okCancelEvents({
        ok: function(value, ev) {
          var id = d.addList(value);
          that.props.setListId(id);
          ev.target.value = '';
        }
      })))));
    }
    return h('div', children);
  }
}));

var Page = React.createFactory(React.createClass({
  displayName: 'Page',
  getInitialState: function() {
    return {
      lists: null,  // all lists
      todos: null,  // all todos for current listId
      listId: this.props.initialListId,  // current list
      tagFilter: null  // current tag
    };
  },
  fetchLists: function() {
    return cLists.find({}, {sort: {name: 1}});
  },
  fetchTodos: function(listId) {
    if (listId === null) {
      return null;
    }
    return cTodos.find({listId: listId}, {sort: {timestamp: 1}});
  },
  updateURL: function() {
    var router = this.props.router, listId = this.state.listId;
    router.navigate(listId === null ? '' : '/lists/' + String(listId));
  },
  componentDidMount: function() {
    var that = this;
    var lists = this.fetchLists();
    var listId = this.state.listId;
    if (listId === null && lists.length > 0) {
      listId = lists[0]._id;
    }
    this.setState({
      lists: lists,
      todos: this.fetchTodos(listId),
      listId: listId
    });
    this.updateURL();

    cLists.on('change', function() {
      that.setState({lists: that.fetchLists()});
    });
    cTodos.on('change', function() {
      that.setState({todos: that.fetchTodos(that.state.listId)});
    });
  },
  componentDidUpdate: function() {
    this.updateURL();
  },
  render: function() {
    var that = this;
    return h('div', [
      h('div#top-tag-filter', TagFilter({
        todos: this.state.todos,
        tagFilter: this.state.tagFilter,
        setTagFilter: function(tagFilter) {
          that.setState({tagFilter: tagFilter});
        }
      })),
      h('div#main-pane', Todos({
        todos: this.state.todos,
        listId: this.state.listId,
        tagFilter: this.state.tagFilter
      })),
      h('div#side-pane', Lists({
        lists: this.state.lists,
        listId: this.state.listId,
        setListId: function(listId) {
          if (listId !== that.state.listId) {
            that.setState({
              todos: that.fetchTodos(listId),
              listId: listId,
              tagFilter: null
            });
          }
        }
      }))
    ]);
  }
}));

////////////////////////////////////////
// UI initialization

var Router = Backbone.Router.extend({
  routes: {
    '': 'main',
    'lists/:listId': 'main'
  }
});
var router = new Router();

var page;
router.on('route:main', function(listId) {
  console.assert(!page);
  if (listId !== null) {
    listId = parseInt(listId, 10);
  }
  var props = {router: router, initialListId: listId};
  page = React.render(Page(props), document.getElementById('page'));
});

Backbone.history.start({pushState: true});
