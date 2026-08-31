package kelium.gui.dev;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import kelium.gui.GameRecorder;
import kelium.report.ReplayRecord;

/**
 * СЫГРАТЬ ПАРТИЮ И СОХРАНИТЬ ЗАПИСЬ — из командной строки, без окна.
 *
 * <p>ЗАЧЕМ. Партию можно сыграть и в самом проигрывателе кнопкой «Сыграть и
 * показать», но тогда её приходится настраивать мышью и ждать расчёта в живом
 * окне. Здесь то же самое делается одной командой: свод, число игроков, сид и
 * состав ботов задаются аргументами, запись ложится в файл, а проигрыватель
 * открывает её первым аргументом ({@code Replay2Gui <файл>}).
 *
 * <p>Запуск:
 * {@code kelium.gui.dev.ЗаписатьПартию [свод] [игроков] [сид] [файл] [боты через запятую]}
 */
public final class ЗаписатьПартию {

    private ЗаписатьПартию() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        String свод = args.length > 0 ? args[0] : kelium.dataio.GameConfig.DEFAULT_RULESET;
        int игроков = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        long сид = args.length > 2 ? Long.parseLong(args[2]) : 1L;
        Path файл = Path.of(args.length > 3 ? args[3]
            : "records/партия-" + игроков + "p-" + свод + ".json");
        // ПО УМОЛЧАНИЮ МАСТЕРА, а не гроссмейстеры: гроссмейстер доигрывает
        // копию партии на каждом выборе приказа и считает вдвое дольше, а
        // смотреть партию хочется сразу.
        List<String> боты = args.length > 4
            ? List.of(args[4].split(","))
            : List.of("punisher:3", "stalker:3", "supplier:3", "builder:3");

        System.out.println("играю: свод " + свод + ", игроков " + игроков
            + ", сид " + сид + ", боты " + боты);
        long начало = System.currentTimeMillis();
        ReplayRecord rec = GameRecorder.play(свод, игроков, сид,
            боты.subList(0, Math.min(игроков, боты.size())),
            строка -> System.out.println("  " + строка));
        Files.createDirectories(файл.getParent());
        rec.save(файл);
        System.out.println("раундов: " + rec.rounds
            + ", секунд на расчёт: " + (System.currentTimeMillis() - начало) / 1000);
        System.out.println("запись: " + файл.toAbsolutePath());
    }
}
