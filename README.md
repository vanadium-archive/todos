# Todos app

Todos is an example app that demonstrates use of [Syncbase][syncbase], and is
originally based on the [Meteor Todos demo app][meteor-todos]. The high-level
requirements for this app are [described here][requirements].

## Running the web application

We assume you've followed the [installation instructions][install] to install
prerequisites and fetch the Vanadium repositories, and installed the Syncbase
and Node.js profiles using `jiri profile install syncbase nodejs`. We also
assume that you've installed the [Vanadium Chrome extension][crx].

The commands below assume that the current working directory is
`$JIRI_ROOT/release/projects/todos`.

First, build all necessary binaries.

    DEBUG=1 make build

Next, if you haven't already, generate credentials to use for running the local
daemons (mounttabled and syncbased). Leave the blessing extension field empty.

    make creds

Next, start local daemons (in another terminal). This script runs mounttabled
and syncbased at ports `$PORT+1` and `$PORT+2` respectively, and configures
Syncbase to persist its data under `tmp/syncbase_$PORT`. It expects to find
credentials in `creds`.

    PORT=4000 ./tools/start_services.sh

    # Or, start from a clean slate.
    rm -rf tmp && PORT=4000 ./tools/start_services.sh

Finally, start the web app.

    DEBUG=1 PORT=4000 make serve

Visit `http://localhost:4000` in your browser to access the app.

### Using Syncbase

By default, the web app will use an in-memory (in-browser-tab) local storage
engine, and will not talk to Syncbase at all. To configure the app to talk to
Syncbase, add `d=syncbase` to the url query params, or simply click the storage
engine indicator in the top right corner to toggle it.

When using Syncbase, by default the app attempts to contact the Vanadium object
name `/localhost:($PORT+1)/syncbase`, where `/localhost:($PORT+1)` is the local
mount table name and `syncbase` is the relative name of the Syncbase service. To
specify a different mount table name, add `mt=<name>` to the url query params,
e.g. `mt=/localhost:5001`. To specify a different Syncbase service name, add
`sb=<name>`, e.g. `sb=/localhost:4002`.

Beware that `start_services.sh` starts Syncbase with completely open ACLs. This
is safe if Syncbase is only accessible on the local network (the default), but
more dangerous if this Syncbase instance is configured to be accessible via a
global mount table.

## Design and implementation

Todos is implemented as a single-page JavaScript web application that
communicates with a local Syncbase daemon through the [Vanadium Chrome
extension][crx]. The app UI is built with HTML and CSS, using React as a
model-view framework.

The data layout and conflict resolution policies for this app are [detailed
here][design], and the v0 sync setup is [described here][demo-sync-setup]. The
basic data layout is as follows, where `todos`, `db`, and `tb` are the Syncbase
app, database, and table names respectively.

    todos/db/tb/<listId>                               --> List
    todos/db/tb/<listId>/todos/<todoId>                --> Todo
    todos/db/tb/<listId>/todos/<todoId>/tags/<tagName> --> nil

For now, when an item is deleted, any sub-items that were added concurrently (on
some other device) are orphaned. Eventually, we'll GC orphaned records; for now,
we don't bother. This orphaning-based approach enables us to use simple
last-one-wins conflict resolution for all records stored in Syncbase.

At startup, the web app checks whether its backing store (e.g. Syncbase) is
empty; if so, it writes some todo lists to the store (see
`browser/defaults.js`). Next, the app proceeds to render the UI. To do so, it
scans the store and sets up in-memory data structures representing the user's
todo lists, then draws the UI (using React) based on the state of these
in-memory data structures.

When a user performs a mutation through the UI, the app issues a corresponding
method call against its dispatcher (see `browser/dispatcher.js`), which ends up
writing to the backing store and emitting a `'change'` event. The web app
listens for `'change'` events; when one is received, it re-reads any pertinent
state from the backing store (again, via the dispatcher interface), updates its
in-memory data structures, and redraws the UI.

When changes are received via Syncbase sync, the dispatcher discovers these
changes (currently via polling; soon, via watch) and emits a `'change'` event,
triggering the same redraw procedure as described above.

## Debugging notes

### Commands

Signature

    $JIRI_ROOT/release/go/bin/vrpc -v23.credentials=creds signature /localhost:4002

Method call

    $JIRI_ROOT/release/go/bin/vrpc -v23.credentials=creds call /localhost:4002 GetPermissions
    $JIRI_ROOT/release/go/bin/vrpc -v23.credentials=creds call /localhost:4002/todos/db/tb Scan '""' '""'

Glob

    $JIRI_ROOT/release/go/bin/namespace -v23.credentials=creds glob "/localhost:4002/..."

Debug

    $JIRI_ROOT/release/go/bin/debug -v23.credentials=creds glob "/localhost:4002/__debug/stats/rpc/server/routing-id/..."
    $JIRI_ROOT/release/go/bin/debug -v23.credentials=creds stats read "/localhost:4002/__debug/stats/rpc/server/routing-id/c61964ab4c72ee522067eb6d5ddd22fc/methods/BeginBatch/latency-ms"

### Integration test setup

For debugging performance issues, it can be helpful to use the JS integration
test configuration. To do so, first run the integration test as follows.

    cd $JIRI_ROOT/release/javascript/syncbase
    NOQUIT=1 NOHEADLESS=1 make test-integration-browser

This command starts a local mount table, identityd, and Syncbase mounted at
test/syncbased, then launches an instance of Chrome with a custom-built Vanadium
extension configured to talk to the local mount table and identityd.

Scroll up in the test output to get the test environment configuration, in
particular the mount table endpoint, `V23_NAMESPACE`. Glob the locally mounted
syncbase as follows.

    $JIRI_ROOT/release/go/bin/namespace -v23.credentials=/usr/local/google/home/sadovsky/vanadium/release/javascript/syncbase/tmp/test-credentials glob "/@5@ws@127.0.0.1:41249@7d24de5a57f6532b184562654ad2c554@m@test/child@@/test/syncbased/..."

Visit `http://localhost:4000/?d=syncbase&sb=test/syncbased` in the launched
Chrome instance to talk to your test syncbase.

### Performance benchmarking

To run a simple benchmark (parallel 100 puts, followed by a scan of those rows),
specify query param `bm=1`. For meaningful performance measurements, be sure to
close the Chrome JavaScript console.

[syncbase]: https://docs.google.com/document/d/12wS_IEPf8HTE7598fcmlN-Y692OWMSneoe2tvyBEpi0/edit#
[meteor-todos]: https://github.com/meteor/simple-todos
[requirements]: https://docs.google.com/document/d/13pbomPQu73Nug8RletnbkqXooPtKMCwPKW9cVYQi_jY/edit
[install]: https://github.com/vanadium/docs/blob/master/installation.md
[crx]: https://github.com/vanadium/docs/blob/master/tools/vanadium-chrome-extension.md
[design]: https://docs.google.com/document/d/1GtBk75QmjSorUW6T6BATCoiS_LTqOrGksgqjqJ1Hiow/edit
[demo-sync-setup]: https://docs.google.com/document/d/1174a7LIL8jnV1fN174PPV4fO74LGNLi6ODAFEp5l5Rw/edit
