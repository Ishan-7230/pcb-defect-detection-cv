import { Router } from "express";
import { AnalyticsController } from "../controllers/analyticsController.js";

const router = Router();

router.get("/defects", AnalyticsController.getAnalyticsSummary);

export default router;
