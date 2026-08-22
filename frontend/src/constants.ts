export const INSTITUTE_NAME = 'Bangalore Computer Education'

/**
 * Single-admin app -- there's no "admin name" field modeled on the backend
 * since the whole product deliberately assumes one admin account (see
 * AdminAuthController's bootstrap-once behavior). If that ever changes,
 * this should become a real field fetched from a /api/admin/me endpoint,
 * same as the student-facing welcome message already does.
 */
export const ADMIN_DISPLAY_NAME = 'Rajesh'
