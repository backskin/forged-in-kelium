package kelium.agents;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.Ctx;
import kelium.engine.GameEngine;

/**
 * ДОИГРЫВАНИЕ ДО КОНЦА ПАРТИИ — оценка варианта настоящим итогом, а не формулой
 * (23.09.2026).
 *
 * <p>Прежние боты судили о позиции обученной формулой с весами (геном). Лог
 * партии показал, во что это вылилось: пасы при живых возможностях, «Стройка —
 * ничего не построил», Наука без трофеев. Формула не знает, чем кончится
 * партия, — доигрывание знает. Движок это позволяет: партия быстрой политикой
 * проигрывается за ~90 мс, копия стола — за 0,06 мс (kelium.СкоростьСимуляции).
 *
 * <p>За всех игроков в доигрывании играет быстрая эвристика ({@link HeuristicAgent}):
 * она слабая, но одинаково слабая за всех, и сравнение вариантов между собой
 * остаётся честным — варианты сравниваются на ОДНИХ И ТЕХ ЖЕ сидах.
 */
public final class Доигрывание {

    private Доигрывание() {
    }

    /**
     * Доиграть партию с копии {@code s} и вернуть итог глазами {@code seat}:
     * отрыв от сильнейшего соперника плюс премия за победу
     * ({@link Lookahead#finalScore}).
     *
     * @param forced          что моё место обязано выбрать первым решением
     *                        этого вида (карта приказа); {@code null} — ничего
     * @param послеМоегоХода  копия снята ПОСЛЕ моего хода: доигрываем со
     *                        следующего круга, чтобы не сыграть его второй раз
     */
    public static double доКонца(GameState s, int seat, ForcedAgent.Forced forced,
                                 boolean послеМоегоХода, long seed) {
        GameState c = s.deepCopy(seed);
        перемешатьСкрытое(c, seat, new Random(seed ^ 0x5DEECE66DL));
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < c.numPlayers(); i++) {
            agents.add(new HeuristicAgent(i, new Random(seed * 31 + i + 7), "balanced"));
        }
        if (forced != null) {
            agents.set(seat, new ForcedAgent(agents.get(seat), forced));
        }
        if (послеМоегоХода) {
            c.circle = c.circle + 1;
        }
        GameEngine engine = new GameEngine(c, agents, null);
        try {
            engine.resume();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
        return Lookahead.finalScore(c, seat);
    }

    /**
     * НЕ ПОДГЛЯДЫВАТЬ. Копия стола содержит то, чего игрок не видит: порядок
     * колод и закрытые карты соперников (задания и арсенал в руке). Перед
     * доигрыванием всё это возвращается в колоды, колоды мешаются, и соперникам
     * раздаётся столько же карт наугад. Бот знает, СКОЛЬКО у соперника карт, но
     * не КАКИЕ, — ровно как игрок за столом.
     */
    static void перемешатьСкрытое(GameState c, int seat, Random rng) {
        kelium.core.Deck задания = c.decks.get("objectives");
        kelium.core.Deck арсенал = c.decks.get("arsenal");
        int[] заданийУ = new int[c.numPlayers()];
        int[] арсеналаУ = new int[c.numPlayers()];
        for (kelium.core.PlayerState p : c.players) {
            if (p.seat == seat) {
                continue;
            }
            if (задания != null) {
                заданийУ[p.seat] = p.objectiveHand.size();
                задания.drawPile.addAll(p.objectiveHand);
                p.objectiveHand.clear();
            }
            if (арсенал != null) {
                арсеналаУ[p.seat] = p.arsenalHand.size();
                арсенал.drawPile.addAll(p.arsenalHand);
                p.arsenalHand.clear();
            }
        }
        for (kelium.core.Deck d : c.decks.values()) {
            java.util.Collections.shuffle(d.drawPile, rng);
        }
        for (kelium.core.PlayerState p : c.players) {
            if (p.seat == seat) {
                continue;
            }
            for (int k = 0; k < заданийУ[p.seat] && задания != null && !задания.drawPile.isEmpty(); k++) {
                p.objectiveHand.add(задания.drawPile.remove(задания.drawPile.size() - 1));
            }
            for (int k = 0; k < арсеналаУ[p.seat] && арсенал != null && !арсенал.drawPile.isEmpty(); k++) {
                p.arsenalHand.add(арсенал.drawPile.remove(арсенал.drawPile.size() - 1));
            }
        }
    }

    /** Сколько кругов в раунде — чтобы знать, есть ли куда доигрывать. */
    static int кругов(GameState s) {
        return Ctx.rules(s).getInt("rounds.circles_per_round");
    }
}
