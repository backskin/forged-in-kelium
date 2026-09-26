package kelium.gui.net;

import java.util.ArrayList;
import java.util.List;

import kelium.core.Agent;
import kelium.report.ReplayRecord;

/**
 * ТОЧКА ВСТРАИВАНИЯ СЕТИ В ОКНО ПАРТИИ ХОСТА.
 *
 * <p>Окно партии ({@code HotSeatWindow}) сажает места по строкам состава:
 * {@code human} — живой за этим экраном, иначе — бот из справочника. Сетевое
 * место — строка {@code net:N}: для него окно берёт агента отсюда, а кадры
 * своей записи отдаёт сюда же. Окно ничего не знает о сети, кроме этих
 * нескольких вызовов; сетевое место оно считает «не своим» — как бота: без
 * шторки и без органов ввода, «ход соперника».
 *
 * <p>Стол на процесс один: хост играет одну сетевую партию за раз.
 */
public final class NetSeats {

    private NetSeats() {
    }

    /** Строка состава для сетевого места. */
    public static final String PREFIX = "net:";

    private static volatile NetHost host;

    static void bind(NetHost h) {
        host = h;
    }

    static void unbind(NetHost h) {
        if (host == h) {
            host = null;
        }
    }

    /** Идёт ли сетевая партия. */
    public static boolean active() {
        return host != null;
    }

    /** Сетевое ли это место. */
    public static boolean claims(String spec) {
        return spec != null && spec.startsWith(PREFIX);
    }

    /** Агент сетевого места или null — место не сетевое / стола нет. */
    public static Agent agentFor(String spec, int seat) {
        NetHost h = host;
        return h == null || !claims(spec) ? null : h.agent(seat);
    }

    /**
     * Бот ли за местом: обычное место — по строке состава; сетевое — живой
     * игрок, пока хост не отдал его место боту.
     */
    public static boolean isBot(String spec, int seat) {
        if (!claims(spec)) {
            return !"human".equals(spec);
        }
        NetHost h = host;
        RemoteAgent a = h == null ? null : h.agent(seat);
        return a != null && a.botted();
    }

    /** Имя сетевого места или null. */
    public static String label(String spec, int seat) {
        NetHost h = host;
        return h == null || !claims(spec) ? null : h.seatName(seat);
    }

    /** Кадры записи партии хоста — зовётся на потоке движка. */
    public static void frame(ReplayRecord r) {
        NetHost h = host;
        if (h != null) {
            h.onFrame(r);
        }
    }

    /** Одно решение партии в ленте: чьё, в каком раунде и круге. */
    public record Step(int seat, int round, int circle) {
    }

    /**
     * СВЯЗЬ С ТЕМ, КТО ВЕДЁТ ПАРТИЮ У ХОСТА (окно партии или {@link NetGame}):
     * лента решений для правила отмены, сама отмена и выход из партии.
     */
    public interface Link {
        /** Решения партии по порядку — параллельно ленте номеров. */
        List<Step> steps();

        /**
         * Откатить партию к решению {@code index} (его самого уже нет) и
         * переиграть по ленте. {@code then} — вызвать, когда ведущий уже
         * переключился на новый прогон: только после этого размыкается
         * ожидание старого.
         */
        void undoTo(int index, Runnable then);

        /** Хост закрыл партию — окно партии уходит в меню. */
        default void exit() {
        }
    }

    /**
     * Окно партии хоста: сюда вешаются шторка паузы и чат, а {@code link}
     * даёт сети ленту решений и отмену. Не сетевая партия — ничего не делает.
     */
    public static void attach(javax.swing.JFrame frame, Link link) {
        NetHost h = host;
        if (h != null) {
            h.attachWindow(frame, link);
        }
    }

    /**
     * КУДА МОЖНО ОТКАТИТЬ МЕСТО {@code seat}, ждущее решения в раунде
     * {@code round}, круге {@code circle}: его решения в этом круге, по
     * порядку, но не глубже первого чужого решения после них (в сети чужое
     * решение запекает всё до него — соседям нельзя переписывать то, на что
     * они уже ответили).
     */
    public static List<Integer> undoTargets(List<Step> steps, int seat, int round, int circle) {
        List<Integer> out = new ArrayList<>();
        for (int i = steps.size() - 1; i >= 0; i--) {
            Step d = steps.get(i);
            if (d.round() != round || d.circle() != circle || d.seat() != seat) {
                break;
            }
            out.add(0, i);
        }
        return out;
    }

    /** Партия окончена. */
    public static void over(ReplayRecord r, List<Integer> moves) {
        NetHost h = host;
        if (h != null) {
            h.finish(r, moves);
        }
    }
}
