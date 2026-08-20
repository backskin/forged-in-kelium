package kelium.gui.replay2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import kelium.report.ReplayRecord;

/**
 * ПОЛНЫЙ ЛОГ ПАРТИИ ОДНИМ ФАЙЛОМ — для разбора багов (заказ дизайнера
 * 20.08.2026: «чтобы я тебе лог партии кидал, там будут ВСЕ настройки, которые
 * её сгенерировали, и говорить шаг, где происходит баг»).
 *
 * <p>ЗАЧЕМ ИМЕННО ТАК. Раньше сообщение о баге выглядело как «шаг 833, сид
 * 97241» — и этого не хватало: одна и та же пара «сид + свод» при других
 * тумблерах дополнений, другой раскладке или другом составе ботов даёт СОВСЕМ
 * другую партию. Значит воспроизвести баг было нельзя, и разбор начинался с
 * переписки об условиях вместо самого бага.
 *
 * <p>Файл делится на две части. Сверху — ШАПКА: всё, чем партия была задана,
 * вплоть до поворотов ЦУ и состояния каждого дополнения. Ниже — ЖУРНАЛ ПО
 * ШАГАМ с номерами: тот же номер, который дизайнер видит в пульте («шаг
 * 547/993»), поэтому «баг на шаге 833» указывает ровно на строку файла.
 *
 * <p>Формат — обычный текст, а не JSON: файл читают глазами и вставляют в чат.
 * Сама запись партии сохраняется отдельным действием («Сохранить запись…») и
 * нужна, когда партию надо не прочитать, а открыть заново.
 */
public final class FullLog {

    private FullLog() {
    }

    /** Собрать текст полного лога записи. */
    public static String build(ReplayRecord rec) {
        StringBuilder b = new StringBuilder();
        b.append("ПОЛНЫЙ ЛОГ ПАРТИИ «Кристаллы Раздора»\n");
        b.append("=".repeat(72)).append("\n\n");

        b.append("КАК ПАРТИЯ БЫЛА ЗАДАНА\n");
        b.append("-".repeat(72)).append("\n");
        line(b, "свод правил", rec.ruleset);
        line(b, "игроков", String.valueOf(rec.players));
        line(b, "сид", String.valueOf(rec.seed));
        line(b, "раскладка", rec.scenarioId == null ? "по сиду" : rec.scenarioId);
        line(b, "файл раскладки", rec.scenarioFile == null ? "авторская" : rec.scenarioFile);
        line(b, "шагов в записи", String.valueOf(rec.frames.size()));
        line(b, "раундов", String.valueOf(rec.rounds));
        line(b, "итог", rec.winner == null ? "никто не победил"
            : "победил " + rec.playerName(rec.winner) + " (" + rec.condition + ")");

        b.append("\nМЕСТА\n");
        b.append("-".repeat(72)).append("\n");
        for (int i = 0; i < rec.players; i++) {
            b.append("  место ").append(i + 1).append(": ");
            b.append(at(rec.seatLabels, i, "?"));
            b.append("  [бот ").append(at(rec.seatIds, i, "?")).append("]");
            b.append("  планшет войск ").append(at(rec.sides, i, "?"));
            if (i < rec.cuFacing.size() && rec.cuFacing.get(i) != null) {
                b.append("  поворот ЦУ ").append(rec.cuFacing.get(i));
            }
            b.append("\n");
        }

        b.append("\nДОПОЛНЕНИЯ\n");
        b.append("-".repeat(72)).append("\n");
        if (rec.expansions.isEmpty()) {
            // Записи, сделанные до 20.08.2026, состояние тумблеров не хранят.
            // Честно говорим об этом, а не выдумываем «всё включено».
            b.append("  не записаны (партия сыграна версией до 20.08.2026)\n");
        } else {
            for (Map.Entry<String, Boolean> e : rec.expansions.entrySet()) {
                b.append("  ").append(kelium.gui.Expansions.title(e.getKey()))
                    .append(": ").append(Boolean.TRUE.equals(e.getValue()) ? "включено" : "выключено")
                    .append("\n");
            }
        }

        if (!rec.unitStock.isEmpty()) {
            b.append("\nЛИЧНЫЙ ЗАПАС ЖЕТОНОВ\n");
            b.append("-".repeat(72)).append("\n");
            for (Map.Entry<String, Integer> e : rec.unitStock.entrySet()) {
                b.append("  ").append(Names.unit(e.getKey())).append(": ")
                    .append(e.getValue()).append("\n");
            }
        }

        b.append("\n\nЖУРНАЛ ПО ШАГАМ\n");
        b.append("=".repeat(72)).append("\n");
        b.append("Номер шага здесь тот же, что в пульте разбора: «шаг 833» —\n");
        b.append("это строка «833» ниже.\n\n");
        for (int i = 0; i < rec.frames.size(); i++) {
            ReplayRecord.Frame f = rec.frames.get(i);
            b.append(String.format("%6d", i)).append("  P").append(f.round)
                .append(" круг ").append(f.circle);
            if (f.seat != null) {
                b.append("  место ").append(f.seat + 1);
            }
            if (f.combat) {
                b.append("  [БОЙ]");
            }
            b.append("  ").append(f.type).append("\n");
            if (f.log != null && !f.log.isBlank()) {
                b.append("        ").append(f.log).append("\n");
            }
            for (ReplayRecord.Thought t : f.thoughts) {
                b.append("        · мысль места ").append(t.seat + 1).append(": ")
                    .append(t.text).append("\n");
            }
        }
        return b.toString();
    }

    /** Записать лог рядом с выбранным файлом. */
    public static void save(ReplayRecord rec, Path file) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, build(rec), StandardCharsets.UTF_8);
    }

    /** Имя по умолчанию: по сиду и числу игроков — чтобы файлы не путались. */
    public static String defaultName(ReplayRecord rec) {
        return "лог-партии-" + rec.players + "p-сид" + rec.seed + ".txt";
    }

    private static void line(StringBuilder b, String key, String value) {
        b.append("  ").append(key).append(": ").append(value == null ? "—" : value).append("\n");
    }

    private static String at(java.util.List<String> list, int i, String dflt) {
        return list != null && i < list.size() && list.get(i) != null ? list.get(i) : dflt;
    }
}
