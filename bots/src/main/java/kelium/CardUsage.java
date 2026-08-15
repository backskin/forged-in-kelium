package kelium;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * CardUsage — ИГРАЮТ ЛИ БОТЫ КАРТАМИ ИЛИ ПРОСТО ЖГУТ ИХ?
 *
 * <p>Вопрос дизайнера 14.08.2026: «если боты чаще сжигают задания и арсенал, чем
 * выполняют и используют, значит их надо мотивировать». До сих пор этого никто не
 * считал: в отчётах было «заданий за партию N», но не было РАЗДЕЛЕНИЯ на
 * выполненные и сожжённые ради верхнего эффекта. Средним по палате нельзя
 * отличить игру картой от избавления от карты.
 *
 * <p>Что считается (события движка, а не догадки):
 * <ul>
 *   <li>задания: {@code objective_drawn} · {@code objective} (ВЫПОЛНЕНО) ·
 *       {@code objective_burn} (сожжено ради верха) · осталось на руке в конце;</li>
 *   <li>арсенал: {@code arsenal} mode=install (УСТАНОВЛЕНО) · mode=burn (утиль) ·
 *       {@code arsenal_spec_use} (СПЕЦ-действие с установленной карты).</li>
 * </ul>
 *
 * <p>Главные два числа — ДОЛЯ ТОЛКУ: сколько карт сыграно по назначению из всех,
 * с которыми что-то произошло. Установленная карта арсенала, которой ни разу не
 * воспользовались СПЕЦ-действием, тоже отмечается: пассивка может работать сама,
 * но карта без единого применения — повод посмотреть на неё отдельно.
 *
 * <p>ВАЖНО ПРО ВЕРСИИ КОЛОД. Третий и четвёртый аргументы позволяют взять другие
 * версии наборов, не трогая свод правил. Это нужно, потому что на диске лежат
 * колоды новее тех, что играет свод 1.7.0 (арсенал 2.0.0 — диктовка дизайнера,
 * задания 1.6.0 — с заданиями-рисунками). Стенд ПЕЧАТАЕТ, сколько карт из набора
 * дошло до колоды: {@code Setup.cullUnimplemented} молча изымает карты, чью
 * пассивку движок ещё не умеет, и без этой строки «колода не работает» выглядело
 * бы как «боты не берут карты».
 *
 * <p>Запуск: {@code kelium.CardUsage [игроков] [партий] [версия арсенала] [версия заданий]}.
 */
public final class CardUsage {

    private CardUsage() {
    }

    private static final List<String> LINEUP =
        List.of("hawk", "dove", "balanced", "opportunist");

    /** Счётчики по одному характеру. */
    private static final class Tally {
        int games;
        int objDrawn;
        int objDone;
        int objBurn;
        int objLeftInHand;
        int arsGot;
        int arsInstall;
        int arsBurn;
        int arsSpec;

        double per(int v) {
            return games == 0 ? 0 : (double) v / games;
        }

        /** Доля толку по заданиям: выполнено из всего, что с картой случилось. */
        double objUseful() {
            int acted = objDone + objBurn;
            return acted == 0 ? 0 : 100.0 * objDone / acted;
        }

        /** Доля толку по арсеналу: установлено из всего, что с картой случилось. */
        double arsUseful() {
            int acted = arsInstall + arsBurn;
            return acted == 0 ? 0 : 100.0 * arsInstall / acted;
        }
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 300;
        String arsenalVer = args.length > 2 && !"-".equals(args[2]) ? args[2] : null;
        String objectiveVer = args.length > 3 && !"-".equals(args[3]) ? args[3] : null;

        GameConfig.pickContentVersion("arsenal", arsenalVer);
        GameConfig.pickContentVersion("objectives", objectiveVer);

        Map<String, Tally> byChar = new LinkedHashMap<>();
        for (String c : LINEUP) {
            byChar.put(c, new Tally());
        }

        // Сколько карт НАБОРА реально дошло до колоды. Считается один раз на
        // подготовленной партии: если движок изъял половину набора, все дальнейшие
        // числа надо читать с этой поправкой, иначе вывод будет ложным.
        int arsenalInDeck = -1;
        int arsenalInFile = -1;
        int objectivesInDeck = -1;
        int objectivesInFile = -1;

