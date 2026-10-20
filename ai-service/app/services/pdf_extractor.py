import io
import logging
from collections import Counter
from typing import NamedTuple

import pdfplumber
from pdfminer.pdfdocument import PDFPasswordIncorrect

from app.errors import (
    EmptyFileError,
    EncryptedPdfError,
    InvalidPdfError,
    NoExtractableTextError,
    TooManyPagesError,
)
from app.schemas.extraction import Heading, PageText
from app.services.heading_detector import Line, detect_headings

logger = logging.getLogger(__name__)

# Every PDF starts with these 5 bytes (the Java backend checks the same thing before storing a file).
PDF_MAGIC = b"%PDF-"

BOLD_FONT_WORDS = ("bold", "black", "heavy")  # e.g. Arial-BoldMT, Calibri-Bold


class Extraction(NamedTuple):
    pages: list[PageText]
    headings: list[Heading]  # best effort, may be empty


def _is_password_error(error: Exception) -> bool:
    """pdfplumber wraps pdfminer's "wrong password" error in its own PdfminerException, with the
    original as the first argument, so look at the error itself and at what it wraps."""
    return isinstance(error, PDFPasswordIncorrect) or any(isinstance(a, PDFPasswordIncorrect) for a in error.args)


def _page_lines(page, page_number: int) -> list[Line]:
    """The page's lines with font facts (dominant size, whole-line bold) for heading detection."""
    lines: list[Line] = []
    for raw in page.extract_text_lines(strip=True, return_chars=True):
        text = raw["text"].strip()
        chars = [c for c in raw["chars"] if not c["text"].isspace()]
        if not text or not chars:
            continue
        size = Counter(round(c["size"] * 2) / 2 for c in chars).most_common(1)[0][0]
        bold_chars = sum(any(word in c["fontname"].lower() for word in BOLD_FONT_WORDS) for c in chars)
        lines.append(Line(page_number, text, size, bold_chars / len(chars) >= 0.8))
    return lines


def extract_document(data: bytes, *, max_pages: int) -> Extraction:
    """Return the text of every page (1-based page numbers) plus best-effort section headings.

    The input is untrusted, so checks run cheapest first. Pages with no text are kept (with empty
    text) so that page numbers stay correct for citations, but a document with no text on ANY page is
    rejected as scanned/image-only. Heading detection is best effort: if it fails for any reason the
    text is still returned, with no headings. Only sizes, counts and error types are logged, never
    document text.

    Raises a PdfProcessingError subclass for every problem the caller can act on.
    """
    if not data:
        raise EmptyFileError("The uploaded file is empty.")
    if not data.startswith(PDF_MAGIC):
        raise InvalidPdfError("The file is not a PDF.")

    pages: list[PageText] = []
    lines: list[Line] = []
    try:
        with pdfplumber.open(io.BytesIO(data)) as pdf:
            if len(pdf.pages) == 0:
                raise InvalidPdfError("The PDF has no pages or is damaged.")
            if len(pdf.pages) > max_pages:
                raise TooManyPagesError(f"The PDF has {len(pdf.pages)} pages; the maximum is {max_pages}.")
            for number, page in enumerate(pdf.pages, start=1):
                # extract_text() returns None for a page with no text
                pages.append(PageText(page_number=number, text=page.extract_text() or ""))
                try:
                    lines.extend(_page_lines(page, number))
                except Exception as e:  # headings are optional: never let them break extraction
                    logger.warning("Reading line details failed on page %d: %s", number, type(e).__name__)
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

    try:
        headings = detect_headings(lines, page_count=len(pages))
    except Exception as e:
        logger.warning("Heading detection failed: %s", type(e).__name__)
        headings = []

    logger.info("Extracted %d pages and %d headings from a %d-byte PDF", len(pages), len(headings), len(data))
    return Extraction(pages, headings)


def extract_pages(data: bytes, *, max_pages: int) -> list[PageText]:
    """Just the page texts (without headings)."""
    return extract_document(data, max_pages=max_pages).pages
