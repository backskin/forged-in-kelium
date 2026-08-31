package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.engine.cards.CardRegistry;
import kelium.engine.cards.EngineCardContext;
import kelium.engine.cards.ObjectiveCard;

/**
 * ДОКУДА ВООБЩЕ ДОХОДЯТ КАРТЫ ЗАДАНИЙ — распределение максимальной близости.
 *
 * <p>ЗАЧЕМ. Требование дизайнера — пять-шесть выполненных заданий за партию,
 * выходит два с половиной. Наведение по заданиям подключено ко всем точкам
 * выбора и ИЗМЕРИМО работает: 25 тысяч попаданий, средний прирост близости 0.42.
 * Сила преследования перебрана отдельным стендом: выполнение растёт до веса 3 и
 * дальше ПАДАЕТ. Значит упор не в желание бота, и надо посмотреть на карты.
 *
 * <p>ЧТО СЧИТАЕТСЯ. Пока карта лежит в руке, на каждом ходу считается её
 * близость к выполнению ({@code ObjectiveCard.progress}) и запоминается
 * НАИБОЛЬШАЯ за всё время. Дальше карты раскладываются по полкам близости.
 * Разница принципиальная и лечится по-разному:
 * <ul>
 *   <li>карта застряла на нуле — условие для бота недостижимо в принципе
 *       (или его не за что зацепить: близость не считается, только «да/нет»);</li>
 *   <li>карта доходит до половины и глохнет — условию нужен многоходовый
 *       замысел, которого у бота нет;</li>
 *   <li>карта доходит почти до единицы — не хватает одного шага, и это
 *       чинится наведением или ценой действия.</li>
 * </ul>
 *
 * <p>Запуск: {@code kelium.БлизостьЗаданий [партий] [игроков] [свод]}
 */
public final class БлизостьЗаданий {

    private БлизостьЗаданий() {
    }

    /** Что накопилось по одной карте. */
    private static final class Карта {
        String имя = "";
        long вРуке;              // сколько раз карта попадала в руку
        long выполнена;
        double суммаМакс;        // сумма наибольших близостей по каждому попаданию
        long нольВсегда;         // попаданий, где близость так и осталась нулевой
        long почтиГотова;        // попаданий, где близость доходила до 0.75+
        double лучшая;           // рекорд по всем партиям
    }

    /** Наблюдатель: на каждом решении меряет близость карт в руке. */
    private static final class Наблюдатель extends Agent {
        private final Agent внутри;
        private final Map<String, Double> максВРуке;   // карта -> лучшая близость
        private final Map<String, Карта> итог;

        Наблюдатель(Agent внутри, Map<String, Double> максВРуке, Map<String, Карта> итог) {
            super(внутри.seat, внутри.name);
            this.внутри = внутри;
            this.максВРуке = максВРуке;
            this.итог = итог;
        }

        @Override
        public Choice choose(GameState state, List<Choice> options,
                             Map<String, Object> context) {
            if (context != null && "spec".equals(String.valueOf(context.get("kind")))) {
                EngineCardContext ctx = new EngineCardContext(state, seat);
                for (String cid : state.player(seat).objectiveHand) {
                    ObjectiveCard oc = CardRegistry.objective(cid);
                    if (oc == null) {
                        continue;
                    }
                    double p;
                    try {
                        p = oc.progress(ctx);
                    } catch (RuntimeException e) {
                        continue;
                    }
                    if (Double.isNaN(p)) {
                        continue;
                    }
                    p = Math.max(0.0, Math.min(1.0, p));
                    String ключ = seat + ":" + cid;
                    максВРуке.merge(ключ, p, Math::max);
                }
            }
            return внутри.choose(state, options, context);
        }

        @Override
        public void observeEvent(Map<String, Object> event) {
            внутри.observeEvent(event);
        }

        @Override
        public void observePublicEvent(Map<String, Object> event) {
            внутри.observePublicEvent(event);
        }
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        String ruleset = args.length > 2 ? args[2] : GameConfig.DEFAULT_RULESET;
        List<String> пул = List.of("builder:3", "supplier:3", "stalker:3", "punisher:3");

        Map<String, Карта> итог = new TreeMap<>();
        long[] полки = new long[5];      // 0 · (0;0.25] · (0.25;0.5] · (0.5;0.75] · (0.75;1)
        long готовых = 0;

