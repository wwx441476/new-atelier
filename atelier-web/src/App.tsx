import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { OnboardingProvider } from './guide/OnboardingContext';
import AdminLayout from './layouts/AdminLayout';
import DataSourcePage from './pages/DataSourcePage';
import MetadataPage from './pages/MetadataPage';
import DimensionPage from './pages/DimensionPage';
import MetricPage from './pages/MetricPage';
import WarningRulePage from './pages/WarningRulePage';

export default function App() {
  return (
    <BrowserRouter
      future={{
        v7_startTransition: true,
        v7_relativeSplatPath: true,
      }}
    >
      <OnboardingProvider>
        <Routes>
          <Route path="/" element={<AdminLayout />}>
          <Route index element={<Navigate to="/datasources" replace />} />
          <Route path="datasources" element={<DataSourcePage />} />
          <Route path="metadata" element={<MetadataPage />} />
          <Route path="dimensions" element={<DimensionPage />} />
          <Route path="metrics" element={<MetricPage />} />
          <Route path="warning-rules" element={<WarningRulePage />} />
          </Route>
        </Routes>
      </OnboardingProvider>
    </BrowserRouter>
  );
}
