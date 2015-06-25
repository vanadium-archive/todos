# Todos

Todos is an example app that demonstrates Syncbase.

## Running the web application

    make serve

## Commands for debugging

    $V23_ROOT/release/go/bin/namespace glob -v23.namespace.root=V23_NAMESPACE -v23.credentials=V23_CREDENTIALS "test/*"
    $V23_ROOT/release/go/bin/vrpc signature -v23.namespace.root=V23_NAMESPACE -v23.credentials=V23_CREDENTIALS "test/syncbased/todos"

## Notes

- problem was that the extension defaults to prod mounttable and assumes
  blessings minted by prod identity server, but local mount table and syncbased
  run with local credentials and do not include dev.v.io in trusted roots.

- one solution is to configure the extension with local identityd,
  identitydBlessingUrl, and namespaceRoot, but this requires running Chrome as
  follows and manually editing the Chrome extension options on each restart.

  /Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome --ignore-certificate-errors --user-data-dir=/tmp/foo

- to avoid manually editing options, we could build the Chrome extension as part
  of "make serve" - that's what our tests do. ew.

- alternatively, we can include dev.v.io in our local mount table and
  syncbased's trusted root sets, and have their "root dirs" allow access to
  anyone. secure b/c these services are only accessible on localhost. for this
  to work, we'd need to configure the webapp to talk to the local mount table.

- even simpler, we could bypass mount table completely and have the webapp talk
  directly to local syncbased. in addition, instead of overriding the trusted
  root set, we can run the local syncbased with a dev.v.io blessing by using
  seekblessings. (both here and above, all extension opts are left untouched,
  and dev.v.io blessings are used.)
