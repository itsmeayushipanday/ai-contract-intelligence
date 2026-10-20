import pytest
from fastapi.testclient import TestClient

from app.config import Settings, get_settings
from app.main import create_app
from tests.pdf_factory import make_encrypted_pdf, make_image_only_pdf, make_pdf


@pytest.fixture
def app():
    return create_app()


@pytest.fixture
def client(app):
    return TestClient(app)


def post_pdf(client, data: bytes, filename: str = "contract.pdf"):
    return client.post("/v1/extract", files={"file": (filename, data, "application/pdf")})


def assert_problem(response, status: int, code: str):
    """Every error is an RFC 9457 problem document with a stable machine-readable code."""
    assert response.status_code == status
    assert response.headers["content-type"].startswith("application/problem+json")
    body = response.json()
    assert body["status"] == status
    assert body["code"] == code
    assert body["title"] and body["detail"]
    return body


# ---------- happy path ----------

def test_extract_returns_page_count_and_text_per_page(client):
    pdf = make_pdf([["Parties: Acme Ltd and Beta Inc."], ["Governing law: India."]])

    response = post_pdf(client, pdf)

    assert response.status_code == 200
    body = response.json()
    assert body["page_count"] == 2
    assert [p["page_number"] for p in body["pages"]] == [1, 2]
    assert "Acme Ltd" in body["pages"][0]["text"]
    assert "Governing law" in body["pages"][1]["text"]


def test_openapi_describes_the_extract_endpoint(client):
    spec = client.get("/openapi.json").json()

    assert "/v1/extract" in spec["paths"]
    assert "ExtractResponse" in spec["components"]["schemas"]


# ---------- errors ----------

def test_missing_file_part_is_a_problem_document_with_invalid_request_code(client):
    body = assert_problem(client.post("/v1/extract"), 422, "invalid_request")

    assert any(e["field"].endswith("file") for e in body["errors"])


def test_empty_file_returns_422_empty_file(client):
    assert_problem(post_pdf(client, b""), 422, "empty_file")


def test_text_file_pretending_to_be_a_pdf_returns_422_invalid_pdf(client):
    assert_problem(post_pdf(client, b"hello, I am not a pdf", "fake.pdf"), 422, "invalid_pdf")


def test_truncated_pdf_returns_422_invalid_pdf(client):
    good = make_pdf([["Some contract text"], ["More"]])

    assert_problem(post_pdf(client, good[: len(good) // 2]), 422, "invalid_pdf")


def test_password_protected_pdf_returns_422_encrypted_pdf(client):
    pdf = make_encrypted_pdf(["Secret"], user_password="s3cret")

    assert_problem(post_pdf(client, pdf), 422, "encrypted_pdf")


def test_scanned_pdf_returns_422_no_extractable_text(client):
    assert_problem(post_pdf(client, make_image_only_pdf()), 422, "no_extractable_text")


def test_file_over_the_size_limit_returns_413(app):
    app.dependency_overrides[get_settings] = lambda: Settings(max_file_size_bytes=2_000)
    client = TestClient(app)
    big = make_pdf([[f"line {n}" for n in range(40)] for _ in range(5)])
    assert len(big) > 2_000  # sanity check for the test itself

    body = assert_problem(post_pdf(client, big), 413, "file_too_large")

    assert "maximum size" in body["detail"]


def test_file_exactly_at_the_size_limit_is_accepted(app):
    pdf = make_pdf([["Fits exactly"]])
    app.dependency_overrides[get_settings] = lambda: Settings(max_file_size_bytes=len(pdf))
    client = TestClient(app)

    assert post_pdf(client, pdf).status_code == 200


def test_too_many_pages_returns_422(app):
    app.dependency_overrides[get_settings] = lambda: Settings(max_pages=2)
    client = TestClient(app)

    assert_problem(post_pdf(client, make_pdf([["a"], ["b"], ["c"]])), 422, "too_many_pages")


def test_unexpected_failure_returns_generic_500_without_internals(app, monkeypatch):
    def explode(*args, **kwargs):
        raise RuntimeError("secret internal detail: db password is hunter2")

    monkeypatch.setattr("app.api.extract.extract_document", explode)
    # raise_server_exceptions=False: behave like the real server, which returns the 500 response
    client = TestClient(app, raise_server_exceptions=False)

    response = post_pdf(client, make_pdf([["x"]]))

    assert_problem(response, 500, "internal_error")
    assert "hunter2" not in response.text and "RuntimeError" not in response.text
