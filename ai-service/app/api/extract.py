from typing import Annotated

from fastapi import APIRouter, File, UploadFile

from app.schemas.extraction import ExtractResponse
from app.services.pdf_extractor import extract_pages

router = APIRouter(prefix="/v1", tags=["extraction"])


# A plain `def` (not `async def`): PDF parsing is CPU-bound and blocking, so FastAPI runs this in a
# worker thread and one slow PDF cannot freeze the rest of the service.
@router.post("/extract", response_model=ExtractResponse)
def extract(file: Annotated[UploadFile, File(description="The PDF to read")]) -> ExtractResponse:
    """Extract the text of a PDF, page by page."""
    pages = extract_pages(file.file.read())
    return ExtractResponse(page_count=len(pages), pages=pages)
