# Todos app

Todos is an example app that demonstrates use of [Syncbase][syncbase].

## Running the web application

The commands below assume that the current working directory is
`$V23_ROOT/experimental/projects/todosapp`.

First, build all necessary binaries.

    DEBUG=1 make build

Next, if you haven't already, generate credentials to use for running the local
Syncbase daemon. When prompted, specify blessing extension "syncbase". Note, the
value of `--v23.credentials` should correspond to the `$TMPDIR` value specified
when running `start_syncbased.sh`.

    ./bin/principal seekblessings --v23.credentials tmp/creds

Next, start a local Syncbase daemon (in another terminal). Note, this script
expects credentials in `$TMPDIR/creds`, and configures Syncbase to persist data
under root directory `$TMPDIR/syncbase`.

    TMPDIR=tmp ./start_syncbased.sh

Finally, start the web app.

    DEBUG=1 make serve

Visit `http://localhost:4000` in your browser to access the app.

### Using Syncbase

By default, the web app will use an in-memory (in-browser-tab) local storage
engine, and will not talk to Syncbase at all. To configure the app to talk to
Syncbase, add `d=syncbase` to the url query params, or simply click the storage
engine indicator in the upper right corner to toggle it.

When using Syncbase, by default the app attempts to contact the Syncbase service
using the Vanadium object name `/localhost:8200`. To specify a different name,
add `n=<name>` to the url query params.

Beware that `start_syncbased.sh` starts Syncbase with completely open ACLs. This
is safe if Syncbase is only accessible locally (the default), but more dangerous
if this Syncbase instance is configured to be accessible via a global mount
table.

## Design and implementation

Todos is implemented as a single-page JavaScript web application that
communicates with a local Syncbase daemon through the
[Vanadium Chrome extension][crx]. The app UI is built using HTML and CSS, using
React as a model-view framework.

The Syncbase data layout and conflict resolution scheme for this app are
[described here][design]. For now, when an item is deleted, any sub-items that
were added concurrently (on some other device) are orphaned. Eventually, we'll
GC orphaned records; for now, we don't bother. This orphaning-based approach
enables us to use simple last-one-wins conflict resolution for all records
stored in Syncbase.

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

## Resources for debugging

- https://sites.google.com/a/google.com/v-prod/
- https://sites.google.com/a/google.com/v-prod/vanadium-services/how-to

### Commands

    $V23_ROOT/release/go/bin/namespace -v23.credentials=V23_CREDENTIALS -v23.namespace.root=V23_NAMESPACE glob "test/..."
    $V23_ROOT/release/go/bin/vrpc -v23.credentials=V23_CREDENTIALS -v23.namespace.root=V23_NAMESPACE signature "test/syncbase"
    $V23_ROOT/release/go/bin/debug -v23.credentials=V23_CREDENTIALS -v23.namespace.root=V23_NAMESPACE stats read /localhost:8200/__debug/stats/rpc/server/routing-id/393ccca2ee7979d026374e76b2846e0b/methods/Delete/latency-ms

[syncbase]: https://docs.google.com/document/d/12wS_IEPf8HTE7598fcmlN-Y692OWMSneoe2tvyBEpi0/edit#
[crx]: https://v.io/tools/vanadium-chrome-extension.html
[design]: https://docs.google.com/document/d/1GtBk75QmjSorUW6T6BATCoiS_LTqOrGksgqjqJ1Hiow/edit
