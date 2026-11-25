import express, { Request, Response } from "express";
import dotenv from "dotenv";
import {
  configureSecurityHeaders,
  configureCors,
  configureRateLimiter,
  sanitizeResponseHeaders
} from "./middleware/security.js";
import { errorHandler } from "./middleware/errorHandler.js";
import inspectionRoutes from "./routes/inspectionRoutes.js";
import analyticsRoutes from "./routes/analyticsRoutes.js";
import modelRoutes from "./routes/modelRoutes.js";

dotenv.config();

const app = express();
const PORT = process.env.PORT || 3000;

// Security Middleware Pipeline
app.use(configureSecurityHeaders());
app.use(configureCors());
app.use(configureRateLimiter());
app.use(sanitizeResponseHeaders);

// Strict Body Parsing to prevent large payload attacks
app.use(express.json({ limit: "2mb" }));
app.use(express.urlencoded({ extended: true, limit: "2mb" }));

// Health Check
app.get("/api/v1/health", (_req: Request, res: Response) => {
  res.status(200).json({
    status: "HEALTHY",
    system: "Industry 4.0 Quality Hub",
    uptimeSeconds: process.uptime(),
    timestamp: new Date().toISOString()
  });
});

// API Routes
app.use("/api/v1/inspections", inspectionRoutes);
app.use("/api/v1/analytics", analyticsRoutes);
app.use("/api/v1/models", modelRoutes);

// Fallback 404
app.use((_req: Request, res: Response) => {
  res.status(404).json({ error: "Not Found", message: "Requested endpoint does not exist." });
});

// Centralized Error Handler (no internal code leakage)
app.use(errorHandler);

const isRunningInTest = process.env.NODE_ENV === "test" || Boolean(process.env.NODE_TEST_CONTEXT);

if (!isRunningInTest) {
  app.listen(PORT, () => {
    console.info(`[INDUSTRY_4.0_HUB] Server active on port ${PORT}`);
  });
}

export default app;
