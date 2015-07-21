'use strict';

/* jshint newcap: false */
/* global Mousetrap */

var _ = require('lodash');
var async = require('async');
var page = require('page');
var React = require('react');
var url = require('url');
var vanadium = require('vanadium');

var defaults = require('./defaults');
var domLog = require('./dom_log');
var h = require('./util').h;

////////////////////////////////////////
// Constants

var DISP_TYPE_COLLECTION = 'mem-collection';
var DISP_TYPE_SYNCBASE = 'syncbase';
var SYNCBASE_NAME = '/localhost:8200';  // default value

////////////////////////////////////////
// Global state

// Dispatcher, initialized by initDispatcher.
var disp;

// Used for query params.
var u = url.parse(window.location.href, true);

////////////////////////////////////////
// Helpers

function noop() {}

function initDispatcher(dispType, syncbaseName, benchmark, cb) {
  var clientCb = cb;
  cb = function(err, resDisp) {
    if (err) return clientCb(err);
    disp = resDisp;
    clientCb();
  };
  if (dispType === DISP_TYPE_COLLECTION) {
    console.assert(!benchmark);
    defaults.initCollectionDispatcher(cb);
  } else if (dispType === DISP_TYPE_SYNCBASE) {
    var vanadiumConfig = {
      logLevel: vanadium.vlog.levels.INFO,
      namespaceRoots: u.query.mounttable ? [u.query.mounttable] : undefined,
      proxy: u.query.proxy
    };
    vanadium.init(vanadiumConfig, function(err, rt) {
      if (err) return cb(err);
      defaults.initSyncbaseDispatcher(rt, syncbaseName, benchmark, cb);
    });
  } else {
    process.nextTick(function() {
      cb(new Error('unknown dispType: ' + dispType));
    });
  }
}

function activateInput(input) {
  input.focus();
  input.select();
}

function okCancelEvents(cbs, cancelOnBlur) {
  var ok = cbs.ok || noop;
  var cancel = cbs.cancel || noop;
  function done(e) {
    var value = e.target.value;
    if (value) {
      ok(value, e);
    } else {
      cancel(e);
    }
  }
  return {
    onKeyDown: function(e) {
      if (e.which === 27) {  // esc
        cancel(e);
      }
    },
    onKeyUp: function(e) {
      if (e.which === 13) {  // enter
        done(e);
      }
    },
    onBlur: function(e) {
      if (cancelOnBlur) {
        cancel(e);
      } else {
        done(e);
      }
    }
  };
}

////////////////////////////////////////
// Components

