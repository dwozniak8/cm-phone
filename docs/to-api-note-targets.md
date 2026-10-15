# Follow-up request: note targets (contact / build / client)

**From:** `cm-phone` (Android) team **To:** CM API team
**Date:** 2026-09-10
**Re:** `POST /device/contacts/{id}/notes` — extend to target related records

---

## Why

Right now every note the app sends is attached to the **contact**. Users want to
say *what the note is about*: the contact itself, or one of that contact's
**related builds / clients**. The pairing/detail data already gives us those
records (`relatedProperties`, `relatedClients` on `Contact`), so the app now
shows a dropdown — "Kontakt" plus each related build/client — and needs the API
to route the note accordingly.

## Proposed change (backwards compatible)

Same endpoint, one new optional field in the body:

### `POST /device/contacts/{contactId}/notes`

```jsonc
{
  "note": "Umówione spotkanie na czwartek 10:00.",
  "target": { "type": "build", "id": "p9" }   // optional
}
```

- `target` **omitted / null** ⇒ current behaviour: note goes to the contact.
- `target.type` ∈ `"contact"` | `"build"` | `"client"`.
- `target.id`:
  - `"contact"` ⇒ ignored (or must equal `{contactId}`),
  - `"build"` ⇒ an id from that contact's `relatedProperties[].id`,
  - `"client"` ⇒ an id from that contact's `relatedClients[].id`.
- `{contactId}` in the path stays the anchor even when the note lands on a
  build/client, so the app doesn't need per-entity note endpoints.

### Response

Still `2xx` + the **`Contact`** (same shape as today). When the note went to a
build/client the contact body may be unchanged — that's fine, the app just needs
the success signal. If it's cheap to reflect a note count per related record
later, add it to `CmRef` (`{ id, label, noteCount? }`) and we'll surface it; not
required now.

### Errors

- `404 ITEM_NOT_FOUND` — unknown `{contactId}`, **or** a `target.id` that isn't a
  build/client linked to this contact. The app shows "Kontakt jest już
  niedostępny" for this.
- `422 VALIDATION_ERROR` — unknown `target.type`.

## Client status

Implemented and live against `FakeCmApi` now:

- `AddNoteRequest { note, target? }`, `NoteTarget { type, id }` in
  `org.fossify.phone.cm`.
- `CmApi.addNote(contactId, note, target?)`.
- `HttpCmApi` sends the body above to `POST /device/contacts/{id}/notes`.
- Dropdown in the "Dopisz notatkę" dialog; hidden when the contact has no
  related builds/clients (only option would be "Kontakt").

If you'd rather use separate paths (`/device/builds/{id}/notes` etc.) or a
different field name, say so and we'll adjust — nothing is shipped to users yet.
