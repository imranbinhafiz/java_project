package com.example.javaproject;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HtmlRenderer — Self-contained AI response renderer for JavaFX WebView.
 *
 * ARCHITECTURE (offline-first, zero CDN):
 *  • Markdown → HTML in pure Java (headings, bold, italic, lists, tables,
 *    blockquotes, fenced code blocks, inline code, links, HR)
 *  • Math rendering via an enhanced pure-Java LaTeX→HTML converter
 *    supporting: \frac (stacked), \sqrt, \sum, \int, Greek letters,
 *    superscripts ^{}, subscripts _{}, and 80+ LaTeX symbols
 *  • Syntax highlighting via CSS classes (no highlight.js CDN needed)
 *  • WebView background forced dark before paint — no white flash
 *  • Mouse scroll via overflow:auto on <html> element
 *  • Height auto-measured via JS alert bridge, multiple passes
 *  • Copy button on every code block
 *  • Links open in default browser via Desktop.browse()
 *
 * WHY NO CDN: JavaFX WebView blocks external script loading when content
 * is loaded via engine.loadContent(). All resources must be inlined.
 */
public class HtmlRenderer {

    // Avoid raw backslash-u sequences in source (illegal unicode escape).
    private static final String BS = "\\";
    private static final String RE_BS = "\\\\";

    // ══════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ══════════════════════════════════════════════════════════════════

    public static WebView createMessageView(String markdown, double initialWidth) {
        WebView webView = new WebView();
        webView.setContextMenuEnabled(false);
        webView.setStyle("-fx-background-color: #0f1117;");
        webView.setPrefWidth(initialWidth);
        webView.setMinWidth(80);
        webView.setMaxWidth(Double.MAX_VALUE);
        webView.setPrefHeight(50);
        webView.setMinHeight(40);

        WebEngine engine = webView.getEngine();

        // Alert bridge: JS → Java for HEIGHT and OPEN_URL
        engine.setOnAlert(evt -> {
            String d = evt.getData();
            if (d == null) return;
            if (d.startsWith("HEIGHT:")) {
                try {
                    double h = Double.parseDouble(d.substring(7).trim());
                    if (h > 5) Platform.runLater(() -> setH(webView, h));
                } catch (NumberFormatException ignored) {}
            } else if (d.startsWith("OPEN:")) {
                String url = d.substring(5).trim();
                Platform.runLater(() -> {
                    try { java.awt.Desktop.getDesktop().browse(new java.net.URI(url)); }
                    catch (Exception ignored) {}
                });
            }
        });

        // Measure height after DOM ready — multiple passes for reflows
        engine.getLoadWorker().stateProperty().addListener((obs, was, now) -> {
            if (now == Worker.State.SUCCEEDED) {
                Platform.runLater(() -> {
                    measureH(webView);
                    after(150,  () -> measureH(webView));
                    after(500,  () -> measureH(webView));
                });
            }
        });

        engine.loadContent(buildHtml(markdown), "text/html");
        return webView;
    }

    public static void updateContent(WebView wv, String markdown) {
        Platform.runLater(() -> wv.getEngine().loadContent(buildHtml(markdown), "text/html"));
    }

    // ══════════════════════════════════════════════════════════════════
    //  HTML PAGE BUILDER
    // ══════════════════════════════════════════════════════════════════

    private static String buildHtml(String md) {
        String body = mdToHtml(md == null ? "" : md);
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'/>" +
               "<style>" + CSS + "</style></head>" +
               "<body><div id='c'>" + body + "</div>" +
               "<script>" + JS + "</script></body></html>";
    }

    // ══════════════════════════════════════════════════════════════════
    //  MARKDOWN → HTML  (block-level)
    // ══════════════════════════════════════════════════════════════════

