package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;
import kelium.engine.cards.CardRegistry;
import kelium.engine.cards.EngineCardContext;
import kelium.engine.cards.ObjectiveCard;

/**
 * НЕВЫПОЛНИМЫЕ ЗАДАНИЯ — какие карты не загораются «ГОТОВО» НИ РАЗУ.
 *
 * <p>ЗАЧЕМ ОТДЕЛЬНЫЙ СТЕНД, если есть {@code БлизостьЗаданий}. Тот отвечает на
 * вопрос «докуда доходят карты» и меряет близость у карт В РУКЕ. Этот отвечает
 * на другой вопрос — «а может ли условие стать истинным вообще» — и меряет его у
 * ВСЕХ карт колоды на каждом ходу каждого игрока, независимо от того, у кого
 * карта на руках. Разница принципиальная: карта, которая почти не приходит в
 * руку, в первом замере выглядит редкой, а во втором честно показывает, что её
 * условие не бывает истинным ни у кого никогда.
 *
 * <p>ПОЧЕМУ ЭТО ГЛАВНЫЙ ВИД ПОЛОМКИ. Правила меняются, карты остаются: убрали из
 * Стройки перенос — умерла карта «перенеси два здания»; убрали ответный бой —
 * умерла карта «уничтожь в ответном бою»; запретили наземным входить на гекс с
 * чужими войсками — умерла карта «встань на гекс с войсками противника». Каждая
 * такая карта не просто бесполезна: она ЗАНИМАЕТ РУКУ и её можно только сжечь,
 * то есть тянет вниз ровно то число, которое дизайнер и хочет поднять.
 *
 * <p>Запуск: {@code kelium.НевыполнимыеЗадания [партий] [игроков] [уровень]}.
 * Отчёт: {@code reports/balance/невыполнимые-задания.md}.
 */
public final class НевыполнимыеЗадания {

    private НевыполнимыеЗадания() {
    }

    /** Что накопилось по одной карте колоды. */
    private static final class Карта {
        String имя = "";
        String природа = "";
        long проверок;          // сколько раз спрашивали условие
        long истинно;           // сколько раз оно было истинным
        long истинноУсиленное;
        double лучшаяБлизость;
        long партийСИстиной;    // в скольких партиях условие было истинным хоть раз
        boolean вЭтойПартии;
    }

    /** Наблюдатель: на каждом ходу спрашивает условие ВСЕХ карт колоды. */
    private static final class Опрос extends Agent {
        private final Agent внутри;
        private final Map<String, Карта> итог;
        private final List<String> колода;

        Опрос(Agent внутри, Map<String, Карта> итог, List<String> колода) {
            super(внутри.seat, внутри.name);
            this.внутри = внутри;
            this.итог = итог;
            this.колода = колода;
        }

        @Override
        public Choice choose(GameState state, List<Choice> options, Map<String, Object> ctx) {
            if (ctx != null && "spec".equals(String.valueOf(ctx.getOrDefault("kind", "")))) {
                EngineCardContext cc = new EngineCardContext(state, seat);
                for (String cid : колода) {
                    ObjectiveCard oc = CardRegistry.objective(cid);
                    if (oc == null) {
                        continue;
                    }
                    Карта k = итог.get(cid);
                    if (k == null) {
                        continue;
                    }
                    k.проверок++;
                    try {
                        if (oc.satisfied(cc)) {
                            k.истинно++;
                            if (!k.вЭтойПартии) {
                                k.вЭтойПартии = true;
                                k.партийСИстиной++;
                            }
                            if (oc.satisfiedEnhanced(cc)) {
                                k.истинноУсиленное++;
                            }
                        }
                        double p = oc.progress(cc);
                        if (!Double.isNaN(p)) {
                            k.лучшаяБлизость = Math.max(k.лучшаяБлизость, Math.min(1.0, p));
                        }
                    } catch (RuntimeException сломалась) {
                        // Условие упало на живом столе — это тоже поломка, и она
                        // важнее любой статистики: карта не может быть выполнена.
                        k.имя = k.имя + " [ПАДАЕТ: " + сломалась.getClass().getSimpleName() + "]";
                    }
                }
            }
            return внутри.choose(state, options, ctx);
        }

