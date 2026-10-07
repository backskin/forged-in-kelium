package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * СЧЁТ ПИТАНИЯ — забывают ли боты запитать свои здания (вопрос дизайнера
 * 07.10.2026: Питание почти всегда играют постройкой станции, а не перекладкой).
 *
 * <p>В начале каждого хода игрока снимок его базы: сколько зданий-потребителей
 * (добытчики, военные) стоят без энергии и сколько свободных кубиков лежит на
 * его источниках (станции, ЦУ). Если свободных кубиков хватает, чтобы запитать
 * незапитанное здание, а ход прошёл без перекладки, — это «забыл запитать».
 * Если свободных нет — здания стоят пустыми от нехватки энергии, а не от забывчивости.
 *
 * <p>Запуск: {@code kelium.СчётПитания [партий] [игроков] [потоков] [боты] [метка]}.
 */
public final class СчётПитания {

    private СчётПитания() {
    }

    static final class Счёт {
        long ходов;
        long ходовСНезапитанным;          // есть хоть одно незапитанное здание-потребитель
        long ходовМожноЗапитать;          // свободных кубиков хватает хотя бы на одно
        long можноИПитаниеДоступно;       // ...и на карте хода есть Питание (верх или открытый низ)
        long можноИПереложил;             // ...и в этом ходу сыграна перекладка
        long можноИПостроилСтанцию;
        long потребителей;
        long незапитано;
        long свободныхКубиков;
        long всегоКубиков;
        long[] незапПоРаундам = new long[12];
        long[] потрПоРаундам = new long[12];
        long[] свобПоРаундам = new long[12];
        long[] ходовПоРаундам = new long[12];
        long добычДействий;
        long добычПусто;                  // сыграна добыча/выпуск, а запитанных зданий этого вида нет
        long выпусков;
        long выпусковПусто;
        final java.util.Map<String, int[]> холостые = new java.util.TreeMap<>(); // действие → [всего, холостых]
        final List<String> примеры = new ArrayList<>();
        long пустыхНиРазу;          // пустое здание, ни разу не запитанное с постройки
        long пустыхПотерявших;      // было запитано, потеряло энергию
        final java.util.Map<String, Integer> пустыеПоВиду = new java.util.TreeMap<>();
        long ячеекПотребителей;     // сколько ячеек энергии у добытчиков и военных
        long кубиковВсего;          // сколько кубиков у игрока всего
        long ходовПерестроено;      // потребителям нужно больше, чем энергии есть вообще
        long добычНичего;
        final java.util.Map<String, Integer> причины = new java.util.TreeMap<>();                 // добыча не дала ни келемия, ни контейнера
        long выпусковНичего;              // выпуск не дал ни войск, ни боеприпасов
        long ходовВсёПусто;               // есть потребители, но ни один не запитан
        long перекладок;
        long перекладокБезТолку;          // после перекладки запитанных зданий не прибавилось

        void слить(Счёт о) {
            ходов += о.ходов;
            ходовСНезапитанным += о.ходовСНезапитанным;
            ходовМожноЗапитать += о.ходовМожноЗапитать;
            можноИПитаниеДоступно += о.можноИПитаниеДоступно;
            можноИПереложил += о.можноИПереложил;
            можноИПостроилСтанцию += о.можноИПостроилСтанцию;
            потребителей += о.потребителей;
            незапитано += о.незапитано;
            свободныхКубиков += о.свободныхКубиков;
            всегоКубиков += о.всегоКубиков;
            for (int i = 0; i < незапПоРаундам.length; i++) {
                незапПоРаундам[i] += о.незапПоРаундам[i];
                потрПоРаундам[i] += о.потрПоРаундам[i];
                свобПоРаундам[i] += о.свобПоРаундам[i];
                ходовПоРаундам[i] += о.ходовПоРаундам[i];
            }
            добычДействий += о.добычДействий;
            добычПусто += о.добычПусто;
            выпусков += о.выпусков;
            выпусковПусто += о.выпусковПусто;
            добычНичего += о.добычНичего;
            ячеекПотребителей += о.ячеекПотребителей;
            пустыхНиРазу += о.пустыхНиРазу;
            пустыхПотерявших += о.пустыхПотерявших;
            о.пустыеПоВиду.forEach((k, v) -> пустыеПоВиду.merge(k, v, Integer::sum));
            кубиковВсего += о.кубиковВсего;
            ходовПерестроено += о.ходовПерестроено;
            for (String п : о.примеры) {
                if (примеры.size() < 30) {
                    примеры.add(п);
                }
            }
            о.холостые.forEach((k, v) -> {
                int[] м = холостые.computeIfAbsent(k, x -> new int[2]);
                м[0] += v[0];
                м[1] += v[1];
            });
            о.причины.forEach((k, v) -> причины.merge(k, v, Integer::sum));
            выпусковНичего += о.выпусковНичего;
            ходовВсёПусто += о.ходовВсёПусто;
            перекладок += о.перекладок;
            перекладокБезТолку += о.перекладокБезТолку;
        }
    }

