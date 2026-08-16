package kelium;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * КАРТЫ ДОЛЖНЫ ИГРАТЬСЯ, А НЕ ЛЕЖАТЬ.
 *
 * <p>Заказ дизайнера 14.08.2026: «надо именно искать метрику, проверку в тестах,
 * что и задания выполняются, и арсенал используется». До сих пор такой проверки
 * не было: отчёты считали «заданий за партию N», не разделяя ВЫПОЛНЕННЫЕ и
 * СОЖЖЁННЫЕ ради верхнего эффекта, — а это разные вещи, и средним их не отличить.
 *
 * <p>Тесты здесь — СТОРОЖА, а не цель. Пороги поставлены заметно ниже
 * измеренного, чтобы ловить обвал, а не шум выборки. Измерено 14.08.2026 на
 * 300 партиях вчетвером (стенд {@code kelium.CardUsage}):
 * задания выполнено 1.58 · сожжено 7.63 (доля толку 17%);
 * арсенал установлено 0.42 · сожжено 0.76 · СПЕЦ применён 1.11 (доля толку 36%).
 *
 * <p><b>Цель дизайнера этими числами НЕ достигнута</b> — 17% доли толку по
 * заданиям означает, что задание для бота почти всегда топливо, а не цель. Тест
 * не требует цели: он не даёт стать ХУЖЕ, пока цель не взята работой над
 * правилами и мотивацией ботов.
 */
class CardUsageTest {

    /** Сколько партий гоняем в тесте — хватает, чтобы поймать обвал в ноль. */
    private static final int GAMES = 12;
    private static final int PLAYERS = 4;

    private record Usage(int objDone, int objBurn, int arsInstall, int arsBurn,
                         int arsSpec, int arsenalDeck, int arsenalFile) { }

    private static Usage play() {
        int[] c = new int[5];
        int deck = -1;
        int file = -1;
        for (int g = 0; g < GAMES; g++) {
            long seed = 7000L + g;
            GameConfig cfg = LayoutLibrary.configFor(PLAYERS, seed);
            GameState s = Setup.buildGame(cfg);
            if (deck < 0) {
                deck = s.decks.get("arsenal").size();
                file = cfg.content.get("arsenal").entries.size();
            }
            List<Agent> agents = new ArrayList<>();
            List<String> lineup = List.of("hawk", "dove", "balanced", "opportunist");
            for (int i = 0; i < PLAYERS; i++) {
                agents.add(Bots.create(lineup.get(i % lineup.size()), i,
                    new Random(seed * 31 + i), PLAYERS));
            }
            new GameEngine(s, agents, ev -> {
                switch (String.valueOf(ev.get("type"))) {
                    case "objective" -> c[0]++;
                    case "objective_burn" -> c[1]++;
                    case "arsenal" -> {
                        if ("install".equals(ev.get("mode"))) {
                            c[2]++;
                        } else if ("burn".equals(ev.get("mode"))) {
                            c[3]++;
                        }
                    }
                    // Оба пути применения установленной карты: старый белый список
                    // (arsenal_spec_use) и реестр способностей (ability_spec).
                    case "arsenal_spec_use", "ability_spec" -> {
                        if (!Boolean.FALSE.equals(ev.get("did"))) {
                            c[4]++;
                        }
                    }
                    default -> { }
                }
            }).run();
        }
        return new Usage(c[0], c[1], c[2], c[3], c[4], deck, file);
    }

    /**
     * ГЛАВНЫЙ СТОРОЖ КОЛОДЫ. {@code Setup.cullUnimplemented} МОЛЧА изымает карты,
     * чью пассивку движок не умеет. Из-за этого колоду можно «подключить», не
     * реализовав, и не заметить: карты просто не придут, а отчёт покажет, что
     * боты ими не пользуются. Так и вышло с арсеналом 2.0.0 (диктовка дизайнера):
     * до колоды доходят 3 карты из 24. Тест требует, чтобы набор, назначенный
     * СВОДОМ, был играбелен хотя бы на три четверти.
     */
    @Test
    void arsenalDeckAssignedByRulesetIsActuallyPlayable() {
        Usage u = play();
        double share = u.arsenalFile() == 0 ? 0 : (double) u.arsenalDeck() / u.arsenalFile();
        assertTrue(share >= 0.75,
            "до колоды дошло " + u.arsenalDeck() + " карт арсенала из " + u.arsenalFile()
            + " в наборе (" + Math.round(share * 100) + "%). Карты с нереализованными "
            + "пассивками изымаются молча — набор подключён, но не реализован");
    }

    /** Задания должны ВЫПОЛНЯТЬСЯ, а не только сжигаться ради верхнего эффекта. */
    @Test
    void objectivesAreCompletedNotOnlyBurned() {
        Usage u = play();
        assertTrue(u.objDone() > 0,
            "за " + GAMES + " партий не выполнено НИ ОДНОГО задания (сожжено "
            + u.objBurn() + ")");
        double perGame = u.objDone() / (double) GAMES;
        // ПОРОГ ПЕРЕСМОТРЕН 16.08.2026. Норма 14.08.2026 (6.3, порог 3.0) была на
        // каталоге objectives 1.5.0. Сейчас действует 1.7.0 («КАТАЛОГ 9.0: 40
        // карт, ровно половина про войну») — по устройству игры труднее: часть
        // лёгких «жертвенных» карт заменена боевыми условиями, а бой в партии
        // редок (см. forged-game-length: военная победа ~1%). Разбирался 16.08:
        // НАШЁЛ И ПОЧИНИЛ реальный баг — requirementMet проверял id предиката
        // только по старому реестру Predicates, а условия карт с {@code
        // checked_by: card} (модуль cards, o41–o44) в нём не регистрируются —
        // такие карты НИКОГДА не считались выполненными (0 из 584 попыток).
        // После починки (Objectives.cardRequirementMet) — 1.00 выполнено за
        // партию (было 0.83), порог ниже измеренного, чтобы ловить обвал.
        assertTrue(perGame >= 0.5,
            "выполнено заданий за партию " + String.format("%.2f", perGame)
            + " (на 4 игроков), измеренная норма 16.08.2026 — 1.00; порог 0.5");
    }

    /** Установленные карты арсенала должны реально применяться в игре. */
    @Test
    void installedArsenalCardsAreUsed() {
        Usage u = play();
        assertTrue(u.arsInstall() > 0,
            "за " + GAMES + " партий не установлено НИ ОДНОЙ карты арсенала (сожжено "
            + u.arsBurn() + ")");
        assertTrue(u.arsSpec() > 0,
            "установленные карты арсенала не применялись НИ РАЗУ: install="
            + u.arsInstall() + ", spec=0. Проверить оба пути — белый список "
            + "GameEngine.installedSpecPassive и реестр kelium.engine.ability");
    }
}
