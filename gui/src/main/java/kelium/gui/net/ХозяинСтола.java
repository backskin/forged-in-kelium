package kelium.gui.net;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import kelium.gui.HotSeatWindow;

/**
 * СТОЛ, ПОДНЯТЫЙ НА ЭТОМ КОМПЬЮТЕРЕ: лобби, рассадка гостей и раздача решений.
 *
 * <p>Хозяин держит настройки стола и решает за свои места — за все ботовские и
 * за своё живое. Гость, подключившись, получает настройки и ОДНО свободное
 * сетевое место; дальше оба крутят свою копию движка и обмениваются номерами
 * выбранных вариантов (см. {@link ЛентаРешений}).
 *
 * <p>Хозяин ещё и ПЕРЕСЫЛЬЩИК: решение гостя приходит к нему и уходит всем
 * остальным. Иначе гостям пришлось бы соединяться каждый с каждым, а это
 * столько же дыр в роутерах, сколько игроков.
 */
public final class ХозяинСтола implements AutoCloseable {

    /** Место за столом: кто за него решает. */
    public enum Кто { Я, БОТ, СЕТЬ }

    private static final class Гость {
        final Линия линия;
        final int место;

        Гость(Линия линия, int место) {
            this.линия = линия;
            this.место = место;
        }
    }

    private final ServerSocket слушатель;
    private final HotSeatWindow.Options настройки;
    private final List<Кто> места;
    private final List<Гость> гости = new CopyOnWriteArrayList<>();
    private final List<Runnable> наИзменение = new CopyOnWriteArrayList<>();
    private volatile ЛентаРешений лента;
    private volatile boolean начали;

    public ХозяинСтола(int порт, HotSeatWindow.Options настройки, List<Кто> места)
            throws IOException {
        String нельзя = НастройкиСтола.почемуНельзя(настройки);
        if (нельзя != null) {
            throw new IllegalArgumentException(нельзя);
        }
        this.настройки = настройки;
        this.места = List.copyOf(места);
        this.слушатель = new ServerSocket(порт);
        Thread t = new Thread(this::принимать, "стол-приём");
        t.setDaemon(true);
        t.start();
    }

    public int порт() {
        return слушатель.getLocalPort();
    }

    /** Сколько сетевых мест ещё свободно. */
    public int свободныхМест() {
        return свободные().size();
    }