        for (int g = 0; g < games; g++) {
            long seed = 5000L + g;
            GameConfig cfg = LayoutLibrary.configFor(players, seed);
            GameState s = Setup.buildGame(cfg);
            if (arsenalInDeck < 0) {
                arsenalInDeck = s.decks.get("arsenal").size();
                arsenalInFile = cfg.content.get("arsenal").entries.size();
                objectivesInDeck = s.decks.get("objectives").size();
                objectivesInFile = cfg.content.get("objectives").entries.size();
            }

            List<Agent> agents = new ArrayList<>();
            List<String> chars = new ArrayList<>();
            int shift = (int) (seed % players);
            for (int i = 0; i < players; i++) {
                String ch = LINEUP.get((i + shift) % LINEUP.size());
                chars.add(ch);
                agents.add(Bots.create(ch, i, new Random(seed * 31 + i), players));
            }

            new GameEngine(s, agents, ev -> {
                Object seatObj = ev.get("seat");
                if (!(seatObj instanceof Number sn)) {
                    return;
                }
                Tally t = byChar.get(chars.get(sn.intValue()));
                switch (String.valueOf(ev.get("type"))) {
                    case "objective_drawn" -> t.objDrawn++;
                    case "objective" -> t.objDone++;
                    case "objective_burn" -> t.objBurn++;
                    // ДВА РАЗНЫХ ПУТИ, считать надо ОБА. Старый (arsenal_spec_use)
                    // ведёт через белый список из четырёх имён пассивок в
                    // GameEngine.installedSpecPassive — в колоде 1.3.0 таких имён
                    // нет, поэтому он мёртв. Новый (ability_spec) — способность
                    // сама кладёт вариант в меню СПЕЦ. Считая только старый, я
                    // получил «СПЕЦ 0.00 за 300 партий» и чуть не объявил, что
                    // боты не пользуются установленными картами.
                    case "arsenal_spec_use", "ability_spec" -> {
                        if (!Boolean.FALSE.equals(ev.get("did"))) {
                            t.arsSpec++;
                        }
                    }
                    case "arsenal" -> {
                        if ("install".equals(ev.get("mode"))) {
                            t.arsInstall++;
                        } else if ("burn".equals(ev.get("mode"))) {
                            t.arsBurn++;
                        }
                    }
                    default -> { }
                }
            }).run();

            for (int i = 0; i < players; i++) {
                Tally t = byChar.get(chars.get(i));
                t.games++;
                t.objLeftInHand += s.player(i).objectiveHand.size();
                t.arsGot += s.player(i).arsenalHand.size();
            }
        }

        Tally all = new Tally();
        for (Tally t : byChar.values()) {
            all.games += t.games;
            all.objDrawn += t.objDrawn;
            all.objDone += t.objDone;
            all.objBurn += t.objBurn;
            all.objLeftInHand += t.objLeftInHand;
            all.arsGot += t.arsGot;
            all.arsInstall += t.arsInstall;
            all.arsBurn += t.arsBurn;
            all.arsSpec += t.arsSpec;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Играют ли боты картами или просто жгут их\n\n");
        sb.append(String.format(Locale.ROOT,
            "По %d партий, %d игрока, раскладки дизайнера. За столом %s, места ротируются.%n%n",
            games, players, LINEUP));
        sb.append("Набор арсенала: **").append(arsenalVer == null ? "как в своде" : arsenalVer)
          .append("**, набор заданий: **")
          .append(objectiveVer == null ? "как в своде" : objectiveVer).append("**.\n\n");
        sb.append(String.format(Locale.ROOT,
            "**Дошло до колоды:** арсенал %d карт из %d в файле, задания %d из %d. "
            + "Разницу изымает `Setup.cullUnimplemented` — карты, чью пассивку движок "
            + "ещё не умеет. Если изъято много, все числа ниже относятся к остатку.%n%n",
            arsenalInDeck, arsenalInFile, objectivesInDeck, objectivesInFile));

        sb.append("## Задания\n\n");
        sb.append("| характер | получено | ВЫПОЛНЕНО | сожжено ради верха "
            + "| осталось на руке | доля толку |\n");
        sb.append("|---|---:|---:|---:|---:|---:|\n");
        for (var e : byChar.entrySet()) {
            Tally t = e.getValue();
            sb.append(String.format(Locale.ROOT,
                "| %s | %.2f | **%.2f** | %.2f | %.2f | **%.0f%%** |%n",
                e.getKey(), t.per(t.objDrawn), t.per(t.objDone), t.per(t.objBurn),
                t.per(t.objLeftInHand), t.objUseful()));
        }
        sb.append(String.format(Locale.ROOT,
            "| **всего** | %.2f | **%.2f** | %.2f | %.2f | **%.0f%%** |%n",
            all.per(all.objDrawn), all.per(all.objDone), all.per(all.objBurn),
            all.per(all.objLeftInHand), all.objUseful()));

        sb.append("\n## Арсенал\n\n");
        sb.append("| характер | на руке в конце | УСТАНОВЛЕНО | сожжено на утиль "
            + "| СПЕЦ применён | доля толку |\n");
        sb.append("|---|---:|---:|---:|---:|---:|\n");
        for (var e : byChar.entrySet()) {
            Tally t = e.getValue();
            sb.append(String.format(Locale.ROOT,
                "| %s | %.2f | **%.2f** | %.2f | %.2f | **%.0f%%** |%n",
                e.getKey(), t.per(t.arsGot), t.per(t.arsInstall), t.per(t.arsBurn),
                t.per(t.arsSpec), t.arsUseful()));
        }
        sb.append(String.format(Locale.ROOT,
            "| **всего** | %.2f | **%.2f** | %.2f | %.2f | **%.0f%%** |%n",
            all.per(all.arsGot), all.per(all.arsInstall), all.per(all.arsBurn),
            all.per(all.arsSpec), all.arsUseful()));

        sb.append("\n## Как читать\n\n");
        sb.append("- **доля толку** — сколько карт сыграно по назначению из всех, с "
            + "которыми вообще что-то произошло (выполнено против сожжено). Ниже 50% "
            + "значит, что карта для бота — источник разового эффекта, а не цель.\n");
        sb.append("- **осталось на руке** — карты, до которых так и не дошли руки. "
            + "Большое число при малом «выполнено» значит, что требования недостижимы, "
            + "а не что бот ленив.\n");
        sb.append("- **СПЕЦ применён** сильно меньше «установлено» — установленные "
            + "карты стоят мёртвым грузом: пассивка работает, но активной игры картой нет.\n");

        String report = sb.toString();
        System.out.println();
        System.out.println(report);
        Path out = Path.of("reports", "balance", "карты-в-деле-" + players + "p.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report, java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("отчёт: " + out.toAbsolutePath());
    }
}
