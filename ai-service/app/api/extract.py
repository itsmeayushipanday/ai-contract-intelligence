from typing import Annotated

from fastapi import APIRouter, Depends, File, UploadFile

from app.config import Settings, get_settings
from app.errors import FileTooLargeError
from app.schemas.extraction import ExtractResponse
from app.services.pdf_extractor import extract_pages

router = APIRouter(prefix="/v1", tags=["extraction"])


# A plain `def` (not `async def`): PDF parsing is CPU-bound and blocking, so FastAPI runs this in a
# worker thread and one slow PDF cannot freeze the rest of the service.
@router.post("/extract", response_model=ExtractResponse)
def extract(
    file: Annotated[UploadFile, File(description="The PDF to read")],
    settings: Annotated[Settings, Depends(get_settings)],
) -> ExtractResponse:
    """Extract the text of a PDF, page by page. Errors are problem-details documents with a `code`."""
    # Read at most limit + 1 bytes: enough to know it is too big, without loading an unbounded upload.
    data = file.file.read(settings.max_file_size_bytes + 1)
    if len(data) > settings.max_file_size_bytes:
        limit_mb = settings.max_file_size_bytes / (1024 * 1024)
        raise FileTooLargeError(f"The file exceeds the maximum size of {limit_mb:g} MB.")

    pages = extract_pages(data, max_pages=settings.max_pages)
    return ExtractResponse(page_count=len(pages), pages=pages)
