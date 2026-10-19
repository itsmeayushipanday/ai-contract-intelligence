import io
import logging

import pdfplumber

from app.schemas.extraction import PageText

logger = logging.getLogger(__name__)


def extract_pages(data: bytes) -> list[PageText]:
    """Return the text of every page, in order, with 1-based page numbers.

    Pages with no extractable text are kept (with empty text) so that page numbers stay correct
    for citations. Only sizes and counts are logged, never document text.
    """
    pages: list[PageText] = []
    with pdfplumber.open(io.BytesIO(data)) as pdf:
        for number, page in enumerate(pdf.pages, start=1):
            # extract_text() returns None for a page with no text
            pages.append(PageText(page_number=number, text=page.extract_text() or ""))
    logger.info("Extracted %d pages from a %d-byte PDF", len(pages), len(data))
    return pages
