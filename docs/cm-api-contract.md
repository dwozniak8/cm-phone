# CM ↔ Phone app — API & frontend contract

**Audience:** CM API team, CM web-app (frontend) team
**Client:** `cm-phone` — an Android dialer (fork of Fossify Phone) that shows CM
contacts, recognises CM numbers on calls, and can push new contacts / notes back
to CM.
**Status:** proposal from the client side. Field names below are what the app
already (de)serialises — changing them means a client change, so please flag any.
**Version:** 1 (2026-09-10)

---

## 1. What the client needs, in one paragraph

Each phone is **paired** to one CM user with a short code. After pairing the app
periodically pulls that user's **full contact list** (~200 rows) and caches it on
device, so every lookup (incoming call, dialer, call history) is offline. From
the call history the user can **create a CM contact** or **append a note** to an
existing one. The app also builds **deep links** into the CM web app for related
properties and prospects. That's the whole surface: 1 pairing endpoint, 1 token
refresh, 1 list read, 2 writes.

---

## 2. Terminology

| Term | Meaning |
|---|---|
| **Pairing code** | Short, single-use, short-lived code the user generates in CM and types into the phone. |
| **Service address** | The URL the user types on the pairing screen (or is sent to by "Auto-configuration"). `POST /device/pair` lives here. |
| **Access token** | Bearer token for all data calls. Short-lived. |
| **Refresh token** | Long-lived token used to mint a new access token without re-pairing. |
| **`baseUrl`** | API origin for everything after pairing (`/contacts`, …). Returned by `/device/pair`. May equal the service address or be a different host. |
| **`appUrl`** | CM **web app** origin, used to open records in a browser. Returned by `/device/pair`. |

The app stores `baseUrl`, `appUrl`, both tokens and the user's display name. It
never persists the pairing code.

---

## 3. Conventions

- **HTTPS only.** Plain HTTP is rejected by the client on modern Android anyway.
- **JSON**, `Content-Type: application/json; charset=utf-8`, UTF-8 everywhere.
- **Timestamps**: integer **milliseconds since the Unix epoch, UTC** (e.g.
  `updatedAt`, the `since` query param).
- **IDs** (`contact.id`, `user.id`, `CmRef.id`) are **opaque strings**. The app
  never parses them; it only echoes them back and puts them in deep links.
- The client **ignores unknown response fields**, so the API may add fields
  without breaking it. The client also omits `null` fields in request bodies.
- **Auth header**: `Authorization: Bearer <accessToken>` on every endpoint
  **except** `/device/pair` and `/device/refresh`.
- Timeouts the client uses: **connect 15 s, read 30 s**. Keep responses well
  under that.
- No pagination is currently implemented on the client (see §7). `GET /contacts`
  must return the whole list (optionally filtered by `since`) in one response.

### Error model (please adopt)

Any non-2xx response should carry this body so the app can tell the user
something useful:

```json
{ "error": { "code": "invalid_pairing_code", "message": "The code has expired." } }
```

`error.code` is a stable snake_case string; `error.message` is human text (may be
shown to the user). Status-code usage:

| Status | When | Client reaction |
|---|---|---|
| `400` | Malformed body / validation error | Toast the message |
| `401` | Missing/expired/invalid access token | Drops pairing, returns to the pairing screen |
| `403` | Token valid but not allowed to do this | Toast the message |
| `404` | Unknown contact id (on note/create paths) | Toast "contact no longer available" |
| `409` | Duplicate (e.g. contact already exists) | Toast the message |
| `422` | Semantic validation (bad phone number, …) | Toast the message |
| `429` | Rate limited | Toast, user can retry later |
| `5xx` | Server error | Toast "could not reach CM" |

On `/device/pair` specifically, use **`401`** for a bad/expired code (the client
maps only `401` there to "invalid code"; anything else it reports as a
connectivity problem).

---

## 4. Flows

### 4.1 Pairing (manual)

1. User logs into CM elsewhere and generates a **pairing code**.
2. On the phone: enters **code** + **service address** (pre-filled with an
   example we will replace with the real default), taps **Sparuj**.
