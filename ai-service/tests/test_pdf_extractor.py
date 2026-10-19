from app.services.pdf_extractor import extract_pages
from tests.pdf_factory import make_pdf


def test_extracts_text_per_page_with_one_based_page_numbers():
    pdf = make_pdf([
        ["Termination", "Either party may terminate with 30 days notice."],
        ["Payment is due within 45 days of invoice."],
    ])

    pages = extract_pages(pdf)

    assert [p.page_number for p in pages] == [1, 2]
    assert "Either party may terminate with 30 days notice." in pages[0].text
    assert "Payment is due within 45 days of invoice." in pages[1].text
    # text from page 2 must not leak into page 1
    assert "Payment" not in pages[0].text


def test_keeps_line_breaks_and_order_within_a_page():
    pdf = make_pdf([["First line", "Second line", "Third line"]])

    text = extract_pages(pdf)[0].text

    assert text.splitlines() == ["First line", "Second line", "Third line"]


def test_blank_page_is_kept_with_empty_text_so_page_numbers_do_not_shift():
    pdf = make_pdf([["Page one"], [], ["Page three"]])

    pages = extract_pages(pdf)

    assert [p.page_number for p in pages] == [1, 2, 3]
    assert pages[1].text == ""
    assert "Page three" in pages[2].text


def test_many_pages_keep_their_order():
    pdf = make_pdf([[f"Marker {n}"] for n in range(1, 26)])

    pages = extract_pages(pdf)

    assert len(pages) == 25
    for page in pages:
        assert f"Marker {page.page_number}" in page.text