    /** Кто сейчас за столом — строкой на экран лобби. */
    public List<String> рассадка() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < места.size(); i++) {
            String кто = switch (места.get(i)) {
                case Я -> "вы";
                // ИМЯ БОТА ПО-ЧЕЛОВЕЧЕСКИ, а не составом из кода: за столом
                // сидит «Ровный · мастер», а не balanced:2.
                case БОТ -> "бот " + поЧеловечески(настройки.seatSpecs().get(i));
                case СЕТЬ -> занято(i) ? "игрок по сети" : "ждём игрока";
            };
            out.add("Место " + (i + 1) + ": " + кто);
        }
        return out;
    }

    /**
     * СОСТАВ БОТА ЧЕЛОВЕЧЕСКИМИ СЛОВАМИ: «balanced:2» → «Ровный · мастер».
     * Названия берутся из справочника ботов — того же, что показывает «Штаб».
     */
    private static String поЧеловечески(String состав) {
        if (состав == null) {
            return "?";
        }
        int двоеточие = состав.indexOf(':');
        String характер = двоеточие < 0 ? состав : состав.substring(0, двоеточие);
        String уровень = двоеточие < 0 ? null : состав.substring(двоеточие + 1);
        String имя = подпись(kelium.agents.BotCatalog.ХАРАКТЕРЫ, характер, характер);
        return уровень == null ? имя
            : имя + " · " + подпись(kelium.agents.BotCatalog.УРОВНИ, уровень, уровень);
    }

    private static String подпись(List<kelium.agents.BotCatalog.Entry> где,
                                   String id, String запасное) {
        for (kelium.agents.BotCatalog.Entry e : где) {
            if (e.id().equals(id)) {
                return e.label();
            }
        }
        return запасное;
    }

    /** Подписаться на изменения рассадки (экран лобби перерисовывается). */
    public void приИзменении(Runnable слушок) {
        наИзменение.add(слушок);
    }

    /**
     * НАЧАТЬ ПАРТИЮ. Возвращает ленту решений для своего окна; гостям уходит
     * сообщение «начали», и они открывают свои окна сами.
     */
    public ЛентаРешений начать() {
        if (свободныхМест() > 0) {
            throw new IllegalStateException("за столом ещё есть пустые сетевые места");
        }
        Set<Integer> свои = new LinkedHashSet<>();
        for (int i = 0; i < места.size(); i++) {
            if (места.get(i) != Кто.СЕТЬ) {
                свои.add(i);
            }
        }
        лента = new ЛентаРешений(свои, this::всем);
        начали = true;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("что", "начали");
        всем(m);
        return лента;
    }

    /** Моё место за столом (то, за которым сижу я сам); −1 — только боты. */
    public int моёМесто() {
        return места.indexOf(Кто.Я);
    }

    public HotSeatWindow.Options настройки() {
        return настройки;
    }

    // ==================== приём и пересылка ====================

    private void принимать() {
        while (!слушатель.isClosed()) {
            try {
                Socket сокет = слушатель.accept();
                Линия линия = new Линия(сокет);
                int место = посадить(линия);
                if (место < 0) {
                    Map<String, Object> нет = new LinkedHashMap<>();
                    нет.put("что", "отказ");
                    нет.put("почему", начали
                        ? "партия уже идёт" : "свободных сетевых мест нет");
                    линия.отправить(нет);
                    линия.close();
                    continue;
                }
                Гость гость = new Гость(линия, место);
                гости.add(гость);
                Map<String, Object> привет = НастройкиСтола.вСообщение(настройки);
                привет.put("что", "стол");
                привет.put("моёМесто", место);
                линия.отправить(привет);
                сообщить();
                Thread t = new Thread(() -> слушать(гость), "стол-гость-" + место);
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                return;        // стол закрыли — слушать больше нечего
            }
        }
    }

    private void слушать(Гость гость) {
        гость.линия.читать(m -> {
            if ("решение".equals(m.get("что"))) {
                int место = kelium.report.Json.i(m, "место");
                int номер = kelium.report.Json.i(m, "номер");
                ЛентаРешений л = лента;
                if (л != null) {
                    л.пришло(место, номер);
                }
                // ПЕРЕСЫЛКА ОСТАЛЬНЫМ: гости между собой не соединены.
                for (Гость другой : гости) {
                    if (другой != гость) {
                        другой.линия.отправить(m);
                    }
                }
            }
        });
        // Чтение вернулось — связь с этим гостем кончилась.
        гости.remove(гость);
        гость.линия.close();
        ЛентаРешений л = лента;
        if (л != null && начали) {
            л.оборвать("игрок места " + (гость.место + 1) + " отключился");
        }
        сообщить();
    }

    private void всем(Map<String, Object> m) {
        for (Гость г : гости) {
            г.линия.отправить(m);
        }
    }

    private synchronized int посадить(Линия линия) {
        if (начали) {
            return -1;
        }
        List<Integer> свободные = свободные();
        return свободные.isEmpty() ? -1 : свободные.get(0);
    }

    private List<Integer> свободные() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < места.size(); i++) {
            if (места.get(i) == Кто.СЕТЬ && !занято(i)) {
                out.add(i);
            }
        }
        return out;
    }

    private boolean занято(int место) {
        for (Гость г : гости) {
            if (г.место == место) {
                return true;
            }
        }
        return false;
    }

    private void сообщить() {
        for (Runnable r : наИзменение) {
            r.run();
        }
    }

    @Override
    public void close() {
        try {
            слушатель.close();
        } catch (IOException ignore) {
            // закрываем
        }
        for (Гость г : гости) {
            г.линия.close();
        }
        гости.clear();
        ЛентаРешений л = лента;
        if (л != null) {
            л.оборвать("стол закрыт хозяином");
        }
    }
}