3. App → `POST <serviceAddress>/device/pair { code }`.
4. API validates the code, creates a device/session, returns tokens **plus
   `baseUrl` and `appUrl`**.
5. App stores everything and immediately does a first `GET /contacts`.

### 4.2 Pairing (auto-configuration)

The pairing screen also has an **"Autokonfiguracja"** button. Today it simply
opens the **service address** in the system browser. The **frontend** must serve
a usable page there that:

- authenticates the CM user (normal web login),
- shows / generates a pairing code (and the service address, in case they typed
  it wrong),
- **ideally** offers a one-tap "open in Phone app" link — see §6.3 (phase 2).

### 4.3 Periodic sync

- On app foreground, if the cache is older than **~4 h**, the app calls
  `GET /contacts?since=<lastSyncMillis>` (first sync omits `since`).
- There is also a manual "Zaktualizuj bazę" action with no `since`.
- The response **replaces** the local cache for the returned rows. Delta
  semantics are defined in §5.3 — we need a way to signal deletions.

### 4.4 Token refresh

- When a data call returns `401`, the app should (future change, not yet wired)
  call `POST /device/refresh { refreshToken }` once, then retry.
- If refresh also fails → the app clears the pairing and shows the pairing
  screen. Please make refresh-token lifetime long (weeks/months) so this is rare.

### 4.5 Unpair

- User can unpair from the app's Settings ("Rozparuj urządzenie"). Currently the
  app just deletes its local tokens + cache. **Optional but wanted:**
  `DELETE /device/pair` (bearer auth) so the server can revoke the session too.

---

## 5. Data models

### 5.1 `Contact`

| Field | Type | Null? | Notes |
|---|---|---|---|
| `id` | string | no | Opaque, stable, unique per CM user. |
| `firstName` | string | no | May be `""`. |
| `lastName` | string | no | May be `""`. |
| `phoneNumbers` | `PhoneNumber[]` | no | May be empty, but such a contact is useless to the app — prefer to omit numberless contacts. First entry is treated as primary. |
| `photoUrl` | string | yes | Absolute HTTPS URL to an image, or omitted. Not yet rendered on calls, but will be. |
| `notes` | string[] | no | Free-text notes, newest last. May be empty. |
| `relatedProperties` | `Ref[]` | no | Real-estate records linked to this contact. May be empty. |
| `relatedClients` | `Ref[]` | no | "Prospect" / seeking-client records linked to this contact. May be empty. |
| `updatedAt` | int (epoch ms) | no | Last modification of this contact server-side. Drives `since`. |

### 5.2 `PhoneNumber`, `Ref`, `User`

```jsonc
// PhoneNumber
{ "number": "+48600100200", "label": "mobile" }   // label optional, free text

// Ref  (a related property or prospect)
{ "id": "p9", "label": "ul. Długa 4/12, Gdańsk" }  // label is what the app shows

// User  (in the pair response)
{ "id": "u_123", "name": "Jan Kowalski" }          // name shown as "Paired with …"
```

- `PhoneNumber.number`: send in **E.164** (`+48…`) when known. The app matches
  by the **last 9 digits**, so national format also works, but E.164 is safest.

### 5.3 Delta sync semantics (decision needed)

`GET /contacts?since=T` must return **every contact with `updatedAt > T`**. That
covers creates and edits. It does **not** cover deletions. Options, pick one:

- **A (preferred):** include tombstones — deleted contacts come back as
  `{ "id": "...", "deleted": true }` when `updatedAt > T`. The app removes them.
- **B:** no `since` support at all; the app always pulls the full list and treats
  it as the complete set (simplest server-side, ~200 rows every 4 h per device).

Until this is decided the client uses "full list replaces local", i.e. option B.

---

## 6. Endpoints

Base for §6.1–6.2 is the **service address**; for §6.4–6.6 it is **`baseUrl`**
from the pair response.

### 6.1 `POST /device/pair`

Exchange a pairing code for tokens and the URLs the app should use.

- **Auth:** none.
- **Request:**

```json
{ "code": "7Q4KZ2" }
```

