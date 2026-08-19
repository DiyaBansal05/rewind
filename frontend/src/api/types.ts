export type BatchStatus = 'ACTIVE' | 'ARCHIVED'
export type RecordingRequestStatus = 'PENDING' | 'APPROVED' | 'DENIED' | 'REVOKED' | 'EXPIRED'

export const DAYS_OF_WEEK = [
  'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY',
] as const
export type DayOfWeek = (typeof DAYS_OF_WEEK)[number]

export interface Batch {
  id: string
  name: string
  courseName: string
  startDate: string
  endDate: string
  classDaysOfWeek: DayOfWeek[]
  classStartTime: string
  classEndTime: string
  status: BatchStatus
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
}

export interface StudentRecordingRequest {
  id: string
  batchId: string
  batchName: string
  classDate: string
  status: RecordingRequestStatus
  requestedAt: string
}

export interface EnrolledBatch {
  batchId: string
  batchName: string
  courseName: string
}

export interface NotificationItem {
  id: string
  message: string
  sentAt: string
}
