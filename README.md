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

    TMPDIR=tmp ./tools/start_syncbased.sh

    # Or, start from a clean slate.
    rm -rf tmp/syncbase* && TMPDIR=tmp ./tools/start_syncbased.sh

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

## Debugging notes

### Links

- https://sites.google.com/a/google.com/v-prod/
- https://sites.google.com/a/google.com/v-prod/vanadium-services/how-to

### Commands

Signature

    $V23_ROOT/release/go/bin/vrpc -v23.credentials=tmp/creds signature /localhost:8200

Method call

    $V23_ROOT/release/go/bin/vrpc -v23.credentials=tmp/creds call /localhost:8200 GetPermissions
    $V23_ROOT/release/go/bin/vrpc -v23.credentials=tmp/creds call /localhost:8200/todos/db/tb Scan '""' '""'

Glob

    $V23_ROOT/release/go/bin/namespace -v23.credentials=tmp/creds glob "/localhost:8200/..."

Debug

    $V23_ROOT/release/go/bin/debug -v23.credentials=tmp/creds glob "/localhost:8200/__debug/stats/rpc/server/routing-id/..."
    $V23_ROOT/release/go/bin/debug -v23.credentials=tmp/creds stats read "/localhost:8200/__debug/stats/rpc/server/routing-id/c61964ab4c72ee522067eb6d5ddd22fc/methods/BeginBatch/latency-ms"

### Integration test setup

For debugging performance issues, it can be helpful to use the JS integration
test configuration. To do so, first run the integration test as follows.

    cd $V23_ROOT/roadmap/javascript/syncbase
    NOQUIT=1 NOHEADLESS=1 make test-integration-browser

This command starts a local mount table, identityd, and Syncbase mounted at
test/syncbased, then launches an instance of Chrome with a custom-built Vanadium
extension configured to talk to the local mount table and identityd.

Scroll up in the test output to get the test environment configuration, in
particular the mount table endpoint, `V23_NAMESPACE`. Glob the locally mounted
syncbase as follows.

    $V23_ROOT/release/go/bin/namespace -v23.credentials=/usr/local/google/home/sadovsky/vanadium/roadmap/javascript/syncbase/tmp/test-credentials glob "/@5@ws@127.0.0.1:41249@7d24de5a57f6532b184562654ad2c554@m@test/child@@/test/syncbased/..."

Visit `http://localhost:4000/?d=syncbase&n=test/syncbased` in the launched
Chrome instance to talk to your test syncbase.

To run a simple benchmark (100 puts, followed by a scan of those rows), specify
query param `bm=1`.

### Open questions

- Why can test browser talk to normal syncbase and not to test syncbase? This is
  the opposite of what I'd expect given the blessings.
  - Glob from test browser to test syncbase (service.listApps) fails with "does
    not have Resolve access".
  - RPCs from test browser to normal syncbase should fail with "untrusted root",
    but instead they succeed.

- Why do test and normal browsers have different performance talking to the
  same (normal) syncbase?
  - Test browser: 100 puts takes 2s, scan takes 3.5s.
  - Normal browser: 100 puts takes 5s, scan takes 9s.

  With dev console closed, scan takes roughly 0.6s on both (see below), but 100
  puts still takes 2s in test browser vs. 4s in normal browser.

- Why is JS scan so slow? Note, latency appears to be proportional to data size,
  with some small fixed overhead. Also note, vrpc scan takes less than 0.3s.

  ANSWER: Turns out if the dev console is closed, scan is much faster (0.6s).
  Issue filed: https://github.com/vanadium/issues/issues/610

[syncbase]: https://docs.google.com/document/d/12wS_IEPf8HTE7598fcmlN-Y692OWMSneoe2tvyBEpi0/edit#
[crx]: https://v.io/tools/vanadium-chrome-extension.html
[design]: https://docs.google.com/document/d/1GtBk75QmjSorUW6T6BATCoiS_LTqOrGksgqjqJ1Hiow/edit