- **Response `200`:**

```json
{
  "accessToken": "eyJhbGciOiJ...",
  "refreshToken": "def50200a1b2...",
  "expiresInSec": 3600,
  "user": { "id": "u_123", "name": "Jan Kowalski" },
  "baseUrl": "https://api.crm.example",
  "appUrl": "https://app.crm.example"
}
```

- `refreshToken` optional (omit if you don't support refresh yet).
- `expiresInSec` — access-token TTL; `0`/omitted = "unknown", the app will just
  react to `401`.
- `baseUrl` / `appUrl` — if omitted, the app falls back to the service address
  for both. Always returning them explicitly is preferred.
- **Errors:** `401` invalid/expired/already-used code.

### 6.2 `POST /device/refresh`

- **Auth:** none (the refresh token is the credential).
- **Request:**

```json
{ "refreshToken": "def50200a1b2..." }
```

- **Response `200`:**

```json
{ "accessToken": "eyJhbGciOiJ...", "refreshToken": "def50200c3d4...", "expiresInSec": 3600 }
```

- `refreshToken` in the response is optional (rotation). If present, the app
  stores the new one.
- **Errors:** `401` invalid/expired refresh token → app forces re-pairing.

### 6.3 `DELETE /device/pair` *(optional)*

- **Auth:** bearer.
- **Request:** empty body.
- **Response:** `204`.
- Revokes the current device session. The app calls its local cleanup
  regardless of the result.

### 6.4 `GET /contacts`

The paired user's contacts.

- **Auth:** bearer.
- **Query:** `since` — optional, epoch ms. Omitted = full list.
- **Response `200`:** a JSON **array** of `Contact` (see §5.1). Not wrapped in an
  object.

```json
[
  {
    "id": "c1",
    "firstName": "Anna",
    "lastName": "Nowak",
    "phoneNumbers": [{ "number": "+48600100200", "label": "mobile" }],
    "photoUrl": "https://app.crm.example/media/contacts/c1.jpg",
    "notes": [
      "Zainteresowana mieszkaniem 2-pokojowym do 600 tys. Oddzwonić w piątek.",
      "Ma zdolność kredytową potwierdzoną przez doradcę."
    ],
    "relatedProperties": [
      { "id": "p9",  "label": "ul. Długa 4/12, Gdańsk" },
      { "id": "p11", "label": "ul. Podwale Staromiejskie 20/3, Gdańsk" }
    ],
    "relatedClients": [
      { "id": "cl3", "label": "Biuro Nieruchomości XYZ" }
    ],
    "updatedAt": 1725800000000
  },
  {
    "id": "c3",
    "firstName": "Katarzyna",
    "lastName": "Wiśniewska",
    "phoneNumbers": [
      { "number": "+48734000111", "label": "mobile" },
      { "number": "+48583030303", "label": "work" }
    ],
    "notes": [],
    "relatedProperties": [],
    "relatedClients": [
      { "id": "cl7", "label": "Deweloper Bałtyk Sp. z o.o." },
      { "id": "cl9", "label": "Fundusz Mieszkania Plus" }
    ],
    "updatedAt": 1725820000000
  }
]
```

- **Errors:** `401`.

### 6.5 `POST /contacts`

Create a contact from a call-log entry ("Zapisz w aplikacji").

- **Auth:** bearer.
- **Request:**

```json
{
  "firstName": "Marek",
  "lastName": "Zieliński",
  "note": "Dzwonił w sprawie działki pod Gdańskiem.",
  "phoneNumbers": [{ "number": "+48889112233" }]
}
```

- `note` optional. `phoneNumbers` will contain exactly one entry (the dialled
  number), no `label`.
- **Response `201`:** the created `Contact` (full shape from §5.1, with a
  server-assigned `id` and `updatedAt`). The app inserts it straight into its
  cache.
- **Errors:** `409` a contact with that number already exists (please still
  return the existing contact's data if you can); `422` invalid phone number.

### 6.6 `POST /contacts/{id}/notes`

Append a note to an existing contact ("Dopisz notatkę").

- **Auth:** bearer.
- **Path:** `{id}` = `Contact.id`.
- **Request:**

```json
{ "note": "Umówione spotkanie na czwartek 10:00." }
```

- **Response `200`:** the **updated** `Contact` (so the app can refresh the row
  and its note count without another round-trip).
- **Errors:** `404` unknown contact.

---

## 7. Sizing, performance, limits

- **~200 contacts per user.** A full `GET /contacts` payload with notes is
  roughly 50–150 KB. That is fine to return unpaginated.
- **Sync cadence:** one automatic `GET /contacts` per app foreground at most
  every ~4 h per device, plus occasional manual refreshes and one right after
  pairing. Writes are rare (user-initiated).
- If you expect users to grow past a few hundred contacts, we should add
  pagination + the `since` tombstone scheme (§5.3 option A) before that.
- **Rate limiting:** `429` with `Retry-After` is fine; the app surfaces it as a
  toast and the user retries. Nothing hammers the API.
- **Clock skew:** `since` is the max `updatedAt` the app has seen, echoed back —
  it is *your* clock, not the phone's, so skew is not an issue as long as
  `updatedAt` and `since` come from the same source.

---

## 8. Frontend (CM web app) responsibilities

### 8.1 Pairing page (at the service address)

Served where the app sends the user for "Autokonfiguracja" (and where they'd
manually go to get a code). Must:

- authenticate the CM user,
- display the **pairing code** and its expiry, with a regenerate button,
- display the **service address** to type back into the app,
- (phase 2) a button that deep-links back into the app, see §8.3.

### 8.2 Record deep links

The app opens these in the system browser (`appUrl` = `appUrl` from the pair
response):

| Tap target in the app | URL opened |
|---|---|
| A related **property** row | `<appUrl>/app/builds/<Ref.id>` |
| A related **prospect** row | `<appUrl>/app/clients/<Ref.id>` |

Please confirm these two path templates, or give the correct ones — they are
currently `/app/builds/:id` and `/app/clients/:id` in the client
(`CM_PROPERTY_PATH`, `CM_CLIENT_PATH`) and easy to change. The page should handle
"not logged in" gracefully (redirect to login, then to the record).

### 8.3 One-tap auto-configuration *(phase 2, needs a client change)*

To make "Autokonfiguracja" truly one tap, the pairing page could offer a link
with a custom scheme that the app registers an intent filter for, e.g.:

```
cmphone://pair?code=7Q4KZ2&service=https%3A%2F%2Fapi.crm.example
```

The app would parse it and run the pair flow automatically. Not implemented yet —
raise it if the team wants it and we'll add the intent filter.

---

## 9. Security notes

- TLS required. Certificate pinning is **not** planned for v1.
- The access token should be **short-lived** (minutes–hours). The refresh token
  can be long-lived but must be **revocable** (via `DELETE /device/pair` and/or a
  CM-side "devices" screen).
- Token **scope**: this client only needs *read contacts*, *create contact*,
  *append note* for the paired user. Please don't issue broader scopes.
- The app stores tokens in Android `EncryptedSharedPreferences`.
- No PII beyond what's in the contact list is sent or requested.

---

## 10. Open questions for the API/frontend teams

1. **Field names / casing** in §5 — OK as-is, or do you have an existing schema
   we should match? (Client change cost is low but non-zero.)
2. **Delta sync** (§5.3): tombstones (A) or full-list-only (B)?
3. **`baseUrl` vs service address** — same host or separate API host?
4. **Deep-link paths** (§8.2): confirm `/app/builds/:id` and `/app/clients/:id`.
5. **Refresh tokens** — supported in v1, or rely on re-pairing for now?
6. **`DELETE /device/pair`** — will it exist for v1?
7. **Pairing code**: length/charset/TTL/one-time? (Client currently accepts
   4–8 chars in the input field — tell us the real constraints.)
8. **Error body** (§3) — will you adopt the `{ "error": { code, message } }`
   envelope?
9. **`createContact` duplicates** (§6.5) — return `409` + existing contact, or
   just succeed idempotently?
