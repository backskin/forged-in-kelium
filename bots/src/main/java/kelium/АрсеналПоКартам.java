package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * КАЖДАЯ КАРТА АРСЕНАЛА ПО ОТДЕЛЬНОСТИ — что берут, ставят, жгут и применяют.
 *
 * <p>ЗАКАЗ ДИЗАЙНЕРА 28.08.2026: сократить колоду арсенала до 28 самых крутых и
 * востребованных карт. Выбирать надо по числам, а не на вкус, а прежние отчёты
 * считали арсенал только в сумме — «взято 1.37 карты на игрока» не говорит,
 * КАКИЕ карты работают.
 *
 * <p>Что считается по каждой карте: сколько раз пришла в руку, сколько раз
 * УСТАНОВЛЕНА под планшет (то есть игрок захотел её постоянную способность),
 * сколько раз сожжена ради верхнего утиля и сколько раз установленная карта
 * реально СРАБОТАЛА спец-применением. Последнее — главный признак: карта,
 * которую ставят и ни разу не применяют, занимает место в колоде зря.
 *
 * <p>Запуск: {@code kelium.АрсеналПоКартам [партий] [игроков] [свод]}
 */
@SuppressWarnings("unchecked")
public final class АрсеналПоКартам {

    private АрсеналПоКартам() {
    }

    private static final class Карта {
        String имя = "";
        String вид = "";
        long пришла;
        long установлена;
        long сожжена;
        long применена;
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        String ruleset = args.length > 2 ? args[2] : GameConfig.DEFAULT_RULESET;
        List<String> пул = List.of("builder:3", "supplier:3", "stalker:3", "punisher:3");

        Map<String, Карта> итог = new TreeMap<>();
        Map<String, Long> поПассивке = new TreeMap<>();

        for (int g = 0; g < games; g++) {
            GameConfig cfg = GameConfig.buildCached(ruleset, players, 33000L + g, null, null);
            GameState s = Setup.buildGame(cfg);
            List<Agent> ags = new ArrayList<>();
            int shift = g % players;
            for (int i = 0; i < players; i++) {
                ags.add(kelium.agents.BotCatalog.create(
                    пул.get((i + shift) % players), i, new Random(i * 331L + g), players));
            }
            GameEngine.playGame(s, ags, ev -> {
                String тип = String.valueOf(ev.get("type"));
                String cid = String.valueOf(ev.get("card"));
                // У события способности поля card НЕТ — ранний выход по нему
                // молча съедал все применения, и колонка стояла нулевой.
                if ("null".equals(cid) && !"ability_spec".equals(тип)) {
                    return;
                }
                switch (тип) {
                    case "arsenal" -> {
                        Карта k = итог.computeIfAbsent(cid, x -> new Карта());
                        String mode = String.valueOf(ev.get("mode"));
                        if (mode.startsWith("install")) {
                            k.установлена++;
                        } else if ("burn".equals(mode)) {
                            k.сожжена++;
                        }
                    }
                    case "arsenal_spec_use" ->
                        итог.computeIfAbsent(cid, x -> new Карта()).применена++;
                    // СПЕЦ через систему способностей: карты арсенала 3.0.0
                    // исполняют себя сами и шлют ability_spec с id пассивки, а
                    // не карты. id карты восстанавливается по каталогу ниже.
                    case "ability_spec" -> {
                        if (Boolean.TRUE.equals(ev.get("did"))) {
                            поПассивке.merge(String.valueOf(ev.get("ability")), 1L, Long::sum);
                        }
                    }
                    default -> { }
                }
            });
            // Сколько раз карта побывала в руке, событием не восстановить: карта
            // приходит с витрины, с обменов науки, с наград и с эффектов, и не
            // все пути шлют событие. Считаем «прошла через игрока» как сумму
            // судеб: установлена или сожжена или осталась в руке к концу.
            for (var p : s.players) {
                for (String cid : p.arsenalHand) {
                    итог.computeIfAbsent(cid, x -> new Карта()).пришла++;
                }
            }
        }

        // Имена, вид и применения-через-способности — из каталога: у события
        // способности стоит id ПАССИВКИ, сопоставляем его карте по bottom.passive.
        GameConfig cfg0 = GameConfig.buildCached(ruleset, players, 1L, null, null);
        for (var e : итог.entrySet()) {
            var card = cfg0.content.get("arsenal").find(e.getKey());
            if (card != null) {
                e.getValue().имя = String.valueOf(card.getOrDefault("name", e.getKey()));
                e.getValue().вид = String.valueOf(card.getOrDefault("kind", "regular"));
                if (card.get("bottom") instanceof Map<?, ?> bm) {
                    String пас = String.valueOf(((Map<String, Object>) bm).get("passive"));
                    e.getValue().применена += поПассивке.getOrDefault(пас, 0L);
                }
            }
        }

        StringBuilder b = new StringBuilder();
        b.append("# Каждая карта арсенала по отдельности\n\n");
        b.append("Свод **").append(ruleset).append("**, партий **").append(games)
            .append("**, игроков ").append(players)
            .append(". Места и характеры ротируются.\n\n");
        b.append("**ТОЛК** = установлена + применена: карта, которую ставят и ")
            .append("применяют, работает; карту, которую только жгут, держит в ")
            .append("колоде один утиль.\n\n");

        List<Map.Entry<String, Карта>> строки = new ArrayList<>(итог.entrySet());
        строки.sort((x, y) -> Long.compare(толк(y.getValue()), толк(x.getValue())));

        b.append("| № | карта | название | вид | установлена | применена | сожжена | ")
            .append("осталась в руке | ТОЛК |\n");
        b.append("|---:|---|---|---|---:|---:|---:|---:|---:|\n");
        int n = 0;
        for (var e : строки) {
            Карта k = e.getValue();
            b.append("| ").append(++n).append(" | ").append(e.getKey())
                .append(" | ").append(k.имя)
                .append(" | ").append("starting".equals(k.вид) ? "стартовая" : "обычная")
                .append(" | ").append(k.установлена)
                .append(" | ").append(k.применена)
                .append(" | ").append(k.сожжена)
                .append(" | ").append(k.пришла)
                .append(" | ").append(толк(k)).append(" |\n");
        }

        Path out = Path.of("reports", "balance", "арсенал-по-картам.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
        System.out.println("карт в отчёте: " + итог.size());
        System.out.println("отчёт: " + out.toAbsolutePath());
    }

    private static long толк(Карта k) {
        return k.установлена + k.применена;
    }

    private static String окр(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
