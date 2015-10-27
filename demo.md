# Demo setup

This page describes how to set things up for a demo.

We assume you've followed the [installation instructions][install] to install
prerequisites and fetch the Vanadium repositories, and installed the base and
Node.js profiles using `jiri v23-profile install base nodejs`. We also assume
that you've installed the [Vanadium Chrome extension][crx].

The commands below assume that the current working directory is
`$JIRI_ROOT/release/projects/todos`.

For detailed explanations of the app setup steps, see [README.md](README.md).

## Single-machine setup

This setup involves a single machine running two instances of Syncbase+Webapp,
one for Alice and one for Bob. You'll probably want to open the two Webapp
windows side-by-side.

Run these commands once:

    DEBUG=1 make build
    make creds

Run these commands (each from its own terminal) on each reset:

    rm -rf tmp && PORT=5000 ./tools/start_services.sh
    PORT=5100 ./tools/start_services.sh

    DEBUG=1 PORT=5000 make serve
    DEBUG=1 PORT=5100 make serve

Open these urls:

    http://<hostname>:5000/?d=syncbase // Alice
    http://<hostname>:5100/?d=syncbase // Bob

## Two-machine setup

This setup involves two machines, one for Alice and the other for Bob. Each
machine runs one instance of Syncbase+Webapp.

Have Alice and Bob do the following on their respective machines.

Run these commands once:

    DEBUG=1 make build
    make creds

Run these commands (each from its own terminal) on each reset:

    rm -rf tmp && PORT=5000 ./tools/start_services.sh

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

[install]: https://github.com/vanadium/docs/blob/master/installation.md
[crx]: https://github.com/vanadium/docs/blob/master/tools/vanadium-chrome-extension.md
