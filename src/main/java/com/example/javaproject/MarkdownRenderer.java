package com.example.javaproject;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.*;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts Markdown-formatted text into styled JavaFX nodes.
 * Supports: **bold**, *italic*, ***bold italic***, `inline code`,
 * ```code blocks```, headers (###), bullet lists, numbered lists,
 * horizontal rules, blockquotes (>), tables (|col|col|),
 * links ([text](url)), and math ($inline$ / $$block$$).
 */
public class MarkdownRenderer {

    // Avoid raw backslash-u sequences in source (illegal unicode escape).
    private static final String BS = "\\";

    private static final String FONT_FAMILY     = "Times New Roman";
    private static final String MONO_FONT       = "Consolas";
    private static final double BASE_FONT_SIZE  = 15.5;
    private static final double CODE_FONT_SIZE  = 15.5;

    // LaTeX → Unicode symbol map
    private static final Map<String, String> MATH_SYMBOLS = new HashMap<>();
    static {
        MATH_SYMBOLS.put("\\alpha",    "α");  MATH_SYMBOLS.put("\\beta",    "β");
        MATH_SYMBOLS.put("\\gamma",    "γ");  MATH_SYMBOLS.put("\\delta",   "δ");
        MATH_SYMBOLS.put("\\epsilon",  "ε");  MATH_SYMBOLS.put("\\zeta",    "ζ");
        MATH_SYMBOLS.put("\\eta",      "η");  MATH_SYMBOLS.put("\\theta",   "θ");
        MATH_SYMBOLS.put("\\iota",     "ι");  MATH_SYMBOLS.put("\\kappa",   "κ");
        MATH_SYMBOLS.put("\\lambda",   "λ");  MATH_SYMBOLS.put("\\mu",      "μ");
        MATH_SYMBOLS.put("\\nu",       "ν");  MATH_SYMBOLS.put("\\xi",      "ξ");
        MATH_SYMBOLS.put("\\pi",       "π");  MATH_SYMBOLS.put("\\rho",     "ρ");
        MATH_SYMBOLS.put("\\sigma",    "σ");  MATH_SYMBOLS.put("\\tau",     "τ");
        MATH_SYMBOLS.put(BS + "upsilon",  "υ");  MATH_SYMBOLS.put("\\phi",     "φ");
        MATH_SYMBOLS.put("\\chi",      "χ");  MATH_SYMBOLS.put("\\psi",     "ψ");
        MATH_SYMBOLS.put("\\omega",    "ω");
        MATH_SYMBOLS.put("\\Alpha",    "Α");  MATH_SYMBOLS.put("\\Beta",    "Β");
        MATH_SYMBOLS.put("\\Gamma",    "Γ");  MATH_SYMBOLS.put("\\Delta",   "Δ");
        MATH_SYMBOLS.put("\\Theta",    "Θ");  MATH_SYMBOLS.put("\\Lambda",  "Λ");
        MATH_SYMBOLS.put("\\Pi",       "Π");  MATH_SYMBOLS.put("\\Sigma",   "Σ");
        MATH_SYMBOLS.put("\\Phi",      "Φ");  MATH_SYMBOLS.put("\\Psi",     "Ψ");
        MATH_SYMBOLS.put("\\Omega",    "Ω");
        MATH_SYMBOLS.put("\\sum",      "∑");  MATH_SYMBOLS.put("\\prod",    "∏");
        MATH_SYMBOLS.put("\\int",      "∫");  MATH_SYMBOLS.put("\\oint",    "∮");
        MATH_SYMBOLS.put("\\infty",    "∞");  MATH_SYMBOLS.put("\\nabla",   "∇");
        MATH_SYMBOLS.put("\\partial",  "∂");  MATH_SYMBOLS.put("\\forall",  "∀");
        MATH_SYMBOLS.put("\\exists",   "∃");  MATH_SYMBOLS.put("\\nexists", "∄");
        MATH_SYMBOLS.put("\\emptyset", "∅");  MATH_SYMBOLS.put("\\in",      "∈");
        MATH_SYMBOLS.put("\\notin",    "∉");  MATH_SYMBOLS.put("\\subset",  "⊂");
        MATH_SYMBOLS.put("\\supset",   "⊃");  MATH_SYMBOLS.put("\\cup",     "∪");
        MATH_SYMBOLS.put("\\cap",      "∩");  MATH_SYMBOLS.put("\\land",    "∧");
        MATH_SYMBOLS.put("\\lor",      "∨");  MATH_SYMBOLS.put("\\neg",     "¬");
        MATH_SYMBOLS.put("\\pm",       "±");  MATH_SYMBOLS.put("\\times",   "×");
        MATH_SYMBOLS.put("\\div",      "÷");  MATH_SYMBOLS.put("\\cdot",    "·");
        MATH_SYMBOLS.put("\\leq",      "≤");  MATH_SYMBOLS.put("\\geq",     "≥");
        MATH_SYMBOLS.put("\\neq",      "≠");  MATH_SYMBOLS.put("\\approx",  "≈");
        MATH_SYMBOLS.put("\\equiv",    "≡");  MATH_SYMBOLS.put("\\cong",    "≅");
        MATH_SYMBOLS.put("\\sim",      "∼");  MATH_SYMBOLS.put("\\propto",  "∝");
        MATH_SYMBOLS.put("\\sqrt",     "√");  MATH_SYMBOLS.put("\\cbrt",    "∛");
        MATH_SYMBOLS.put("\\rightarrow","→"); MATH_SYMBOLS.put("\\leftarrow","←");
        MATH_SYMBOLS.put("\\Rightarrow","⇒"); MATH_SYMBOLS.put("\\Leftarrow","⇐");
        MATH_SYMBOLS.put("\\leftrightarrow","↔"); MATH_SYMBOLS.put("\\Leftrightarrow","⟺");
        MATH_SYMBOLS.put(BS + "uparrow",  "↑");  MATH_SYMBOLS.put("\\downarrow","↓");
        MATH_SYMBOLS.put("\\langle",   "⟨");  MATH_SYMBOLS.put("\\rangle",  "⟩");
        MATH_SYMBOLS.put("\\lfloor",   "⌊");  MATH_SYMBOLS.put("\\rfloor",  "⌋");
        MATH_SYMBOLS.put("\\lceil",    "⌈");  MATH_SYMBOLS.put("\\rceil",   "⌉");
        MATH_SYMBOLS.put("\\circ",     "∘");  MATH_SYMBOLS.put("\\bullet",  "•");
        MATH_SYMBOLS.put("\\ldots",    "…");  MATH_SYMBOLS.put("\\cdots",   "⋯");
        MATH_SYMBOLS.put("\\frac",     "⁄");  MATH_SYMBOLS.put("\\overline","‾");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  MAIN RENDER ENTRY POINT
    // ═══════════════════════════════════════════════════════════════════════════

    public static VBox render(String markdown) {
        VBox container = new VBox(4);
        container.getStyleClass().add("md-container");

        if (markdown == null || markdown.isBlank()) return container;

        String[] lines = markdown.split("\n");
        int i = 0;

        while (i < lines.length) {
            String line = lines[i];

            // ── Code block (```) ──────────────────────────────────────────
            if (line.trim().startsWith("```")) {
                String lang = line.trim().substring(3).trim();
                StringBuilder codeBlock = new StringBuilder();
                i++;
                while (i < lines.length && !lines[i].trim().startsWith("```")) {
                    codeBlock.append(lines[i]).append("\n");
                    i++;
                }
                if (i < lines.length) i++;
                container.getChildren().add(buildCodeBlock(codeBlock.toString(), lang));
                continue;
            }

            // ── Horizontal rule (--- or ***) ──────────────────────────────
            if (line.trim().matches("^[-*_]{3,}$")) {
                container.getChildren().add(buildHorizontalRule());
                i++;
                continue;
            }

            // ── Headers (# ## ### #### etc.) ──────────────────────────────
            if (line.trim().matches("^#{1,6}\\s+.*")) {
                int level = 0;
                String trimmed = line.trim();
                while (level < trimmed.length() && trimmed.charAt(level) == '#') level++;
                String headerText = trimmed.substring(level).trim();
                container.getChildren().add(buildHeader(headerText, level));
                i++;
                continue;
            }

            // ── Blockquote (> text) ───────────────────────────────────────
            if (line.trim().startsWith("> ") || line.trim().equals(">")) {
                List<String> quoteLines = new ArrayList<>();
                while (i < lines.length && (lines[i].trim().startsWith("> ") || lines[i].trim().equals(">"))) {
                    quoteLines.add(lines[i].trim().replaceFirst("^>\\s?", ""));
                    i++;
                }
                container.getChildren().add(buildBlockquote(quoteLines));
                continue;
            }

            // ── Table (| col | col |) ─────────────────────────────────────
            if (line.trim().startsWith("|") && line.trim().endsWith("|")) {
                List<String> tableLines = new ArrayList<>();
                while (i < lines.length && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                    tableLines.add(lines[i].trim());
                    i++;
                }
                Node table = buildTable(tableLines);
                if (table != null) {
                    container.getChildren().add(table);
                }
                continue;
            }

            // ── Block math ($$...$$) ──────────────────────────────────────
            if (line.trim().startsWith("$$")) {
                StringBuilder mathBlock = new StringBuilder();
                String firstLine = line.trim().substring(2);
                if (firstLine.endsWith("$$") && firstLine.length() > 2) {
                    // single-line $$...$$ 
                    container.getChildren().add(buildBlockMath(firstLine.substring(0, firstLine.length() - 2).trim()));
                    i++;
                    continue;
                }
                if (!firstLine.isBlank()) mathBlock.append(firstLine);
                i++;
                while (i < lines.length && !lines[i].trim().startsWith("$$")) {
                    mathBlock.append(lines[i]).append(" ");
                    i++;
                }
                if (i < lines.length) i++; // skip closing $$
                container.getChildren().add(buildBlockMath(mathBlock.toString().trim()));
                continue;
            }

            // ── Bullet list (- or * at start) ────────────────────────────
            if (line.trim().matches("^[-*]\\s+.*")) {
                List<String> items = new ArrayList<>();
                while (i < lines.length && lines[i].trim().matches("^[-*]\\s+.*")) {
                    items.add(lines[i].trim().replaceFirst("^[-*]\\s+", ""));
                    i++;
                }
                container.getChildren().add(buildBulletList(items));
                continue;
            }

            // ── Numbered list (1. 2. etc.) ────────────────────────────────
            if (line.trim().matches("^\\d+\\.\\s+.*")) {
                List<String> items = new ArrayList<>();
                while (i < lines.length && lines[i].trim().matches("^\\d+\\.\\s+.*")) {
                    items.add(lines[i].trim().replaceFirst("^\\d+\\.\\s+", ""));
                    i++;
                }
                container.getChildren().add(buildNumberedList(items));
                continue;
            }

            // ── Empty line → spacer ──────────────────────────────────────
            if (line.trim().isEmpty()) {
                Region spacer = new Region();
                spacer.setMinHeight(6);
                spacer.setMaxHeight(6);
                container.getChildren().add(spacer);
                i++;
                continue;
            }

            // ── Regular paragraph with inline formatting ─────────────────
            container.getChildren().add(buildParagraph(line));
            i++;
        }

        return container;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  BUILDERS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Builds a code block with language label and copy button */
    private static VBox buildCodeBlock(String code, String language) {
        VBox block = new VBox(0);
        block.getStyleClass().add("md-code-block");
        block.setPadding(new Insets(0));

        HBox header = new HBox();
        header.getStyleClass().add("md-code-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(6, 12, 6, 12));

        Label langLabel = new Label(language.isEmpty() ? "code" : language);
        langLabel.getStyleClass().add("md-code-lang");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label copyBtn = new Label("📋 Copy");
        copyBtn.getStyleClass().add("md-code-copy-btn");
        copyBtn.setOnMouseClicked(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(code.trim());
            Clipboard.getSystemClipboard().setContent(cc);
            copyBtn.setText("✓ Copied!");
            javafx.animation.PauseTransition pause =
                new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
            pause.setOnFinished(ev -> copyBtn.setText("📋 Copy"));
            pause.play();
        });

        header.getChildren().addAll(langLabel, spacer, copyBtn);

        Label codeLabel = new Label(code.stripTrailing());
        codeLabel.getStyleClass().add("md-code-text");
        codeLabel.setWrapText(true);
        codeLabel.setPadding(new Insets(10, 14, 10, 14));

        block.getChildren().addAll(header, codeLabel);
        return block;
    }

    /**
     * Builds a header (h1–h6) with a bottom border line.
     * Gradient-like color is achieved through CSS class overrides.
     */
    private static VBox buildHeader(String text, int level) {
        VBox wrapper = new VBox(0);
        wrapper.getStyleClass().add("md-header-wrapper");
        wrapper.setPadding(new Insets(level <= 2 ? 10 : 5, 0, level <= 2 ? 6 : 3, 0));

        TextFlow flow = new TextFlow();
        flow.getStyleClass().addAll("md-header", "md-h" + Math.min(level, 6));

        List<Text> spans = parseInlineFormatting(text);
        for (Text span : spans) {
            span.getStyleClass().addAll("md-header-text", "md-h" + Math.min(level, 6) + "-text");
            span.setFont(Font.font(FONT_FAMILY, FontWeight.BOLD, FontPosture.REGULAR, getHeaderSize(level)));
        }
        flow.getChildren().addAll(spans);
        wrapper.getChildren().add(flow);

        // Bottom border for H1 and H2
        if (level <= 2) {
            Rectangle border = new Rectangle();
            border.getStyleClass().add("md-header-border");
            border.setHeight(2);
            border.setFill(level == 1 ? Color.web("#6366f1") : Color.web("#818cf8", 0.7));
            border.setArcWidth(2);
            border.setArcHeight(2);
            // Bind width to parent
            wrapper.widthProperty().addListener((obs, old, w) -> border.setWidth(w.doubleValue()));
            VBox.setMargin(border, new Insets(3, 0, 0, 0));
            wrapper.getChildren().add(border);
        }

        return wrapper;
    }

    /**
     * Builds a bullet list with smart icon detection:
     * [x] → ✓  |  [>] → →  |  default → •
     */
    private static VBox buildBulletList(List<String> items) {
        VBox list = new VBox(3);
        list.getStyleClass().add("md-list");
        list.setPadding(new Insets(2, 0, 2, 8));

        for (String item : items) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.TOP_LEFT);

            String icon;
            String styleClass;
            String displayItem = item;

            if (item.startsWith("[x] ") || item.startsWith("[X] ")) {
                icon = "✓";
                styleClass = "md-bullet-done";
                displayItem = item.substring(4);
            } else if (item.startsWith("[>] ")) {
                icon = "→";
                styleClass = "md-bullet-action";
                displayItem = item.substring(4);
            } else {
                icon = "•";
                styleClass = "md-bullet";
            }

            Label bullet = new Label(icon);
            bullet.getStyleClass().add(styleClass);
            bullet.setMinWidth(16);

            TextFlow textFlow = new TextFlow();
            textFlow.getStyleClass().add("md-list-text");
            List<Text> spans = parseInlineFormatting(displayItem);
            for (Text span : spans) span.getStyleClass().add("md-body-text");
            textFlow.getChildren().addAll(spans);

            row.getChildren().addAll(bullet, textFlow);
            list.getChildren().add(row);
        }
        return list;
    }

    /** Builds a numbered list */
    private static VBox buildNumberedList(List<String> items) {
        VBox list = new VBox(3);
        list.getStyleClass().add("md-list");
        list.setPadding(new Insets(2, 0, 2, 8));

        for (int idx = 0; idx < items.size(); idx++) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.TOP_LEFT);

            Label number = new Label((idx + 1) + ".");
            number.getStyleClass().add("md-number");
            number.setMinWidth(20);

            TextFlow textFlow = new TextFlow();
            textFlow.getStyleClass().add("md-list-text");
            List<Text> spans = parseInlineFormatting(items.get(idx));
            for (Text span : spans) span.getStyleClass().add("md-body-text");
            textFlow.getChildren().addAll(spans);

            row.getChildren().addAll(number, textFlow);
            list.getChildren().add(row);
        }
        return list;
    }

    /**
     * Builds a blockquote with left accent border and tinted background.
     * Parses: > quoted text
     */
    private static HBox buildBlockquote(List<String> lines) {
        HBox outer = new HBox(0);
        outer.getStyleClass().add("md-blockquote-outer");
        VBox.setMargin(outer, new Insets(4, 0, 4, 0));

        // Left colored border
        Rectangle leftBar = new Rectangle(4, 1);
        leftBar.getStyleClass().add("md-blockquote-bar");
        leftBar.setFill(Color.web("#10b981"));
        leftBar.setArcWidth(3);
        leftBar.setArcHeight(3);

        VBox content = new VBox(3);
        content.getStyleClass().add("md-blockquote");
        content.setPadding(new Insets(10, 14, 10, 14));
        HBox.setHgrow(content, Priority.ALWAYS);

        // Bind left bar height to content height
        content.heightProperty().addListener((obs, old, h) -> leftBar.setHeight(h.doubleValue()));

        for (String line : lines) {
            TextFlow flow = new TextFlow();
            flow.getStyleClass().add("md-blockquote-text");
            List<Text> spans = parseInlineFormatting(line);
            for (Text span : spans) {
                span.getStyleClass().add("md-blockquote-span");
                span.setFont(Font.font(FONT_FAMILY, FontWeight.NORMAL, FontPosture.ITALIC, BASE_FONT_SIZE));
            }
            flow.getChildren().addAll(spans);
            content.getChildren().add(flow);
        }

        outer.getChildren().addAll(leftBar, content);
        return outer;
    }

    /**
     * Builds a markdown table with header row and alternating row colors.
     * Parses: | H1 | H2 |  /  |---|---|  /  | C1 | C2 |
     */
    private static Node buildTable(List<String> tableLines) {
        if (tableLines.size() < 2) return null;

        VBox table = new VBox(0);
        table.getStyleClass().add("md-table");
        VBox.setMargin(table, new Insets(6, 0, 6, 0));

        List<String[]> rows = new ArrayList<>();
        for (String line : tableLines) {
            // Skip separator rows like |---|---|
            if (line.replaceAll("[|:\\-\\s]", "").isEmpty()) continue;
            String[] cells = line.substring(1, line.length() - 1).split("\\|");
            for (int i = 0; i < cells.length; i++) cells[i] = cells[i].trim();
            rows.add(cells);
        }

        if (rows.isEmpty()) return null;

        int colCount = rows.get(0).length;

        for (int r = 0; r < rows.size(); r++) {
            String[] cells = rows.get(r);
            HBox row = new HBox(0);

            boolean isHeader = (r == 0);
            row.getStyleClass().add(isHeader ? "md-table-header-row" : (r % 2 == 0 ? "md-table-row-even" : "md-table-row-odd"));

            for (int c = 0; c < colCount; c++) {
                String cellText = c < cells.length ? cells[c] : "";

                HBox cell = new HBox();
                cell.getStyleClass().add(isHeader ? "md-table-header-cell" : "md-table-cell");
                cell.setPadding(new Insets(8, 14, 8, 14));
                HBox.setHgrow(cell, Priority.ALWAYS);

                TextFlow tf = new TextFlow();
                List<Text> spans = parseInlineFormatting(cellText);
                for (Text span : spans) {
                    if (isHeader) {
                        span.setFont(Font.font(FONT_FAMILY, FontWeight.BOLD, FontPosture.REGULAR, BASE_FONT_SIZE));
                        span.getStyleClass().add("md-table-header-text");
                    } else {
                        span.getStyleClass().add("md-body-text");
                    }
                }
                tf.getChildren().addAll(spans);
                cell.getChildren().add(tf);
                row.getChildren().add(cell);
            }
            table.getChildren().add(row);
        }
        return table;
    }

    /**
     * Builds an inline/block math display by converting LaTeX symbols to Unicode.
     */
    private static HBox buildBlockMath(String latex) {
        HBox box = new HBox();
        box.getStyleClass().add("md-math-block");
        box.setPadding(new Insets(10, 18, 10, 18));
        box.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(box, new Insets(4, 0, 4, 0));

        String rendered = convertMathToUnicode(latex);
        Label mathLabel = new Label(rendered);
        mathLabel.getStyleClass().add("md-math-text");
        mathLabel.setWrapText(true);
        box.getChildren().add(mathLabel);
        return box;
    }

    /** Builds a regular paragraph with inline formatting */
    private static TextFlow buildParagraph(String line) {
        TextFlow flow = new TextFlow();
        flow.getStyleClass().add("md-paragraph");
        flow.setPadding(new Insets(1, 0, 1, 0));
        flow.setLineSpacing(2);

        List<Node> nodes = parseInlineFormattingWithLinks(line);
        for (Node node : nodes) {
            if (node instanceof Text t) t.getStyleClass().add("md-body-text");
        }
        flow.getChildren().addAll(nodes);
        return flow;
    }

    /** Builds a horizontal rule */
    private static Region buildHorizontalRule() {
        Region hr = new Region();
        hr.getStyleClass().add("md-hr");
        hr.setMinHeight(1);
        hr.setMaxHeight(1);
        hr.setPrefHeight(1);
        VBox.setMargin(hr, new Insets(8, 0, 8, 0));
        return hr;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  INLINE FORMATTING PARSERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Parses inline markdown + links + inline math → list of JavaFX Nodes.
     * Handles: **bold**, *italic*, ***bold italic***, `code`, [text](url), $math$
     */
    private static List<Node> parseInlineFormattingWithLinks(String line) {
        List<Node> result = new ArrayList<>();

        Pattern pattern = Pattern.compile(
                "\\*\\*\\*(.+?)\\*\\*\\*"    +   // Group 1: bold italic
                "|\\*\\*(.+?)\\*\\*"          +   // Group 2: bold
                "|\\*(.+?)\\*"                +   // Group 3: italic
                "|`([^`]+)`"                  +   // Group 4: inline code
                "|\\[([^\\]]+)]\\(([^)]+)\\)" +   // Group 5,6: link text + url
                "|\\$([^$]+)\\$"                  // Group 7: inline math
        );

        Matcher matcher = pattern.matcher(line);
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                result.add(makePlain(line.substring(lastEnd, matcher.start())));
            }

            if (matcher.group(1) != null) {
                Text t = new Text(matcher.group(1));
                t.setFont(Font.font(FONT_FAMILY, FontWeight.BOLD, FontPosture.ITALIC, BASE_FONT_SIZE));
                t.getStyleClass().add("md-bold-italic");
                result.add(t);
            } else if (matcher.group(2) != null) {
                Text t = new Text(matcher.group(2));
                t.setFont(Font.font(FONT_FAMILY, FontWeight.BOLD, BASE_FONT_SIZE));
                t.getStyleClass().add("md-bold");
                result.add(t);
            } else if (matcher.group(3) != null) {
                Text t = new Text(matcher.group(3));
                t.setFont(Font.font(FONT_FAMILY, FontWeight.BOLD, FontPosture.ITALIC, BASE_FONT_SIZE));
                t.getStyleClass().add("md-italic");
                result.add(t);
            } else if (matcher.group(4) != null) {
                Text t = new Text(matcher.group(4));
                t.setFont(Font.font(MONO_FONT, CODE_FONT_SIZE));
                t.getStyleClass().add("md-inline-code");
                result.add(t);
            } else if (matcher.group(5) != null && matcher.group(6) != null) {
                // Hyperlink
                result.add(buildLinkLabel(matcher.group(5), matcher.group(6)));
            } else if (matcher.group(7) != null) {
                // Inline math
                String mathUnicode = convertMathToUnicode(matcher.group(7));
                Text t = new Text(mathUnicode);
                t.setFont(Font.font(MONO_FONT, FontPosture.ITALIC, BASE_FONT_SIZE));
                t.getStyleClass().add("md-math-inline");
                result.add(t);
            }

            lastEnd = matcher.end();
        }

        if (lastEnd < line.length()) {
            result.add(makePlain(line.substring(lastEnd)));
        }

        if (result.isEmpty()) {
            result.add(makePlain(line));
        }

        return result;
    }

    /**
     * Legacy version – returns only Text nodes (used internally by builders that
     * don't need link nodes, e.g. headers, list items, table cells, blockquotes).
     */
    private static List<Text> parseInlineFormatting(String line) {
        List<Text> result = new ArrayList<>();
        for (Node node : parseInlineFormattingWithLinks(line)) {
            if (node instanceof Text t) {
                result.add(t);
            } else if (node instanceof Label lbl) {
                // Convert link label back to styled text for contexts that need Text only
                Text t = new Text(lbl.getText());
                t.getStyleClass().add("md-link");
                t.setFont(Font.font(FONT_FAMILY, FontWeight.NORMAL, BASE_FONT_SIZE));
                result.add(t);
            }
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Creates a clickable link Label that opens the URL in the default browser */
    private static Label buildLinkLabel(String text, String url) {
        Label link = new Label(text);
        link.getStyleClass().add("md-link");
        link.setOnMouseClicked(e -> {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        link.setOnMouseEntered(e -> link.getStyleClass().add("md-link-hover"));
        link.setOnMouseExited(e -> link.getStyleClass().remove("md-link-hover"));
        return link;
    }

    /** Creates a plain-text Text node */
    private static Text makePlain(String text) {
        Text t = new Text(text);
        t.setFont(Font.font(FONT_FAMILY, FontWeight.BOLD, BASE_FONT_SIZE));
        return t;
    }

    /** Converts a LaTeX string to Unicode using the symbol map */
    private static String convertMathToUnicode(String latex) {
        // Sort keys by length descending to avoid partial replacements
        List<String> keys = new ArrayList<>(MATH_SYMBOLS.keySet());
        keys.sort((a, b) -> Integer.compare(b.length(), a.length()));

        String result = latex;
        for (String key : keys) {
            result = result.replace(key, MATH_SYMBOLS.get(key));
        }

        // Handle ^{...} superscripts → unicode superscript where possible
        result = convertSuperscripts(result);
        // Handle _{...} subscripts → unicode subscript where possible
        result = convertSubscripts(result);
        // Remove remaining LaTeX braces
        result = result.replaceAll("[{}]", "");

        return result;
    }

    private static final String SUP_DIGITS = "⁰¹²³⁴⁵⁶⁷⁸⁹";
    private static final String SUB_DIGITS = "₀₁₂₃₄₅₆₇₈₉";

    private static String convertSuperscripts(String s) {
        StringBuilder sb = new StringBuilder();
        Pattern p = Pattern.compile("\\^\\{?([^}^_\\s]+)\\}?");
        Matcher m = p.matcher(s);
        int last = 0;
        while (m.find()) {
            sb.append(s, last, m.start());
            String inner = m.group(1);
            StringBuilder sup = new StringBuilder();
            for (char c : inner.toCharArray()) {
                if (c >= '0' && c <= '9') sup.append(SUP_DIGITS.charAt(c - '0'));
                else sup.append(c);
            }
            sb.append(sup);
            last = m.end();
        }
        sb.append(s.substring(last));
        return sb.toString();
    }

    private static String convertSubscripts(String s) {
        StringBuilder sb = new StringBuilder();
        Pattern p = Pattern.compile("_\\{?([^}_^\\s]+)\\}?");
        Matcher m = p.matcher(s);
        int last = 0;
        while (m.find()) {
            sb.append(s, last, m.start());
            String inner = m.group(1);
            StringBuilder sub = new StringBuilder();
            for (char c : inner.toCharArray()) {
                if (c >= '0' && c <= '9') sub.append(SUB_DIGITS.charAt(c - '0'));
                else sub.append(c);
            }
            sb.append(sub);
            last = m.end();
        }
        sb.append(s.substring(last));
        return sb.toString();
    }

    /** Returns font size for header levels */
    private static double getHeaderSize(int level) {
        return switch (level) {
            case 1 -> 23;
            case 2 -> 20;
            case 3 -> 17.5;
            case 4 -> 16;
            default -> BASE_FONT_SIZE;
        };
    }
}
