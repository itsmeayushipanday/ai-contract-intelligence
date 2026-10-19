"""Errors for PDF processing and how they are turned into HTTP responses.

Every error body is an RFC 9457 "problem details" document plus a stable machine-readable `code`,
the same format the Java backend uses, so the two services speak one error language.
"""

import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

logger = logging.getLogger(__name__)

PROBLEM_JSON = "application/problem+json"


class PdfProcessingError(Exception):
    """Base class: a problem with the uploaded PDF that the caller can understand and act on."""

    status_code = 422
    code = "pdf_error"
    title = "PDF could not be processed"

    def __init__(self, detail: str):
        super().__init__(detail)
        self.detail = detail


class EmptyFileError(PdfProcessingError):
    code = "empty_file"
    title = "Empty file"


class InvalidPdfError(PdfProcessingError):
    code = "invalid_pdf"
    title = "Invalid PDF"


class EncryptedPdfError(PdfProcessingError):
    code = "encrypted_pdf"
    title = "Encrypted PDF"


class NoExtractableTextError(PdfProcessingError):
    code = "no_extractable_text"
    title = "No extractable text"


class TooManyPagesError(PdfProcessingError):
    code = "too_many_pages"
    title = "Too many pages"


class FileTooLargeError(PdfProcessingError):
    status_code = 413
    code = "file_too_large"
    title = "File too large"


def problem_response(status: int, title: str, detail: str, code: str, **extra) -> JSONResponse:
    body = {"type": "about:blank", "title": title, "status": status, "detail": detail, "code": code, **extra}
    return JSONResponse(status_code=status, content=body, media_type=PROBLEM_JSON)


def register_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(PdfProcessingError)
    async def handle_pdf_error(_: Request, exc: PdfProcessingError) -> JSONResponse:
        return problem_response(exc.status_code, exc.title, exc.detail, exc.code)

    @app.exception_handler(RequestValidationError)
    async def handle_validation_error(_: Request, exc: RequestValidationError) -> JSONResponse:
        # Only where and why. exc.errors() also carries the submitted input, which we never echo back.
        problems = [{"field": ".".join(str(p) for p in e["loc"]), "message": e["msg"]} for e in exc.errors()]
        return problem_response(422, "Invalid request", "The request is missing or has invalid fields.",
                                "invalid_request", errors=problems)

    @app.exception_handler(Exception)
    async def handle_unexpected(_: Request, exc: Exception) -> JSONResponse:
        # The real cause goes to our log only. The caller gets nothing internal.
        logger.error("Unhandled error: %s", type(exc).__name__, exc_info=exc)
        return problem_response(500, "Internal server error", "An unexpected error occurred.", "internal_error")
