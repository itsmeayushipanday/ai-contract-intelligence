from fastapi.testclient import TestClient

from app.main import create_app


def test_health_reports_ok_with_service_name_and_version():
    client = TestClient(create_app())

    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "ai-service", "version": "0.1.0"}


def test_unknown_route_returns_404():
    client = TestClient(create_app())

    assert client.get("/does-not-exist").status_code == 404


def test_openapi_document_lists_the_health_endpoint():
    client = TestClient(create_app())

    spec = client.get("/openapi.json").json()

    assert "/health" in spec["paths"]
    assert spec["info"]["title"] == "AI Contract Intelligence - AI Service"
