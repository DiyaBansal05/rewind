export type BatchStatus = 'ACTIVE' | 'ARCHIVED'
export type RecordingRequestStatus = 'PENDING' | 'APPROVED' | 'DENIED' | 'REVOKED' | 'EXPIRED'
export type EnrollmentStatus = 'PENDING' | 'APPROVED' | 'DENIED'

export const DAYS_OF_WEEK = [
  'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY',
] as const
export type DayOfWeek = (typeof DAYS_OF_WEEK)[number]

/** One weekday's class time ("HH:mm:ss"). endTime before startTime = runs past midnight. */
export interface WeeklySlot {
  dayOfWeek: DayOfWeek
  startTime: string
  endTime: string
}

export interface Batch {
  id: string
  name: string
  courseName: string
  startDate: string
  endDate: string
  weeklySchedule: WeeklySlot[]
  status: BatchStatus
}

/** An admin's change to one class date. regularStart/End: the usual time that day, if any. */
export interface ClassChange {
  id: string
  classDate: string
  cancelled: boolean
  startTime: string | null
  endTime: string | null
  note: string | null
  regularStart: string | null
  regularEnd: string | null
}

export interface RegistrationLink {
  token: string
  registrationPath: string
}

export interface QueueItem {
  id: string
  studentName: string
  studentPhone: string
  batchId: string
  batchName: string
  classDate: string
  status: RecordingRequestStatus
  requestedAt: string
  decidedAt: string | null
  accessExpiresAt: string | null
  watchPercent: number
  openCount: number
  lastWatchedAt: string | null
}

export type RequestTab = 'PENDING' | 'APPROVED' | 'ALL'
export type RequestCounts = Record<RecordingRequestStatus | 'ALL', number>

export interface ViewingSession {
  openedAt: string
  success: boolean
  maxPositionSeconds: number | null
  lastProgressAt: string | null
  device: string | null
}

export interface RequestDetail {
  summary: QueueItem
  durationSeconds: number | null
  watchedSeconds: number
  lastPositionSeconds: number
  firstOpenedAt: string | null
  sessions: ViewingSession[]
}

export type DecisionOutcome =
  | 'APPROVED' | 'DENIED' | 'EXTENDED' | 'REVOKED'
  | 'NOT_FOUND' | 'ALREADY_DECIDED' | 'NOT_ACTIVE' | 'INVALID_CANDIDATE'
  | 'NO_MATCH' | 'MULTIPLE_CANDIDATES' | 'LOOKUP_NOT_ALLOWED' | 'ERROR'

/** What approving a request would share, so the admin can watch it first. */
export interface CheckResponse {
  id: string
  batchName: string
  classDate: string
  classStart: string | null
  classEnd: string | null
  /** True when the class ran at a changed time (or was an extra class) that day. */
  classTimeChanged: boolean
  candidates: RecordingCandidate[]
  problem: DecisionOutcome | null
  message: string | null
}

export interface BulkItemResult {
  id: string
  outcome: DecisionOutcome
  message: string | null
  candidates: RecordingCandidate[]
}

export interface AnalyticsReport {
  days: number
  from: string
  to: string
  totalRequests: number
  previousPeriodRequests: number
  approvalRatePercent: number | null
  medianDecisionMinutes: number | null
  grantedCount: number
  watchedCount: number
  watchedRatePercent: number | null
  averageWatchPercent: number | null
  pendingNow: number
  perDay: { date: string; count: number }[]
  perBatch: { batchId: string; batchName: string; count: number }[]
  topRequesters: { studentId: string; name: string; phoneNumber: string; batchNames: string[]; count: number }[]
  missedWeekdays: { day: DayOfWeek; count: number }[]
}

export interface RecordingCandidate {
  recordingFileId: string
  meetingTopic: string
  startTime: string
  previewUrl: string
}

export interface StudentRecordingRequest {
  id: string
  batchId: string
  batchName: string
  classDate: string
  status: RecordingRequestStatus
  requestedAt: string
  decidedAt: string | null
  accessExpiresAt: string | null
  watchUrl: string | null
  watchPercent: number
  lastPositionSeconds: number
  durationSeconds: number | null
}

export interface EnrolledBatch {
  batchId: string
  batchName: string
  courseName: string
  status: EnrollmentStatus
  classDays: DayOfWeek[]
  /** Dates the batch actually had class recently (admin changes applied), newest first. */
  recentClassDates: string[]
}

export interface EnrollmentRequestItem {
  id: string
  studentName: string
  studentPhone: string
  batchId: string
  batchName: string
  courseName: string
  requestedAt: string
}

export interface EnrolledStudent {
  enrollmentId: string
  studentId: string
  name: string
  phoneNumber: string
}

export interface NotificationItem {
  id: string
  message: string
  sentAt: string
}

export interface BatchEnrollment {
  batchId: string
  batchName: string
}

export interface StudentSummary {
  id: string
  name: string
  phoneNumber: string
  batches: BatchEnrollment[]
  totalRequests: number
}

export interface StudentRequestDetail {
  id: string
  batchId: string
  batchName: string
  classDate: string
  status: RecordingRequestStatus
  requestedAt: string
}
