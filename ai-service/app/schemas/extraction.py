from pydantic import BaseModel, Field


class PageText(BaseModel):
    """The text of one PDF page. Page numbers start at 1, the way people count pages."""

    page_number: int = Field(ge=1)
    # Empty string for pages without extractable text. The page is still listed so numbers never shift.
    text: str


class ExtractResponse(BaseModel):
    page_count: int = Field(ge=0)
    pages: list[PageText]
