# Demo setup

This page describes how to set things up for a demo.
For detailed explanations of the setup steps, see [README.md](README.md).

FIXME: Currently, once anything is deleted, outgoing sync permanently stops
working.

## Single-machine setup

Run these commands once:

    DEBUG=1 make build
    ./bin/principal seekblessings --v23.credentials tmp/creds

Run these commands (each from its own terminal) on each reset:

    rm -rf tmp/syncbase* && PORT=5000 ./tools/start_services.sh
    PORT=5100 ./tools/start_services.sh

    DEBUG=1 PORT=5000 make serve
    DEBUG=1 PORT=5100 make serve

Open these urls:

    http://localhost:5000/?d=syncbase // Alice
    http://localhost:5100/?d=syncbase // Bob

### Syncing a list

1. In Alice's window, create list "Groceries".
2. Add todo items and tags (as desired).
3. Click the status button, then type in Bob's email address.
4. Copy the `/share/...` part of the url to the clipboard.
5. Switch to Bob's window.
6. Replace everything after `localhost:5100` with the copied path, hit enter.
7. After a second, Bob should see the synced "Groceries" list.
8. Add, edit, and remove todos and tags to your heart's content and watch sync
   do its magic.

## Two-machine setup

Have Alice and Bob do the following on their respective machines.

Run these commands once:

    DEBUG=1 make build
    ./bin/principal seekblessings --v23.credentials tmp/creds

Run these commands (each from its own terminal) on each reset:

    rm -rf tmp/syncbase* && PORT=5000 ./tools/start_services.sh

    DEBUG=1 PORT=5000 make serve

Open this url:

    http://localhost:5000/?d=syncbase

### Syncing a list

1. In Alice's window, create list "Groceries".
2. Add todo items and tags (as desired).
3. Click the status button, then type in Bob's email address.
4. Send Bob the entire url, and have him open that url.
5. After a second, Bob should see the synced "Groceries" list.
6. Add, edit, and remove todos and tags to your heart's content and watch sync
   do its magic.
