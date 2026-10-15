# CM device API — implementation handoff for the cm-phone team

**From:** CM API team **To:** `cm-phone` (Android) team
**Date:** 2026-09-10 **Status:** API implemented and live on the dev environment
**Companion doc:** `docs/cm-phone-contract.md` (your v1 proposal, now annotated)

---

## What this is

The device-pairing + contact-sync API you proposed is **built**. It lives under a
**`/device/*`** path prefix and is deployed. This document maps every point of
your v1 contract to what actually shipped, so you can finish the client.

**Legend**

| | Meaning |
| --- | --- |
| ✅ | Shipped exactly as you proposed — build to the contract, no client change |
| ⚠️ | **Client change required** — differs from the contract |
| ℹ️ | Note / clarification, usually no code change |

---

## TL;DR — client changes required

1. **Path prefix.** All data calls are `<baseUrl>/device/…`, not `<baseUrl>/…`. `baseUrl` from the pair response has **no path**. — §1
2. **Error body is a bare string.** `{ "error": "INVALID_PAIRING_CODE" }`, not `{ "error": { code, message } }`. No server-side message text — map codes → user text on device. — §2
3. **Every POST returns `201`** (not `200`) on success. Treat any `2xx` as success. — §3
4. **Pairing code is case-sensitive and always uppercase.** Upper-case the user's input before `POST /device/pair` or it will 401. — §4
5. **Wire the refresh flow.** Access token TTL is 1 h; the refresh token is **rotated on every use** — persist the new `refreshToken` from every `/device/refresh` response. — §5
6. **`GET /device/contacts` is the complete set.** Delete local contacts that are absent from the response (option B, no tombstones). — §6
7. **Duplicate on create → `409` with no contact body.** Don't parse a contact out of the 409 — use your local cache. — §7
8. **Autoconfig deep-link scheme is `cmphone://pair?code=…&service=…`** (your §8.3 proposal). The old `clinetmobile://…` scheme is gone. Intent filter still needed for one-tap (phase 2). — §4

Everything else matches your contract. Full point-by-point status table at the end.

---

## 1. Base URL and paths — ⚠️

`POST /device/pair` returns `baseUrl` (API origin, **no path suffix**) and `appUrl`
(web-app origin). All calls after pairing are `baseUrl` + the path below.

| Your contract (§6) | Actual path |
| --- | --- |
| `POST /device/pair` (at service address) | `POST <serviceAddress>/device/pair` ✅ |
| `POST /device/refresh` | `POST <serviceAddress>/device/refresh` ✅ |
| `DELETE /device/pair` | `DELETE <baseUrl>/device/pair` ✅ |
| `GET /contacts` | **`GET <baseUrl>/device/contacts`** ⚠️ |
| `POST /contacts` | **`POST <baseUrl>/device/contacts`** ⚠️ |
| `POST /contacts/{id}/notes` | **`POST <baseUrl>/device/contacts/{id}/notes`** ⚠️ |

> Your `CM_PROPERTY_PATH` / `CM_CLIENT_PATH` deep-link constants are **unchanged** —
> only the API data paths gained the `/device` prefix. In the current deployment
> `baseUrl` == the service address (same host).

**Auth header** — `Authorization: Bearer <accessToken>` on every `/device/*` call
**except** `/device/pair` and `/device/refresh`. ✅ (as your §3)

---

## 2. Error envelope — ⚠️ (the biggest change)

Your §3 proposed:

```json
{ "error": { "code": "invalid_pairing_code", "message": "The code has expired." } }
```

**What ships:**

```json
{ "error": "INVALID_PAIRING_CODE" }
```

`error` is a **single string**, `UPPER_SNAKE_CASE`, stable. **There is no
server-provided message** — the client maps the code to Polish text locally (you
already do this for the pair-screen `401`). This applies to **every** non-2xx
response, including framework ones.

### Error codes the client will see

| `error` | HTTP | When | Suggested client reaction |
| --- | --- | --- | --- |
| `INVALID_PAIRING_CODE` | `401` | `POST /device/pair` — wrong / expired / already-used / malformed code | Pair screen: "Nieprawidłowy lub wygasły kod" |
| `INVALID_REFRESH_TOKEN` | `401` | `POST /device/refresh` — refresh token unknown / expired / already rotated | Clear pairing → pair screen |
| `DEVICE_SESSION_REVOKED` | `401` | Any bearer call — missing / malformed / expired / revoked access token | Try refresh once; if that also 401s → clear pairing |
| `CONTACT_ALREADY_EXISTS` | `409` | `POST /device/contacts` — a contact with that number already exists | Toast "Kontakt już istnieje"; open the local match |
| `INVALID_PHONE_NUMBER` | `422` | `POST /device/contacts` — number not parseable / < 9 digits | Toast "Nieprawidłowy numer telefonu" |
| `ITEM_NOT_FOUND` | `404` | `POST /device/contacts/{id}/notes` — unknown / foreign contact id | Toast "Kontakt jest już niedostępny" |
| `VALIDATION_ERROR` | `400` | Malformed JSON body / missing required field | Toast generic "Nieprawidłowe dane" |
| `ACCESS_ERROR` | `403` | Authenticated but not allowed | Toast generic |
| `APP_ERROR` / `INTERNAL_SERVER_ERROR` | `5xx` | Server error | Toast "Nie udało się połączyć z CM" |

