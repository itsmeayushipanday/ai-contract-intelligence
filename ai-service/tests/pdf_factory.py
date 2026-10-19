"""Builds small real PDFs for tests, so extraction is exercised for real instead of mocked."""

import io

from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas


def make_pdf(pages: list[list[str]]) -> bytes:
    """One PDF page per entry; each entry is the list of text lines on that page ([] = blank page)."""
    buffer = io.BytesIO()
    pdf = canvas.Canvas(buffer, pagesize=A4)
    for lines in pages:
        y = 800
        for line in lines:
            pdf.drawString(72, y, line)
            y -= 18
        pdf.showPage()
    pdf.save()
    return buffer.getvalue()
