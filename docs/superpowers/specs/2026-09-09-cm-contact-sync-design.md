# CM contact sync — design

**Status:** in implementation (batched, confirmed live in-app)
**Date:** 2026-09-09
**Repo:** cm-phone (fork of Fossify Phone)

## Goal

Integrate the dialer with **CM** (an external CRM). The app becomes CM-aware: it
shows CM contacts instead of native favorites, recognises CM numbers on incoming
calls and in the dialer, and lets the user push new contacts back to CM from the
call history.

## Constraints & decisions

- **`cm-api` does not exist yet.** This spec defines the contract the app needs.
  Until the real API is up, the app talks to an in-app **`FakeCmApi`** returning
  canned data (default in debug / when base URL is blank).
- **Auth = pairing code.** User logs into CM elsewhere, generates a short code,
  types it into the app. No OAuth, no embedded browser.
- **~200 contacts per user.** Small. No database — a single JSON file in the app
  files dir, hydrated into an in-memory index. All number lookups are synchronous
  and offline.
- **Sync every few hours.** Triggered on `MainActivity.onResume` when the cache is
  older than a threshold, plus a manual "Zaktualizuj bazę" button. No WorkManager.
- **Full cache.** Note / linked property / linked client are stored locally too,
  so the CM contact detail screen works offline.
- **Networking:** hand-rolled `CmApi` interface over OkHttp + kotlinx.serialization
  (already a project dep). Retrofit was considered but a plain interface gives a
  clean `Fake` / `Http` split and avoids two dependencies. The interface itself
  documents the contract.
- **Token storage:** `EncryptedSharedPreferences` (`androidx.security:security-crypto`).
- **UI refresh:** EventBus, matching the rest of the app.
- **Placement:** `src/main` — all flavors (`core` / `foss` / `gplay`).

## API contract (requirements for `cm-api`)

Base URL configurable in Settings. All calls except pairing send
`Authorization: Bearer <accessToken>`.

### `POST /device/pair`
Request: `{ "code": "ABC123" }`
Response: `{ "accessToken": "...", "refreshToken": "...", "expiresInSec": 3600,
"user": { "id": "...", "name": "Jan Kowalski" } }`
Errors: `401` invalid/expired code.

### `POST /device/refresh`
Request: `{ "refreshToken": "..." }`
Response: same shape as pair (minus `user`, optional).
Errors: `401` → app clears auth, CM tab returns to the pairing prompt.

### `GET /contacts?since=<epochMillis>`
`since` omitted = full list. Response:
```json
[
  {
    "id": "c1",
    "firstName": "Anna",
    "lastName": "Nowak",
    "phoneNumbers": [{ "number": "+48600100200", "label": "mobile" }],
    "photoUrl": "https://.../c1.jpg",
    "notes": ["Zainteresowana mieszkaniem 2-pok.", "Ma zdolność kredytową."],
    "relatedProperties": [{ "id": "p9", "label": "ul. Długa 4/12, Gdańsk" }],
    "relatedClients": [{ "id": "cl3", "label": "Biuro Nieruchomości XYZ" }],
    "updatedAt": 1725800000000
  }
]
```
`photoUrl` nullable; `notes`, `relatedProperties`, `relatedClients` default to `[]`.
Each `CmRef.id` deep-links into the CM web app: property ->
`<cmAppUrl>/app/builds/<id>`, prospect -> `<cmAppUrl>/app/clients/<id>`.

### `POST /contacts`
Request: `{ "firstName": "...", "lastName": "...", "note": "...",
"phoneNumbers": [{ "number": "+48..." }] }`
Response: the created contact (same shape as list items).

### `DELETE /device/pair` (optional, for explicit logout)

## Modules (`org.fossify.phone.cm`)

| File | Responsibility |
|---|---|
| `CmContact.kt` | `CmContact` (`notes`, `relatedProperties`, `relatedClients` lists), `CmPhoneNumber`, `CmRef` models (`@Serializable`). `fullName`. |
| `CmDtos.kt` | `PairRequest/Response`, `RefreshRequest/Response`, `CreateContactRequest`, `CmUser`. |
| `CmApi.kt` | `interface CmApi` — blocking `pair`, `refresh`, `getContacts`, `createContact`. |
| `HttpCmApi.kt` | OkHttp + kotlinx.serialization implementation. |
| `FakeCmApi.kt` | Canned data. Pair accepts any 4–8 char code. |
| `CmClient.kt` | Picks `FakeCmApi` vs `HttpCmApi` by base URL; builds OkHttp with auth interceptor (Bearer + one refresh retry on 401). |
| `CmAuthStore.kt` | `EncryptedSharedPreferences`: tokens, user name, `isPaired`, `save()`, `clear()`. |
| `CmContactStore.kt` | Loads/saves `cm_contacts.json`; in-memory `List` + `Map<String, CmContact>` index (E.164 + last-9-digits key). `findByNumber`, `getAll`, `replaceAll`, `upsert`. |
| `CmSyncManager.kt` | `syncIfStale(context)`, `syncNow(context, cb)`. Background thread. Posts `CmContactsUpdatedEvent`. |
| `CmEvents.kt` | `CmContactsUpdatedEvent`, `CmPairingStateChangedEvent`. |
| `CmExt.kt` | `Context.cmAuthStore`, number-normalisation helper. |

