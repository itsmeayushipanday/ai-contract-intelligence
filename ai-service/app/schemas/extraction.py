from pydantic import BaseModel, Field


class PageText(BaseModel):
    """The text of one PDF page. Page numbers start at 1, the way people count pages."""

    page_number: int = Field(ge=1)
    # Empty string for pages without extractable text. The page is still listed so numbers never shift.
    text: str


class Heading(BaseModel):
    """A section heading found by a heuristic. BEST EFFORT: expect misses and the odd false positive."""

    page_number: int = Field(ge=1)
    text: str
    # The numbering if the heading has one, e.g. "5", "5.2" or "IV" (from "5.2 Payment" or "Article IV").
    number: str | None = None


class ExtractResponse(BaseModel):
    page_count: int = Field(ge=0)
    pages: list[PageText]
    # Best effort (see Heading). Empty when none were found or detection failed; extraction still succeeds.
    headings: list[Heading] = []
