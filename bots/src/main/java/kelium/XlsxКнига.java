package kelium;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * XLSX СВОИМИ РУКАМИ — книга Excel без единой сторонней библиотеки.
 *
 * <p>ЗАЧЕМ ОНА ВООБЩЕ ПОЯВИЛАСЬ. Таблицы карт до сих пор собирал
 * {@code tools/таблицы-карт.py} через openpyxl. На машине, где идёт работа,
 * Python не установлен вовсе (в PATH лежит заглушка из Microsoft Store), и
 * запросить таблицу было нечем. Класс закрывает эту дыру: книга собирается тем
 * же Java, которым собирается игра, и не зависит ни от Python, ни от Excel.
 *
 * <p>КАК УСТРОЕН .XLSX. Это обычный zip с XML внутри. Здесь пишется
 * минимальный, но честный набор частей: типы содержимого, связи, книга, стили
 * и по листу на каждый лист. Строки кладутся ВСТРОЕННЫМИ
 * ({@code t="inlineStr"}), а не в общую таблицу строк: словарь сэкономил бы
 * место, но карточные тексты почти не повторяются, а читать и чинить такой
 * файл было бы вдвое труднее.
 *
 * <p>ЧТО УМЕЕТ: шапка (белым по тёмно-синему), перенос по словам, тонкая
 * рамка, ширины колонок, закреплённая первая строка и автофильтр — ровно то,
 * что делал питоновский сборщик, и ничего сверх.
 */
public final class XlsxКнига {

    /** Один лист книги: шапка, ширины колонок и строки. */
    public static final class Лист {
        private final String имя;
        private final List<String> шапка;
        private final int[] ширины;
        private final List<List<Object>> строки = new ArrayList<>();

        private Лист(String имя, int[] ширины, List<String> шапка) {
            this.имя = имя;
            this.ширины = ширины;
            this.шапка = шапка;
        }

        /** Дописать строку. {@code null} — пустая ячейка, число — числом. */
        public void строка(Object... ячейки) {
            строки.add(Arrays.asList(ячейки));
        }

        public int строк() {
            return строки.size();
        }

        public String имя() {
            return имя;
        }
    }

    private final List<Лист> листы = new ArrayList<>();

    /**
     * Завести лист. Имя режется до 31 знака — предел самого формата, и Excel
     * молча отказывается открывать книгу, где он нарушен.
     */
    public Лист лист(String имя, int[] ширины, String... шапка) {
        Лист л = new Лист(имя.length() > 31 ? имя.substring(0, 31) : имя,
            ширины, Arrays.asList(шапка));
        листы.add(л);
        return л;
    }

    /** Записать книгу на диск. */
    public void записать(Path цель) throws Exception {
        Files.createDirectories(цель.toAbsolutePath().getParent());
        try (OutputStream out = Files.newOutputStream(цель);
             ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            часть(zip, "[Content_Types].xml", типыСодержимого());
            часть(zip, "_rels/.rels", корневыеСвязи());
            часть(zip, "xl/workbook.xml", книга());
            часть(zip, "xl/_rels/workbook.xml.rels", связиКниги());
            часть(zip, "xl/styles.xml", стили());
            for (int i = 0; i < листы.size(); i++) {
                часть(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", лист(листы.get(i)));
            }
        }
    }

    private static void часть(ZipOutputStream zip, String имя, String xml) throws Exception {
        zip.putNextEntry(new ZipEntry(имя));
        zip.write(xml.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    // ======================================================================
    //  Части книги
    // ======================================================================

    private String типыСодержимого() {
        StringBuilder sb = new StringBuilder(ШАПКА_XML);
        sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">");
        sb.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>");
        sb.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
        sb.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
        sb.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int i = 0; i < листы.size(); i++) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i + 1)
              .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return sb.append("</Types>").toString();
    }

    private String корневыеСвязи() {
        return ШАПКА_XML
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
            + "</Relationships>";
    }

    private String книга() {
        StringBuilder sb = new StringBuilder(ШАПКА_XML);
        sb.append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"")
          .append(" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
        for (int i = 0; i < листы.size(); i++) {
            sb.append("<sheet name=\"").append(экран(листы.get(i).имя))
              .append("\" sheetId=\"").append(i + 1)
              .append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        return sb.append("</sheets></workbook>").toString();
    }

    private String связиКниги() {
        StringBuilder sb = new StringBuilder(ШАПКА_XML);
        sb.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 0; i < листы.size(); i++) {
            sb.append("<Relationship Id=\"rId").append(i + 1)
              .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
              .append(i + 1).append(".xml\"/>");
        }
        sb.append("<Relationship Id=\"rId").append(листы.size() + 1)
          .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        return sb.append("</Relationships>").toString();
    }

