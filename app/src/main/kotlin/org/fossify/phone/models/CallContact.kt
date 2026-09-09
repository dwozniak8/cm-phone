package org.fossify.phone.models

// a simpler Contact model containing just info needed at the call screen
data class CallContact(
    var name: String,
    var photoUri: String,
    var number: String,
    var numberLabel: String,
    // true when the name came from the CM cache rather than a system contact
    var isCmContact: Boolean = false,
)
