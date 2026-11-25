import helmet from "helmet";
import cors from "cors";
import rateLimit from "express-rate-limit";
import { Request, Response, NextFunction } from "express";

export function configureSecurityHeaders() {
  return helmet({
    contentSecurityPolicy: {
      directives: {
        defaultSrc: ["'self'"],
        scriptSrc: ["'self'"],
        styleSrc: ["'self'"],
        imgSrc: ["'self'", "data:"],
        connectSrc: ["'self'"]
      }
    },
    crossOriginEmbedderPolicy: true,
    crossOriginOpenerPolicy: true,
    crossOriginResourcePolicy: { policy: "same-origin" },
    dnsPrefetchControl: { allow: false },
    frameguard: { action: "deny" },
    hidePoweredBy: true,
    hsts: { maxAge: 31536000, includeSubDomains: true, preload: true },
    noSniff: true
  });
}

export function configureCors() {
  const rawOrigins = process.env.ALLOWED_ORIGINS || "http://localhost:3000";
  const allowed = rawOrigins.split(",").map((s) => s.trim());

  return cors({
    origin: (origin, callback) => {
      // Allow mobile apps (no origin header) or listed origins
      if (!origin || allowed.includes(origin)) {
        callback(null, true);
      } else {
        callback(new Error("CORS validation failed: Origin not authorized"));
      }
    },
    methods: ["GET", "POST"],
    allowedHeaders: ["Content-Type", "Authorization", "X-Device-Id"],
    maxAge: 86400
  });
}

export function configureRateLimiter() {
  return rateLimit({
    windowMs: 15 * 60 * 1000, // 15 minutes
    max: 200, // limit each IP to 200 requests per windowMs
    standardHeaders: true,
    legacyHeaders: false,
    message: {
      error: "Rate Limit Exceeded",
      message: "Too many requests from this IP. Please try again after 15 minutes."
    }
  });
}

export function sanitizeResponseHeaders(_req: Request, res: Response, next: NextFunction): void {
  res.removeHeader("X-Powered-By");
  res.removeHeader("Server");
  next();
}
