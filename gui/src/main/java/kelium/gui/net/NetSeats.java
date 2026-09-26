package kelium.gui.net;

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

    /** Партия окончена. */
    public static void over(ReplayRecord r, List<Integer> moves) {
        NetHost h = host;
        if (h != null) {
            h.finish(r, moves);
        }
    }
}