    @SuppressWarnings("unchecked")
    static int число(Map<String, Object> ev, String ключ) {
        if (ev.get("telemetry") instanceof Map<?, ?> t && t.get(ключ) instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    static boolean потребитель(BuildingToken b) {
        return b.energySlots > 0 && b.type != BuildingType.COMMAND_CENTER
            && b.type != BuildingType.POWER_PLANT;
    }

    static boolean военное(BuildingType t) {
        return t == BuildingType.BARRACKS || t == BuildingType.FACTORY || t == BuildingType.AIRBASE;
    }

    /** Снимок базы игрока: [потребителей, незапитано, свободных кубиков, всего кубиков, мин. недостача]. */
    static int[] снимок(PlayerState p) {
        int потр = 0;
        int незап = 0;
        int своб = 0;
        int всего = 0;
        int минНедост = Integer.MAX_VALUE;
        for (BuildingToken b : p.buildingsOnField()) {
            if (b.type == BuildingType.POWER_PLANT || b.type == BuildingType.COMMAND_CENTER) {
                своб += b.energyIdle;
                всего += b.energyIdle;
            }
            всего += b.energyPlaced;
            if (потребитель(b)) {
                потр++;
                if (!b.powered()) {
                    незап++;
                    минНедост = Math.min(минНедост, b.energySlots - b.energyPlaced);
                }
            }
        }
        return new int[]{потр, незап, своб, всего, минНедост};
    }

    static int запитано(PlayerState p, boolean добытчики) {
        int n = 0;
        for (BuildingToken b : p.buildingsOnField()) {
            if (потребитель(b) && b.powered()
                    && (добытчики ? b.type == BuildingType.MINER : военное(b.type))) {
                n++;
            }
        }
        return n;
    }

    public static void main(String[] args) throws Exception {
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 40;
        int игроков = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        int потоков = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        List<String> боты = List.of((args.length > 3 ? args[3]
            : "punisher:3,stalker:3,supplier:3,builder:3").split(","));
        String метка = args.length > 4 ? args[4] : "свод";
        String свод = System.getProperty("kelium.свод", "1.50.0");

        ExecutorService пул = Executors.newFixedThreadPool(потоков);
        List<Future<Счёт>> fs = new ArrayList<>();
        for (int g = 0; g < партий; g++) {
            final int номер = g;
            fs.add(пул.submit(() -> партия(свод, игроков, 9000L + номер, боты, номер)));
        }
        Счёт в = new Счёт();
        for (Future<Счёт> f : fs) {
            в.слить(f.get());
        }
        пул.shutdown();

        StringBuilder md = new StringBuilder();
        md.append("# Питание на деле — ").append(метка).append("\n\n");
        md.append(String.format(Locale.ROOT, "Свод %s, партий %d, игроков %d, боты %s. Ходов: %d.%n%n",
            свод, партий, игроков, боты, в.ходов));
        md.append("Снимок — в начале хода игрока, до его действий.\n\n");
        md.append("| мерило | значение |\n|---|---:|\n");
        md.append(String.format(Locale.ROOT, "| зданий-потребителей в среднем (добытчики + военные) | %.2f |%n", (double) в.потребителей / в.ходов));
        md.append(String.format(Locale.ROOT, "| из них без энергии | %.2f (%.0f%%) |%n", (double) в.незапитано / в.ходов, 100.0 * в.незапитано / Math.max(1, в.потребителей)));
        md.append(String.format(Locale.ROOT, "| свободных кубиков на источниках | %.2f из %.2f всех |%n", (double) в.свободныхКубиков / в.ходов, (double) в.всегоКубиков / в.ходов));
        md.append(String.format(Locale.ROOT, "| пустых: ни разу не запитанных / потерявших энергию | %d / %d |%n", в.пустыхНиРазу, в.пустыхПотерявших));
        md.append("| пустые по виду | " + в.пустыеПоВиду + " |\n");
        md.append(String.format(Locale.ROOT, "| ячеек энергии у потребителей / кубиков у игрока | %.2f / %.2f |%n", (double) в.ячеекПотребителей / в.ходов, (double) в.кубиковВсего / в.ходов));
        md.append(String.format(Locale.ROOT, "| ходов, когда потребителям нужно больше энергии, чем есть всего | %.0f%% |%n", 100.0 * в.ходовПерестроено / в.ходов));
        md.append(String.format(Locale.ROOT, "| ходов, когда есть здание без энергии | %.0f%% |%n", 100.0 * в.ходовСНезапитанным / в.ходов));
        md.append(String.format(Locale.ROOT, "| ходов, когда ни одно здание не запитано (есть что запитывать) | %.0f%% |%n", 100.0 * в.ходовВсёПусто / в.ходов));
        md.append(String.format(Locale.ROOT, "| ходов, когда свободных кубиков хватает запитать здание | %.0f%% |%n", 100.0 * в.ходовМожноЗапитать / в.ходов));
        md.append(String.format(Locale.ROOT, "| ...и Питание есть на карте хода | %d |%n", в.можноИПитаниеДоступно));
        md.append(String.format(Locale.ROOT, "| ......сыграна перекладка | %d (%.0f%%) |%n", в.можноИПереложил, 100.0 * в.можноИПереложил / Math.max(1, в.можноИПитаниеДоступно)));
        md.append(String.format(Locale.ROOT, "| ......вместо неё построена станция | %d (%.0f%%) |%n", в.можноИПостроилСтанцию, 100.0 * в.можноИПостроилСтанцию / Math.max(1, в.можноИПитаниеДоступно)));
        md.append(String.format(Locale.ROOT, "| добыча при нуле запитанных добытчиков | %d из %d |%n", в.добычПусто, в.добычДействий));
        md.append(String.format(Locale.ROOT, "| выпуск при нуле запитанных военных | %d из %d |%n", в.выпусковПусто, в.выпусков));
        md.append(String.format(Locale.ROOT, "| добыча, не давшая ничего | %d из %d |%n", в.добычНичего, в.добычДействий));
        md.append(String.format(Locale.ROOT, "| выпуск, не давший ничего | %d из %d |%n", в.выпусковНичего, в.выпусков));
        md.append(String.format(Locale.ROOT, "| перекладок, не прибавивших ни одного запитанного здания | %d из %d |%n", в.перекладокБезТолку, в.перекладок));
        md.append("\n## Холостые действия (на столе не изменилось ничего)\n\n| действие | сыграно | холостых |\n|---|---:|---:|\n");
        int[] итого = new int[2];
        в.холостые.forEach((k, v) -> {
            итого[0] += v[0];
            итого[1] += v[1];
            md.append(String.format(Locale.ROOT, "| %s | %d | %d (%.1f%%) |%n", k, v[0], v[1], 100.0 * v[1] / Math.max(1, v[0])));
        });
        md.append(String.format(Locale.ROOT, "| **всего** | %d | %d (%.1f%%) |%n", итого[0], итого[1], 100.0 * итого[1] / Math.max(1, итого[0])));
        md.append("\nПримеры холостых:\n\n");
        for (String п : в.примеры) {
            md.append("- ").append(п).append('\n');
        }
        md.append("\n## Почему добыча не дала ничего\n\n| причина | раз |\n|---|---:|\n");
        в.причины.forEach((k, v) -> md.append("| ").append(k).append(" | ").append(v).append(" |\n"));
        md.append("\n## По раундам (в начале хода)\n\n| раунд | потребителей | без энергии | свободных кубиков |\n|---:|---:|---:|---:|\n");
        for (int r = 1; r < в.ходовПоРаундам.length; r++) {
            long n = в.ходовПоРаундам[r];
            if (n == 0) {
                continue;
            }
            md.append(String.format(Locale.ROOT, "| %d | %.2f | %.2f | %.2f |%n", r,
                (double) в.потрПоРаундам[r] / n, (double) в.незапПоРаундам[r] / n, (double) в.свобПоРаундам[r] / n));
        }
        Path out = Path.of("reports", "боты", "питание-" + метка + ".md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, md, StandardCharsets.UTF_8);
        System.out.println(md);
        System.out.println("записано: " + out.toAbsolutePath());
    }

    static Счёт партия(String свод, int игроков, long сид, List<String> боты, int номер) {
        GameConfig cfg = GameConfig.buildCached(свод, игроков, сид, null, null);
        GameState s = Setup.buildGame(cfg);
        List<Agent> ags = new ArrayList<>();
        for (int i = 0; i < игроков; i++) {
            String id = боты.get((i + номер) % боты.size());
            ags.add(kelium.agents.BotCatalog.create(id, i, new Random(сид * 31 + i), игроков));
        }
        Счёт с = new Счёт();
        // состояние текущего хода
        int[] кто = {-1};
        boolean[] можно = {false};
        boolean[] питаниеНаКарте = {false};
        boolean[] переложил = {false};
        boolean[] станция = {false};
        int[] запДоб = {0};   // запитанных добытчиков перед очередным действием
        int[] запВоен = {0};  // запитанных военных перед очередным действием
        long[] отпечаток = {0};
        java.util.Set<Integer> былоЗапитано = new java.util.HashSet<>();
        Runnable закрыть = () -> {
            if (кто[0] >= 0 && можно[0] && питаниеНаКарте[0]) {
                с.можноИПитаниеДоступно++;
                if (переложил[0]) {
                    с.можноИПереложил++;
                } else if (станция[0]) {
                    с.можноИПостроилСтанцию++;
                }
            }
        };
        GameEngine.playGame(s, ags, ev -> {
            String тип = String.valueOf(ev.get("type"));
            if ("turn_orders".equals(тип)) {
                закрыть.run();
                int seat = ((Number) ev.get("seat")).intValue();
                кто[0] = seat;
                PlayerState p = s.player(seat);
                int[] сн = снимок(p);
                int r = Math.min(s.round, с.ходовПоРаундам.length - 1);
                с.ходов++;
                с.ходовПоРаундам[r]++;
                с.потребителей += сн[0];
                с.потрПоРаундам[r] += сн[0];
                с.незапитано += сн[1];
                с.незапПоРаундам[r] += сн[1];
                с.свободныхКубиков += сн[2];
                с.свобПоРаундам[r] += сн[2];
                с.всегоКубиков += сн[3];
                int ячеек = 0;
                for (BuildingToken b : p.buildingsOnField()) {
                    if (потребитель(b)) {
                        ячеек += b.energySlots;
                        if (b.powered()) {
                            былоЗапитано.add(b.uid);
                        } else {
                            if (былоЗапитано.contains(b.uid)) {
                                с.пустыхПотерявших++;
                            } else {
                                с.пустыхНиРазу++;
                            }
                            с.пустыеПоВиду.merge(b.type.code + "/" + b.energySlots + "яч", 1, Integer::sum);
                        }
                    }
                }
                // ЦУ своё тоже ест: один кубик держит ЦУ запитанным
                int кубы = сн[3];
                с.ячеекПотребителей += ячеек;
                с.кубиковВсего += кубы;
                if (ячеек > кубы - 1) {
                    с.ходовПерестроено++;
                }
                if (сн[1] > 0) {
                    с.ходовСНезапитанным++;
                }
                if (сн[0] > 0 && сн[1] == сн[0]) {
                    с.ходовВсёПусто++;
                }
                можно[0] = сн[1] > 0 && сн[2] >= сн[4];
                if (можно[0]) {
                    с.ходовМожноЗапитать++;
                }
                String top = String.valueOf(ev.get("top"));
                boolean низ = Boolean.TRUE.equals(ev.get("bottom_open"));
                String bottom = String.valueOf(ev.get("bottom"));
                питаниеНаКарте[0] = "settle".equals(top) || "mobilize".equals(top)
                    || (низ && ("settle".equals(bottom) || "mobilize".equals(bottom)));
                переложил[0] = false;
                станция[0] = false;
                запДоб[0] = запитано(p, true);
                запВоен[0] = запитано(p, false);
                отпечаток[0] = kelium.agents.Lookahead.materialSignature(s, seat);
            } else if ("action".equals(тип) && Boolean.TRUE.equals(ev.get("ok"))
                    && !Boolean.TRUE.equals(ev.get("free"))) {
                int seat = ((Number) ev.get("seat")).intValue();
                if (seat != кто[0]) {
                    return;
                }
                String a = String.valueOf(ev.get("action"));
                String fork = String.valueOf(ev.get("fork"));
                PlayerState p = s.player(seat);
                long теперь = kelium.agents.Lookahead.materialSignature(s, seat);
                int[] х = с.холостые.computeIfAbsent(fork + ":" + a, k -> new int[2]);
                х[0]++;
                if (теперь == отпечаток[0]) {
                    х[1]++;
                    if (с.примеры.size() < 30) {
                        с.примеры.add("р" + s.round + " к" + s.circle + " место " + seat + " " + ags.get(seat).name
                            + ": " + fork + ":" + a + " — " + ev.get("detail") + " " + ev.get("telemetry"));
                    }
                }
                отпечаток[0] = теперь;
                if ("energy_swap".equals(a)) {
                    переложил[0] = true;
                    с.перекладок++;
                    int после = запитано(p, true) + запитано(p, false);
                    if (после <= запДоб[0] + запВоен[0]) {
                        с.перекладокБезТолку++;
                    }
                } else if ("power".equals(fork) && "build".equals(a)) {
                    станция[0] = true;
                } else if ("mining".equals(a)) {
                    с.добычДействий++;
                    if (запДоб[0] == 0) {
                        с.добычПусто++;
                    }
                    if (число(ev, "kelium") + число(ev, "containers") == 0) {
                        с.добычНичего++;
                        String причина = число(ev, "miners") == 0 ? "нет добытчиков"
                            : число(ev, "miners_storage_full") > 0 ? "склад полон"
                            : число(ev, "miners_unpowered") >= число(ev, "miners") ? "все без энергии"
                            : число(ev, "miners_no_kelium") > 0 && число(ev, "miners_unpowered") + число(ev, "miners_no_kelium") >= число(ev, "miners") ? "без энергии или жила пуста"
                            : число(ev, "miners_skipped") > 0 ? "бот пропустил добытчик"
                            : "иное";
                        с.причины.merge(причина + (переложил[0] ? " (Питание уже было в ходу)" : ""), 1, Integer::sum);
                    }
                } else if ("assembly".equals(a)) {
                    с.выпусков++;
                    if (запВоен[0] == 0) {
                        с.выпусковПусто++;
                    }
                    if (число(ev, "units") + число(ev, "ammo") == 0) {
                        с.выпусковНичего++;
                    }
                }
                запДоб[0] = запитано(p, true);
                запВоен[0] = запитано(p, false);
            }
        });
        закрыть.run();
        return с;
    }
}
