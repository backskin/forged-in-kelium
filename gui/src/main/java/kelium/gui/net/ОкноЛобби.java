package kelium.gui.net;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import kelium.gui.HotSeatWindow;
import kelium.gui.kp.KpButton;
import kelium.gui.replay2.Theme;

/**
 * ЛОББИ СЕТЕВОГО СТОЛА — маленькое окно между «Штабом» и партией.
 *
 * <p>У хозяина оно показывает адрес, по которому к нему подключаются, и
 * рассадку: кто уже пришёл, каких мест ждём. У гостя — то же самое с другой
 * стороны: подключились, место такое-то, ждём, когда хозяин начнёт.
 *
 * <p>Нарочно простое. Настоящее лобби со списком столов, паролями и чатом —
 * это отдельная работа; чтобы сесть и сыграть с другом по адресу, хватает
 * адреса, рассадки и одной кнопки.
 */
public final class ОкноЛобби {

    /** Порт по умолчанию: ни с чем известным не спорит и легко диктуется вслух. */
    public static final int ПОРТ = 54321;

    private final JFrame окно = new JFrame("Кристаллы Раздора — сетевой стол");
    private final JPanel строки = new JPanel();
    private final JLabel заголовок = new JLabel();
    private final JLabel подпись = new JLabel();
    private KpButton кнопка;

    private ОкноЛобби() {
        Theme.apply(false);
        окно.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        окно.getContentPane().setBackground(Theme.bg());
        окно.getContentPane().setLayout(new BorderLayout());

        JPanel столбец = new JPanel();
        столбец.setBackground(Theme.bg());
        столбец.setLayout(new BoxLayout(столбец, BoxLayout.Y_AXIS));
        столбец.setBorder(BorderFactory.createEmptyBorder(
            Theme.px(16), Theme.px(18), Theme.px(16), Theme.px(18)));

        заголовок.setFont(Theme.font(16, Font.BOLD));
        заголовок.setForeground(Theme.ink());
        заголовок.setAlignmentX(Component.LEFT_ALIGNMENT);
        столбец.add(заголовок);

        подпись.setFont(Theme.font(12, Font.PLAIN));
        подпись.setForeground(Theme.ink2());
        подпись.setAlignmentX(Component.LEFT_ALIGNMENT);
        столбец.add(javax.swing.Box.createVerticalStrut(Theme.px(6)));
        столбец.add(подпись);

        строки.setBackground(Theme.bg());
        строки.setLayout(new BoxLayout(строки, BoxLayout.Y_AXIS));
        строки.setAlignmentX(Component.LEFT_ALIGNMENT);
        столбец.add(javax.swing.Box.createVerticalStrut(Theme.px(12)));
        столбец.add(строки);

        окно.add(столбец, BorderLayout.CENTER);
        окно.setMinimumSize(new Dimension(Theme.px(420), Theme.px(300)));
    }

    // ==================== хозяин ====================

