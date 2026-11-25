import { Router, Request, Response } from "express";

const router = Router();

const GC10_CLASSES = [
  "crease",
  "crescent_gap",
  "inclusion",
  "oil_spot",
  "punching_hole",
  "rolled_pit",
  "silk_spot",
  "waist_folding",
  "water_spot",
  "welding_line"
];

router.get("/active", (_req: Request, res: Response) => {
  res.status(200).json({
    architecture: "YOLO11n",
    version: process.env.ACTIVE_MODEL_VERSION || "1.0.0",
    inputShape: [1, 3, 640, 640],
    outputShape: [1, 14, 8400],
    classesCount: GC10_CLASSES.length,
    classes: GC10_CLASSES,
    dataset: "GC10-DET (Surface Defect Detection)",
    recommendedConfidenceThreshold: 0.25,
    recommendedIouThreshold: 0.45,
    offlineExecutionVerified: true
  });
});

export default router;
