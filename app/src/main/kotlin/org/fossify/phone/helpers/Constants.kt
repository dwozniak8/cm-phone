package org.fossify.phone.helpers

import org.fossify.commons.helpers.TAB_CALL_HISTORY
import org.fossify.commons.helpers.TAB_CONTACTS
import org.fossify.commons.helpers.TAB_FAVORITES

// shared prefs
const val SPEED_DIAL = "speed_dial"
const val REMEMBER_SIM_PREFIX = "remember_sim_"
const val GROUP_SUBSEQUENT_CALLS = "group_subsequent_calls"
const val OPEN_DIAL_PAD_AT_LAUNCH = "open_dial_pad_at_launch"
const val DISABLE_PROXIMITY_SENSOR = "disable_proximity_sensor"
const val DISABLE_SWIPE_TO_ANSWER = "disable_swipe_to_answer"
const val SHOW_TABS = "show_tabs"
const val FAVORITES_CONTACTS_ORDER = "favorites_contacts_order"
const val FAVORITES_CUSTOM_ORDER_SELECTED = "favorites_custom_order_selected"
const val WAS_OVERLAY_SNACKBAR_CONFIRMED = "was_overlay_snackbar_confirmed"
const val DIALPAD_VIBRATION = "dialpad_vibration"
const val DIALPAD_BEEPS = "dialpad_beeps"
const val HIDE_DIALPAD_NUMBERS = "hide_dialpad_numbers"
const val ALWAYS_SHOW_FULLSCREEN = "always_show_fullscreen"

// CM integration
const val CM_BASE_URL = "cm_base_url"
const val CM_PAIRING_URL = "cm_pairing_url"
const val CM_APP_URL = "cm_app_url"
const val CM_LAST_SYNC_TS = "cm_last_sync_ts"
// CM API base URL pre-filled on the pairing screen. 10.0.2.2 is the dev
// machine as seen from the Android emulator; change for a real deployment.
const val DEFAULT_CM_PAIRING_URL = "http://10.0.2.2:8888"
const val DEFAULT_CM_APP_URL = "https://app.crm.example"
const val CM_SYNC_INTERVAL_MS = 4 * 60 * 60 * 1000L
const val CM_CONTACT_ID = "cm_contact_id"

// deep-link paths into the CM web app, joined onto cmAppUrl
const val CM_PROPERTY_PATH = "/app/builds"
const val CM_CLIENT_PATH = "/app/clients"

const val ALL_TABS_MASK = TAB_CONTACTS or TAB_FAVORITES or TAB_CALL_HISTORY

val tabsList = arrayListOf(TAB_CONTACTS, TAB_FAVORITES, TAB_CALL_HISTORY)

private const val PATH = "org.fossify.phone.action."
const val ACCEPT_CALL = PATH + "ACCEPT_CALL"
const val DECLINE_CALL = PATH + "DECLINE_CALL"

const val DIALPAD_TONE_LENGTH_MS = 150L // The length of DTMF tones in milliseconds
