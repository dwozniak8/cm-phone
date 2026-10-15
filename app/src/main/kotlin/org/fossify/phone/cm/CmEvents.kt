package org.fossify.phone.cm

/** Posted after the local CM contact cache changes (sync, or a create-from-history). */
class CmContactsUpdatedEvent

/** Posted when the device pairs or unpairs, so the CM tab can switch its state. */
class CmPairingStateChangedEvent(val paired: Boolean)
