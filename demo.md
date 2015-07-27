# Demo setup

This page describes how to set things up for a demo.
For detailed explanations of the app setup steps, see [README.md](README.md).

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

    http://<hostname>:5000/?d=syncbase // Alice
    http://<hostname>:5100/?d=syncbase // Bob

## Two-machine setup

Have Alice and Bob do the following on their respective machines.

Run these commands once:

    DEBUG=1 make build
    ./bin/principal seekblessings --v23.credentials tmp/creds

Run these commands (each from its own terminal) on each reset:

    rm -rf tmp/syncbase* && PORT=5000 ./tools/start_services.sh

    DEBUG=1 PORT=5000 make serve

Open this url:

    http://<hostname>:5000/?d=syncbase

## Syncing a list

1. In Alice's window, create list "Groceries".
2. Add todo items and tags (as desired).
3. Click the status button, then type in Bob's email address.
4. Copy the `/share/...` part of the url. The hex suffix encodes the syncgroup
   name, which includes Alice's mount table name.
5. In Bob's window, replace everything after `<hostname>:5000` with the copied
   `/share/...` path.
5. After a second, Bob should see the synced "Groceries" list.
6. Add, edit, and remove todos and tags to your heart's content and watch sync
   do its magic.

Note, it's important to use `<hostname>` urls rather than `localhost` urls
because the web app parses the url from which it was loaded and adds 1 to the
port number to determine the local mount table name, which it uses as a prefix
for all syncgroup names that it creates. If the host is `localhost:5000`, the
app will use `/localhost:5001` as the mount table name, and remote peers will
not be able to contact the syncgroup. If we switch to a "predefined, global
mount table" model, this will no longer be an issue.