    /**
     * ПОДНЯТЬ СТОЛ на этом компьютере и ждать гостей.
     *
     * @param настройки стол, собранный в «Штабе»; живые места, кроме своего,
     *                  считаются сетевыми
     * @param моёМесто  место хозяина или −1, если он только смотрит
     */
    public static void поднять(HotSeatWindow.Options настройки, int моёМесто) {
        List<ХозяинСтола.Кто> места = new ArrayList<>();
        for (int i = 0; i < настройки.players(); i++) {
            boolean живое = "human".equals(настройки.seatSpecs().get(i));
            места.add(i == моёМесто ? ХозяинСтола.Кто.Я
                : живое ? ХозяинСтола.Кто.СЕТЬ : ХозяинСтола.Кто.БОТ);
        }
        ОкноЛобби л = new ОкноЛобби();
        ХозяинСтола хозяин;
        try {
            хозяин = new ХозяинСтола(ПОРТ, настройки, места);
        } catch (Exception e) {
            л.беда("Стол не поднялся", String.valueOf(e.getMessage()));
            л.показать();
            return;
        }
        л.заголовок.setText("Стол поднят");
        л.подпись.setText("Гостям диктовать: " + адресЭтогоКомпьютера() + " : " + хозяин.порт());
        л.кнопка = new KpButton("Начать партию", "когда все места заняты", null);
        л.кнопка.setPreferredSize(new Dimension(Theme.px(260), Theme.px(44)));
        л.кнопка.setMaximumSize(new Dimension(Theme.px(320), Theme.px(44)));
        л.кнопка.setAlignmentX(Component.LEFT_ALIGNMENT);
        л.кнопка.onClick(() -> {
            if (хозяин.свободныхМест() > 0) {
                return;
            }
            ЛентаРешений лента = хозяин.начать();
            л.окно.dispose();
            HotSeatWindow.открытьПоСети(настройки, лента, хозяин.моёМесто(), хозяин::close);
        });
        Runnable обновить = () -> SwingUtilities.invokeLater(() -> {
            л.список(хозяин.рассадка());
            л.кнопка.setState(хозяин.свободныхМест() == 0
                ? KpButton.State.AVAILABLE : KpButton.State.DISABLED);
            л.кнопка.setTexts("Начать партию", хозяин.свободныхМест() == 0
                ? "все на местах" : "ждём ещё " + хозяин.свободныхМест());
        });
        хозяин.приИзменении(обновить);
        обновить.run();
        л.окно.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (!л.ушлиВПартию) {
                    хозяин.close();
                }
            }
        });
        л.показать();
    }

    // ==================== гость ====================

    /**
     * СПРОСИТЬ АДРЕС и подключиться. Адрес игроку диктует хозяин стола — он
     * видит его у себя в лобби первой строкой.
     */
    public static void спроситьАдрес() {
        ОкноЛобби л = new ОкноЛобби();
        л.заголовок.setText("Подключиться к столу");
        л.подпись.setText("Адрес хозяина и порт — их видно у него в лобби");

        javax.swing.JTextField поле = new javax.swing.JTextField("127.0.0.1:" + ПОРТ);
        поле.setFont(Theme.font(14, Font.PLAIN));
        поле.setForeground(Theme.ink());
        поле.setBackground(Theme.tile());
        поле.setCaretColor(Theme.ink());
        поле.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.border()),
            BorderFactory.createEmptyBorder(Theme.px(6), Theme.px(8),
                Theme.px(6), Theme.px(8))));
        поле.setMaximumSize(new Dimension(Theme.px(320), Theme.px(34)));
        поле.setAlignmentX(Component.LEFT_ALIGNMENT);

        KpButton идём = new KpButton("Подключиться", "сесть за чужой стол", null);
        идём.setPreferredSize(new Dimension(Theme.px(260), Theme.px(44)));
        идём.setMaximumSize(new Dimension(Theme.px(320), Theme.px(44)));
        идём.setAlignmentX(Component.LEFT_ALIGNMENT);
        идём.onClick(() -> {
            String[] части = поле.getText().trim().split(":");
            String адрес = части[0].isBlank() ? "127.0.0.1" : части[0];
            int порт = ПОРТ;
            if (части.length > 1) {
                try {
                    порт = Integer.parseInt(части[1].trim());
                } catch (NumberFormatException e) {
                    л.беда("Непонятный порт", "после двоеточия должно стоять число");
                    return;
                }
            }
            л.окно.dispose();
            подключиться(адрес, порт);
        });

        л.строки.add(поле);
        л.строки.add(javax.swing.Box.createVerticalStrut(Theme.px(10)));
        л.строки.add(идём);
        л.показать();
    }

    /** ПОДКЛЮЧИТЬСЯ к чужому столу и ждать, пока хозяин начнёт. */
    public static void подключиться(String адрес, int порт) {
        ОкноЛобби л = new ОкноЛобби();
        л.заголовок.setText("Подключаемся");
        л.подпись.setText(адрес + " : " + порт);
        л.показать();
        Thread t = new Thread(() -> {
            ГостьСтола гость;
            try {
                гость = new ГостьСтола(адрес, порт, 10);
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> л.беда("Стол не отвечает",
                    "по адресу " + адрес + " : " + порт + " никто не поднял стол "
                        + "(" + e.getMessage() + ")"));
                return;
            }
            try {
                if (!гость.дождатьсяСтола(15)) {
                    String почему = гость.отказ() == null
                        ? "стол не ответил настройками" : гость.отказ();
                    SwingUtilities.invokeLater(() -> л.беда("За стол не сели", почему));
                    гость.close();
                    return;
                }
                SwingUtilities.invokeLater(() -> {
                    л.заголовок.setText("Вы за столом");
                    л.подпись.setText("Ваше место " + (гость.моёМесто() + 1)
                        + " · ждём, пока хозяин начнёт партию");
                    л.список(List.of("Игроков: " + гость.настройки().players(),
                        "Свод: " + гость.настройки().rulesetId(),
                        "Сид: " + гость.настройки().seed()));
                });
                // Ждём старта столько, сколько нужно: хозяин может собирать стол
                // долго, и выкидывать гостя за это неправильно.
                while (!гость.дождатьсяНачала(600)) {
                    if (гость.отказ() != null) {
                        break;
                    }
                }
                ЛентаРешений лента = гость.лента();
                HotSeatWindow.Options настройки = гость.настройки();
                SwingUtilities.invokeLater(() -> {
                    л.ушлиВПартию = true;
                    л.окно.dispose();
                    HotSeatWindow.открытьПоСети(настройки, лента, гость.моёМесто(),
                        гость::close);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                гость.close();
            }
        }, "лобби-гость");
        t.setDaemon(true);
        t.start();
    }

    // ==================== мелочи ====================

    private boolean ушлиВПартию;

    private void показать() {
        окно.pack();
        окно.setLocationRelativeTo(null);
        окно.setVisible(true);
    }

    private void беда(String что, String почему) {
        заголовок.setText(что);
        подпись.setText(почему);
        список(List.of());
    }

    private void список(List<String> что) {
        строки.removeAll();
        for (String s : что) {
            JLabel l = new JLabel(s);
            l.setFont(Theme.font(13, Font.PLAIN));
            l.setForeground(Theme.ink());
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            строки.add(l);
            строки.add(javax.swing.Box.createVerticalStrut(Theme.px(4)));
        }
        if (кнопка != null) {
            строки.add(javax.swing.Box.createVerticalStrut(Theme.px(10)));
            строки.add(кнопка);
        }
        строки.revalidate();
        строки.repaint();
        окно.pack();
    }

    /**
     * АДРЕС ЭТОГО КОМПЬЮТЕРА В СЕТИ — тот, который гость впечатает у себя.
     * Берётся первый не-петлевой адрес живого сетевого интерфейса: «localhost»
     * гостю бесполезен, а спрашивать адрес у игрока — значит отправить его в
     * настройки Windows.
     */
    static String адресЭтогоКомпьютера() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof java.net.Inet4Address && !a.isLoopbackAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignore) {
            // сети может не быть вовсе — тогда честно скажем «localhost»
        }
        return "127.0.0.1";
    }
}
