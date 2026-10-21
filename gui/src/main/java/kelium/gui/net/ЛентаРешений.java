package kelium.gui.net;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;

/**
 * СЕТЕВАЯ ПАРТИЯ КАК ОБЩАЯ ЛЕНТА РЕШЕНИЙ.
 *
 * <p>Движок воспроизводим: тот же свод, тот же сид, те же решения — та же
 * партия. На этом уже стоят сохранения ({@link kelium.gui.GameSave}) и разбор
 * спорных ситуаций. Сетевой стол устроен так же: у каждого участника крутится
 * СВОЯ копия движка, а по сети летят только номера выбранных вариантов — ровно
 * та лента, которая иначе легла бы в файл сохранения.
 *
 * <p>У каждой точки решения РОВНО ОДИН ХОЗЯИН: место принадлежит либо тому, кто
 * поднял стол (его живые места и все боты), либо подключившемуся гостю. Хозяин
 * спрашивает своего агента и публикует номер; остальные в этот момент стоят и
 * ждут этот номер. Порядок точек решения у всех одинаков — его задаёт сам
 * движок, — поэтому очередь одна на партию и номер места в сообщении нужен не
 * для адресации, а для СВЕРКИ: разъехались копии — партия встаёт вслух, а не
 * доигрывает у каждого свою.
 *
 * <p>ЧТО ЭТО СТОИТ. У клиента в памяти лежит весь стол, включая чужие руки, —
 * интерфейс их не показывает, но это защита от соседа, а не от человека,
 * который полезет в память. Решение дизайнера 21.10.2026: для игры с друзьями
 * этого достаточно; честный тонкий клиент (сервер шлёт обрезанный по месту
 * кадр) описан в design-docs/ПЛАН — сетевая игра через тонкий клиент.
 */
public final class ЛентаРешений {

    /** Сколько ждать чужого решения, прежде чем сказать, что связи нет. */
    private static final long ТЕРПЕНИЕ_СЕК = 600;

    private final Set<Integer> свои;
    private final Consumer<Map<String, Object>> отправка;
    private final BlockingQueue<int[]> входящие = new LinkedBlockingQueue<>();
    private volatile String беда;

    /**
     * @param свои     места, за которые решает ЭТОТ участник
     * @param отправка куда отдать сообщение о своём решении (сокет); у стола
     *                 без сети — {@code null}
     */
    public ЛентаРешений(Set<Integer> свои, Consumer<Map<String, Object>> отправка) {
        this.свои = Set.copyOf(свои);
        this.отправка = отправка;
    }

    public boolean владею(int место) {
        return свои.contains(место);
    }

    /** Пришло решение от другого участника — положить в очередь. */
    public void пришло(int место, int номер) {
        входящие.add(new int[]{место, номер});
    }

    /** Связь оборвалась или стол закрыт: ждущие должны не висеть вечно. */
    public void оборвать(String почему) {
        беда = почему;
        входящие.add(new int[]{-1, -1});
    }

    /** Дождаться чужого решения для этого места. */
    private int ждать(int место, int вариантов) {
        int[] пара;
        try {
            пара = входящие.poll(ТЕРПЕНИЕ_СЕК, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new kelium.core.GameAborted("партия прервана");
        }
        if (пара == null) {
            throw new IllegalStateException("Сетевой стол: за " + ТЕРПЕНИЕ_СЕК
                + " секунд не пришло решение места " + (место + 1) + ".");
        }
        if (пара[0] < 0) {
            throw new kelium.core.GameAborted(
                беда == null ? "сетевой стол закрыт" : беда);
        }
        // СВЕРКА, А НЕ АДРЕСАЦИЯ: очередь одна, и если из сети пришло решение
        // другого места — значит копии движка разъехались. Доигрывать такую
        // партию нельзя: дальше у каждого будет своя игра с одинаковым счётом.
        if (пара[0] != место) {
            throw new IllegalStateException("Сетевой стол разъехался: ждали решение места "
                + (место + 1) + ", а пришло место " + (пара[0] + 1)
                + ". Партия остановлена, чтобы у игроков не разошлись столы.");
        }
        if (пара[1] < 0 || пара[1] >= вариантов) {
            throw new IllegalStateException("Сетевой стол разъехался: место " + (место + 1)
                + " выбрало вариант " + пара[1] + ", а вариантов здесь " + вариантов + ".");
        }
        return пара[1];
    }

    private void опубликовать(int место, int номер) {
        if (отправка == null) {
            return;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("что", "решение");
        m.put("место", место);
        m.put("номер", номер);
        отправка.accept(m);
    }

    /** Обернуть весь стол: своё спрашиваем, чужое ждём из сети. */
    public List<Agent> обернуть(List<Agent> агенты) {
        List<Agent> out = new ArrayList<>(агенты.size());
        for (Agent a : агенты) {
            out.add(new Место(a, this));
        }
        return out;
    }

    /**
     * ОДНО МЕСТО ЗА СЕТЕВЫМ СТОЛОМ. Своё — спрашиваем настоящего агента и
     * отправляем номер; чужое — ждём номер и берём вариант по нему, НЕ
     * заглядывая в настоящего агента вовсе (иначе у чужого места на экране
     * хозяина поднялась бы точка решения).
     */
    private static final class Место extends Agent {

        private final Agent внутри;
        private final ЛентаРешений лента;

        Место(Agent внутри, ЛентаРешений лента) {
            super(внутри.seat, внутри.name);
            this.внутри = внутри;
            this.лента = лента;
        }

        @Override
        public Choice choose(GameState state, List<Choice> options, Map<String, Object> context) {
            if (!лента.владею(seat)) {
                return options.get(лента.ждать(seat, options.size()));
            }
            Choice picked = внутри.choose(state, options, context);
            лента.опубликовать(seat, options.indexOf(picked));
            return picked;
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
}
