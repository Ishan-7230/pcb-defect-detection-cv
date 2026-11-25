import { Request, Response, NextFunction } from "express";

export function errorHandler(
  err: Error,
  _req: Request,
  res: Response,
  _next: NextFunction
): void {
  const isProduction = process.env.NODE_ENV === "production";

  // Log error internally
  console.error(`[SECURE_ERROR_LOG] ${err.name}: ${err.message}`);

  // Prevent code leakage in client response
  res.status(500).json({
    error: "Internal Server Error",
    message: isProduction
      ? "An unexpected error occurred. Request ID logged."
      : err.message
  });
}
