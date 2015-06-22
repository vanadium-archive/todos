# Todos

Todos is an example app that demonstrates Syncbase.

## Running the web application

    make serve

## Commands for debugging

    $V23_ROOT/release/go/bin/namespace glob -v23.namespace.root=V23_NAMESPACE -v23.credentials=V23_CREDENTIALS "test/*"
    $V23_ROOT/release/go/bin/vrpc signature -v23.namespace.root=V23_NAMESPACE -v23.credentials=V23_CREDENTIALS "test/syncbased/todos"
