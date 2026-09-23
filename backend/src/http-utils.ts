export class HttpError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
  }
}

export function toClientError(error: unknown): { status: number; message: string } {
  if (error instanceof HttpError) return { status: error.status, message: error.message };
  return { status: 500, message: "Internal server error" };
}
