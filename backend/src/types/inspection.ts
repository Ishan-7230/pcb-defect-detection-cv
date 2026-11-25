export interface BoundingBox {
  left: number;
  top: number;
  right: number;
  bottom: number;
}

export interface DefectItem {
  classId: number;
  label: string;
  confidence: number;
  boundingBox: BoundingBox;
}

export type InspectionStatusType =
  | "DEFECT_DETECTED"
  | "NO_TRAINED_DEFECT_DETECTED"
  | "ERROR";

export interface InspectionPayload {
  id: string;
  timestamp: number;
  status: InspectionStatusType;
  defects: DefectItem[];
  inferenceLatencyMs: number;
  endToEndLatencyMs: number;
  fps: number;
  modelName: string;
  deviceId?: string;
  notes?: string;
}

export interface DefectFrequency {
  label: string;
  count: number;
  percentage: number;
}

export interface AnalyticsSummary {
  totalInspections: number;
  totalDefectsDetected: number;
  defectRatePercentage: number;
  averageInferenceLatencyMs: number;
  averageEndToEndLatencyMs: number;
  defectBreakdown: DefectFrequency[];
}