On `/device/pair` the only code you get is `INVALID_PAIRING_CODE` (`401`);
anything non-`401` there is a connectivity problem, as your §3 already assumes. ✅

Status-code semantics otherwise match your §3 table. ✅

---

## 3. Success HTTP status — ℹ️ / ⚠️

Every `POST` in this API returns **`201`** on success (framework default), even
where your contract sketched `200`:

| Endpoint | Contract said | Actually returns |
| --- | --- | --- |
| `POST /device/pair` | `200` | `201` |
| `POST /device/refresh` | `200` | `201` |
| `POST /device/contacts` | `201` | `201` ✅ |
| `POST /device/contacts/{id}/notes` | `200` | `201` |
| `DELETE /device/pair` | `204` | `204` ✅ |
| `GET /device/contacts` | `200` | `200` ✅ |

**Client action:** treat **any `2xx`** as success and parse the body; do not
branch on the exact number. (The server may later normalise pair/refresh/notes to
`200` for contract fidelity — that must not change client behaviour.)

---

## 4. Pairing — ⚠️ + ✅

### Pairing code — ⚠️

- **6 characters**, from the alphabet `QWERTYUPASDFGHKLZXCVBNM23456789`
  (upper-case letters + digits `2`–`9`; no `0 1 I O` and similar).
- **Case-sensitive.** The server does **not** upper-case the input. Issued codes
  are upper-case, so **the client must upper-case the user's input** before
  sending, or `POST /device/pair` returns `401 INVALID_PAIRING_CODE`.
- **Single-use**, **10-minute TTL**, **one active code per user** — regenerating
  on the web page invalidates the previous one.
- Your input field accepting 4–8 chars is fine; real codes are always 6.

### `POST /device/pair` — ✅ (shape unchanged)

Request:
```json
{ "code": "7Q4KZ2" }
```
Response `201`:
```json
{
  "accessToken": "…",
  "refreshToken": "…",
  "expiresInSec": 3600,
  "user": { "id": "…", "name": "Jan Kowalski" },
  "baseUrl": "https://api.cm.example",
  "appUrl":  "https://app.cm.example"
}
```
- `baseUrl` / `appUrl` are **always present**. ℹ️
- `user.name` is `"<firstName> <lastName>"` trimmed; **may be `""`** if the CM user
  has no name set — fall back to the CM user id or a generic label. ℹ️
- Errors: `401 INVALID_PAIRING_CODE`. ✅

### Auto-configuration — ⚠️ (phase 2)

The web pairing page (`<appUrl>/app/settings/android`) shows the code, the
service address, and an **"Autokonfiguracja"** button linking to:

```
cmphone://pair?code=7Q4KZ2&service=https%3A%2F%2Fapi.cm.example
```

This is exactly your §8.3 proposal. **The old `clinetmobile://settings?url=…&code=…`
scheme is removed.** To make Autokonfiguracja one-tap, register an intent filter
for `cmphone://pair` and parse `code` + `service`. Until then, the button just
opens the pairing page in the browser (manual copy of the code still works). ✅

---

## 5. Tokens and the refresh flow — ⚠️

| | Value |
| --- | --- |
| Access token TTL | **3600 s** (1 h). `expiresInSec` is **always `3600`**, never `0`/omitted. |
| Refresh token TTL | **60 days** |
| Refresh rotation | **Every `POST /device/refresh` returns a new `refreshToken`; the old one dies immediately (single-use).** |

### `POST /device/refresh` — ✅ (shape) / ⚠️ (must be wired)

Request:
```json
{ "refreshToken": "…" }
```
Response `201`:
```json
{ "accessToken": "…", "refreshToken": "…", "expiresInSec": 3600 }
```

Your §4.4 flags this as "future change, not yet wired". To wire it:

1. On any `401` from a `/device/*` bearer call → `POST /device/refresh` **once**,
   then retry the original call.
2. **Persist the new `refreshToken` from the response every time** — it is always
   rotated. Storing the old one will fail the *next* refresh with
   `401 INVALID_REFRESH_TOKEN`.