        @Override public void observeEvent(Map<String, Object> e) {
            внутри.observeEvent(e);
        }

        @Override public void observePublicEvent(Map<String, Object> e) {
            внутри.observePublicEvent(e);
        }
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8);
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 12;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        int level = args.length > 2 ? Integer.parseInt(args[2]) : 2;

        Map<String, Карта> итог = new TreeMap<>();
        List<String> роли = Bots.ROSTER_4;
        for (int g = 0; g < games; g++) {
            long seed = 31000L + g;
            GameConfig cfg = LayoutLibrary.configFor(players, seed);
            GameState s = Setup.buildGame(cfg);
            List<String> колода = new ArrayList<>();
            for (Map<String, Object> e : cfg.content.get("objectives").entries) {
                String cid = String.valueOf(e.get("id"));
                колода.add(cid);
                Карта k = итог.computeIfAbsent(cid, x -> new Карта());
                if (k.имя.isEmpty()) {
                    k.имя = String.valueOf(e.get("name"));
                    k.природа = String.valueOf(e.get("type"));
                }
                k.вЭтойПартии = false;
            }
            List<Agent> agents = new ArrayList<>();
            int shift = (int) (seed % players);
            for (int i = 0; i < players; i++) {
                String ch = роли.get((i + shift) % роли.size());
                agents.add(new Опрос(Bots.create(ch, Bots.Level.of(level), i,
                    new Random(seed * 31 + i), players), итог, колода));
            }
            new GameEngine(s, agents, ev -> { }).run();
            out.printf(Locale.ROOT, "партия %d из %d%n", g + 1, games);
        }

        List<Map.Entry<String, Карта>> строки = new ArrayList<>(итог.entrySet());
        строки.sort((a, b) -> {
            int c = Long.compare(a.getValue().истинно, b.getValue().истинно);
            return c != 0 ? c : a.getKey().compareTo(b.getKey());
        });
        StringBuilder md = new StringBuilder();
        md.append("# Невыполнимые задания — где условие не становится истинным\n\n")
            .append("Свод **").append(GameConfig.DEFAULT_RULESET).append("**, партий **")
            .append(games).append("**, игроков ").append(players)
            .append(", боты уровня ").append(level)
            .append(".\n\nУсловие каждой карты КОЛОДЫ спрашивается у каждого игрока на каждом "
                + "предложении СПЕЦ-действия — независимо от того, у кого карта на руках. "
                + "Карта, у которой «истинно» ноль, не может быть выполнена в этой "
                + "редакции правил никем.\n\n")
            .append("| карта | название | природа | проверок | истинно | из них усиленное | партий с истиной | лучшая близость |\n")
            .append("|---|---|---|---:|---:|---:|---:|---:|\n");
        List<String> мёртвые = new ArrayList<>();
        for (var e : строки) {
            Карта k = e.getValue();
            md.append(String.format(Locale.ROOT, "| %s | %s | %s | %d | %d | %d | %d | %.2f |%n",
                e.getKey(), k.имя, природа(k.природа), k.проверок, k.истинно,
                k.истинноУсиленное, k.партийСИстиной, k.лучшаяБлизость));
            if (k.истинно == 0) {
                мёртвые.add(e.getKey() + " «" + k.имя + "»");
            }
        }
        md.append("\n## Не загорелись ни разу\n\n");
        if (мёртвые.isEmpty()) {
            md.append("Таких карт нет — каждое условие колоды хоть раз было истинным.\n");
        } else {
            for (String м : мёртвые) {
                md.append("- ").append(м).append("\n");
            }
        }
        Path dir = Path.of("reports", "balance");
        Files.createDirectories(dir);
        Path file = dir.resolve("невыполнимые-задания.md");
        Files.writeString(file, md, StandardCharsets.UTF_8);
        out.println();
        out.println("не загорелись ни разу: " + (мёртвые.isEmpty() ? "нет" : мёртвые));
        out.println("отчёт: " + file);
    }

    private static String природа(String type) {
        return switch (type) {
            case "state" -> "состояние";
            case "incident" -> "происшествие";
            case "sacrifice" -> "жертва";
            default -> type;
        };
    }
}
