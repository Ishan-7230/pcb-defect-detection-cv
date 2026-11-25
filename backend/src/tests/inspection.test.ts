import { test, describe, beforeEach } from "node:test";
import assert from "node:assert";
import { InspectionController } from "../controllers/inspectionController.js";
import { AnalyticsController } from "../controllers/analyticsController.js";
import { InspectionPayloadSchema } from "../middleware/validator.js";
import { Request, Response } from "express";

interface MockResponse extends Response {
  statusCode: number;
  jsonBody: Record<string, unknown> | null;
}

function createMockResponse(): MockResponse {
  const res = {
    statusCode: 200,
    jsonBody: null as Record<string, unknown> | null,
    status(code: number) {
      this.statusCode = code;
      return this;
    },
    json(body: Record<string, unknown>) {
      this.jsonBody = body;
      return this;
    }
  };
  return res as unknown as MockResponse;
}

describe("Industry 4.0 Backend Unit & Security Tests", () => {
  beforeEach(() => {
    InspectionController.clear();
  });

  test("Schema Validator accepts valid inspection payload", () => {
    const validPayload = {
      id: "a0000000-0000-0000-0000-000000000001",
      timestamp: Date.now(),
      status: "DEFECT_DETECTED",
      defects: [
        {
          classId: 2,
          label: "inclusion",
          confidence: 0.89,
          boundingBox: { left: 100, top: 120, right: 240, bottom: 260 }
        }
      ],
      inferenceLatencyMs: 24.5,
      endToEndLatencyMs: 38.2,
      fps: 26.0,
      modelName: "yolo_detector.onnx"
    };

    const parsed = InspectionPayloadSchema.safeParse(validPayload);
    assert.strictEqual(parsed.success, true);
  });

  test("Schema Validator rejects invalid payload with missing/corrupt fields", () => {
    const invalidPayload = {
      id: "not-a-valid-uuid",
      status: "INVALID_STATUS",
      defects: "not-an-array"
    };

    const parsed = InspectionPayloadSchema.safeParse(invalidPayload);
    assert.strictEqual(parsed.success, false);
  });

  test("InspectionController records new inspection and retrieves it by ID", () => {
    const payload = {
      id: "b0000000-0000-0000-0000-000000000002",
      timestamp: Date.now(),
      status: "DEFECT_DETECTED" as const,
      defects: [
        {
          classId: 0,
          label: "crease",
          confidence: 0.94,
          boundingBox: { left: 50, top: 60, right: 150, bottom: 160 }
        }
      ],
      inferenceLatencyMs: 18.2,
      endToEndLatencyMs: 28.5,
      fps: 30.0,
      modelName: "yolo_detector.onnx"
    };

    const reqCreate = { body: payload } as Request;
    const resCreate = createMockResponse();

    InspectionController.createInspection(reqCreate, resCreate);
    assert.strictEqual(resCreate.statusCode, 201);
    assert.strictEqual(resCreate.jsonBody?.defectsLogged, 1);

    // Retrieve by ID
    const reqGet = { params: { id: payload.id } } as unknown as Request;
    const resGet = createMockResponse();

    InspectionController.getInspectionById(reqGet, resGet);
    assert.strictEqual(resGet.statusCode, 200);
    assert.strictEqual(resGet.jsonBody?.id, payload.id);
    assert.strictEqual(resGet.jsonBody?.status, "DEFECT_DETECTED");
  });

  test("InspectionController rejects duplicate inspection ID with 409 Conflict", () => {
    const payload = {
      id: "c0000000-0000-0000-0000-000000000003",
      timestamp: Date.now(),
      status: "NO_TRAINED_DEFECT_DETECTED" as const,
      defects: [],
      inferenceLatencyMs: 20.0,
      endToEndLatencyMs: 30.0,
      fps: 25.0,
      modelName: "yolo_detector.onnx"
    };

    const req1 = { body: payload } as Request;
    const res1 = createMockResponse();
    InspectionController.createInspection(req1, res1);
    assert.strictEqual(res1.statusCode, 201);

    const req2 = { body: payload } as Request;
    const res2 = createMockResponse();
    InspectionController.createInspection(req2, res2);
    assert.strictEqual(res2.statusCode, 409);
  });

  test("AnalyticsController aggregates defect breakdown and metrics correctly", () => {
    // Insert 2 records: one with defects, one without
    const record1 = {
      id: "d0000000-0000-0000-0000-000000000004",
      timestamp: Date.now(),
      status: "DEFECT_DETECTED" as const,
      defects: [
        { classId: 2, label: "inclusion", confidence: 0.88, boundingBox: { left: 10, top: 10, right: 50, bottom: 50 } },
        { classId: 2, label: "inclusion", confidence: 0.76, boundingBox: { left: 60, top: 60, right: 100, bottom: 100 } }
      ],
      inferenceLatencyMs: 20.0,
      endToEndLatencyMs: 30.0,
      fps: 25.0,
      modelName: "yolo_detector.onnx"
    };

    const record2 = {
      id: "d0000000-0000-0000-0000-000000000005",
      timestamp: Date.now(),
      status: "NO_TRAINED_DEFECT_DETECTED" as const,
      defects: [],
      inferenceLatencyMs: 16.0,
      endToEndLatencyMs: 24.0,
      fps: 32.0,
      modelName: "yolo_detector.onnx"
    };

    InspectionController.createInspection({ body: record1 } as Request, createMockResponse());
    InspectionController.createInspection({ body: record2 } as Request, createMockResponse());

    const resAnalytics = createMockResponse();
    AnalyticsController.getAnalyticsSummary({} as Request, resAnalytics);

    assert.strictEqual(resAnalytics.statusCode, 200);
    const summary = resAnalytics.jsonBody as unknown as {
      totalInspections: number;
      totalDefectsDetected: number;
      defectRatePercentage: number;
      averageInferenceLatencyMs: number;
      defectBreakdown: Array<{ label: string; count: number }>;
    };
    assert.strictEqual(summary.totalInspections, 2);
    assert.strictEqual(summary.totalDefectsDetected, 2);
    assert.strictEqual(summary.defectRatePercentage, 50);
    assert.strictEqual(summary.averageInferenceLatencyMs, 18.0);
    assert.strictEqual(summary.defectBreakdown.length, 1);
    assert.strictEqual(summary.defectBreakdown[0].label, "inclusion");
    assert.strictEqual(summary.defectBreakdown[0].count, 2);
  });
});
