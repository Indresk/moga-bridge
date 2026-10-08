# Frontend (React)

Location: `src/`. A small single-page React 19 app (Vite) that runs in the Android WebView. UI strings are Spanish; code and comments are English. There is no router: three tabs switch the visible view.

## Layers

Dependencies point down the list: a file may import only from layers below it.

```text
App.jsx                composition root: wires hooks to views, owns the shared error and the active tab
  views/               one screen each (ConnectionView, TestView, MappingView)
    components/        presentational, reusable pieces (no backend calls)
      hooks/           behaviour and state (talk to the backend through lib/api.js)
        lib/           api.js (the only invoke/listen), events.js, keys.js, platform.js
styles/                tokens.css (design tokens), base.css, components.css, pad.css
assets/legacy/         icons copied from the original app (homage)
```

Rules: **only `lib/api.js` calls `invoke`/`listen`/`addPluginListener`**; components never call the backend; hooks never render; views compose components and receive hook results as props.

## `lib/`

| File | Purpose |
|---|---|
| `api.js` | One function per Tauri command plus event subscriptions (`onStatus`, `onControllerState`, `onControllerError`, `onDevicesDiscovered`). |
| `events.js` | Event and plugin names; mirror `src-tauri/src/constants/events.rs`. |
| `keys.js` | Android key-code table, the control list (grouped for the UI), presets, the default mapping; mirror `KeyMapping` in `schemas/settings.rs`. |
| `platform.js` | `isAndroid` (the UI degrades gracefully in a desktop browser). |

## Hooks

| Hook | Owns |
|---|---|
| `useController` | Connection status, pairing flags, `connect`/`disconnect`. **Not** the live state. |
| `useControllerState` | The live controller state. Subscribes and tells the backend to start `moga-state` **only while mounted and the app is visible** (`set_state_stream`), and seeds itself with the latest state. Used only by `TestView`. |
| `useDeviceDiscovery` | Paired/discovered lists, scan start/stop, the plugin discovery listener (unregistered on unmount). |
| `useKeyMapping` | Local key-mapping edits, presets, save. Reads the select value *before* `setState` (React runs updaters later, when `event.currentTarget` is `null`). |
| `useOutputSettings` | Output mode, stick layout, helper status, isolation; refreshes when the app becomes visible. |
| `usePolling` | Calls a function repeatedly **only while enabled and the app is visible**, with a back-off schedule and immediate refresh on return. |
| `usePageVisible` | `document.visibilityState` as a hook. |

`onError` is the shared `setError` from `App` (stable identity, safe in dependency arrays).

## Components

`Button`, `Panel`, `SectionTitle`, `Message` (tones: info, warning, error, success), `StatusBadge` (legacy glyph, green when connected), `StatusTag`, `Tabs`, `Header`, `DeviceList`, `GamepadView` (+ `StickView`, `DpadView`), `ModeOption` (radio card, can be disabled with a note), `Switch`, `MappingGrid`, `CopyField` (selectable command + copy with feedback), `HelperPanel` (the *Ayudante uinput* panel).

## Views

| View | Shows |
|---|---|
| `ConnectionView` | Scan, paired/discovered controllers, connect/disconnect, the idle power-off note. |
| `TestView` | Live gamepad picture following the active stick layout, the isolation switch (gamepad mode), raw report. |
| `MappingView` | Pocket-only disclaimer, the helper panel, output mode, stick layout and button correspondence; the keyboard key mapping when the keyboard output is in effect. |

`MappingView` shows *effective* mode: the virtual gamepad is disabled until the helper is reachable, and keyboard is shown meanwhile (the saved preference is kept).

## Behaviour worth knowing

- **App flow:** the app jumps to *Prueba* when a controller connects; if a connected session ends unexpectedly it returns to *Conexión* where the explanation is shown.
- **Performance:** the live state never lives in `App`, so other tabs do not re-render per report; the backend does not emit it unless asked. Polling and the live stream both stop when the app is hidden. See [overview.md](overview.md#the-hot-path).
- **Cleanup:** every subscription, listener and timer has an unmount cleanup (`useControllerState`, `useDeviceDiscovery`, `usePolling`, `CopyField`).

## Styling

Plain CSS with design tokens (`tokens.css`): a homage to the original app's Holo look — accent blue `#33B5E5`, "on" green `#66CC00`, orange for warnings, dark greys. Holo-style tabs (blue underline) and section headings. Mobile first (the app runs on a phone); layouts stay usable at 320 px.

## Adding a screen or setting

1. **Backend:** add the command ([backend-rust.md](backend-rust.md#adding-a-command)) and a wrapper in `lib/api.js`.
2. **State:** put behaviour in a hook (`hooks/useThing.js`) that calls `api.js` and reports errors through `onError`.
3. **UI:** build the view from existing components; create a new component only when it is reused or clearly separable.
4. **Wire:** instantiate the hook in `App.jsx` and pass it to the view; for a new tab add it to `tabs` and the view switch.
5. **Names:** keep event/control names in sync with Rust and Kotlin (see [AGENTS.md](../../AGENTS.md)).
6. Run `pnpm build`; test on a device.

## Testing

There are no frontend tests yet ([roadmap](../project/roadmap.md)); hooks and components are structured so they can be tested with Vitest and Testing Library by mocking `lib/api.js`.