`Config.kt` / `Constants.kt` gain `cmBaseUrl`, `cmLastSyncTs`, `cmPairingUrl`, `cmAppUrl`.

## UI changes

### Batch 1 — Foundation + pairing
- New `CmFragment` replaces `FavoritesFragment` in the middle tab slot
  (`ViewPagerAdapter`, `MainActivity`). `FavoritesFragment` + `fragment_favorites.xml`
  removed; favorites-only menu items (view type, column count, custom sort) pruned.
- Tab label → `cm_tab` ("CM").
- Unpaired state: prompt "Podaj kod parowania" + code field + "Sparuj" +
  "Kliknij tutaj, aby zobaczyć kod" (opens `cmPairingUrl`).
- Paired state: empty list + placeholder (list rendering is Batch 2).

### Batch 2 — Sync + list
- `CmSyncManager.syncIfStale` in `MainActivity.onResume`; "Zaktualizuj bazę" button
  in the CM tab (and/or overflow menu).
- CM tab renders `CmContactStore.getAll()` via a `CmContactsAdapter`
  (name + number + photo). Search filters the list.

### Batch 3 — CM contact detail
- `CmContactActivity`: avatar + name + one call row per phone number (reuses
  `startCallWithConfirmationCheck`, SIM selector when applicable). Below, three
  read-only **collapsible** sections, each hidden when empty, each titled
  `"<name> (<count>)"`: **Notatki**, **Powiązane nieruchomości**,
  **Powiązani klienci poszukujący**. Opening one collapses the others; the first
  non-empty section starts expanded. Every property / prospect row has an
  "open in CM" button → `launchViewIntent` to
  `<cmAppUrl>/app/builds/<id>` or `<cmAppUrl>/app/clients/<id>`. Data from the
  local cache. `cmAppUrl` is a new Config value (default `https://app.crm.example`).
- CM tab item click → `CmContactActivity`.

### Batch 4 — Incoming / ongoing call name
- `getCallContact()` in `CallContactHelper.kt`: also look the number up in
  `CmContactStore`; on hit the CM name (and number label) win over the system
  contact and `CallContact.isCmContact` is set. Feeds `CallActivity`,
  `CallNotificationManager` and the conference list.
- `CallActivity`: when `isCmContact`, a blinking **"Kontakt z aplikacji"** badge
  (`caller_cm_badge`) shows under the caller name.

### Batch 5 — Dialer surfaces CM contacts
- While typing in `DialpadActivity`, CM contacts matching the input (by digits or
  T9 name) are listed **above** the system-contact results in their own section
  (`dialpad_cm_list`, reusing `item_cm_contact`); a system contact whose number is
  already a CM number is dropped from the lower list.
- A reminder bar (`dialpad_cm_hint`, "Sprawdź czy kontakt nie jest już zapisany w
  aplikacji") shows at the very top whenever there is input.
- Tapping a CM row places the call (`startCallWithConfirmationCheck`).
- (An earlier confirmation-dialog design, `CmNumberInUseDialog`, was dropped in
  favour of this inline treatment.)

### Batch 6 — Call history CM actions menu
- `RecentCallsAdapter`: tapping the **avatar or the name** opens a compact
  `menu_recent_cm_options` popup (the "…" overflow keeps its full menu):
  - **Zapisz w aplikacji** (only when not in CM) → `CmSaveContactDialog`
    (imię, nazwisko, notatka; phone read-only, prefilled). Save →
    `CmApi.createContact` → optimistic `CmContactStore.upsert` +
    `CmContactsUpdatedEvent`.
  - **Dopisz notatkę** → `CmAddNoteDialog` if in CM (`CmApi.addNote`, new
    contract endpoint), otherwise `CmSaveContactDialog` focused on the note.
  - **Otwórz szczegóły** → `CmContactActivity` if in CM, else the system
    contact details (hidden when neither exists).
  - **Zadzwoń** → normal call flow.
- The old "not in CM" trailing icon was dropped in favour of this menu.

### Batch 7 — Cleanup
- Strings + PL translations for every new string, ProGuard keep rules for
  kotlinx.serialization models, lint/detekt baselines, `release` build green.
- Settings: CM base URL + "Rozparuj urządzenie".

## Non-goals

- Editing CM contacts from the app beyond create + append-note.
- Property / client detail views (labels only).
- Real-time push. Conflict resolution beyond "server list replaces local".
- Multi-account on one device (pairing replaces any previous pairing).

## Testing

Each batch is verified by hand in the running app against `FakeCmApi`:
pair → list → detail → simulated incoming call → dialer warning → save-from-history.
Unit tests where they pay off: `CmContactStore` number indexing/lookup,
`FakeCmApi` create-then-list.
