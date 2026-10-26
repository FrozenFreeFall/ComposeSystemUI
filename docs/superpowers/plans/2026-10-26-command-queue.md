# CommandQueue Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement the steps below.

**Goal:** Let Android 16 system commands update the existing Compose bars and panels.

**Architecture:** The system flavor registers a Binder callback with StatusBarManagerService. CommandQueue decodes the supported status bar commands, posts them to the main thread, and calls SystemUiController directly. The demo flavor has an empty bridge. Existing device callbacks continue supplying clock, battery and Wi-Fi state.

**Tech Stack:** Kotlin, Compose mutableStateOf, Android 16 Binder/Parcel, JUnit 4.

**Spec:** User request: 补齐系统方面的 CommandQueue 收到相关信息修改状态栏的逻辑. Existing constraints are documented in README.md.

## Global Constraints

- System flavor targets Android 16 / API 36; demo remains API 26+.
- No Flow, Store, Reducer, business coroutines or packaged framework stubs.
- All Compose and window mutations occur on the main thread.
- Default display only, matching SystemWindowHost.

## Review Focus

- Registration snapshot must precede queued live callbacks.
- Disable changes must cancel shade previews and block all panel/navigation entry points.
- Commands for other displays must not affect the default display.
- Stopping the service must discard queued work and release controller references.
- A hidden navigation bar must not retain keyboard focus.

## Tasks

- [x] Add behavior tests for disable flags, bar visibility and appearance regions; confirm failure before implementing SystemBarState.
- [x] Add flavor-specific CommandQueue, runtime transaction-name lookup, registration snapshot and lifecycle cleanup. Decode only commands supported by the existing bars/panels.
- [x] Connect service/controller lifecycle, status icons, disable policies, appearance, visibility, transient bars and IME state to the existing windows/UI.
- [x] Run both flavors' unit tests, assemble and lint, review changes, and document runtime verification limits.

## Implementation notes

- WMS windowState is authoritative. App requestedVisibleTypes must not override IME/split-screen forced visibility. A regression test was observed failing before this correction.
- System window roots remain visible as Insets providers; WMS controls their surfaces.
- Transient bars need explicit automatic hiding through IWindowManager, with touch/panel/focus suspension.
- Changes stay in the existing checkout, without an unsolicited commit or branch operation.
