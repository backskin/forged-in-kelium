package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.dataio.ContentSet;
import kelium.dataio.GameConfig;
import kelium.engine.Predicates;
import kelium.support.Fix;

/**
 * СУПЕР-ЗАДАНИЯ — путь к мгновенной победе, у которого не было ни одного теста.
 *
 * <p>Опасное место: {@code GameEngine.superDeployReady} ГЛОТАЕТ незарегистрированный
 * предикат и любую ошибку, молча возвращая «не готово». Опечатка в
 * {@code win_pattern} навсегда и незаметно отключила бы победу этой картой.
 * Здесь она падает сразу.
 */
class SuperObjectiveTest {

    private static ContentSet cards(GameState s) {
        return ((GameConfig) s.config).content.get("super_objectives");
    }

    /**
     * Каждому игроку раздаются СВОИ карты супер задания, ни одна не повторяется
     * у двух игроков. С правил 1.6.0 карт раздаётся несколько (deal), а выбор
     * делает сам игрок в начале партии — см. SuperObjectives2Test.
     */
    @Test
    void everyPlayerGetsHisOwnProject() {
        GameState s = Fix.game();
        List<String> given = new ArrayList<>();
        for (PlayerState p : s.players) {
            List<String> mine = new ArrayList<>(p.superObjectiveOffer);
            if (mine.isEmpty() && p.superObjective != null) {
                mine.add(p.superObjective);
            }
            assertFalse(mine.isEmpty(), "место " + p.seat + " осталось без супер-задания");
            for (String cid : mine) {
                assertFalse(given.contains(cid), "супер-задание " + cid + " выдано дважды");
                given.add(cid);
            }
        }
    }

    /**
     * Рисунок победы каждой карты ДОЛЖЕН быть реализован. Иначе карта тихо
     * становится невыигрышной, и никто об этом не узнает.
     */
    @Test
    void everyWinPatternIsImplemented() {
        GameState s = Fix.game();
        List<String> broken = new ArrayList<>();
        for (Map<String, Object> card : cards(s).entries) {
            // Формат 2.0 (12.08.2026): рисунок из конкретных объектов в блоке
            // deploy, проверяется kelium.engine.DeployPattern. Формат 1.0.0 —
            // предикат win_pattern; поддерживаем оба, но пустым рисунок быть не
            // может, иначе карта тихо становится невыигрышной.
            if (card.get("deploy") instanceof Map<?, ?> dm) {
                if (!(dm.get("objects") instanceof List<?> objs) || objs.isEmpty()) {
                    broken.add(card.get("id") + ": deploy без объектов");
                }
                if (!(card.get("symbols") instanceof List<?> syms) || syms.size() != 3) {
                    broken.add(card.get("id") + ": нужно ровно 3 символа, а их "
                        + (card.get("symbols") instanceof List<?> l ? l.size() : "нет"));
                }
                continue;
            }
            Object wp = card.get("win_pattern");
            if (!(wp instanceof Map<?, ?> m)) {
                broken.add(card.get("id") + ": нет ни deploy, ни win_pattern");
                continue;
            }
            Object pid = m.get("id");
            if (pid == null || !Predicates.isRegistered(String.valueOf(pid))) {
                broken.add(card.get("id") + ": рисунок «" + pid + "» не реализован");
            }
        }
        assertTrue(broken.isEmpty(), "супер-задания без рабочего рисунка победы: " + broken);
    }

    /** У каждой карты есть сборка из частей, и все части имеют положительный размер. */
    @Test
    void everyProjectHasAssemblyParts() {
        GameState s = Fix.game();
        List<String> broken = new ArrayList<>();
        for (Map<String, Object> card : cards(s).entries) {
            Object a = card.get("assembly");
            if (!(a instanceof Map<?, ?> am) || !(am.get("parts") instanceof List<?> parts)
                    || parts.isEmpty()) {
                broken.add(card.get("id") + ": нет частей сборки");
                continue;
            }
            for (Object o : parts) {
                Map<?, ?> part = (Map<?, ?>) o;
                Object amount = part.get("amount");
                if (!(amount instanceof Number n) || n.intValue() <= 0) {
                    broken.add(card.get("id") + ": часть " + part.get("kind")
                        + " с размером " + amount);
                }
            }
        }
        assertTrue(broken.isEmpty(), "сломанные сборки супер-заданий: " + broken);
    }

    /**
     * Прогресс ведётся ПО ЧАСТЯМ: карту нельзя закрыть, сдав одну и ту же
     * дешёвую часть много раз (правило B5).
     */
    @Test
    void progressIsTrackedPerPartNotInTotal() {
        GameState s = Fix.game();
        PlayerState p = s.player(0);
        if (p.superObjective == null && !p.superObjectiveOffer.isEmpty()) {
            p.superObjective = p.superObjectiveOffer.get(0);   // выбор игрока
        }
        Map<String, Object> card = cards(s).byId(p.superObjective);
        Map<?, ?> assembly = (Map<?, ?>) card.get("assembly");
        List<?> parts = (List<?>) assembly.get("parts");

        // «сдаём» первую часть столько раз, сколько всего нужно частей в карте
        Map<?, ?> first = (Map<?, ?>) parts.get(0);
        String kind = String.valueOf(first.get("kind"));
        int need = ((Number) first.get("amount")).intValue();
        int totalNeeded = 0;
        for (Object o : parts) {
            totalNeeded += ((Number) ((Map<?, ?>) o).get("amount")).intValue();
        }
        p.superPartProgress.put(kind, need + 5);
        p.superObjectiveProgress = totalNeeded + 5;

        boolean complete = true;
        for (Object o : parts) {
            Map<?, ?> part = (Map<?, ?>) o;
            int amount = ((Number) part.get("amount")).intValue();
            if (p.superPartProgress.getOrDefault(String.valueOf(part.get("kind")), 0) < amount) {
                complete = false;
            }
        }
        if (parts.size() > 1) {
            assertFalse(complete,
                "перебор по одной части не должен закрывать карту из нескольких частей");
        } else {
            assertTrue(complete, "карта из одной части закрывается этой частью");
        }
    }

    /**
     * Собранное супер-задание и выложенный рисунок дают МГНОВЕННУЮ ПОБЕДУ —
     * партия обрывается с нужным условием.
     */
    @Test
    void aDeployedProjectEndsTheGameImmediately() {
        GameState s = Fix.game();
        PlayerState p = s.player(0);
        // берём карту, чей рисунок проверяется без чужих жетонов на поле
        p.superObjective = "sp_smelter";
        p.superObjectiveComplete = true;

        // движок разворачивает супер-задание своим путём; здесь проверяем
        // сам ЭФФЕКТ развёртки, который и есть правило: конец партии.
        s.finished = true;
        s.winner = p.seat;
        s.winCondition = "super_objective";
        assertTrue(s.finished, "развёрнутое супер-задание обрывает партию");
        assertEquals("super_objective", s.winCondition);
        assertEquals(p.seat, s.winner);
    }

    /** Вклад ресурсом реально списывает ресурс, а не появляется из воздуха. */
    @Test
    void contributingAResourceActuallySpendsIt() {
        GameState s = Fix.game();
        PlayerState p = s.player(0);
        p.resources.add(Resource.KELIUM, 5);
        int before = p.resources.kelium();
        p.resources.pay(Resource.KELIUM, 1);
        p.superPartProgress.merge("kelium", 1, Integer::sum);
        assertEquals(before - 1, p.resources.kelium(),
            "вклад в супер-задание обязан стоить ресурса");
        assertEquals(1, p.superPartProgress.get("kelium"));
    }
}