var TagsPane = React.createFactory(React.createClass({
  displayName: 'TagsPane',
  render: function() {
    var that = this;
    var tagFilter = this.props.tagFilter;
    var tagInfos = [], totalCount = 0;
    _.each(this.props.todos, function(todo) {
      _.each(todo.tags, function(tag) {
        var tagInfo = _.find(tagInfos, {tag: tag});
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
    return h('div#tags-pane', [
      h('div.label', {key: 'label'}, 'Show:')
    ].concat(_.map(tagInfos, function(tagInfo) {
      var count = h('span.count', {key: 'count'}, '(' + tagInfo.count + ')');
      return h('div.tag' + (tagInfo.selected ? '.selected' : ''), {
        key: 'tag:' + (tagInfo.tag || ''),
        onClick: function() {
          var newTagFilter = tagFilter === tagInfo.tag ? null : tagInfo.tag;
          that.props.setTagFilter(newTagFilter);
        }
      }, [(tagInfo.tag || 'All items'), count]);
    })));
  }
}));

var TodoTags = React.createFactory(React.createClass({
  displayName: 'TodoTags',
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
      children.push(h('div.tag.removable', {key: tag}, [
        h('div.name', {key: 'name'}, tag),
        h('div.remove', {
          key: 'remove',
          onClick: function(e) {
            disp.removeTag(that.props.todoId, tag);
          }
        })
      ]));
    });
    if (this.state.addingTag) {
      children.push(h('div.tag.edittag', {
        key: 'edittag'
      }, h('input#edittag-input', _.assign({
        type: 'text',
        defaultValue: ''
      }, okCancelEvents({
        ok: function(value) {
          disp.addTag(that.props.todoId, value);
          that.setState({addingTag: false});
        },
        cancel: function() {
          that.setState({addingTag: false});
        }
      })))));
    } else {
      children.push(h('div.tag.addtag', {
        key: 'addtag',
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
    var that = this, todo = this.props.todo, et = this.state.editingText;
    var hDescription;
    if (et) {
      hDescription = h('div.description', {
        key: 'description'
      }, h('input#todo-input', _.assign({
        type: 'text',
        defaultValue: todo.text
      }, okCancelEvents({
        ok: function(value) {
          disp.editTodoText(todo._id, value);
          that.setState({editingText: false});
        },
        cancel: function() {
          that.setState({editingText: false});
        }
      }))));
    } else {
      hDescription = h('div.description', {
        key: 'description',
        onDoubleClick: function() {
          that.setState({editingText: true});
        }
      }, todo.text);
    }
    var opts = (et ? '.edit' : '') + (todo.done ? '.done' : '');
    return h('li.todo-row' + opts, [
      h('div.destroy', {
        key: 'destroy',
        onClick: function() {
          disp.removeTodo(todo._id);
        }
      }),
      h('input.checkbox', {
        key: 'checkbox',
        type: 'checkbox',
        checked: todo.done,
        onChange: function() {
          disp.markTodoDone(todo._id, !todo.done);
        }
      }),
      hDescription,
      TodoTags({key: 'tags', todoId: todo._id, tags: todo.tags})
    ]);
  }
}));

var TodosPane = React.createFactory(React.createClass({
  displayName: 'TodosPane',
  render: function() {
    var that = this;
    var children = [];
    if (!this.props.listId || !this.props.todos) {
      children.push(h('div.loading', {key: 'loading'}, 'Loading...'));
    } else {
      var tagFilter = this.props.tagFilter, items = [];
      _.each(this.props.todos, function(todo) {
        if (tagFilter === null || _.contains(todo.tags, tagFilter)) {
          items.push(Todo({key: todo._id, todo: todo}));
        }
      });
      children.push(h('div#new-todo', {key: 'new-todo'}, h('input', _.assign({
        type: 'text',
        placeholder: 'New item'
      }, okCancelEvents({
        ok: function(value, e) {
          disp.addTodo(that.props.listId, {
            text: value,
            tags: tagFilter ? [tagFilter] : [],
            done: false,
            timestamp: Date.now()
          });
          e.target.value = '';
        }
      })))));
      children.push(h('ul#todo-list', {key: 'todo-list'}, items));
    }
    return h('div#todos-pane', children);
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
    var that = this, list = this.props.list;
    var children = [];
    // http://facebook.github.io/react/docs/forms.html#controlled-components
    if (this.state.editingName) {
      children.push(h('div.edit', {
        key: 'edit'
      }, h('input#list-name-input', _.assign({
        type: 'text',
        defaultValue: list.name
      }, okCancelEvents({
        ok: function(value) {
          disp.editListName(list._id, value);
          that.setState({editingName: false});
        },
        cancel: function() {
          that.setState({editingName: false});
        }
      })))));
    } else {
      if (this.props.useSyncbase) {
        children.push(h('div.status', {
          key: 'status',
          onClick: function(e) {
            e.stopPropagation();
            that.props.openStatusDialog(list._id);
          }
        }, h('div.circle' + (list.sg ? '.shared' : ''))));
      }
      children.push(h('div.display', {
        key: 'display'
      }, h('a.list-name' + (list.name ? '' : '.empty'), {
        href: '/lists/' + list._id,
        onClick: function(e) {
          e.preventDefault();
        }
      }, list.name)));
    }
    return h('div.list' + (list.selected ? '.selected' : ''), {
      onClick: function() {
        that.props.setListId(list._id);
      },
      onDoubleClick: function() {
        that.setState({editingName: true});
      }
    }, children);
  }
}));

var StatusPane = React.createFactory(React.createClass({
  displayName: 'StatusPane',
  componentDidMount: function() {
    var that = this;
    Mousetrap.bind('esc', function() {
      that.props.close();
    });
  },
  componentWillUnmount: function() {
    Mousetrap.unbind('esc');
  },
  render: function() {
    var that = this, list = this.props.list, shared = Boolean(list.sg);
    var loc = window.location;
    var shareUrl = loc.origin + '/share/' + list._id + loc.search;
    return h('div#status-pane', {
      onClick: function(e) {
        e.stopPropagation();
        if (e.target === e.currentTarget) {
          that.props.close();
        }
      }
    }, h('div.status-dialog', [
      h('h3', {key: 'title'}, 'Share with others'),
      h('input', _.assign({
        key: 'input',
        placeholder: 'Add email address',
        // TODO(sadovsky): Let the user add members to an existing SG once
        // Syncbase supports it.
        disabled: shared
      }, okCancelEvents({
        ok: function(value, e) {
          disp.createSyncGroup(list._id);
          e.target.value = '';
        },
        cancel: function(e) {
          e.target.value = '';
        }
      }, true))),
      // TODO(sadovsky): Exclude self from displayed member list?
      !shared ? null : h('div.shared-with', {key: 'shared-with'}, [
        h('div.subtitle', {key: 'subtitle'}, 'Currently shared with'),
        h('div.emails', {
          key: 'emails'
        }, _.map(list.sg.members, function(member) {
          return h('div.email', {key: member}, member);
        }))
      ]),
      !shared ? null : h('div.url', {key: 'url'}, [
        h('div.subtitle', {key: 'subtitle'}, 'Url to share with invitees'),
        h('div.value', {key: 'value'}, shareUrl)
      ]),
      h('div.close', {
        key: 'close',
        onClick: function() {
          that.props.close();
        }
      })
    ]));
  }
}));

var ListsPane = React.createFactory(React.createClass({
  displayName: 'ListsPane',
  render: function() {
    var that = this;
    var children = [h('div.lists-title', {key: 'title'}, 'Todo Lists')];
    if (!this.props.lists) {
      children.push(h('div.loading', {key: 'loading'}, 'Loading...'));
    } else {
      var lists = [];
      _.each(this.props.lists, function(list) {
        list.selected = that.props.listId === list._id;
        lists.push(List({
          key: list._id,
          list: list,
          useSyncbase: that.props.useSyncbase,
          setListId: that.props.setListId,
          openStatusDialog: that.props.openStatusDialog
        }));
      });
      children.push(h('div', {key: 'lists'}, lists));
      children.push(h('div.new-list', {key: 'new-list'}, h('input', _.assign({
        type: 'text',
        placeholder: 'New list'
      }, okCancelEvents({
        ok: function(value, e) {
          disp.addList({name: value}, function(err, listId) {
            if (err) throw err;
            that.props.setListId(listId);
          });
          e.target.value = '';
        }
      })))));
    }
    return h('div#lists-pane', children);
  }
}));

var DispType = React.createFactory(React.createClass({
  render: function() {
    var that = this;
    return h('div#disp-type.' + this.props.dispType, {
      onClick: function() {
        that.props.toggleDispType();
      }
    }, this.props.dispType);
  }
}));

var Page = React.createFactory(React.createClass({
  displayName: 'Page',
  getInitialState: function() {
    return {
      dispInitialized: false,
      // Note, SG data is attached to individual list items.
      lists: {seq: 0, items: null},  // all lists
      todos: {},  // map of listId to {seq, items}
      listId: this.props.initialListId,  // current list
      tagFilter: null,  // current tag
      showStatusDialog: false
    };
  },
  getSyncGroup_: function(listId, cb) {
    console.assert(this.props.dispType === DISP_TYPE_SYNCBASE);
    disp.getSyncGroup(listId, cb);
  },
  getLists_: function(cb) {
    disp.getLists(function(err, lists) {
      if (err) return cb(err);
      // Sort lists by name in the UI.
      return cb(null, _.sortBy(lists, 'name'));
    });
  },
  getTodos_: function(listId, cb) {
    if (!listId) {
      return process.nextTick(cb);
    }
    disp.getTodos(listId, function(err, todos) {
      if (err) return cb(err);
      // Sort todos by timestamp in the UI.
      return cb(null, _.sortBy(todos, 'timestamp'));
    });
  },
  setListId_: function(listId) {
    if (listId === this.state.listId) return;
    this.setState({listId: listId, tagFilter: null});
  },
  updateURL: function() {
    var listId = this.state.listId;
    var pathname = !listId ? '/' : '/lists/' + listId;
    // Note, this doesn't trigger a re-render; it's purely visual.
    window.history.replaceState({}, '', pathname + window.location.search);
  },
  componentWillMount: function() {
    var that = this, props = this.props;
    var dt = props.dispType, sn = props.syncbaseName, bm = props.benchmark;
    function done() {
      that.setState({dispInitialized: true});
    }
    initDispatcher(dt, sn, bm, function(err) {
      if (err) throw err;
      if (props.joinListId) {
        console.assert(dt === DISP_TYPE_SYNCBASE);
        disp.joinSyncGroup(props.joinListId, function(err) {
          if (err) throw err;
          done();
        });
      } else {
        done();
      }
    });
  },
  componentDidMount: function() {
    console.assert(!this.state.dispInitialized);
  },
  componentDidUpdate: function(prevProps, prevState) {
    var that = this;
    this.updateURL();

    // Only run the code below when disp has just been initialized.
    if (prevState.dispInitialized || !this.state.dispInitialized) {
      return;
    }

    // Returns the list id for the list that should be displayed.
    function getListId() {
      var listId = that.state.listId, lists = that.state.lists.items;
      // If listId refers to an unknown list, set it to null.
      if (!_.includes(_.pluck(lists, '_id'), listId)) {
        listId = null;
      }
      // If listId is not set, set it to the id of the first list.
      if (!listId && lists.length > 0) {
        listId = lists[0]._id;
      }
      return listId;
    }

    // Updates lists. Calls cb once the setState call has completed.
    // TODO(sadovsky): If possible, simplify how we deal with concurrent state
    // updates, here and elsewhere. The current approach is fairly subtle and
    // error-prone. Our goal is simple: never show stale data, even in the
    // presence of sync.
    function updateLists(cb) {
      var listsSeq = that.state.lists.seq + 1;
      that.getLists_(function(err, lists) {
        if (err) return cb(err);
        // Use setState(cb) form to ensure atomicity.
        // References: https://goo.gl/CZ82Vp and https://goo.gl/vVCp8B
        that.setState(function(state) {
          if (listsSeq <= state.lists.seq) {
            return {};
          }
          return {lists: {seq: listsSeq, items: lists}};
        }, cb);
      });
    }

    // Updates todos for the specified list. Calls cb once the setState call
    // has completed.
    function updateTodos(listId, cb) {
      var stateTodos = that.state.todos[listId];
      var todosSeq = (stateTodos ? stateTodos.seq : 0) + 1;
      that.getTodos_(listId, function(err, todos) {
        if (err) return cb(err);
        // Use setState(cb) form to ensure atomicity.
        // https://goo.gl/CZ82Vp
        that.setState(function(state) {
          var stateTodos = state.todos[listId];
          if (stateTodos && todosSeq <= stateTodos.seq) {
            return {};
          }
          state.todos[listId] = {seq: todosSeq, items: todos};
          return {todos: state.todos};
        }, cb);
      });
    }

    // TODO(sadovsky): Only read (and only redraw) what's needed based on what
    // changed.
    disp.on('change', function() {
      updateLists(function(err) {
        if (err) throw err;
        var listId = getListId();
        updateTodos(listId, function(err) {
          if (err) throw err;
        });
      });
    });

    // Load initial lists and todos. Note that changes can come in concurrently
    // via sync.
    updateLists(function(err) {
      if (err) throw err;
      // Set initial listId if needed.
      var listId = getListId();
      if (listId !== that.state.listId) {
        that.setState({listId: listId});
      }
      // Get todos for all lists.
      var listIds = _.pluck(that.state.lists.items, '_id');
      async.each(listIds, updateTodos, function(err) {
        if (err) throw err;
      });
    });
  },
  render: function() {
    if (this.props.benchmark) {
      return null;
    }

    var that = this, listId = this.state.listId;
    var useSyncbase = this.props.dispType === DISP_TYPE_SYNCBASE;
    // If currTodos is {}, todos.items will be undefined, as desired.
    var currTodos = this.state.todos[listId] || {};
    return h('div#page-pane', [
      DispType({
        key: 'DispType',
        dispType: this.props.dispType,
        toggleDispType: function() {
          var newDispType = DISP_TYPE_SYNCBASE;
          if (that.props.dispType === DISP_TYPE_SYNCBASE) {
            newDispType = DISP_TYPE_COLLECTION;
          }
          window.location.href = '/?d=' + newDispType + '&n=' + SYNCBASE_NAME;
        }
      }),
      ListsPane({
        key: 'ListsPane',
        lists: this.state.lists.items,
        listId: listId,
        useSyncbase: useSyncbase,
        setListId: this.setListId_,
        openStatusDialog: function(listId) {
          console.assert(useSyncbase);
          if (that.state.showStatusDialog) {
            return;
          }
          // Note, setState just schedules render, so setListId's state update
          // will be merged with ours.
          that.setListId_(listId);
          that.setState({
            showStatusDialog: true
          });
        }
      }),
      h('div#tags-and-todos-pane', {key: 'tags-and-todos-pane'}, [
        TagsPane({
          key: 'TagsPane',
          todos: currTodos.items,
          tagFilter: this.state.tagFilter,
          setTagFilter: function(tagFilter) {
            that.setState({tagFilter: tagFilter});
          }
        }),
        TodosPane({
          key: 'TodosPane',
          todos: currTodos.items,
          listId: listId,
          tagFilter: this.state.tagFilter
        })
      ]),
      !this.state.showStatusDialog ? null : StatusPane({
        key: 'status',
        list: _.find(this.state.lists.items, {_id: listId}),
        close: function() {
          that.setState({showStatusDialog: false});
        }
      })
    ]);
  }
}));

////////////////////////////////////////
// Initialization

// Start our DOM log module. Developers can press Ctrl+L (or Meta+L) to toggle
// visibility of the log.
domLog.init();

function render(listId, doJoin) {
  var dispType = u.query.d || DISP_TYPE_COLLECTION;
  var syncbaseName = u.query.n || SYNCBASE_NAME;
  var benchmark = Boolean(u.query.bm);
  var props = {
    initialListId: listId,
    dispType: dispType,
    syncbaseName: syncbaseName,
    benchmark: benchmark
  };
  if (doJoin) {
    props.joinListId = listId;
  }
  React.render(Page(props), document.querySelector('#page'));
}

// Configure Page.js routes. Note, ctx here is a Page.js context object, not a
// Vanadium context object.
page('/', function(ctx) {
  render(null, false);
});
page('/lists/:listId', function(ctx) {
  render(ctx.params.listId, false);
});
page('/share/:listId', function(ctx) {
  render(ctx.params.listId, true);
});

// Start Page.js router.
// https://visionmedia.github.io/page.js/
page({click: false});