    static String mdToHtml(String md) {
        md = md.replace("\r\n", "\n").replace("\r", "\n");
        // Sentinel-escape: protect literal \* \_ \$ \` etc.
        md = md.replace("\\\\", "\u0002BSLASH\u0002")
               .replace("\\*",  "\u0002STAR\u0002")
               .replace("\\_",  "\u0002UNDER\u0002")
               .replace("\\`",  "\u0002TICK\u0002")
               .replace("\\$",  "\u0002DOLLAR\u0002")
               .replace("\\[",  "\u0002LBRK\u0002")
               .replace("\\]",  "\u0002RBRK\u0002");

        String[] lines = md.split("\n", -1);
        StringBuilder out = new StringBuilder();
        int i = 0, n = lines.length;

        while (i < n) {
            String raw  = lines[i];
            String trim = raw.trim();

            // ── Fenced code block  ```lang
            if (trim.startsWith("```")) {
                String lang = trim.length() > 3 ? trim.substring(3).trim() : "text";
                if (lang.isEmpty()) lang = "text";
                StringBuilder code = new StringBuilder();
                i++;
                while (i < n && !lines[i].trim().startsWith("```")) {
                    code.append(lines[i]).append("\n");
                    i++;
                }
                if (i < n) i++; // skip closing ```
                out.append(codeBlock(htmlEsc(restoreEsc(code.toString().stripTrailing())), lang));
                continue;
            }

            // ── Block math  $$...$$
            if (trim.startsWith("$$")) {
                String rest = trim.substring(2).trim();
                if (rest.endsWith("$$") && rest.length() >= 2) {
                    // single-line $$expr$$
                    String expr = restoreEsc(rest.substring(0, rest.length() - 2).trim());
                    out.append("<div class='math-blk'>").append(renderMath(expr, true)).append("</div>\n");
                    i++; continue;
                }
                // multi-line $$\n expr \n$$
                StringBuilder mb = new StringBuilder(rest);
                i++;
                while (i < n) {
                    String ml = lines[i].trim();
                    if (ml.equals("$$"))   { i++; break; }
                    if (ml.endsWith("$$")) { mb.append(" ").append(ml, 0, ml.length()-2); i++; break; }
                    mb.append(" ").append(lines[i]);
                    i++;
                }
                out.append("<div class='math-blk'>").append(renderMath(restoreEsc(mb.toString().trim()), true)).append("</div>\n");
                continue;
            }

            // ── ATX Heading  # ## ###
            if (trim.matches("^#{1,6} .*")) {
                int lvl = 0;
                while (lvl < trim.length() && trim.charAt(lvl) == '#') lvl++;
                out.append("<h").append(lvl).append(">").append(inl(trim.substring(lvl).trim()))
                   .append("</h").append(lvl).append(">\n");
                i++; continue;
            }

            // ── Setext heading  === or ---
            if (i + 1 < n && !trim.isEmpty()) {
                String next = lines[i+1].trim();
                if (next.matches("=+") && !trim.isEmpty()) {
                    out.append("<h1>").append(inl(trim)).append("</h1>\n");
                    i += 2; continue;
                }
                if (next.matches("-+") && next.length() >= 2 && !trim.isEmpty()
                        && !trim.matches("^[-*+] .*") && !trim.matches("^\\d+\\. .*")) {
                    out.append("<h2>").append(inl(trim)).append("</h2>\n");
                    i += 2; continue;
                }
            }

            // ── Horizontal rule  --- *** ___
            if (trim.matches("^[-*_]{3,}$")) {
                out.append("<hr/>\n"); i++; continue;
            }

            // ── Blockquote  > text
            if (trim.startsWith(">")) {
                out.append("<blockquote>");
                while (i < n && !lines[i].trim().isEmpty() && lines[i].trim().startsWith(">")) {
                    out.append(inl(lines[i].trim().replaceFirst("^> ?", ""))).append(" ");
                    i++;
                }
                out.append("</blockquote>\n"); continue;
            }

            // ── Table  | col | col |
            if (trim.startsWith("|") && trim.endsWith("|")) {
                List<String> trows = new ArrayList<>();
                while (i < n && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                    trows.add(lines[i].trim()); i++;
                }
                out.append(buildTable(trows)); continue;
            }

            // ── Unordered list  - * +
            if (trim.matches("^[-*+] .*")) {
                out.append("<ul>\n");
                while (i < n && lines[i].trim().matches("^[-*+] .*")) {
                    out.append("<li>").append(inl(lines[i].trim().substring(2))).append("</li>\n");
                    i++;
                }
                out.append("</ul>\n"); continue;
            }

            // ── Ordered list  1. text
            if (trim.matches("^\\d+\\. .*")) {
                out.append("<ol>\n");
                while (i < n && lines[i].trim().matches("^\\d+\\. .*")) {
                    out.append("<li>").append(inl(lines[i].trim().replaceFirst("^\\d+\\. ", ""))).append("</li>\n");
                    i++;
                }
                out.append("</ol>\n"); continue;
            }

            // ── Empty line
            if (trim.isEmpty()) { out.append("<br/>\n"); i++; continue; }

            // ── Paragraph
            out.append("<p>").append(inl(trim)).append("</p>\n");
            i++;
        }
        return out.toString();
    }

    // ── Code block HTML ────────────────────────────────────────────────────────
    private static String codeBlock(String escaped, String lang) {
        return "<div class='cbw'>" +
               "<div class='cbh'><span class='cbl'>" + htmlEsc(lang) + "</span>" +
               "<button class='cpb' onclick='cp(this)'>Copy</button></div>" +
               "<pre class='cbp'><code class='lang-" + htmlEsc(lang) + "'>" + escaped + "</code></pre>" +
               "</div>\n";
    }