3. If `/device/refresh` itself returns `401 INVALID_REFRESH_TOKEN` → clear the
   pairing, show the pair screen.

A device that syncs at least once every 60 days never hits a refresh expiry.

### `DELETE /device/pair` — ✅

Exists in v1. Bearer auth, empty body, `204`. Revokes the session server-side.
Call it best-effort from "Rozparuj urządzenie" **before** your local cleanup; run
local cleanup regardless of the result.

---

## 6. `GET /device/contacts` — ✅ (mostly) / ⚠️ (delete semantics)

- **Response is a bare JSON array** of `Contact` (not wrapped). ✅
- Returns the user's **full contact list** every time. ✅
- **`since` query param is accepted and ignored** in v1 (delta option **B**). You
  may keep sending it or drop it. ℹ️
- **⚠️ Treat the response as the authoritative complete set: remove local
  contacts that are not in it.** There are no `{ id, deleted: true }` tombstones.
  (Your §5.3 already says the client does "full list replaces local" until
  decided — that behaviour is now the contract.)
- **`photoUrl` is omitted** in v1 (CM has no contact photos yet). The field stays
  in the contract for later. ℹ️
- `phoneNumbers` contains **0 or 1** entry (CM stores one number per contact);
  first entry is primary; `label` may be `null`. Numbers are sent as stored
  (national or E.164) — **match by the last 9 digits**; the server dedupes the
  same way. ℹ️
- `notes` is `string[]`, **oldest first, newest last**. ✅
- `updatedAt` is epoch ms on the server clock. ✅
- `relatedProperties` / `relatedClients` are `{ id, label }` — property label is
  the address, prospect label is the linked contact name (or company). ✅

---

## 7. `POST /device/contacts` — ✅ / ⚠️ (409 body)

Request (your §6.5, unchanged):
```json
{ "firstName": "Marek", "lastName": "Zieliński",
  "note": "Dzwonił w sprawie działki.", "phoneNumbers": [{ "number": "+48889112233" }] }
```
- `note` optional; `phoneNumbers` has exactly one entry, no `label`. ✅
- Success `201` → the created `Contact` (full §5.1 shape, server `id` + `updatedAt`). ✅
- **⚠️ Duplicate → `409 { "error": "CONTACT_ALREADY_EXISTS" }` — no contact
  payload.** Your §6.5 asked us to "still return the existing contact's data if
  you can"; we don't (the global error format is code-only). You already hold the
  contact locally — match the dialled number against your cache and open it.
