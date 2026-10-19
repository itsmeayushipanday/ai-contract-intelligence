from fastapi.testclient import TestClient

from app.main import create_app
from tests.pdf_factory import make_pdf

client = TestClient(create_app())


def post_pdf(data: bytes, filename: str = "contract.pdf"):
    return client.post("/v1/extract", files={"file": (filename, data, "application/pdf")})


def test_extract_returns_page_count_and_text_per_page():
    pdf = make_pdf([["Parties: Acme Ltd and Beta Inc."], ["Governing law: India."]])

    response = post_pdf(pdf)

    assert response.status_code == 200
    body = response.json()
    assert body["page_count"] == 2
    assert [p["page_number"] for p in body["pages"]] == [1, 2]
    assert "Acme Ltd" in body["pages"][0]["text"]
    assert "Governing law" in body["pages"][1]["text"]


def test_extract_without_a_file_part_is_rejected_by_validation():
    response = client.post("/v1/extract")

    assert response.status_code == 422


def test_openapi_describes_the_extract_endpoint():
    spec = client.get("/openapi.json").json()

    assert "/v1/extract" in spec["paths"]
    assert "ExtractResponse" in spec["components"]["schemas"]