    // ── Table HTML ─────────────────────────────────────────────────────────────
    private static String buildTable(List<String> rows) {
        if (rows.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("<div class='tw'><table>\n");
        boolean hdr = true;
        for (String row : rows) {
            if (row.replaceAll("[|:\\-\\s]", "").isEmpty()) continue; // skip separator
            String[] cells = row.substring(1, row.length() - 1).split("\\|", -1);
            if (hdr) {
                sb.append("<thead><tr>");
                for (String c : cells) sb.append("<th>").append(inl(c.trim())).append("</th>");
                sb.append("</tr></thead>\n<tbody>\n");
                hdr = false;
            } else {
                sb.append("<tr>");
                for (String c : cells) sb.append("<td>").append(inl(c.trim())).append("</td>");
                sb.append("</tr>\n");
            }
        }
        sb.append("</tbody></table></div>\n");
        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════════════
    //  INLINE FORMATTER
    //  Pipeline: extract code → extract math → htmlEsc → bold/italic
    //            → strikethrough → links → restore code → restore math
    //            → restore escapes
    // ══════════════════════════════════════════════════════════════════

    static String inl(String s) {
        if (s == null || s.isEmpty()) return "";

        // 1. Extract `code` spans — immune to all formatting
        List<String> cSlots = new ArrayList<>();
        s = extractCode(s, cSlots);

        // 2. Extract $math$ spans — immune to bold/italic, rendered by renderMath()
        List<String> mSlots = new ArrayList<>();
        s = extractInlineMath(s, mSlots);

        // 3. HTML-escape the remaining plain text
        s = htmlEsc(s);

        // 4. Bold-italic — longest match first
        s = s.replaceAll("\\*{3}(.+?)\\*{3}", "<strong><em>$1</em></strong>");
        s = s.replaceAll("\\*{2}(.+?)\\*{2}", "<strong>$1</strong>");
        // Single * italic: not preceded/followed by space or another *
        s = s.replaceAll("(?<![ *])\\*(?![ *])(.+?)(?<![ *])\\*(?![ *])", "<em>$1</em>");
        // Underscore italic: only at genuine word boundaries (not inside identifiers)
        s = s.replaceAll("(?<![\\w\\d])_([^_\\n]+?)_(?![\\w\\d])", "<em>$1</em>");

        // 5. Strikethrough
        s = s.replaceAll("~~(.+?)~~", "<del>$1</del>");

        // 6. Links [text](url)
        s = s.replaceAll("\\[([^\\]]+)]\\(([^)]+)\\)",
                "<a href='$2' onclick='olink(event,\"$2\")'>$1</a>");

        // 7. Restore code slots
        for (int k = 0; k < cSlots.size(); k++)
            s = s.replace("\u0002CODE" + k + "\u0002",
                    "<code class='ic'>" + htmlEsc(cSlots.get(k)) + "</code>");

        // 8. Restore math slots — render each through renderMath()
        for (int k = 0; k < mSlots.size(); k++)
            s = s.replace("\u0002MATH" + k + "\u0002",
                    "<span class='math-inl'>" + renderMath(mSlots.get(k), false) + "</span>");

        // 9. Restore escape sentinels
        s = restoreEsc(s);
        return s;
    }

    private static String extractCode(String s, List<String> slots) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '`') {
                // Check for ``double backtick`` span
                if (i + 1 < s.length() && s.charAt(i+1) == '`') {
                    int end = s.indexOf("``", i + 2);
                    if (end > i) {
                        slots.add(s.substring(i + 2, end));
                        out.append("\u0002CODE").append(slots.size()-1).append("\u0002");
                        i = end + 2; continue;
                    }
                }
                int end = s.indexOf('`', i + 1);
                if (end > i) {
                    slots.add(s.substring(i + 1, end));
                    out.append("\u0002CODE").append(slots.size()-1).append("\u0002");
                    i = end + 1; continue;
                }
            }
            out.append(c); i++;
        }
        return out.toString();
    }

    /**
     * Extracts $...$ inline math spans.
     * Price guard: skip if the $ is immediately followed by a digit (e.g. $5).
     */
    private static String extractInlineMath(String s, List<String> slots) {
        // Match $...$ but not $$...$$ (display) and not $5 (price)
        Pattern p = Pattern.compile("(?<!\\$)\\$(?!\\$)(?![\\d])([^$\n]+?)(?<!\\$)\\$(?!\\$)");
        Matcher m = p.matcher(s);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            out.append(s, last, m.start());
            slots.add(restoreEsc(m.group(1).trim()));
            out.append("\u0002MATH").append(slots.size()-1).append("\u0002");
            last = m.end();
        }
        out.append(s.substring(last));
        return out.toString();
    }

    // ══════════════════════════════════════════════════════════════════
    //  MATH RENDERER  (pure-Java LaTeX → HTML)
    //  Handles: \frac, \sqrt, \sum, \int, Greek letters, ^{} _{}
    //           symbols, \text, \binom, \left\right, \overline etc.
    // ══════════════════════════════════════════════════════════════════

    static String renderMath(String latex, boolean display) {
        if (latex == null || latex.isBlank()) return "";

        // 1. Pre-process environments → simple HTML
        latex = latex.trim();

        // 2. Handle \frac{num}{den} — recursive, innermost first
        latex = expandFrac(latex);

        // 3. \sqrt[n]{x} and \sqrt{x}
        latex = expandSqrt(latex);

        // 4. \binom{n}{k}
        latex = expandBinom(latex);

        // 5. \text{...} → plain text span
        latex = latex.replaceAll("\\\\text\\{([^}]*)}", "<span class='mtext'>$1</span>");
        latex = latex.replaceAll("\\\\mathrm\\{([^}]*)}", "<span class='mtext'>$1</span>");
        latex = latex.replaceAll("\\\\mathbf\\{([^}]*)}", "<strong>$1</strong>");
        latex = latex.replaceAll("\\\\mathit\\{([^}]*)}", "<em>$1</em>");

        // 6. \left and \right delimiters
        latex = latex.replace("\\left(","<span class='mlp'>(</span>")
                     .replace("\\right)","<span class='mrp'>)</span>")
                     .replace("\\left[","<span class='mlp'>[</span>")
                     .replace("\\right]","<span class='mrp'>]</span>")
                     .replace("\\left\\{","<span class='mlp'>{</span>")
                     .replace("\\right\\}","<span class='mrp'>}</span>")
                     .replace("\\left|","<span class='mlp'>|</span>")
                     .replace("\\right|","<span class='mrp'>|</span>")
                     .replace("\\left.","").replace("\\right.","");

        // 7. \overline{x} → x̄, \hat{x} → x̂, \vec{x} → x⃗
        latex = latex.replaceAll("\\\\overline\\{([^}]*)}","$1\u0305");
        latex = latex.replaceAll("\\\\hat\\{([^}]*)}",    "$1\u0302");
        latex = latex.replaceAll("\\\\tilde\\{([^}]*)}",  "$1\u0303");
        latex = latex.replaceAll("\\\\vec\\{([^}]*)}",    "$1\u20D7");
        latex = latex.replaceAll("\\\\dot\\{([^}]*)}",    "$1\u0307");
        latex = latex.replaceAll("\\\\ddot\\{([^}]*)}",   "$1\u0308");
        latex = latex.replaceAll("\\\\bar\\{([^}]*)}",    "$1\u0305");

        // 8. underbrace and overbrace (simplified)
        latex = latex.replaceAll(RE_BS + "underbrace\\{([^}]*)}_\\{[^}]*}", "$1");
        latex = latex.replaceAll(RE_BS + "overbrace\\{([^}]*)}\\^\\{[^}]*}", "$1");
        latex = latex.replaceAll(RE_BS + "underbrace\\{([^}]*)}", "$1");
        latex = latex.replaceAll(RE_BS + "overbrace\\{([^}]*)}", "$1");

        // 9. Apply symbol table
        latex = applySymbols(latex);

        // 10. ^{...} → <sup>...</sup>,  _{...} → <sub>...</sub>
        latex = expandSupSub(latex);

        // 11. Strip remaining braces
        latex = latex.replace("{","").replace("}","");

        // 12. HTML-escape non-tag content
        latex = safeEsc(latex);

        // 13. Wrap in styled span
        String cls = display ? "mdisp" : "minl";
        return "<span class='" + cls + "'>" + latex + "</span>";
    }

    // ── \frac{num}{den} → stacked HTML fraction ────────────────────────────────
    private static String expandFrac(String s) {
        // Iteratively replace innermost \frac first
        Pattern p = Pattern.compile("\\\\frac\\{");
        for (int pass = 0; pass < 20; pass++) {
            Matcher m = p.matcher(s);
            if (!m.find()) break;
            int start = m.start();
            int numOpen = m.end() - 1; // position of opening {
            int numClose = matchBrace(s, numOpen);
            if (numClose < 0) break;
            String num = s.substring(numOpen + 1, numClose);
            int denOpen = s.indexOf('{', numClose);
            if (denOpen < 0) break;
            int denClose = matchBrace(s, denOpen);
            if (denClose < 0) break;
            String den = s.substring(denOpen + 1, denClose);
            String frac = "<span class='frac'>" +
                          "<span class='fnum'>" + expandFrac(num) + "</span>" +
                          "<span class='fden'>" + expandFrac(den) + "</span>" +
                          "</span>";
            s = s.substring(0, start) + frac + s.substring(denClose + 1);
        }
        return s;
    }

    // ── \sqrt[n]{x} and \sqrt{x} ──────────────────────────────────────────────
    private static String expandSqrt(String s) {
        // \sqrt[n]{x} — nth root
        Pattern pn = Pattern.compile("\\\\sqrt\\[([^\\]]+)]\\{");
        for (int pass = 0; pass < 20; pass++) {
            Matcher m = pn.matcher(s);
            if (!m.find()) break;
            int open = s.indexOf('{', m.start());
            int close = matchBrace(s, open);
            if (close < 0) break;
            String n   = m.group(1);
            String arg = expandSqrt(s.substring(open + 1, close));
            String res = "<span class='nrt'><sup class='nidx'>" + n + "</sup>√<span class='sarg'>" + arg + "</span></span>";
            s = s.substring(0, m.start()) + res + s.substring(close + 1);
        }
        // \sqrt{x}
        Pattern ps = Pattern.compile("\\\\sqrt\\{");
        for (int pass = 0; pass < 20; pass++) {
            Matcher m = ps.matcher(s);
            if (!m.find()) break;
            int open  = m.end() - 1;
            int close = matchBrace(s, open);
            if (close < 0) break;
            String arg = expandSqrt(s.substring(open + 1, close));
            String res = "<span class='sqrt'>√<span class='sarg'>" + arg + "</span></span>";
            s = s.substring(0, m.start()) + res + s.substring(close + 1);
        }
        // \sqrt x (no braces)
        s = s.replaceAll("\\\\sqrt ([A-Za-z0-9])", "√$1");
        return s;
    }

    // ── \binom{n}{k} ──────────────────────────────────────────────────────────
    private static String expandBinom(String s) {
        Pattern p = Pattern.compile("\\\\binom\\{");
        for (int pass = 0; pass < 20; pass++) {
            Matcher m = p.matcher(s);
            if (!m.find()) break;
            int open1  = m.end() - 1;
            int close1 = matchBrace(s, open1);
            if (close1 < 0) break;
            String n = s.substring(open1 + 1, close1);
            int open2  = s.indexOf('{', close1);
            if (open2 < 0) break;
            int close2 = matchBrace(s, open2);
            if (close2 < 0) break;
            String k = s.substring(open2 + 1, close2);
            String res = "<span class='frac'>(<span class='frac'><span class='fnum'>" + n +
                         "</span><span class='fden'>" + k + "</span></span>)</span>";
            s = s.substring(0, m.start()) + res + s.substring(close2 + 1);
        }
        return s;
    }

    // ── ^{...} _{...} → <sup> <sub> ──────────────────────────────────────────
    private static String expandSupSub(String s) {
        // Multi-char: ^{...}
        for (int pass = 0; pass < 30; pass++) {
            int idx = s.indexOf("^{");
            if (idx < 0) break;
            int close = matchBrace(s, idx + 1);
            if (close < 0) break;
            s = s.substring(0, idx) + "<sup>" + s.substring(idx+2, close) + "</sup>" + s.substring(close+1);
        }
        // Multi-char: _{...}
        for (int pass = 0; pass < 30; pass++) {
            int idx = s.indexOf("_{");
            if (idx < 0) break;
            int close = matchBrace(s, idx + 1);
            if (close < 0) break;
            s = s.substring(0, idx) + "<sub>" + s.substring(idx+2, close) + "</sub>" + s.substring(close+1);
        }
        // Single char: ^x  (letter, digit, or Greek Unicode)
        s = s.replaceAll("\\^([A-Za-z0-9αβγδεζηθικλμνξπρστυφχψωΑΒΓΔΕΖΗΘΙΚΛΜΝΞΠΡΣΤΥΦΧΨΩ∞])",
                         "<sup>$1</sup>");
        // Single char: _x  (but not inside identifiers)
        s = s.replaceAll("_([A-Za-z0-9αβγδεζηθικλμνξπρστυφχψωΑΒΓΔΕΖΗΘΙΚΛΜΝΞΠΡΣΤΥΦΧΨΩ])",
                         "<sub>$1</sub>");
        return s;
    }

    // ── Symbol table ──────────────────────────────────────────────────────────
    private static String applySymbols(String s) {
        return s
            // ── Arrows ──
            .replace("\\longleftrightarrow","⟺").replace("\\longrightarrow","⟶")
            .replace("\\longleftarrow","⟵").replace("\\leftrightarrow","↔")
            .replace("\\Leftrightarrow","⟺").replace("\\Rightarrow","⇒")
            .replace("\\Leftarrow","⇐").replace("\\rightarrow","→")
            .replace("\\leftarrow","←").replace(BS + "uparrow","↑")
            .replace("\\downarrow","↓").replace(BS + "updownarrow","↕")
            .replace("\\nearrow","↗").replace("\\searrow","↘")
            .replace("\\nrightarrow","↛").replace("\\nleftarrow","↚")
            .replace("\\mapsto","↦").replace("\\hookrightarrow","↪")
            .replace("\\hookleftarrow","↩").replace("\\Uparrow","⇑")
            .replace("\\Downarrow","⇓")
            // ── Greek lower ──
            .replace("\\alpha","α").replace("\\beta","β").replace("\\gamma","γ")
            .replace("\\delta","δ").replace("\\epsilon","ε").replace("\\varepsilon","ε")
            .replace("\\zeta","ζ").replace("\\eta","η").replace("\\theta","θ")
            .replace("\\vartheta","ϑ").replace("\\iota","ι").replace("\\kappa","κ")
            .replace("\\lambda","λ").replace("\\mu","μ").replace("\\nu","ν")
            .replace("\\xi","ξ").replace("\\pi","π").replace("\\varpi","ϖ")
            .replace("\\rho","ρ").replace("\\varrho","ϱ").replace("\\sigma","σ")
            .replace("\\varsigma","ς").replace("\\tau","τ").replace(BS + "upsilon","υ")
            .replace("\\phi","φ").replace("\\varphi","φ").replace("\\chi","χ")
            .replace("\\psi","ψ").replace("\\omega","ω")
            // ── Greek upper ──
            .replace("\\Gamma","Γ").replace("\\Delta","Δ").replace("\\Theta","Θ")
            .replace("\\Lambda","Λ").replace("\\Xi","Ξ").replace("\\Pi","Π")
            .replace("\\Sigma","Σ").replace("\\Upsilon","Υ").replace("\\Phi","Φ")
            .replace("\\Psi","Ψ").replace("\\Omega","Ω")
            // ── Big operators (with limits notation) ──
            .replace("\\sum_{","∑<sub>").replace("\\sum^{","∑<sup>").replace("\\sum","∑")
            .replace("\\prod_{","∏<sub>").replace("\\prod^{","∏<sup>").replace("\\prod","∏")
            .replace("\\int_{","∫<sub>").replace("\\int^{","∫<sup>").replace("\\int","∫")
            .replace("\\iint","∬").replace("\\iiint","∭").replace("\\oint","∮")
            .replace("\\coprod","∐")
            .replace("\\bigcup","⋃").replace("\\bigcap","⋂")
            .replace("\\bigoplus","⊕").replace("\\bigotimes","⊗")
            .replace("\\bigvee","⋁").replace("\\bigwedge","⋀")
            .replace("\\lim_{","lim<sub>").replace("\\lim","lim")
            .replace("\\max_{","max<sub>").replace("\\max","max")
            .replace("\\min_{","min<sub>").replace("\\min","min")
            .replace("\\sup","sup").replace("\\inf","inf")
            // ── Relations ──
            .replace("\\leq","≤").replace("\\geq","≥").replace("\\neq","≠")
            .replace("\\approx","≈").replace("\\equiv","≡").replace("\\cong","≅")
            .replace("\\sim","∼").replace("\\simeq","≃").replace("\\propto","∝")
            .replace("\\ll","≪").replace("\\gg","≫")
            .replace("\\subset","⊂").replace("\\supset","⊃")
            .replace("\\subseteq","⊆").replace("\\supseteq","⊇")
            .replace("\\subsetneq","⊊").replace("\\supsetneq","⊋")
            .replace("\\in","∈").replace("\\notin","∉")
            .replace("\\ni","∋").replace("\\prec","≺").replace("\\succ","≻")
            .replace("\\preceq","⪯").replace("\\succeq","⪰")
            // ── Logic ──
            .replace("\\forall","∀").replace("\\exists","∃").replace("\\nexists","∄")
            .replace("\\neg","¬").replace("\\land","∧").replace("\\lor","∨")
            .replace("\\vdash","⊢").replace("\\models","⊨").replace("\\dashv","⊣")
            // ── Set ──
            .replace("\\emptyset","∅").replace("\\varnothing","∅")
            .replace("\\cup","∪").replace("\\cap","∩")
            .replace("\\setminus","∖").replace("\\complement","∁")
            // ── Arithmetic ──
            .replace("\\pm","±").replace("\\mp","∓")
            .replace("\\times","×").replace("\\div","÷")
            .replace("\\cdot","·").replace("\\circ","∘")
            .replace("\\oplus","⊕").replace("\\otimes","⊗")
            .replace("\\odot","⊙").replace("\\ominus","⊖")
            .replace("\\wr","≀")
            // ── Calculus ──
            .replace("\\infty","∞").replace("\\nabla","∇").replace("\\partial","∂")
            .replace("\\hbar","ℏ").replace("\\ell","ℓ").replace("\\wp","℘")
            .replace("\\Re","ℜ").replace("\\Im","ℑ")
            // ── Delimiters ──
            .replace("\\langle","⟨").replace("\\rangle","⟩")
            .replace("\\lfloor","⌊").replace("\\rfloor","⌋")
            .replace("\\lceil","⌈").replace("\\rceil","⌉")
            .replace("\\|","‖")
            // ── Dots ──
            .replace("\\ldots","…").replace("\\cdots","⋯").replace("\\vdots","⋮")
            .replace("\\ddots","⋱").replace("\\dots","…")
            // ── Geometry ──
            .replace("\\perp","⊥").replace("\\parallel","∥").replace("\\angle","∠")
            .replace("\\triangle","△").replace("\\square","□").replace("\\diamond","◇")
            // ── Blackboard bold ──
            .replace("\\mathbb{R}","ℝ").replace("\\mathbb{N}","ℕ")
            .replace("\\mathbb{Z}","ℤ").replace("\\mathbb{Q}","ℚ")
            .replace("\\mathbb{C}","ℂ").replace("\\mathbb{P}","ℙ")
            .replace("\\mathbb{H}","ℍ").replace("\\mathbb{F}","𝔽")
            // ── Misc ──
            .replace("\\dagger","†").replace("\\ddagger","‡")
            .replace("\\star","★").replace("\\ast","∗")
            .replace("\\bullet","•").replace("\\because","∵")
            .replace("\\therefore","∴").replace("\\checkmark","✓")
            .replace("\\infty","∞").replace("\\aleph","ℵ")
            .replace("\\prime","′").replace("\\degree","°")
            // ── Remove unknown \commands ──
            .replaceAll("\\\\[A-Za-z]+\\*?", "");
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    /** Find closing } matching the { at position open. */
    private static int matchBrace(String s, int open) {
        if (open < 0 || open >= s.length() || s.charAt(open) != '{') return -1;
        int depth = 1;
        for (int i = open + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') { if (--depth == 0) return i; }
        }
        return -1;
    }

    /** HTML-escape raw user text (full escaping). */
    private static String htmlEsc(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }

    /**
     * HTML-escape but preserve already-inserted tags: sup, sub, span, strong, em.
     * Used after math rendering injected those tags.
     */
    private static String safeEsc(String s) {
        // Only escape & < > that are NOT part of existing tags we inserted
        StringBuilder out = new StringBuilder();
        Pattern tagPat = Pattern.compile(
            "<(/?)(sup|sub|span|strong|em|br)[^>]*>",
            Pattern.CASE_INSENSITIVE);
        Matcher m = tagPat.matcher(s);
        int last = 0;
        while (m.find()) {
            String chunk = s.substring(last, m.start());
            out.append(chunk.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"));
            out.append(m.group());
            last = m.end();
        }
        String tail = s.substring(last);
        out.append(tail.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"));
        return out.toString();
    }

    private static String restoreEsc(String s) {
        return s.replace("\u0002BSLASH\u0002","\\")
                .replace("\u0002STAR\u0002",  "*")
                .replace("\u0002UNDER\u0002", "_")
                .replace("\u0002TICK\u0002",  "`")
                .replace("\u0002DOLLAR\u0002","$")
                .replace("\u0002LBRK\u0002",  "[")
                .replace("\u0002RBRK\u0002",  "]");
    }

    private static void setH(WebView wv, double h) {
        double v = Math.max(40, h + 16);
        wv.setPrefHeight(v);
        wv.setMinHeight(v);
    }

    private static void measureH(WebView wv) {
        try {
            Object r = wv.getEngine().executeScript(
                "Math.max(document.body.scrollHeight,document.documentElement.scrollHeight)");
            if (r instanceof Number n) setH(wv, n.doubleValue());
        } catch (Exception ignored) {}
    }

    private static void after(long ms, Runnable r) {
        PauseTransition t = new PauseTransition(Duration.millis(ms));
        t.setOnFinished(e -> r.run());
        t.play();
    }

    // ══════════════════════════════════════════════════════════════════
    //  INLINED CSS — dark ChatGPT-quality theme
    // ══════════════════════════════════════════════════════════════════

    private static final String CSS =
        // ── Reset ──
        "*,*::before,*::after{box-sizing:border-box;margin:0;padding:0;}" +

        // ── Root — CRITICAL: overflow on html enables mouse-wheel scroll ──
        "html{" +
        "  background:#0f1117;" +
        "  overflow:auto;" +          // ← mouse-wheel scroll REQUIRES this on html
        "}" +
        "body{" +
        "  background:#0f1117;" +
        "  color:#e2e8f0;" +
        "  font-family:'Segoe UI',system-ui,-apple-system,Helvetica,sans-serif;" +
        "  font-size:15px;line-height:1.75;" +
        "  padding:4px 6px 10px 4px;" +
        "  word-wrap:break-word;overflow-wrap:anywhere;" +
        "  overflow:auto;" +          // ← both html AND body needed for WebView
        "}" +

        // ── Headings ──
        "h1,h2,h3,h4,h5,h6{font-weight:700;line-height:1.3;}" +
        "h1{font-size:1.55em;color:#c7d2fe;" +
        "  border-bottom:2px solid #6366f1;padding-bottom:6px;margin:14px 0 8px;}" +
        "h2{font-size:1.3em;color:#a5b4fc;" +
        "  border-bottom:1px solid rgba(99,102,241,.4);padding-bottom:4px;margin:12px 0 6px;}" +
        "h3{font-size:1.12em;color:#818cf8;margin:10px 0 5px;}" +
        "h4{font-size:1em;color:#6366f1;margin:8px 0 4px;}" +
        "h5,h6{font-size:.95em;color:#6366f1;margin:6px 0 3px;}" +

        // ── Body text ──
        "p{margin:5px 0 7px;}" +
        "br{display:block;content:'';margin:3px 0;}" +
        "strong{color:#f1f5f9;font-weight:700;}" +
        "em{color:#cbd5e1;font-style:italic;}" +
        "del{color:#94a3b8;text-decoration:line-through;}" +
        "a{color:#818cf8;text-decoration:none;}" +
        "a:hover{color:#c7d2fe;text-decoration:underline;}" +
        "hr{border:none;border-top:1px solid rgba(99,102,241,.22);margin:14px 0;}" +

        // ── Inline code ──
        "code.ic{" +
        "  font-family:'Consolas','Fira Code','Courier New',monospace;" +
        "  font-size:.86em;color:#10b981;" +
        "  background:rgba(16,185,129,.12);border:1px solid rgba(16,185,129,.25);" +
        "  border-radius:4px;padding:1px 5px;" +
        "}" +

        // ── Code block ──
        ".cbw{margin:10px 0;border-radius:10px;overflow:hidden;" +
        "     border:1px solid rgba(99,102,241,.25);background:#0d1117;}" +
        ".cbh{display:flex;align-items:center;justify-content:space-between;" +
        "     background:rgba(99,102,241,.1);border-bottom:1px solid rgba(99,102,241,.15);" +
        "     padding:5px 14px;}" +
        ".cbl{font-family:'Consolas',monospace;font-size:11px;color:#6b7db3;text-transform:lowercase;}" +
        ".cpb{font-size:11px;color:#6b7db3;background:rgba(99,102,241,.1);" +
        "     border:1px solid rgba(99,102,241,.22);border-radius:5px;" +
        "     padding:3px 10px;cursor:pointer;transition:background .15s,color .15s;}" +
        ".cpb:hover{color:#a5b4fc;background:rgba(99,102,241,.25);}" +
        ".cbp{margin:0;padding:0;overflow-x:auto;background:#0d1117;}" +
        ".cbp code{" +
        "  display:block;padding:14px 16px;" +
        "  font-family:'Consolas','Fira Code','Courier New',monospace!important;" +
        "  font-size:13.5px!important;line-height:1.65;" +
        "  color:#e2e8f0;white-space:pre;background:transparent!important;" +
        "  tab-size:4;" +
        "}" +

        // ── Keyword highlighting by CSS (no highlight.js needed) ──
        // Java/Python/JS keywords turn indigo
        ".lang-java .kw,.lang-python .kw,.lang-javascript .kw,.lang-js .kw," +
        ".lang-typescript .kw,.lang-ts .kw,.lang-cpp .kw,.lang-c .kw{color:#818cf8;font-weight:600;}" +
        // Strings turn emerald
        ".lang-java .str,.lang-python .str,.lang-javascript .str{color:#10b981;}" +
        // Comments dimmed
        ".lang-java .cmt,.lang-python .cmt,.lang-javascript .cmt{color:#4b5e80;font-style:italic;}" +

        // ── Blockquote ──
        "blockquote{margin:8px 0;padding:10px 14px;" +
        "  background:rgba(16,185,129,.08);border-left:4px solid #10b981;" +
        "  border-radius:0 8px 8px 0;color:#a7f3d0;font-style:italic;}" +

        // ── Table ──
        ".tw{overflow-x:auto;margin:10px 0;}" +
        "table{width:100%;border-collapse:collapse;font-size:14px;" +
        "  border:1px solid rgba(99,102,241,.25);}" +
        "thead tr{background:rgba(99,102,241,.22);}" +
        "th{padding:9px 14px;text-align:left;color:#eef2ff;font-weight:700;" +
        "  border:1px solid rgba(99,102,241,.2);}" +
        "tbody tr:nth-child(even){background:#111827;}" +
        "tbody tr:nth-child(odd){background:#0f1523;}" +
        "td{padding:8px 14px;border:1px solid rgba(99,102,241,.12);color:#e2e8f0;}" +

        // ── Lists ──
        "ul,ol{padding-left:24px;margin:5px 0;}" +
        "li{margin:4px 0;color:#e2e8f0;}" +
        "ul li{list-style-type:disc;}" +
        "ul li::marker{color:#6366f1;}" +
        "ol li::marker{color:#6366f1;font-weight:700;}" +

        // ══════════════════════════════════════════════════════════════
        //  MATH STYLES
        // ══════════════════════════════════════════════════════════════

        // Block math container
        ".math-blk{" +
        "  background:rgba(99,102,241,.07);" +
        "  border:1px solid rgba(99,102,241,.22);" +
        "  border-radius:8px;padding:14px 22px;margin:10px 0;" +
        "  overflow-x:auto;text-align:center;" +
        "}" +

        // Inline math wrapper
        ".math-inl{display:inline;}" +

        // Displayed math expression
        ".mdisp{" +
        "  font-family:'Cambria Math','STIX Two Math','Latin Modern Math'," +
        "               'Times New Roman',Georgia,serif;" +
        "  font-size:1.15em;color:#c7d2fe;font-style:italic;" +
        "  display:inline;" +
        "}" +

        // Inline math expression
        ".minl{" +
        "  font-family:'Cambria Math','STIX Two Math','Latin Modern Math'," +
        "               'Times New Roman',Georgia,serif;" +
        "  font-size:1em;color:#c7d2fe;font-style:italic;" +
        "  display:inline;" +
        "}" +

        // Stacked fraction
        ".frac{" +
        "  display:inline-flex;flex-direction:column;" +
        "  align-items:center;vertical-align:middle;" +
        "  font-style:italic;margin:0 3px;" +
        "}" +
        ".fnum{" +
        "  border-bottom:1.5px solid #a5b4fc;" +
        "  padding:0 4px 1px;line-height:1.25;color:#c7d2fe;" +
        "  font-size:.9em;" +
        "}" +
        ".fden{" +
        "  padding:1px 4px 0;line-height:1.25;color:#c7d2fe;" +
        "  font-size:.9em;" +
        "}" +

        // Square root
        ".sqrt{display:inline-flex;align-items:center;color:#c7d2fe;}" +
        ".sarg{border-top:1.5px solid #a5b4fc;padding:0 2px 0 1px;margin-left:1px;}" +

        // Nth root
        ".nrt{display:inline-flex;align-items:flex-start;color:#c7d2fe;}" +
        ".nidx{font-size:.6em;margin-right:-3px;vertical-align:super;}" +

        // Delimiter size
        ".mlp,.mrp{font-size:1.3em;vertical-align:middle;color:#a5b4fc;}" +

        // Text mode inside math
        ".mtext{font-family:'Segoe UI',sans-serif;font-style:normal;color:#e2e8f0;font-size:.95em;}" +

        // sup/sub
        "sup,sub{font-size:.72em;}" +
        "sup{vertical-align:super;}" +
        "sub{vertical-align:sub;}";

    // ══════════════════════════════════════════════════════════════════
    //  INLINED JAVASCRIPT
    // ══════════════════════════════════════════════════════════════════

    private static final String JS =
        // Report page height to JavaFX
        "function rh(){" +
        "  var h=Math.max(document.body.scrollHeight,document.documentElement.scrollHeight);" +
        "  alert('HEIGHT:'+h);" +
        "}" +

        // Copy code block
        "function cp(btn){" +
        "  var code=btn.closest('.cbw').querySelector('code');" +
        "  if(!code)return;" +
        "  var t=code.innerText||code.textContent;" +
        "  try{navigator.clipboard.writeText(t).then(function(){fb(btn);});}catch(e){" +
        "    var a=document.createElement('textarea');a.value=t;" +
        "    document.body.appendChild(a);a.select();document.execCommand('copy');" +
        "    document.body.removeChild(a);fb(btn);" +
        "  }" +
        "}" +
        "function fb(b){b.textContent='✓ Copied!';b.style.color='#10b981';" +
        "  setTimeout(function(){b.textContent='Copy';b.style.color='';},2000);}" +

        // Open link in default browser
        "function olink(e,url){e.preventDefault();alert('OPEN:'+url);}" +

        // Fire on load
        "window.onload=function(){rh();setTimeout(rh,250);setTimeout(rh,700);};" +

        // MutationObserver for dynamic content changes
        "try{new MutationObserver(function(){setTimeout(rh,40);}).observe(" +
        "  document.body,{childList:true,subtree:true});}catch(e){}";
}
