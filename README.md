# Todos

Todos is an example app that demonstrates Syncbase.

## Running the web application

    make serve

## Resources for debugging

    https://sites.google.com/a/google.com/v-prod/
    https://sites.google.com/a/google.com/v-prod/vanadium-services/how-to

    $V23_ROOT/release/go/bin/namespace -v23.credentials=V23_CREDENTIALS -v23.namespace.root=V23_NAMESPACE glob "test/..."
    $V23_ROOT/release/go/bin/vrpc -v23.credentials=V23_CREDENTIALS -v23.namespace.root=V23_NAMESPACE signature "test/syncbase"
    $V23_ROOT/release/go/bin/debug -v23.credentials=V23_CREDENTIALS -v23.namespace.root=V23_NAMESPACE stats read /localhost:8200/__debug/stats/rpc/server/routing-id/393ccca2ee7979d026374e76b2846e0b/methods/Delete/latency-ms
