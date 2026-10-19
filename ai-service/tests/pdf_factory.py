"""Builds small real PDFs for tests, so extraction is exercised for real instead of mocked."""

import io

from PIL import Image
from reportlab.lib.pagesizes import A4
from reportlab.lib.pdfencrypt import StandardEncryption
from reportlab.lib.utils import ImageReader
from reportlab.pdfgen import canvas


def _build(pages: list[list[str]], **canvas_options) -> bytes:
    buffer = io.BytesIO()
    pdf = canvas.Canvas(buffer, pagesize=A4, **canvas_options)
    for lines in pages:
        y = 800
        for line in lines:
            pdf.drawString(72, y, line)
            y -= 18
        pdf.showPage()
    pdf.save()
    return buffer.getvalue()


def make_pdf(pages: list[list[str]]) -> bytes:
    """One PDF page per entry; each entry is the list of text lines on that page ([] = blank page)."""
    return _build(pages)


def make_encrypted_pdf(lines: list[str], user_password: str, owner_password: str = "owner-secret") -> bytes:
    """A PDF that needs `user_password` to open. With user_password="" it opens freely (restricted-edit PDFs)."""
    encryption = StandardEncryption(user_password, ownerPassword=owner_password)
    return _build([lines], encrypt=encryption)


def make_image_only_pdf(page_count: int = 1) -> bytes:
    """Like a scanned document: every page is a picture and has no text layer at all."""
    buffer = io.BytesIO()
    pdf = canvas.Canvas(buffer, pagesize=A4)
    picture = ImageReader(Image.new("RGB", (200, 100), "lightgray"))
    for _ in range(page_count):
        pdf.drawImage(picture, 72, 600, width=200, height=100)
        pdf.showPage()
    pdf.save()
    return buffer.getvalue()
