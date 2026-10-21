package kelium.gui.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import kelium.gui.HotSeatWindow;
import kelium.report.Json;

/**
 * ПОДКЛЮЧЕНИЕ К ЧУЖОМУ СТОЛУ: получить настройки, занять место, дождаться
 * начала и дальше обмениваться решениями.
 *
 * <p>Своё место гость не выбирает — его даёт хозяин: свободных сетевых мест за
 * столом ровно столько, сколько хозяин оставил, и спорить за них незачем.
 */
public final class ГостьСтола implements AutoCloseable {

    private final Линия линия;
    private final CountDownLatch столПришёл = new CountDownLatch(1);
    private final CountDownLatch начали = new CountDownLatch(1);
    private volatile HotSeatWindow.Options настройки;
    private volatile int моёМесто = -1;
    private volatile String отказ;
    private volatile ЛентаРешений лента;
    private volatile Runnable наОбрыв;

    public ГостьСтола(String адрес, int порт, int секундНаСвязь) throws IOException {
        Socket сокет = new Socket();
        сокет.connect(new InetSocketAddress(адрес, порт), секундНаСвязь * 1000);
        this.линия = new Линия(сокет);
        Thread t = new Thread(this::слушать, "гость-" + адрес);
        t.setDaemon(true);
        t.start();
    }

    /** Что делать, когда связь со столом оборвётся (экран скажет игроку). */
    public void приОбрыве(Runnable слушок) {
        this.наОбрыв = слушок;
    }

    /**
     * Дождаться настроек стола. Возвращает {@code false}, если хозяин отказал
     * (мест нет или партия уже идёт) либо стол не ответил вовремя.
     */
    public boolean дождатьсяСтола(int секунд) throws InterruptedException {
        return столПришёл.await(секунд, TimeUnit.SECONDS) && отказ == null;
    }

    /** Дождаться, пока хозяин начнёт партию. */
    public boolean дождатьсяНачала(int секунд) throws InterruptedException {
        return начали.await(секунд, TimeUnit.SECONDS);
    }

    public String отказ() {
        return отказ;
    }

    public HotSeatWindow.Options настройки() {
        return настройки;
    }

    public int моёМесто() {
        return моёМесто;
    }

    /** Лента решений для окна партии: своё — одно место, остальное из сети. */
    public ЛентаРешений лента() {
        if (лента == null) {
            лента = new ЛентаРешений(Set.of(моёМесто), линия::отправить);
        }
        return лента;
    }

    private void слушать() {
        линия.читать(m -> {
            String что = String.valueOf(m.get("что"));
            switch (что) {
                case "стол" -> {
                    настройки = НастройкиСтола.изСообщения(m);
                    моёМесто = Json.i(m, "моёМесто");
                    столПришёл.countDown();
                }
                case "отказ" -> {
                    отказ = Json.s(m, "почему");
                    столПришёл.countDown();
                }
                case "начали" -> начали.countDown();
                case "решение" -> {
                    ЛентаРешений л = лента;
                    if (л != null) {
                        л.пришло(Json.i(m, "место"), Json.i(m, "номер"));
                    }
                }
                default -> {
                    // чужое сообщение — не наше дело
                }
            }
        });
        // Чтение вернулось: стол закрылся или связь оборвалась.
        столПришёл.countDown();
        начали.countDown();
        ЛентаРешений л = лента;
        if (л != null) {
            л.оборвать("связь со столом потеряна");
        }
        Runnable r = наОбрыв;
        if (r != null) {
            r.run();
        }
    }

    @Override
    public void close() {
        линия.close();
    }
}
