# Minimal notifications implementation plan

> **For agentic workers:** Implement the assigned task in the current session; keep the common contracts below. Use failing behavior tests for the notification state and operations, and one final independent review.

**Goal:** Add real notification reception and a small Compose notification list to both existing build flavors.

**Architecture:** Share the notification listener, repository, models, and Compose UI in `main`. Only listener registration differs: the demo is bound by Android after the user grants notification access; the system build registers a listener directly with its platform privileges. Keep callbacks and Compose state on the main thread, with no Flow, MVI, or business coroutines.

**Tech stack:** Existing Kotlin, Compose Material3, Android NotificationListenerService, Robolectric/JUnit, SDK 36; demo minSdk 26, system minSdk 36.

**Spec:** The user's request for the simplest notification logic and Compose UI for ordinary and system builds. Builder decisions: newest-first flat cards, add/update/remove, initial active-notification snapshot, open, clear one/all clearable items, horizontal swipe, and a status-bar count. Reuse the current shade and quick settings. Omit grouping, ranking, heads-up, replies, RemoteViews, and lockscreen notification surfaces.

## Global constraints

- Both flavors share models, state, operations, and presentation; flavor-specific registration only.
- Ordinary apps require the user's notification-access grant; do not change that grant programmatically.
- System registration must have matching unregister/late-callback cleanup.
- Do not cancel ongoing/non-clearable notifications; wait for system callbacks to confirm cancellation.
- Ignore this app's window-service notification (package + ID 100 + null tag), and group summaries in this flat view.
- Closing the shade must not clear notifications. Release window input focus before sending a content PendingIntent.
- Keep changes in the user-provided workspace; do not commit, publish, or change other features.

## Review focus

- Updating a key replaces its card; removed/filtered updates cannot leave stale cards.
- Disconnected or obsolete listener instances cannot mutate a newly connected list.
- A canceled content PendingIntent and rejected cancellation produce a useful error without breaking the panel.
- A system listener must not display another foreground user's notifications, and must release its Binder registration.
- Revoked access/reconnection must clear stale content and rebuild the active snapshot.

## Task 1: Shared notification data and listener

Files: new `data/NotificationRepository.kt`, `data/SimpleNotificationListener.kt`; tests under `src/test/.../data`.

Contract: `NotificationItem(key, packageName, appName, title, text, postTime, icon, clearable, canOpen)`; `NotificationRepository(Context)` exposes Compose `items: List<NotificationItem>`, `connected: Boolean`, `error: String?`. Expose `contentIntent(key)`, `dismiss(key)`, `dismissAll()`, `reportError(message)` and listener snapshot/post/remove/disconnect methods guarded by instance identity.

- [x] Write and run failing tests for initial snapshot, same-key updates, removal, clearability, service/summary filtering, stale listener events, and reconnect.
- [x] Implement the shared state and listener callbacks using the simplest keyed map and a newest-first immutable list.
- [x] Run focused tests; a single Gradle process owns builds to avoid APK output races.

## Task 2: Compose presentation

File: new `ui/NotificationsSection.kt`.

Contract: `NotificationsSection(items, connected, error, onOpen, onDismiss, onClearAll, onAccessSettings: (() -> Unit)?)`. Render app icon/name, title/text, timestamp, empty/disconnected state; clearable rows support horizontal swipe and a clear button, all rows support opening if available. Use current theme/TV-focus conventions. No nested vertical scroller: the existing shade owns scrolling.

- [x] Implement the common stateless presentation.
- [x] Check it in both flavor builds and visually on the available demo emulator.

## Task 3: Registration and integration

Files: new flavor-specific `platform/NotificationAccess.kt`; update application, service, controller, activity, demo manifest, shade/status bar, and documentation.

- [x] Verify official Android registration and PendingIntent launch requirements.
- [x] Own one repository in the Application; pass it into the controller. Start/stop the system registration with the window service; demo listener lifetime belongs to Android.
- [x] Add the demo notification-access settings entry, and display connection state.
- [x] Add common open/dismiss/clear actions and shade UI; keep notifications available when quick settings are disabled.
- [x] Update README with exact setup and unsupported advanced features.

## Task 4: Verification and review

- [x] Both flavors: unit tests, assemble, lint; inspect merged manifests for permission separation.
- [x] Demo emulator: access was not granted, so verify the permission entry and disconnected state without changing the grant; real data/action behavior covered by tests.
- [x] Independent review of notification lifecycle, permissions, and UI integration; fix material findings and rerun affected checks.
- [x] Record evidence and any target-ROM testing limitation in BUILD_VALIDATION.md.
