import pytest

from app.errors import (
    EmptyFileError,
    EncryptedPdfError,
    InvalidPdfError,
    NoExtractableTextError,
    TooManyPagesError,
)
from app.services.pdf_extractor import extract_pages
from tests.pdf_factory import make_encrypted_pdf, make_image_only_pdf, make_pdf

MAX_PAGES = 500


# ---------- happy path ----------

def test_extracts_text_per_page_with_one_based_page_numbers():
    pdf = make_pdf([
        ["Termination", "Either party may terminate with 30 days notice."],
        ["Payment is due within 45 days of invoice."],
    ])

    pages = extract_pages(pdf, max_pages=MAX_PAGES)

    assert [p.page_number for p in pages] == [1, 2]
    assert "Either party may terminate with 30 days notice." in pages[0].text
    assert "Payment is due within 45 days of invoice." in pages[1].text
    # text from page 2 must not leak into page 1
    assert "Payment" not in pages[0].text


def test_keeps_line_breaks_and_order_within_a_page():
    pdf = make_pdf([["First line", "Second line", "Third line"]])

    text = extract_pages(pdf, max_pages=MAX_PAGES)[0].text

    assert text.splitlines() == ["First line", "Second line", "Third line"]


def test_blank_page_is_kept_with_empty_text_so_page_numbers_do_not_shift():
    pdf = make_pdf([["Page one"], [], ["Page three"]])

    pages = extract_pages(pdf, max_pages=MAX_PAGES)

    assert [p.page_number for p in pages] == [1, 2, 3]
    assert pages[1].text == ""
    assert "Page three" in pages[2].text


def test_many_pages_keep_their_order():
    pdf = make_pdf([[f"Marker {n}"] for n in range(1, 26)])

    pages = extract_pages(pdf, max_pages=MAX_PAGES)

    assert len(pages) == 25
    for page in pages:
        assert f"Marker {page.page_number}" in page.text


def test_pdf_with_only_an_owner_password_still_opens_and_extracts():
    # "restricted editing" PDFs open without a password; there is no reason to refuse them
    pdf = make_encrypted_pdf(["Open to read"], user_password="")

    pages = extract_pages(pdf, max_pages=MAX_PAGES)

    assert "Open to read" in pages[0].text


def test_exactly_the_page_limit_is_accepted():
    pdf = make_pdf([[f"Page {n}"] for n in range(1, 4)])

    assert len(extract_pages(pdf, max_pages=3)) == 3


# ---------- problems the caller can act on ----------

def test_empty_input_is_rejected():
    with pytest.raises(EmptyFileError):
        extract_pages(b"", max_pages=MAX_PAGES)


@pytest.mark.parametrize("content", [b"just some text", b"MZ\x90\x00 pretend exe", b"%PD"])
def test_input_that_is_not_a_pdf_is_rejected(content):
    with pytest.raises(InvalidPdfError):
        extract_pages(content, max_pages=MAX_PAGES)


def test_truncated_pdf_is_rejected_as_invalid_not_a_crash():
    good = make_pdf([["Some contract text"], ["More text"]])
    truncated = good[: len(good) // 2]  # starts like a PDF, but the file is cut off

    with pytest.raises(InvalidPdfError):
        extract_pages(truncated, max_pages=MAX_PAGES)


def test_pdf_with_garbage_after_the_header_is_rejected():
    with pytest.raises(InvalidPdfError):
        extract_pages(b"%PDF-1.7\n" + b"\x00\xff garbage " * 50, max_pages=MAX_PAGES)


def test_password_protected_pdf_is_reported_as_encrypted():
    pdf = make_encrypted_pdf(["Secret terms"], user_password="s3cret")

    with pytest.raises(EncryptedPdfError):
        extract_pages(pdf, max_pages=MAX_PAGES)


def test_scanned_image_only_pdf_is_reported_as_having_no_text():
    with pytest.raises(NoExtractableTextError):
        extract_pages(make_image_only_pdf(page_count=2), max_pages=MAX_PAGES)


def test_pdf_where_every_page_is_blank_is_reported_as_having_no_text():
    with pytest.raises(NoExtractableTextError):
        extract_pages(make_pdf([[], [], []]), max_pages=MAX_PAGES)


def test_more_pages_than_the_limit_is_rejected_before_any_text_is_extracted():
    pdf = make_pdf([[f"Page {n}"] for n in range(1, 5)])

    with pytest.raises(TooManyPagesError) as error:
        extract_pages(pdf, max_pages=3)

    assert "4 pages" in error.value.detail and "3" in error.value.detail


def test_error_messages_never_contain_document_text():
    pdf = make_encrypted_pdf(["CONFIDENTIAL-CLAUSE-TEXT"], user_password="s3cret")

    with pytest.raises(EncryptedPdfError) as error:
        extract_pages(pdf, max_pages=MAX_PAGES)

    assert "CONFIDENTIAL" not in error.value.detail