    /**
     * Стили: 0 — обычная ячейка, 1 — шапка, 2 — тело, 3 — тело жирным (первая
     * колонка, номер карты). Цвета те же, что были у питоновского сборщика,
     * чтобы книга выглядела привычно.
     */
    private String стили() {
        return ШАПКА_XML
            + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
            + "<fonts count=\"3\">"
            + "<font><sz val=\"10\"/><name val=\"Arial\"/></font>"
            + "<font><b/><color rgb=\"FFFFFFFF\"/><sz val=\"10\"/><name val=\"Arial\"/></font>"
            + "<font><b/><sz val=\"10\"/><name val=\"Arial\"/></font>"
            + "</fonts>"
            + "<fills count=\"3\">"
            + "<fill><patternFill patternType=\"none\"/></fill>"
            + "<fill><patternFill patternType=\"gray125\"/></fill>"
            + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1F3864\"/><bgColor indexed=\"64\"/></patternFill></fill>"
            + "</fills>"
            + "<borders count=\"2\">"
            + "<border><left/><right/><top/><bottom/><diagonal/></border>"
            + "<border><left style=\"thin\"><color rgb=\"FFD0D0D0\"/></left>"
            + "<right style=\"thin\"><color rgb=\"FFD0D0D0\"/></right>"
            + "<top style=\"thin\"><color rgb=\"FFD0D0D0\"/></top>"
            + "<bottom style=\"thin\"><color rgb=\"FFD0D0D0\"/></bottom><diagonal/></border>"
            + "</borders>"
            + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
            + "<cellXfs count=\"4\">"
            + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
            + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\""
            + " applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\">"
            + "<alignment vertical=\"center\" wrapText=\"1\"/></xf>"
            + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\""
            + " applyBorder=\"1\" applyAlignment=\"1\"><alignment vertical=\"top\" wrapText=\"1\"/></xf>"
            + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"1\" xfId=\"0\""
            + " applyFont=\"1\" applyBorder=\"1\" applyAlignment=\"1\">"
            + "<alignment vertical=\"top\" wrapText=\"1\"/></xf>"
            + "</cellXfs>"
            + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
            + "</styleSheet>";
    }

    private String лист(Лист л) {
        int колонок = л.шапка.size();
        StringBuilder sb = new StringBuilder(ШАПКА_XML);
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        // Закреплённая первая строка: без неё шапка уезжает на второй экран.
        sb.append("<sheetViews><sheetView workbookViewId=\"0\">")
          .append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
          .append("</sheetView></sheetViews>");
        if (л.ширины != null && л.ширины.length > 0) {
            sb.append("<cols>");
            for (int i = 0; i < л.ширины.length; i++) {
                sb.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1)
                  .append("\" width=\"").append(л.ширины[i]).append("\" customWidth=\"1\"/>");
            }
            sb.append("</cols>");
        }
        sb.append("<sheetData>");
        sb.append("<row r=\"1\">");
        for (int i = 0; i < колонок; i++) {
            ячейка(sb, i, 1, л.шапка.get(i), 1);
        }
        sb.append("</row>");
        int r = 2;
        for (List<Object> строка : л.строки) {
            sb.append("<row r=\"").append(r).append("\">");
            for (int i = 0; i < колонок; i++) {
                Object з = i < строка.size() ? строка.get(i) : null;
                ячейка(sb, i, r, з, i == 0 ? 3 : 2);
            }
            sb.append("</row>");
            r++;
        }
        sb.append("</sheetData>");
        sb.append("<autoFilter ref=\"A1:").append(буква(колонок - 1)).append(r - 1).append("\"/>");
        return sb.append("</worksheet>").toString();
    }

    /** Одна ячейка. Числа пишутся числами, всё прочее — встроенной строкой. */
    private static void ячейка(StringBuilder sb, int колонка, int строка, Object значение, int стиль) {
        String адрес = буква(колонка) + строка;
        if (значение == null || "".equals(значение)) {
            sb.append("<c r=\"").append(адрес).append("\" s=\"").append(стиль).append("\"/>");
            return;
        }
        if (значение instanceof Number) {
            sb.append("<c r=\"").append(адрес).append("\" s=\"").append(стиль).append("\"><v>")
              .append(значение).append("</v></c>");
            return;
        }
        sb.append("<c r=\"").append(адрес).append("\" s=\"").append(стиль)
          .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
          .append(экран(String.valueOf(значение)))
          .append("</t></is></c>");
    }

    /** Имя колонки по её номеру с нуля: 0 → A, 26 → AA. */
    private static String буква(int индекс) {
        StringBuilder sb = new StringBuilder();
        int n = индекс;
        while (n >= 0) {
            sb.insert(0, (char) ('A' + n % 26));
            n = n / 26 - 1;
        }
        return sb.toString();
    }

    /**
     * Экранирование для XML. Управляющие знаки выбрасываются: в карточных
     * текстах их быть не должно, а книга с ними не откроется вовсе.
     */
    private static String экран(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> {
                    if (c == '\n' || c == '\t' || c >= 0x20) {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static final String ШАПКА_XML =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
}