        for (int g = 0; g < games; g++) {
            GameConfig cfg = GameConfig.buildCached(ruleset, players, 77000L + g, null, null);
            GameState s = Setup.buildGame(cfg);
            Map<String, Double> макс = new HashMap<>();
            Map<String, Boolean> выполнена = new HashMap<>();
            List<Agent> ags = new ArrayList<>();
            int shift = g % players;
            for (int i = 0; i < players; i++) {
                ags.add(new Наблюдатель(kelium.agents.BotCatalog.create(
                    пул.get((i + shift) % players), i, new Random(i * 771L + g), players),
                    макс, итог));
            }
            GameEngine.playGame(s, ags, ev -> {
                if ("objective".equals(String.valueOf(ev.get("type")))) {
                    выполнена.put(ev.get("seat") + ":" + ev.get("card"), Boolean.TRUE);
                }
            });
            for (var e : макс.entrySet()) {
                String cid = e.getKey().substring(e.getKey().indexOf(':') + 1);
                Карта k = итог.computeIfAbsent(cid, x -> new Карта());
                if (k.имя.isEmpty()) {
                    var card = cfg.content.get("objectives").find(cid);
                    k.имя = card == null ? cid : String.valueOf(card.get("name"));
                }
                double p = e.getValue();
                k.вРуке++;
                k.суммаМакс += p;
                k.лучшая = Math.max(k.лучшая, p);
                if (p <= 0.0) {
                    k.нольВсегда++;
                }
                if (p >= 0.75) {
                    k.почтиГотова++;
                }
                if (Boolean.TRUE.equals(выполнена.get(e.getKey()))) {
                    k.выполнена++;
                }
                if (p >= 1.0) {
                    готовых++;
                    полки[4]++;
                } else if (p > 0.75) {
                    полки[4]++;
                } else if (p > 0.5) {
                    полки[3]++;
                } else if (p > 0.25) {
                    полки[2]++;
                } else if (p > 0.0) {
                    полки[1]++;
                } else {
                    полки[0]++;
                }
            }
        }

        long всего = 0;
        for (long v : полки) {
            всего += v;
        }

        StringBuilder b = new StringBuilder();
        b.append("# Докуда доходят карты заданий\n\n");
        b.append("Свод **").append(ruleset).append("**, партий **").append(games)
            .append("**, игроков ").append(players)
            .append(". Места и характеры ротируются.\n\n");
        b.append("Близость — то же число, которым пользуется наведение ботов: ")
            .append("0 «даже не начинал», 1 «готово». По каждому попаданию карты ")
            .append("в руку берётся НАИБОЛЬШАЯ близость за всё время, что она там ")
            .append("пролежала.\n\n");

        b.append("## Куда доходят все карты вместе\n\n");
        b.append("| полка близости | попаданий в руку, шт | доля |\n|---|---:|---:|\n");
        String[] имена = {"0 — с места не сдвинулась", "до 0.25", "0.25–0.5",
            "0.5–0.75", "0.75 и выше"};
        for (int i = 0; i < полки.length; i++) {
            b.append("| ").append(имена[i]).append(" | ").append(полки[i])
                .append(" | ").append(проц(полки[i], всего)).append(" |\n");
        }
        b.append("\nИз них дошли ровно до единицы (ГОТОВО): **").append(готовых)
            .append("**, это ").append(проц(готовых, всего)).append(" всех попаданий.\n");

        b.append("\n## По каждой карте\n\n");
        b.append("| карта | название | в руке, раз | средняя лучшая близость | ")
            .append("рекорд | застряла на нуле | доходила до 0.75+ | выполнена |\n");
        b.append("|---|---|---:|---:|---:|---:|---:|---:|\n");
        List<Map.Entry<String, Карта>> строки = new ArrayList<>(итог.entrySet());
        строки.sort((x, y) -> Double.compare(
            x.getValue().суммаМакс / Math.max(1, x.getValue().вРуке),
            y.getValue().суммаМакс / Math.max(1, y.getValue().вРуке)));
        for (var e : строки) {
            Карта k = e.getValue();
            b.append("| ").append(e.getKey()).append(" | ").append(k.имя)
                .append(" | ").append(k.вРуке)
                .append(" | ").append(окр(k.суммаМакс / Math.max(1, k.вРуке)))
                .append(" | ").append(окр(k.лучшая))
                .append(" | ").append(проц(k.нольВсегда, k.вРуке))
                .append(" | ").append(проц(k.почтиГотова, k.вРуке))
                .append(" | ").append(k.выполнена).append(" |\n");
        }

        b.append("\n## Как читать\n\n");
        b.append("**Застряла на нуле** — условие для бота недостижимо в принципе, ")
            .append("либо карта не считает близость и отвечает только «да/нет»: ")
            .append("наводить бота на такую карту нечем.\n\n");
        b.append("**Доходит до середины и глохнет** — условию нужен многоходовый ")
            .append("замысел; одним действием его не закрыть, а замысла у бота нет.\n\n");
        b.append("**Доходит до 0.75 и выше, но не выполнена** — не хватает ")
            .append("последнего шага. Вот это чинится наведением и ценой действий.\n");

        Path out = Path.of("reports", "balance", "близость-заданий.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
        System.out.println("попаданий в руку: " + всего
            + ", застряло на нуле: " + проц(полки[0], всего)
            + ", дошло до готового: " + проц(готовых, всего));
        System.out.println("отчёт: " + out.toAbsolutePath());
    }

    private static String проц(long часть, long всего) {
        return всего == 0 ? "—"
            : String.format(Locale.ROOT, "%.1f%%", 100.0 * часть / всего);
    }

    private static String окр(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
