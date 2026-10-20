"""Heading detection end to end: real PDFs with real fonts, through the extractor and the HTTP API."""

import pytest
from fastapi.testclient import TestClient

from app.main import create_app
from app.services import pdf_extractor
from app.services.pdf_extractor import extract_document, extract_pages
from tests.pdf_factory import make_pdf

BOLD = "Helvetica-Bold"
BODY_LINE = "The parties agree to the terms set out in this agreement and the schedules attached to it."


def contract_pdf() -> bytes:
    return make_pdf([
        [("SERVICES AGREEMENT", BOLD, 16), BODY_LINE, BODY_LINE],
        [("1. Definitions", BOLD, 11), BODY_LINE, BODY_LINE, ("2. Payment Terms", BOLD, 11), BODY_LINE],
        [("3. Termination", BOLD, 11), BODY_LINE, BODY_LINE, "Either party may terminate on 30 days notice."],
    ])


def test_headings_are_found_with_page_numbers_and_numbering():
    headings = extract_document(contract_pdf(), max_pages=500).headings

    found = [(h.page_number, h.number, h.text) for h in headings]
    assert (1, None, "SERVICES AGREEMENT") in found
    assert (2, "1", "1. Definitions") in found
    assert (2, "2", "2. Payment Terms") in found
    assert (3, "3", "3. Termination") in found


def test_body_sentences_are_not_reported_as_headings():
    headings = extract_document(contract_pdf(), max_pages=500).headings

    assert all(BODY_LINE not in h.text for h in headings)
    assert all("terminate on 30 days" not in h.text for h in headings)
    assert len(headings) == 4


def test_document_without_headings_returns_an_empty_list_and_still_extracts():
    pdf = make_pdf([[BODY_LINE, BODY_LINE], [BODY_LINE]])

    document = extract_document(pdf, max_pages=500)

    assert document.headings == []
    assert len(document.pages) == 2


def test_heading_detection_does_not_change_the_extracted_page_text():
    pdf = contract_pdf()

    pages = extract_pages(pdf, max_pages=500)

    assert "3. Termination" in pages[2].text
    assert "Either party may terminate on 30 days notice." in pages[2].text


def test_a_failure_in_heading_detection_never_breaks_text_extraction(monkeypatch):
    def explode(*args, **kwargs):
        raise RuntimeError("heuristic bug")

    monkeypatch.setattr(pdf_extractor, "detect_headings", explode)

    document = extract_document(contract_pdf(), max_pages=500)

    assert document.headings == []
    assert len(document.pages) == 3
    assert "Payment Terms" in document.pages[1].text


def test_a_failure_while_reading_line_details_never_breaks_text_extraction(monkeypatch):
    def explode(*args, **kwargs):
        raise RuntimeError("line details bug")

    monkeypatch.setattr(pdf_extractor, "_page_lines", explode)

    document = extract_document(contract_pdf(), max_pages=500)

    assert document.headings == []
    assert len(document.pages) == 3


def test_api_response_includes_headings():
    client = TestClient(create_app())

    response = client.post("/v1/extract", files={"file": ("c.pdf", contract_pdf(), "application/pdf")})

    assert response.status_code == 200
    body = response.json()
    assert {"page_number": 2, "text": "2. Payment Terms", "number": "2"} in body["headings"]
    assert body["page_count"] == 3


def test_openapi_documents_the_heading_model_as_best_effort():
    spec = TestClient(create_app()).get("/openapi.json").json()

    heading_schema = spec["components"]["schemas"]["Heading"]
    assert "BEST EFFORT" in heading_schema["description"]
    assert "headings" in spec["components"]["schemas"]["ExtractResponse"]["properties"]
