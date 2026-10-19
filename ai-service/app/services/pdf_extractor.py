import io
import logging

import pdfplumber
from pdfminer.pdfdocument import PDFPasswordIncorrect

from app.errors import (
    EmptyFileError,
    EncryptedPdfError,
    InvalidPdfError,
    NoExtractableTextError,
    TooManyPagesError,
)
from app.schemas.extraction import PageText

logger = logging.getLogger(__name__)

# Every PDF starts with these 5 bytes (the Java backend checks the same thing before storing a file).
PDF_MAGIC = b"%PDF-"


def _is_password_error(error: Exception) -> bool:
    """pdfplumber wraps pdfminer's "wrong password" error in its own PdfminerException, with the
    original as the first argument, so look at the error itself and at what it wraps."""
    return isinstance(error, PDFPasswordIncorrect) or any(isinstance(a, PDFPasswordIncorrect) for a in error.args)


def extract_pages(data: bytes, *, max_pages: int) -> list[PageText]:
    """Return the text of every page, in order, with 1-based page numbers.

    The input is untrusted, so checks run cheapest first. Pages with no text are kept (with empty
    text) so that page numbers stay correct for citations, but a document with no text on ANY page is
    rejected as scanned/image-only. Only sizes, counts and error types are logged, never document text.

    Raises a PdfProcessingError subclass for every problem the caller can act on.
    """
    if not data:
        raise EmptyFileError("The uploaded file is empty.")
    if not data.startswith(PDF_MAGIC):
        raise InvalidPdfError("The file is not a PDF.")

    pages: list[PageText] = []
    try:
        with pdfplumber.open(io.BytesIO(data)) as pdf:
            if len(pdf.pages) == 0:
                raise InvalidPdfError("The PDF has no pages or is damaged.")
            if len(pdf.pages) > max_pages:
                raise TooManyPagesError(f"The PDF has {len(pdf.pages)} pages; the maximum is {max_pages}.")
            for number, page in enumerate(pdf.pages, start=1):
                # extract_text() returns None for a page with no text
                pages.append(PageText(page_number=number, text=page.extract_text() or ""))
    except (InvalidPdfError, TooManyPagesError):
        raise
    except Exception as e:
        if _is_password_error(e):
            raise EncryptedPdfError("The PDF is password-protected and cannot be read.") from e
        # Parsers raise many different errors for damaged files. At this boundary with untrusted input,
        # all of them mean "this PDF cannot be read". The error type is logged, never the content.
        logger.warning("PDF parsing failed: %s", type(e).__name__)
        raise InvalidPdfError("The PDF is damaged or could not be read.") from e

    if not any(page.text.strip() for page in pages):
        raise NoExtractableTextError(
            "The PDF has no extractable text. It may be a scanned document (images only); OCR is not supported."
        )

    logger.info("Extracted %d pages from a %d-byte PDF", len(pages), len(data))
    return pages
