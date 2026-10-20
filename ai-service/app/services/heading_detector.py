"""Best-effort detection of section headings from how text looks (font size, boldness, numbering).

A PDF does not store "this is a heading", so this is a heuristic: it will miss some headings and flag
a few false ones. Callers must treat the result as a hint (for citations such as "Section 5"), never as
ground truth. This module is pure logic (no PDF library), so it is easy to unit-test with fake lines.
"""

import re
from collections import Counter
from dataclasses import dataclass

from app.schemas.extraction import Heading

# Tunable rules of thumb
LARGER_THAN_BODY = 1.15  # a line this much bigger than the body text is a heading candidate
MAX_HEADING_LENGTH = 100  # longer lines are paragraphs, not headings
MAX_SHORT_BOLD_LENGTH = 60  # bold lines up to this long count as headings even without numbering
RUNNING_HEADER_SHARE = 0.3  # text repeated on this share of pages (and 3+ pages) is a header/footer
MAX_HEADINGS = 1000  # never return an unbounded list

# "5.", "5.2 Payment", "5.2.1)"  /  "Article IV", "SECTION 3", "Clause 12"
NUMBERED = re.compile(r"^(\d+(?:\.\d+)*)[.)]?\s+\S")
LABELLED = re.compile(r"^(?:article|section|clause)\s+([ivxlcdm]+|\d+(?:\.\d+)*)\b", re.IGNORECASE)
DOT_LEADERS = re.compile(r"\.{4,}")  # table-of-contents lines: "Termination ........ 12"


@dataclass(frozen=True)
class Line:
    """One line of text on a page, with the font facts needed to judge whether it is a heading."""

    page_number: int
    text: str
    size: float  # the line's dominant font size, in points
    bold: bool  # the whole line is set in a bold font


def _number_of(text: str) -> str | None:
    match = NUMBERED.match(text) or LABELLED.match(text)
    return match.group(1) if match else None


def _body_size(lines: list[Line]) -> float:
    """The most common font size, weighted by how much text uses it (rounded to half points)."""
    weight: Counter[float] = Counter()
    for line in lines:
        weight[round(line.size * 2) / 2] += len(line.text)
    return weight.most_common(1)[0][0]


def _bold_is_a_signal(lines: list[Line]) -> bool:
    """If most of the document is bold, boldness says nothing about headings."""
    total = sum(len(line.text) for line in lines)
    bold = sum(len(line.text) for line in lines if line.bold)
    return total > 0 and bold / total < 0.5


def _looks_like_heading(line: Line, body_size: float, bold_signal: bool) -> bool:
    text = line.text
    if not (3 <= len(text) <= MAX_HEADING_LENGTH):
        return False
    if not any(ch.isalpha() for ch in text):  # page numbers, rules like "-----"
        return False
    if text.endswith((".", ";", ",")):  # sentences, not headings
        return False
    if DOT_LEADERS.search(text):  # table of contents entry
        return False

    if line.size >= body_size * LARGER_THAN_BODY:
        return True

    if bold_signal and line.bold and not text.endswith(":"):
        letters = [ch for ch in text if ch.isalpha()]
        all_caps = len(letters) >= 3 and all(ch.isupper() for ch in letters)
        return _number_of(text) is not None or all_caps or len(text) <= MAX_SHORT_BOLD_LENGTH
    return False


def _normalised(text: str) -> str:
    return " ".join(text.lower().split())


def detect_headings(lines: list[Line], page_count: int) -> list[Heading]:
    """Return the probable section headings in reading order. Never raises for odd input."""
    if not lines:
        return []

    body_size = _body_size(lines)
    bold_signal = _bold_is_a_signal(lines)
    candidates = [line for line in lines if _looks_like_heading(line, body_size, bold_signal)]

    # Running headers/footers ("Acme Corp - Confidential") repeat on many pages and are not sections.
    pages_with_text: dict[str, set[int]] = {}
    for line in candidates:
        pages_with_text.setdefault(_normalised(line.text), set()).add(line.page_number)
    running = {
        text for text, pages in pages_with_text.items()
        if len(pages) >= 3 and len(pages) / max(page_count, 1) >= RUNNING_HEADER_SHARE
    }

    return [
        Heading(page_number=line.page_number, text=line.text, number=_number_of(line.text))
        for line in candidates
        if _normalised(line.text) not in running
    ][:MAX_HEADINGS]
