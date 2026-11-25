import { Request, Response } from "express";
import { InspectionController } from "./inspectionController.js";
import { AnalyticsSummary, DefectFrequency } from "../types/inspection.js";

export class AnalyticsController {
  static getAnalyticsSummary(_req: Request, res: Response): void {
    const records = InspectionController.getAllRecords();

    if (records.length === 0) {
      const emptySummary: AnalyticsSummary = {
        totalInspections: 0,
        totalDefectsDetected: 0,
        defectRatePercentage: 0,
        averageInferenceLatencyMs: 0,
        averageEndToEndLatencyMs: 0,
        defectBreakdown: []
      };
      res.status(200).json(emptySummary);
      return;
    }

    const totalInspections = records.length;
    let totalDefectsCount = 0;
    let defectiveInspectionsCount = 0;
    let totalInferenceLatency = 0;
    let totalEndToEndLatency = 0;

    const classCounter: Map<string, number> = new Map();

    for (const record of records) {
      totalInferenceLatency += record.inferenceLatencyMs;
      totalEndToEndLatency += record.endToEndLatencyMs;

      if (record.defects.length > 0) {
        defectiveInspectionsCount++;
        totalDefectsCount += record.defects.length;

        for (const defect of record.defects) {
          const current = classCounter.get(defect.label) || 0;
          classCounter.set(defect.label, current + 1);
        }
      }
    }

    const defectBreakdown: DefectFrequency[] = Array.from(classCounter.entries())
      .map(([label, count]) => ({
        label,
        count,
        percentage:
          totalDefectsCount > 0 ? (count / totalDefectsCount) * 100 : 0
      }))
      .sort((a, b) => b.count - a.count);

    const summary: AnalyticsSummary = {
      totalInspections,
      totalDefectsDetected: totalDefectsCount,
      defectRatePercentage: (defectiveInspectionsCount / totalInspections) * 100,
      averageInferenceLatencyMs: totalInferenceLatency / totalInspections,
      averageEndToEndLatencyMs: totalEndToEndLatency / totalInspections,
      defectBreakdown
    };

    res.status(200).json(summary);
  }
}
