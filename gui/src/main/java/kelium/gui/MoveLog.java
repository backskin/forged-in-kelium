package kelium.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;

/**
 * ЛЕНТА РЕШЕНИЙ ПАРТИИ — из чего складывается сохранение.
 *
 * <p>Партию не обязательно хранить целиком: движок ВОСПРОИЗВОДИМ. Один и тот же
 * сид, та же раскладка и те же принятые решения дают ровно ту же партию — на
 * этом стоит вся сверка спорных ситуаций в проекте. Поэтому сохранение — это
 * настройки стола плюс НОМЕРА ВЫБРАННЫХ ОПЦИЙ по порядку, а не слепок сотен
 * полей состояния, который пришлось бы чинить после каждого нового правила.
 *
 * <p>Даёт это ещё и полный журнал: загруженная партия проигрывается с первого
 * хода, и в её записи есть всё, что было до сохранения, — а журнал по заказу
 * приоритет номер один.
 */
public final class MoveLog {

    private MoveLog() {
    }

    /**
     * ПИШУЩАЯ ОБЁРТКА: пропускает решение настоящего агента и запоминает номер
     * выбранной опции. Номер, а не саму опцию: движок строит список опций сам, и
     * при повторе он будет тот же — значит хранить достаточно указателя в него.
     */
    public static final class Recording extends Agent {

        private final Agent inner;
        private final List<Integer> log;

        public Recording(Agent inner, List<Integer> log) {
            super(inner.seat, inner.name);
            this.inner = inner;
            this.log = log;
        }

        @Override
        public Choice choose(GameState state, List<Choice> options, Map<String, Object> context) {
            Choice picked = inner.choose(state, options, context);
            int idx = options.indexOf(picked);
            synchronized (log) {
                log.add(idx);
            }
            return picked;
        }

        @Override
        public void observeEvent(Map<String, Object> event) {
            inner.observeEvent(event);
        }

        @Override
        public void observePublicEvent(Map<String, Object> event) {
            inner.observePublicEvent(event);
        }

        /** Настоящий агент под обёрткой. */
        public Agent inner() {
            return inner;
        }
    }

    /**
     * ПРОИГРЫВАЮЩАЯ ОБЁРТКА: пока лента не кончилась, отвечает записанным
     * номером, дальше — спрашивает настоящего агента. Так загруженная партия
     * доезжает до места сохранения и продолжается живой игрой.
     *
     * <p>Если записанный номер не подходит к списку опций, партия НЕ
     * продолжается наугад: это значит, что сохранение и правила разошлись, и
     * молча доигрывать не ту партию нельзя.
     */
    public static final class Playback extends Agent {

        private final Agent inner;
        private final List<Integer> log;
        private final int[] cursor;
        private final Runnable onCaughtUp;
        private boolean announced;

        public Playback(Agent inner, List<Integer> log, int[] cursor, Runnable onCaughtUp) {
            super(inner.seat, inner.name);
            this.inner = inner;
            this.log = log;
            this.cursor = cursor;
            this.onCaughtUp = onCaughtUp;
        }

        @Override
        public Choice choose(GameState state, List<Choice> options, Map<String, Object> context) {
            if (cursor[0] < log.size()) {
                int idx = log.get(cursor[0]++);
                if (idx < 0 || idx >= options.size()) {
                    throw new IllegalStateException(
                        "Сохранение не сходится с правилами: на шаге " + cursor[0]
                            + " записан вариант " + idx + ", а вариантов " + options.size()
                            + ". Партию по такому сохранению не восстановить.");
                }
                return options.get(idx);
            }
            if (!announced) {
                announced = true;
                if (onCaughtUp != null) {
                    onCaughtUp.run();
                }
            }
            return inner.choose(state, options, context);
        }

        @Override
        public void observeEvent(Map<String, Object> event) {
            inner.observeEvent(event);
        }

        @Override
        public void observePublicEvent(Map<String, Object> event) {
            inner.observePublicEvent(event);
        }

        public Agent inner() {
            return inner;
        }
    }

    /** Обернуть весь стол пишущими обёртками. */
    public static List<Agent> recording(List<Agent> agents, List<Integer> log) {
        List<Agent> out = new ArrayList<>(agents.size());
        for (Agent a : agents) {
            out.add(new Recording(a, log));
        }
        return out;
    }

    /**
     * Обернуть весь стол проигрывающими обёртками с ОБЩИМ курсором: решения
     * шли вперемешку по местам, и порядок в ленте — общий на партию.
     */
    public static List<Agent> playback(List<Agent> agents, List<Integer> log,
                                        Runnable onCaughtUp) {
        int[] cursor = new int[1];
        List<Agent> out = new ArrayList<>(agents.size());
        for (Agent a : agents) {
            out.add(new Playback(a, log, cursor, onCaughtUp));
        }
        return out;
    }
}
