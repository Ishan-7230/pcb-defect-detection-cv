import { z } from "zod";
import { Request, Response, NextFunction } from "express";

const BoundingBoxSchema = z.object({
  left: z.number().min(0),
  top: z.number().min(0),
  right: z.number().min(0),
  bottom: z.number().min(0)
});

const DefectItemSchema = z.object({
  classId: z.number().int().min(0),
  label: z.string().min(1).max(100),
  confidence: z.number().min(0).max(1),
  boundingBox: BoundingBoxSchema
});

export const InspectionPayloadSchema = z.object({
  id: z.string().uuid(),
  timestamp: z.number().int().positive(),
  status: z.enum(["DEFECT_DETECTED", "NO_TRAINED_DEFECT_DETECTED", "ERROR"]),
  defects: z.array(DefectItemSchema).max(100),
  inferenceLatencyMs: z.number().min(0),
  endToEndLatencyMs: z.number().min(0),
  fps: z.number().min(0),
  modelName: z.string().min(1).max(128),
  deviceId: z.string().max(128).optional(),
  notes: z.string().max(1000).optional()
});

export function validateInspectionPayload(req: Request, res: Response, next: NextFunction): void {
  const result = InspectionPayloadSchema.safeParse(req.body);
  if (!result.success) {
    res.status(400).json({
      error: "Validation Failed",
      details: result.error.errors.map((e) => ({
        path: e.path.join("."),
        message: e.message
      }))
    });
    return;
  }
  req.body = result.data;
  next();
}