- Invalid number → `422 { "error": "INVALID_PHONE_NUMBER" }` (fails libphonenumber
  parsing *and* isn't a plausible ≥ 9-digit number). ✅
- Dedup is by the **last 9 digits** of the number. ℹ️

---

## 8. `POST /device/contacts/{id}/notes` — ✅

Request:
```json
{ "note": "Umówione spotkanie na czwartek 10:00." }
```
- Success → the **updated `Contact`** (so you can refresh the row + note count
  without a second call). HTTP status is `201` (see §3), body shape ✅.
- Unknown / foreign `{id}` → `404 { "error": "ITEM_NOT_FOUND" }`. ✅

---

## 9. Deep links — ✅ (no client change)

| Tap target | URL opened |
| --- | --- |
| Related property | `<appUrl>/app/builds/<Ref.id>` |
| Related prospect | `<appUrl>/app/clients/<Ref.id>` |

Both are now **real routes** in the CM web app that render the record's detail
view; wrapped in the app's access guard, so an unauthenticated visit goes through
web login and back to the record. `appUrl` comes from the pair response. Your
`CM_PROPERTY_PATH` / `CM_CLIENT_PATH` values are correct — **do not change them**.

---

## 10. Security — ℹ️ (matches your §9)

- TLS required; no certificate pinning.
- Token scope is implicitly "this user, contacts only" — no broader scope issued.
- `EncryptedSharedPreferences` for tokens stays correct.
- No PII beyond the contact list is exchanged.

---

## Point-by-point status against `cm-phone-contract.md`

| Contract section | Status | Client action |
| --- | --- | --- |
| §2 Terminology (`baseUrl`, `appUrl`, tokens) | ✅ | — `baseUrl` carries no path (§1 here) |
| §3 HTTPS / JSON / UTF-8 / ms timestamps | ✅ | — |
| §3 Auth header (`Bearer`, except pair/refresh) | ✅ | — |
| §3 **Error envelope** `{error:{code,message}}` | ⚠️ | Read `error` as a **string**; map codes → text locally (§2 here) |
| §3 Status-code table (400/401/403/404/409/422/429/5xx) | ✅ | — |
| §4.1 Manual pairing flow | ✅ | Upper-case the code before sending (§4 here) |
| §4.2 Auto-config opens the service address | ✅ | Web page served at `<appUrl>/app/settings/android` |
| §4.3 Periodic sync, ~4 h, full list | ✅ | Also **delete** local rows absent from the response (§6 here) |
| §4.4 Token refresh on `401` | ⚠️ | **Wire it**; persist the rotated `refreshToken` each time (§5 here) |
| §4.5 Unpair | ✅ | `DELETE /device/pair` exists; call best-effort |
| §5.1 `Contact` model / fields | ✅ | `photoUrl` omitted in v1 |
| §5.2 `PhoneNumber` / `Ref` / `User` | ✅ | `phoneNumbers` ≤ 1 entry; `user.name` may be `""` |
| §5.2 Match numbers by last 9 digits | ✅ | Server dedupes the same way |
| §5.3 Delta sync — **option A vs B** | ⚠️→**B** | No `since`, no tombstones; full list is authoritative; remove absent rows |
| §6.1 `POST /device/pair` | ✅ | Returns `201`, not `200` (§3 here) |
| §6.2 `POST /device/refresh` | ✅ | Supported in v1; rotated; returns `201` |
| §6.3 `DELETE /device/pair` | ✅ | Implemented in v1 (`204`) |
| §6.4 `GET /contacts` | ⚠️ path | → `GET <baseUrl>/device/contacts`; `since` ignored |
| §6.5 `POST /contacts` | ⚠️ path + 409 | → `/device/contacts`; duplicate `409` has **no** contact body |
| §6.6 `POST /contacts/{id}/notes` | ⚠️ path | → `/device/contacts/{id}/notes`; returns `201` with the updated `Contact` |
| §7 Sizing / no pagination / `429` | ✅ | ~200 rows unpaginated; no aggressive rate-limit configured |
| §8.1 Pairing web page | ✅ | Live at `<appUrl>/app/settings/android` (code + service address + regenerate) |
| §8.2 Deep-link path templates | ✅ | `/app/builds/:id`, `/app/clients/:id` confirmed — **no client change** |
| §8.3 One-tap `cmphone://pair` scheme | ⚠️ | Scheme adopted; **register the intent filter** for one-tap (phase 2) |
| §9 Security notes | ✅ | — |
| §10 Q1 field names/casing | ✅ | Accepted; `photoUrl` omitted, `phoneNumbers` 0/1, `label` nullable |
| §10 Q2 delta sync | answered | **Option B** |
| §10 Q3 `baseUrl` vs service address | answered | Same host; `baseUrl` returned, no path |
| §10 Q4 deep-link paths | answered | Confirmed as-is |
| §10 Q5 refresh tokens | answered | **Yes, v1**; 60-day, rotated |
| §10 Q6 `DELETE /device/pair` | answered | **Yes, v1** |
| §10 Q7 pairing code constraints | answered | 6 chars, uppercase charset, case-sensitive, single-use, 10 min |
| §10 Q8 error body envelope | answered | **`{ "error": "CODE" }`** string — not adopted |
| §10 Q9 create-contact duplicates | answered | **`409 CONTACT_ALREADY_EXISTS`**, no body, not idempotent |

---

## Appendix — endpoint quick reference

| Method & path | Auth | Success | Error codes |
| --- | --- | --- | --- |
| `POST <svc>/device/pair` | none | `201` + tokens/user/urls | `401 INVALID_PAIRING_CODE` |
| `POST <svc>/device/refresh` | none | `201` + tokens | `401 INVALID_REFRESH_TOKEN` |
| `DELETE <base>/device/pair` | Bearer | `204` | `401 DEVICE_SESSION_REVOKED` |
| `GET <base>/device/contacts` | Bearer | `200` + `Contact[]` | `401 DEVICE_SESSION_REVOKED` |
| `POST <base>/device/contacts` | Bearer | `201` + `Contact` | `409 CONTACT_ALREADY_EXISTS`, `422 INVALID_PHONE_NUMBER`, `401` |
| `POST <base>/device/contacts/{id}/notes` | Bearer | `201` + `Contact` | `404 ITEM_NOT_FOUND`, `401` |

`<svc>` = service address the user typed · `<base>` = `baseUrl` from the pair response (same host in the current deployment).

---

## Open items on the CM side (not blocking the client)

- Normalise `POST /device/pair` · `/device/refresh` · `/device/contacts/{id}/notes`
  to HTTP `200` for contract fidelity (client should still accept any `2xx`).
- Optionally include the existing contact in the `409` body (needs a response
  format change on our side) if the client really wants it inline.
- Pairing-code case-insensitivity on the server, if you'd rather not upper-case
  client-side — tell us and we'll fold it in.
