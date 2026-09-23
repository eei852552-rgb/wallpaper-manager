import assert from "node:assert/strict";
import test from "node:test";
import { HttpError, toClientError } from "../src/http-utils.js";

test("client errors preserve safe status and message", () => {
  assert.deepEqual(toClientError(new HttpError(403, "Admin permission required")), {
    status: 403,
    message: "Admin permission required",
  });
});

test("unknown errors become a generic 500", () => {
  assert.deepEqual(toClientError(new Error("private service-account detail")), {
    status: 500,
    message: "Internal server error",
  });
});
