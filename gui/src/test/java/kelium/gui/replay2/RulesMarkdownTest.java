package kelium.gui.replay2;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;

import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

import org.junit.jupiter.api.Test;

/**
 * СПРАВОЧНИК ПРАВИЛ (заказ дизайнера 25.09.2026): книга правил из markdown
 * глав — в цифровой версии, по главам и с поиском.
 *
 * <p>Сторожит то, из-за чего справочник начнёт врать молча: книга не нашлась,
 * глава не набралась или протекла вёрсточная разметка, узел раздела в дереве
 * прокручивает не к своему заголовку, поиск различает «е» и «ё» или не находит
 * склонённое слово.
 */
class RulesMarkdownTest {

    @Test
    void bookIsFoundAndHasChapters() {
        Path dir = RulesMarkdown.bookDir();
        assertNotNull(dir, "папка книги правил не нашлась рядом с данными");
        List<Path> ch = RulesMarkdown.chapters(dir);
        assertTrue(ch.size() >= 10, "глав мало: " + ch);
        for (Path p : ch) {
            assertFalse(p.getFileName().toString().startsWith("00"), "план книги — не глава");
        }
    }

    @Test
    void everyChapterRendersClean() throws Exception {
        RulesMarkdown.Style st = HelpWindow.liveStyle(false);
        for (HelpBook.Section s : RulesMarkdown.chapterSections(RulesMarkdown.bookDir())) {
            String html = RulesMarkdown.render(s.file, st);
            assertTrue(html.length() > 500, "глава пустая: " + s.title);
            for (String junk : List.of("<!--", ":надвое:", ":крупно:", ":вовсю:", ":фазы:",
                    "**", "[[", "ИЛЛЮСТРАЦИЯ —")) {
                assertFalse(html.contains(junk), "в главе «" + s.title + "» протекло: " + junk);
            }
            assertFalse(s.children.isEmpty(), "у главы нет разделов: " + s.title);
            // Узел раздела прокручивает к заголовку по номеру — заголовков в
            // набранной главе должно быть ровно столько, сколько узлов в дереве.
            int nodes = s.flatten().size() - 1;
            HTMLEditorKit kit = new HTMLEditorKit();
            HTMLDocument doc = (HTMLDocument) kit.createDefaultDocument();
            doc.putProperty("IgnoreCharsetDirective", Boolean.TRUE);
            kit.read(new StringReader("<html><body>" + html + "</body></html>"), doc, 0);
            assertEquals(nodes, HelpWindow.headings(doc).length,
                "заголовков не столько, сколько разделов: " + s.title);
        }
    }

    @Test
    void iconsResolveToFiles() {
        Path ch4 = null;
        for (Path p : RulesMarkdown.chapters(RulesMarkdown.bookDir())) {
            if (RulesMarkdown.chapterNumber(p) == 4) {
                ch4 = p;
            }
        }
        assertNotNull(ch4, "нет главы 4");
        String html = RulesMarkdown.render(ch4, HelpWindow.liveStyle(false));
        assertTrue(html.contains("<img") && html.contains(".png"),
            "значки «[иконка: …]» главы 4 не стали картинками");
        assertTrue(html.contains("href='book:"), "ссылки «глава N» не стали ссылками");
    }

    @Test
    void searchIgnoresCaseAndYo() {
        assertEquals("ещё".replace('ё', 'е'), HelpWindow.norm("ЕЩЁ"));
        assertEquals(HelpWindow.norm("Трофеёв"), HelpWindow.norm("трофеев"));
        String text = HelpWindow.norm("Два ТРОФЕЯ и три трофеи; трофейной стороной");
        String q = HelpWindow.norm("трофей");
        int[] a = HelpWindow.next(text, q, 0);
        assertNotNull(a, "склонённое слово не нашлось");
        assertEquals("трофея", text.substring(a[0], a[1]), "подсвечивается слово целиком");
        int[] b = HelpWindow.next(text, q, a[1]);
        assertEquals("трофеи", text.substring(b[0], b[1]));
        int[] c = HelpWindow.next(text, q, b[1]);
        assertEquals("трофейной", text.substring(c[0], c[1]));
        assertNull(HelpWindow.next(text, q, c[1]));
        // Короткое слово — буква в букву, без отбрасывания окончания.
        assertEquals("бой", HelpWindow.needle("бой"));
        assertArrayEquals(new int[]{0, 3}, HelpWindow.next("бой и боя", "бой", 0));
    }
}
