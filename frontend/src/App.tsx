import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { Landing } from './pages/Landing'
import { AdminLogin } from './pages/AdminLogin'
import { AdminLayout } from './pages/admin/AdminLayout'
import { AdminRequestsPage } from './pages/admin/AdminRequestsPage'
import { AdminBatchesPage } from './pages/admin/AdminBatchesPage'
import { AdminBatchDetailPage } from './pages/admin/AdminBatchDetailPage'
import { AdminStudentsPage } from './pages/admin/AdminStudentsPage'
import { StudentLogin } from './pages/StudentLogin'
import { StudentDashboard } from './pages/StudentDashboard'
import { Register } from './pages/Register'
import { ProtectedRoute } from './auth/ProtectedRoute'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Landing />} />
        <Route path="/register" element={<Register />} />
        <Route path="/admin/login" element={<AdminLogin />} />
        <Route
          path="/admin"
          element={
            <ProtectedRoute role="ADMIN">
              <AdminLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<Navigate to="requests" replace />} />
          <Route path="requests" element={<AdminRequestsPage />} />
          <Route path="batches" element={<AdminBatchesPage />} />
          <Route path="batches/:batchId" element={<AdminBatchDetailPage />} />
          <Route path="students" element={<AdminStudentsPage />} />
        </Route>
        <Route path="/student/login" element={<StudentLogin />} />
        <Route
          path="/student"
          element={
            <ProtectedRoute role="STUDENT">
              <StudentDashboard />
            </ProtectedRoute>
          }
        />
      </Routes>
    </BrowserRouter>
  )
}

export default App
