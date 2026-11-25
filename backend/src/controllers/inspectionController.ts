import { Request, Response } from "express";
import { InspectionPayload } from "../types/inspection.js";

// In-memory inspection store (can be backed by SQLite, PostgreSQL, or MongoDB)
const inspectionDatabase: Map<string, InspectionPayload> = new Map();

export class InspectionController {
  static createInspection(req: Request, res: Response): void {
    const payload = req.body as InspectionPayload;

    if (inspectionDatabase.has(payload.id)) {
      res.status(409).json({ error: "Conflict", message: "Inspection ID already recorded." });
      return;
    }

    inspectionDatabase.set(payload.id, payload);

    res.status(201).json({
      message: "Inspection telemetry recorded successfully.",
      id: payload.id,
      defectsLogged: payload.defects.length,
      status: payload.status
    });
  }

  static getInspections(req: Request, res: Response): void {
    const { status, limit = "50", offset = "0" } = req.query;

    let records = Array.from(inspectionDatabase.values()).sort(
      (a, b) => b.timestamp - a.timestamp
    );

    if (status && typeof status === "string") {
      records = records.filter((r) => r.status === status);
    }

    const parsedLimit = Math.min(parseInt(limit as string, 10) || 50, 100);
    const parsedOffset = parseInt(offset as string, 10) || 0;
    const paginated = records.slice(parsedOffset, parsedOffset + parsedLimit);

    res.status(200).json({
      total: records.length,
      limit: parsedLimit,
      offset: parsedOffset,
      records: paginated
    });
  }

  static getInspectionById(req: Request, res: Response): void {
    const { id } = req.params;
    const record = inspectionDatabase.get(id);

    if (!record) {
      res.status(404).json({ error: "Not Found", message: "Inspection record not found." });
      return;
    }

    res.status(200).json(record);
  }

  static getAllRecords(): InspectionPayload[] {
    return Array.from(inspectionDatabase.values());
  }

  static clear(): void {
    inspectionDatabase.clear();
  }
}
