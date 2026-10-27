'use strict';

/**
 * Notification preferences, server side (task P7-05 / P7-06).
 *
 * This is a deliberate port of `com.insta.nova.data.firebase.NotificationPrefs`. The two
 * copies must agree: the client decides whether to show a switch as on, and the server
 * decides whether to send. If they disagree, a user turns a toggle off and keeps getting
 * notifications — a bug nobody can reproduce from the client alone.
 *
 * Kept fail-open for the same reason as the Java version: a malformed preference must never
 * silently mute a user's messages.
 */

const TYPES = {
  LIKES: 'notifyLikes',
  COMMENTS: 'notifyComments',
  FOLLOWS: 'notifyFollows',
  MENTIONS: 'notifyMentions',
  MESSAGES: 'notifyMessages',
  CALLS: 'notifyCalls',
  STORIES: 'notifyStories',
  LIVE: 'notifyLive',
};

/**
 * Canonical notification type (what is stored on the document and sent in the FCM payload)
 * -> preference key. They are not the same string on purpose: the document field must match
 * `NotificationRoute` on the client (`like`, `comment`, `follow`), while the settings map
 * keeps the column names the settings screen has always used (`notifyLikes`).
 */
const PREF_KEY_BY_TYPE = {
  like: TYPES.LIKES,
  comment: TYPES.COMMENTS,
  follow: TYPES.FOLLOWS,
  mention: TYPES.MENTIONS,
  message: TYPES.MESSAGES,
  call: TYPES.CALLS,
  story: TYPES.STORIES,
  live: TYPES.LIVE,
};

const QUIET_START = 'quietStartHour';
const QUIET_END = 'quietEndHour';

/** Preference key for a canonical type; unknown types fall back to "always allowed". */
function prefKeyFor(type) {
  return PREF_KEY_BY_TYPE[type] || null;
}

function asBoolean(raw, fallback) {
  if (typeof raw === 'boolean') return raw;
  if (typeof raw === 'number') return raw !== 0;
  if (typeof raw === 'string') {
    const s = raw.trim().toLowerCase();
    if (s === 'true') return true;
    if (s === 'false') return false;
  }
  return fallback;
}

function asHour(raw) {
  let value;
  if (typeof raw === 'number') value = Math.trunc(raw);
  else if (typeof raw === 'string' && raw.trim() !== '' && Number.isFinite(Number(raw))) {
    value = Math.trunc(Number(raw));
  } else return null;
  return value >= 0 && value <= 23 ? value : null;
}

/** @param prefKey a preference key such as `notifyLikes`, not a canonical type. */
function allows(settings, prefKey) {
  if (!settings || !prefKey) return true;
  return asBoolean(settings[prefKey], true);
}

function inQuietHours(settings, hourOfDay) {
  if (!settings || hourOfDay < 0 || hourOfDay > 23) return false;
  const start = asHour(settings[QUIET_START]);
  const end = asHour(settings[QUIET_END]);
  if (start === null || end === null || start === end) return false;
  if (start < end) return hourOfDay >= start && hourOfDay < end;
  return hourOfDay >= start || hourOfDay < end;
}

/** Calls bypass quiet hours; a missed call is not recoverable the way a delayed like is. */
/** @param prefKey a preference key, e.g. `prefKeyFor('call')`. */
function deliverNow(settings, prefKey, hourOfDay) {
  if (prefKey === TYPES.CALLS) return allows(settings, prefKey);
  return allows(settings, prefKey) && !inQuietHours(settings, hourOfDay);
}

module.exports = { TYPES, PREF_KEY_BY_TYPE, QUIET_START, QUIET_END, prefKeyFor, allows, inQuietHours, deliverNow };
