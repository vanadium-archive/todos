# TODOs demo: Syncbase and Firebase

This is a TODOs application for Android which uses either Firebase or Syncbase
as a persistence layer.

## Building & running

- Install [Android Studio](http://developer.android.com/tools/studio/index.html)
- Open a project rooted in this directory

## Switching between persistence layers

To switch between Firebase and Syncbase use: `Build -> Select Build Variant`
and choose between the various options (e.g., `firebaseDebug`, `syncbaseDebug` etc.)

### Firebase

The firebase app is at https://vivid-heat-7354.firebaseio.com/.
Ross Wang and Alex Fandrianto manage the administration of the database.

*WARNING*: There is no authentication required in this demo, and the TODO list
is global, accessible to all. In a real TODOs app this would not be the case of
course. In the mean time, don't put anything sensitive there!
