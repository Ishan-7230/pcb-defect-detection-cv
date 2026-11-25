import { Router } from "express";
import { InspectionController } from "../controllers/inspectionController.js";
import { validateInspectionPayload } from "../middleware/validator.js";

const router = Router();

router.post("/", validateInspectionPayload, InspectionController.createInspection);
router.get("/", InspectionController.getInspections);
router.get("/:id", InspectionController.getInspectionById);

export default router;
