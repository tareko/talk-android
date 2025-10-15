# Prompt: Recreate Call Activity Resume Behavior from `master`

You are working on the Nextcloud Talk Android app. Start from the current `master` branch. The app should keep an ongoing call alive when it is backgrounded and reuse the existing `CallActivity` when the user returns from a notification, the recents screen, or picture-in-picture. Implement the following:

1. **Call lifecycle**
   - Prevent `CallActivity` from tearing down the ongoing call when it is backgrounded. Calls must continue without forcing a hangup, and the foreground notification should stay active.
   - Ensure WebRTC renderers (self view and PiP) are only initialized once and are safely reused when the UI is restored.
   - Avoid triggering `onCreate` when the user re-enters the call while it is already running. Resume the existing activity instance instead of launching a new one.

2. **Bring existing call to the front**
   - Add a helper (e.g., `CallActivity.show(...)`) that first searches for an existing `CallActivity` and, if found, moves it to the front. Only create a new activity if no running instance exists.
   - Update all in-app entry points (`ChatActivity`, foreground service notification actions, incoming call notification taps, etc.) to use the helper so they reuse the running activity.
   - Make sure notification actions continue to support the "Leave meeting" broadcast flow.

3. **Participant state handling**
   - Update `CallParticipantList` so self participant snapshots are indexed by session ID and by actor identity. Aggregated updates should not clear active sessions or mark the user as disconnected while the call is running.
   - Treat in-call flags as 64-bit values and preserve session ID lists when cloning participants to avoid lossy conversions.
   - Ignore partial updates that would otherwise trigger a disconnect and only tear down the call when an explicit self removal arrives while no call is active.

4. **Call UI stability**
   - Guard `CallActivity`'s teardown logic with a flag so lifecycle-driven `onDestroy` calls do not hang up active calls. Only dispose local streams during an explicit hangup.
   - When returning from PiP, restore the existing renderers without reinitializing them or rejoining the room.

After making these changes, ensure the build compiles (run `./gradlew :app:compileGenericDebugKotlin` if the Android SDK is available) and add any necessary documentation or strings. Finally, commit your changes with a descriptive message.
