"""Unit tests for the heading heuristics, using fake lines (no PDFs needed)."""

from app.services.heading_detector import Line, detect_headings

BODY = 11.0
BIG = 14.0


def body(text="This clause sets out the obligations of the supplier in some detail here", page=1):
    return Line(page, text, BODY, False)


def texts(headings):
    return [h.text for h in headings]


def test_larger_font_line_is_a_heading():
    lines = [Line(1, "Termination", BIG, False), body(), body(), body()]

    headings = detect_headings(lines, page_count=1)

    assert texts(headings) == ["Termination"]
    assert headings[0].page_number == 1


def test_bold_numbered_line_at_body_size_is_a_heading_with_its_number():
    lines = [body(), Line(2, "5.2 Payment Terms", BODY, True), body(page=2), body(page=2)]

    headings = detect_headings(lines, page_count=2)

    assert [(h.text, h.number, h.page_number) for h in headings] == [("5.2 Payment Terms", "5.2", 2)]


def test_label_style_numbering_is_extracted():
    lines = [Line(1, "ARTICLE IV", BIG, True), Line(1, "Section 3 Liability", BODY, True), body(), body(), body()]

    headings = detect_headings(lines, page_count=1)

    assert [(h.text, h.number) for h in headings] == [("ARTICLE IV", "IV"), ("Section 3 Liability", "3")]


def test_all_caps_bold_line_is_a_heading():
    lines = [Line(1, "CONFIDENTIALITY", BODY, True), body(), body(), body()]

    assert texts(detect_headings(lines, page_count=1)) == ["CONFIDENTIALITY"]


def test_short_bold_line_without_numbering_is_a_heading():
    lines = [Line(1, "Governing Law", BODY, True), body(), body(), body()]

    assert texts(detect_headings(lines, page_count=1)) == ["Governing Law"]


def test_plain_body_text_is_not_a_heading():
    assert detect_headings([body(), body(), body()], page_count=1) == []


def test_sentences_are_not_headings_even_if_large_or_bold():
    lines = [
        Line(1, "The supplier shall deliver the goods on time.", BIG, False),
        Line(1, "Payment is due within thirty days, subject to clause 4;", BODY, True),
        body(), body(), body(),
    ]

    assert detect_headings(lines, page_count=1) == []


def test_bold_label_ending_with_colon_is_not_a_heading():
    lines = [Line(1, "Note:", BODY, True), body(), body(), body()]

    assert detect_headings(lines, page_count=1) == []


def test_page_numbers_and_rules_are_not_headings():
    lines = [Line(1, "12", BIG, False), Line(1, "- - - - -", BIG, False), Line(1, "© 2026", BIG, False), body(), body()]

    # "© 2026" has no letters, "12" and the rule neither
    assert detect_headings(lines, page_count=1) == []


def test_table_of_contents_lines_with_dot_leaders_are_not_headings():
    lines = [Line(1, "5. Termination ............. 12", BIG, False), body(), body(), body()]

    assert detect_headings(lines, page_count=1) == []


def test_very_long_large_line_is_a_paragraph_not_a_heading():
    lines = [Line(1, "word " * 40, BIG, False), body(), body(), body()]

    assert detect_headings(lines, page_count=1) == []


def test_running_header_repeated_on_many_pages_is_dropped_but_real_headings_stay():
    lines = []
    for page in range(1, 7):
        lines.append(Line(page, "Acme Corp Confidential", BIG, False))  # header on every page
        lines.append(body(page=page))
        lines.append(body(page=page))
    lines.append(Line(2, "Definitions", BIG, False))
    lines.append(Line(5, "Termination", BIG, False))

    headings = detect_headings(lines, page_count=6)

    assert texts(headings) == ["Definitions", "Termination"]


def test_same_heading_text_on_two_pages_of_a_short_document_is_kept():
    lines = [Line(1, "Schedule", BIG, False), body(), Line(2, "Schedule", BIG, False), body(page=2)]

    assert len(detect_headings(lines, page_count=2)) == 2


def test_when_almost_everything_is_bold_boldness_is_not_a_signal():
    lines = [Line(1, "Some bold clause text goes here in full", BODY, True) for _ in range(10)]
    lines.append(Line(1, "Short Bold Line", BODY, True))

    assert detect_headings(lines, page_count=1) == []


def test_headings_keep_reading_order():
    lines = [
        Line(1, "1. Definitions", BODY, True), body(),
        Line(2, "2. Payment", BODY, True), body(page=2),
        Line(3, "3. Termination", BODY, True), body(page=3),
    ]

    assert texts(detect_headings(lines, page_count=3)) == ["1. Definitions", "2. Payment", "3. Termination"]


def test_no_lines_means_no_headings():
    assert detect_headings([], page_count=0) == []


def test_result_is_capped():
    # plenty of body text, so the body size is clearly the smaller one (as in a real contract)
    lines = [Line(1, f"Heading {n}", BIG, False) for n in range(1500)] + [body() for _ in range(600)]

    assert len(detect_headings(lines, page_count=1)) == 1000


def test_body_size_is_the_most_common_size_by_amount_of_text():
    # if the larger size is what most of the text uses, that IS the body size and nothing stands out
    lines = [Line(1, f"Big line number {n}", BIG, False) for n in range(50)] + [body()]

    assert detect_headings(lines, page_count=1) == []
