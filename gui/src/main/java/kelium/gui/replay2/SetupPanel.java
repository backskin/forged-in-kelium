package kelium.gui.replay2;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import kelium.dataio.GameConfig;
import kelium.gui.GameRecorder;
import kelium.gui.PlacesDialog;
import kelium.gui.ToolIcons;
import kelium.gui.TransportIcons;
import kelium.report.ReplayRecord;

/**
 * SetupPanel — НАСТРОЙКА ПАРТИИ, которая не живёт на экране постоянно.
 *
 * <p>В версии 1.0 форма настроек занимала полторы сотни пикселей сверху ВСЕГДА, хотя
 * её трогают один раз перед прогоном. Здесь она раскрывается по строке-кнопке и
 * сворачивается сама, как только партия сыграна: место уходит полю.
 *
 * <p>Главная кнопка «Сыграть и показать» стоит отдельным краем панели, а не в
 * общей сетке, и не может уехать за край (в 1.0 на окне 1360×900 она сжималась в
 * полоску шесть пикселей и не нажималась).
 *
 * <p>Сами настройки лежат ОДНОЙ СТРОКОЙ ГРУПП (правка 14.08.2026, дважды: сперва
 * была одна жёсткая строка сетки, и на узком окне её просто срезало по краю
 * вместе с кнопками «другая сборка» и «стол…»; затем — строка с переносом,
 * но перенос ломал группу поперёк без подписи). Теперь внутри группы настройки
 * стоят столбиком по центру, а не в ряд, — строка вся целиком заметно уже и
 * почти всегда умещается без переноса; если совсем не хватает места — короткая
 * прокрутка вбок, а не обрезка по краю.
 */
public final class SetupPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final Session session;
    private final Consumer<String> say;

    private final JComboBox<Integer> players = new JComboBox<>(new Integer[]{2, 3, 4});
    private final JTextField seed = new JTextField("777");
    private final JComboBox<String> ruleset = new JComboBox<>();
    /** Выбор версии колоды по типу набора: «objectives», «arsenal». */
    private final java.util.Map<String, JComboBox<String>> cardSets =
        new java.util.LinkedHashMap<>();
    /** Первый пункт таких списков: версию называют правила. */
    private static final String AS_IN_RULES = "как в правилах";
    private final JComboBox<FieldOption> fieldBox = new JComboBox<>();
    /**
     * ХАРАКТЕР бота на каждом месте. УРОВЕНЬ выбирается отдельным списком
     * ({@link #levels}) — заказ дизайнера 25.08.2026: за столом это два разных
     * вопроса, и в одном списке из шестнадцати строк они смешаны.
     */
    @SuppressWarnings("unchecked")
    private final JComboBox<GameRecorder.SeatOption>[] seats = new JComboBox[4];
    /** Уровень силы бота на каждом месте: от новичка до гроссмейстера. */
    @SuppressWarnings("unchecked")
    private final JComboBox<GameRecorder.SeatOption>[] levels = new JComboBox[4];
    @SuppressWarnings("unchecked")
    private final JComboBox<String>[] cuFacing = new JComboBox[4];
    private final JLabel[] seatCaption = new JLabel[4];
    private final SeatChip[] seatChip = new SeatChip[4];
    private final JButton playButton;
    private final SpinnerIcon playSpinner;
    private JButton tableButton;

    /** Подсказка кнопки «игроки…»: что именно сейчас выбрано за столом. */
    private void refreshTableButton() {
        String s = TableDialog.summary(playerCount());
        tableButton.setToolTipText(Ui2.tip("ХАРАКТЕР бота, поворот ЦУ, КОЛОДА приказов "
            + "и СТОРОНЫ планшетов — войск и хранилища — каждого места за столом."
            + (s.isEmpty() ? " Колода сейчас не выбрана — раздаётся по сиду, "
                + "стороны планшетов — как в правилах." : " Колоды и планшеты сейчас "
                + "выбраны — " + s + ".")));
    }

    /** Сид сборки картонных блоков — отдельный от сида партии. */
    private Long blockSeed;
    /** Мешок раскладок: пока не опустеет, поле не повторится. */
    private final List<Integer> fieldBag = new ArrayList<>();
    private final Random rng = new Random();
    private boolean filling;

    private Runnable onPlay = () -> { };
    private Runnable onPreview = () -> { };

    /** Вариант раскладки: {@code file} не null — поле из папки библиотеки. */
    private record FieldOption(String id, String label, Path file) {
        @Override public String toString() {
            return label;
        }
    }

    public SetupPanel(Session session, Consumer<String> say) {
        this.session = session;
        this.say = say;
        setOpaque(true);
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border()));
        // ГЛАВНАЯ КНОПКА — В СВОЁМ КРАЮ ПАНЕЛИ, а не в колонке той же сетки: пока она
        // жила в сетке, вторая строка (кто на местах) занимала колонки до самой
        // кнопки, и выбор характера четвёртого игрока упирался в неё без просвета.
        setLayout(new java.awt.BorderLayout());
        // ОДНА ГОРИЗОНТАЛЬНАЯ ЛИНИЯ ИЗ ГРУПП, КАЖДАЯ — СВОЕЙ ЕСТЕСТВЕННОЙ ШИРИНЫ
        // (правка 14.08.2026, третья: столбик по центру решил перенос строк, но
        // сами блоки всё равно тянулись во всю доступную ширину — «поле:»
        // распухало в пустой список на полстроки, хотя «другая сборка» под ним
        // была вдвое уже). Теперь блок выровнен ВЛЕВО и никогда не растягивается
        // ШИРЕ своего содержимого ({@link #group}); если блокам не хватает места
        // — сперва их сжимает до минимума (см. {@link RibbonRow}), а когда и
        // минимума мало — появляется тонкая прокрутка вбок, а не обрезка или наезд.
        RibbonRow row1 = new RibbonRow();
        // Прозрачная: фон рисует сама панель настроек, и цвет получается один
        row1.setOpaque(false);
        // Отступы чуть щедрее (просьба дизайнера 17.08.2026: «больше воздуха,
        // дыхания между разделами» — было 8/10/4/10).
        row1.setBorder(BorderFactory.createEmptyBorder(Theme.px(11), Theme.px(14),
            Theme.px(8), Theme.px(14)));

        players.setSelectedItem(4);
        players.setFont(Theme.body());
        players.setToolTipText(Ui2.tip("Сколько игроков за столом. Список полей сразу "
            + "перестроится под это число."));

        seed.setFont(Theme.mono(13, Font.PLAIN));
        seed.setToolTipText(Ui2.tip("Зерно случайности. Одинаковый сид, одинаковое поле и "
            + "одинаковый состав ботов дают ровно ту же партию."));
        Dimension seedSize = new Dimension(Theme.px(76), seed.getPreferredSize().height);
        seed.setPreferredSize(seedSize);
        seed.setMinimumSize(seedSize);

        // ГРУППЫ ЛЕНТЫ, КАК В WORD (просьба дизайнера 14.08.2026: «панель должна
        // быть чище, удобнее, красивее — как у современного Word»). Раньше все
        // настройки первой строки стояли подряд без разбивки: игроков, сид,
        // правила, задания, арсенал, поле — потом три кнопки — глазу не за что
        // зацепиться, все семь равнозначны на вид. Теперь родственные настройки
        // стоят кучкой под общей подписью, а между кучками — тонкая линия
        // (Ui2.hairline), в точности как «Шрифт» / «Абзац» на ленте Word.
        row1.add(group("Партия",
            cell(Ui2.label("игроков:"), players),
            cell(Ui2.label("сид:"), seed,
                Ui2.iconButton(TransportIcons.of("DICE", Theme.px(16)),
                    "Взять случайный сид.", 24,
                    () -> seed.setText(String.valueOf(Math.abs(rng.nextInt(1_000_000))))))));
        row1.add(divider());

        for (String r : GameConfig.availableRulesets(null)) {
            ruleset.addItem(r);
        }
        if (ruleset.getItemCount() == 0) {
            ruleset.addItem(GameConfig.DEFAULT_RULESET);
        }
        ruleset.setSelectedItem(GameConfig.DEFAULT_RULESET);
        ruleset.setFont(Theme.body());
        ruleset.setToolTipText(Ui2.tip("Версия правил: по ней берутся значения и карты."));
        // ЧЕТЫРЕ СПИСКА ОДНОЙ ШИРИНЫ: содержимое у них разной длины, и без этого
        // «поле» выходило вдвое шире «правил» — строка выглядела случайной, хотя
        // настройки в ней равнозначные. Раньше это делал sizegroup общей сетки;
        // ячейки стоят каждая сама по себе, поэтому ширина задаётся прямо.
        sameWidth(ruleset);

        // ВЫБОР КОЛОД НА ПАРТИЮ (просьба дизайнера 13.08.2026). Обычно версию
        // набора называют правила, и «как в правилах» — первый пункт списка.
        // Остальные пункты — редакции, лежащие на диске рядом: так одни и те же
        // правила можно сыграть на другом наборе заданий и сравнить колоды.
        row1.add(group("Правила и колоды",
            cell(Ui2.label("правила:"), ruleset),
            cardSet("задания:", "objectives",
                "Колода КАРТ ЗАДАНИЙ. «Как в правилах» — версия, названная выбранной "
                + "редакцией правил. Другой пункт берёт колоду той версии, оставляя "
                + "правила прежними."),
            cardSet("арсенал:", "arsenal",
                "Колода КАРТ АРСЕНАЛА. «Как в правилах» — версия, названная выбранной "
                + "редакцией правил.")));
        row1.add(divider());

        fieldBox.setFont(Theme.body());
        fieldBox.setToolTipText(Ui2.tip("Раскладка поля: авторские варианты на выбранное "
            + "число игроков и всё, что нарисовано конструктором и лежит в папках "
            + "библиотеки. «Любая (по сиду)» оставляет выбор движку."));
        sameWidth(fieldBox);
        JButton blocks = new JButton("другая сборка", ToolIcons.of("BLOCKS"));
        blocks.setFont(Theme.body());
        blocks.setFocusable(false);
        blocks.setToolTipText(Ui2.tip("Разложить поле ДРУГИМИ картонными блоками: "
            + "печатные контейнеры окажутся на других ячейках. Сид партии, раскладка "
            + "гексов и боты те же."));
        blocks.addActionListener(e -> {
            blockSeed = (long) Math.abs(rng.nextInt(1_000_000));
            onPreview.run();
            say.accept("Поле собрано другими блоками (сборка " + blockSeed
                + "). Контейнеры переехали; сама партия та же.");
        });
        row1.add(group("Поле",
            cell(Ui2.label("поле:"), fieldBox,
                Ui2.iconButton(TransportIcons.of("SHUFFLE", Theme.px(16)),
                    "Случайная раскладка. Идём перетасованным мешком: пока все поля не "
                    + "покажутся, повторов не будет.", 24, this::pickRandomField)),
            blocks));
        row1.add(divider());

        // ИГРОКИ: ВСЁ хозяйство места — характер бота, поворот ЦУ, колода
        // приказов, стороны обоих планшетов — одним окном (просьба дизайнера
        // 14.08.2026: раньше характер и ЦУ жили СНАРУЖИ отдельной строкой, а
        // здесь — только колода и планшеты; «нелогично, что часть вынесена»).
        // Своя иконка (SEATS — места за столом), не общая со «сборкой из
        // блоков»: раньше обе кнопки делили один и тот же значок BLOCKS, и
        // ничего в нём не намекало на людей за столом.
        tableButton = new JButton("игроки…", TransportIcons.of("SEATS", Theme.px(16)));
        tableButton.setFont(Theme.body());
        tableButton.setFocusable(false);
        tableButton.addActionListener(e -> {
            if (TableDialog.show(this, String.valueOf(ruleset.getSelectedItem()),
                    playerCount(), seats, levels, cuFacing, seatChip,
                    this::randomSeatBot)) {
                refreshTableButton();
                onPreview.run();
                say.accept("Стол пересобран: " + TableDialog.summary(playerCount()));
            }
        });
        refreshTableButton();

        // ВСЁ СЛУЧАЙНО ОДНОЙ КНОПКОЙ (просьба дизайнера 14.08.2026). Кубики у
        // каждой настройки уже были, но чтобы получить незнакомую партию, их
        // надо было нажать семь раз подряд.
        JButton randomAll = new JButton("всё случайно",
            TransportIcons.of("DICE", Theme.px(16)));
        randomAll.setFont(Theme.font(14, Font.PLAIN));
        randomAll.setFocusable(false);
        randomAll.setToolTipText(Ui2.tip("Разом случайные: число игроков, сид, "
            + "раскладка поля, сборка блоков, характеры всех ботов, повороты ЦУ, "
            + "колоды приказов и стороны планшетов каждого места."
            + "\n\nВЕРСИЯ ПРАВИЛ И ВЕРСИИ КАРТОЧНЫХ НАБОРОВ НЕ ТРОГАЮТСЯ: их выбирают, "
            + "чтобы сравнивать редакции между собой, и случайная подмена сделала бы "
            + "сравнение бессмысленным."));
        randomAll.addActionListener(e -> randomAll());
        row1.add(group("Состав", tableButton, randomAll));
        row1.add(divider());
        row1.add(expansionsGroup());

        // ГЛАВНАЯ КНОПКА: текст в две строки, значок крупный. Одной строкой она
        // раздувалась в ширину и прилипала к «другой сборке» — отсюда и отступ слева.
        playButton = Ui2.textButton("<html><center>Сыграть<br>и показать</center></html>",
            "Сыграть партию с этими настройками и показать её по шагам.", () -> onPlay.run());
        playButton.setFont(Theme.font(13, Font.BOLD));
        playButton.setIcon(TransportIcons.of("PLAY", Theme.px(22)));
        playButton.setIconTextGap(Theme.px(8));
        // Б4 (заказ дизайнера 17.08.2026): пока партия считается, вместо
        // статичной подписи «играю партию…» на кнопке крутится живой спиннер.
        playSpinner = new SpinnerIcon(playButton, Theme.px(22), Theme.accent());
        playButton.setMargin(new java.awt.Insets(Theme.px(6), Theme.px(12),
            Theme.px(6), Theme.px(14)));
        // ОТСТУП СЛЕВА — пустой рамкой у собственного края панели. Кнопка живёт
        // ВНЕ сетки формы, поэтому ни первая строка, ни выбор ботов во второй уже
        // не могут в неё упереться.
        JPanel ctaBox = new JPanel(new java.awt.BorderLayout());
        ctaBox.setOpaque(false);
        // Отступы СО ВСЕХ СТОРОН: кнопка не должна упираться ни в соседей слева, ни
        // в края панели сверху, снизу и справа.
        ctaBox.setBorder(BorderFactory.createEmptyBorder(
            Theme.px(8), Theme.px(22), Theme.px(8), Theme.px(12)));
        ctaBox.add(playButton, java.awt.BorderLayout.CENTER);

        // ХАРАКТЕР БОТА И ПОВОРОТ ЦУ — ЖИВЫЕ КОМПОНЕНТЫ, но БЕЗ СВОЕЙ СТРОКИ НА
        // ЛЕНТЕ (просьба дизайнера 14.08.2026: убрать двустрочность, сделать
        // ленту однострочной). Раньше это была вторая строка панели с четырьмя
        // ячейками; теперь те же самые компоненты (с теми же слушателями —
        // расстановка обновляется сразу по щелчку, как и было) стоят на месте
        // только внутри окна «игроки…» ({@link TableDialog}), а здесь просто
        // создаются и ждут своего часа.
        for (int i = 0; i < 4; i++) {
            final int seat = i;
            seats[i] = new JComboBox<>();
            seats[i].setFont(Theme.body());
            // Список НЕ ДИКТУЕТ ширину диалога: у ботов длинные имена («Стратег ·
            // сбалансированный»). Полное имя видно в раскрытом списке и в подсказке.
            seats[i].setPreferredSize(new Dimension(Theme.px(170),
                seats[i].getPreferredSize().height));
            seats[i].setMinimumSize(new Dimension(Theme.px(120),
                seats[i].getPreferredSize().height));
            reloadSeatOptions(seats[i], i);
            seats[i].addActionListener(e -> {
                GameRecorder.SeatOption o =
                    (GameRecorder.SeatOption) seats[seat].getSelectedItem();
                seats[seat].setToolTipText(o == null ? null : Ui2.tip(o.tip()));
                refresh();
            });

            // УРОВЕНЬ — свой список. Ширина меньше, чем у характера: названия
            // короче, и растянутый список выглядел бы пустым.
            levels[i] = new JComboBox<>();
            levels[i].setFont(Theme.body());
            levels[i].setPreferredSize(new Dimension(Theme.px(140),
                levels[i].getPreferredSize().height));
            levels[i].setMinimumSize(new Dimension(Theme.px(110),
                levels[i].getPreferredSize().height));
            for (GameRecorder.SeatOption o : GameRecorder.levelOptions()) {
                levels[i].addItem(o);
            }
            // ПО УМОЛЧАНИЮ МАСТЕР, А НЕ ГРОССМЕЙСТЕР: гроссмейстер доигрывает
            // копию партии на каждом выборе приказа и считает вдвое дольше.
            // Смотреть партию хочется сразу, а не ждать расчёта.
            levels[i].setSelectedIndex(1);
            levels[i].addActionListener(e -> {
                GameRecorder.SeatOption o =
                    (GameRecorder.SeatOption) levels[seat].getSelectedItem();
                levels[seat].setToolTipText(o == null ? null : Ui2.tip(o.tip()));
                refresh();
            });
            levels[i].setToolTipText(Ui2.tip(GameRecorder.levelOptions().get(1).tip()));
            // СТОРОНЫ СВЕТА ВМЕСТО НОМЕРОВ. «Сторона 4» человеку ничего не говорит,
            // а «запад» видно на поле сразу (просьба дизайнера 13.08.2026).
            String[] facings = new String[7];
            facings[0] = "авто";
            for (int s = 0; s < 6; s++) {
                facings[s + 1] = compass(s);
            }
            cuFacing[i] = new JComboBox<>(facings);
            cuFacing[i].setFont(Theme.body());
            cuFacing[i].setToolTipText(Ui2.tip("Стартовый ПОВОРОТ ЦУ этого места: куда "
                + "смотрит «нос» центра управления — стык двух его стенок. «Авто» — "
                + "к центру поля."));
            cuFacing[i].addActionListener(e -> refresh());

            // ПЛАШКА ЦВЕТА МЕСТА вместо цветного текста: цветной шрифт сливался с
            // тёмным и светлым фоном, и сопоставить его с цветом жетона на поле
            // было нельзя (замечание дизайнера 13.08.2026). Цвет здесь — НОМЕР
            // МЕСТА, а не выбор игрока (колоду выбирают отдельно, см. TableDialog).
            seatChip[i] = new SeatChip(i, "Место " + (i + 1));
            seatChip[i].setFontSize(11);
            seatCaption[i] = new JLabel();
        }

        // ЛЕНТА — ОДНОЙ СТРОКОЙ ЦЕЛИКОМ (просьба дизайнера 14.08.2026): раньше
        // вторая строка (места игроков) занимала всю ширину под собой всегда,
        // даже когда в ней взглянуть было не на что после переноса характера и
        // ЦУ в диалог. Теперь под лентой сразу поле.
        // ПРОКРУТКА ПО ГОРИЗОНТАЛИ — СТРАХОВКА НА СОВСЕМ УЗКОЕ ОКНО. Лента —
        // одна строка групп и не переносится; если группам всё равно не хватает
        // места, лучше короткая прокрутка вбок, чем наезд групп друг на друга
        // или обрезка по краю окна (ровно так и было до этой правки).
        javax.swing.JScrollPane row1Scroll = new javax.swing.JScrollPane(row1,
            javax.swing.JScrollPane.VERTICAL_SCROLLBAR_NEVER,
            javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        row1Scroll.setOpaque(false);
        row1Scroll.getViewport().setOpaque(false);
        row1Scroll.setBorder(BorderFactory.createEmptyBorder());
        // ТОНКАЯ ЛИНИЯ, А НЕ ОБЫЧНЫЙ СКРОЛЛБАР (просьба дизайнера 14.08.2026):
        // без кнопок-стрелок, минимальной высоты — включается редко (см.
        // RibbonRow.getScrollableTracksViewportWidth), поэтому не должен
        // выглядеть как полноценный элемент управления.
        Ui2.thinHorizontalBar(row1Scroll, 6);

        JPanel form = new JPanel(new java.awt.BorderLayout());
        form.setOpaque(false);
        form.add(row1Scroll, java.awt.BorderLayout.CENTER);
        add(form, java.awt.BorderLayout.CENTER);
        add(ctaBox, java.awt.BorderLayout.EAST);

        // всё, что меняет расстановку, обновляет поле сразу — партию для этого не играем
        players.addActionListener(e -> {
            for (int i = 0; i < 4; i++) {
                reloadSeatOptions(seats[i], i);
            }
            reloadFields();
            enableSeats();
            refreshTableButton();
            refresh();
        });
        ruleset.addActionListener(e -> {
            reloadFields();
            refresh();
        });
        fieldBox.addActionListener(e -> refresh());
        seed.addActionListener(e -> refresh());
        seed.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                refresh();
            }
        });
        reloadFields();
        enableSeats();
    }

    /** Фон — из темы на каждую отрисовку: переключение темы не должно его терять. */
    @Override
    protected void paintComponent(java.awt.Graphics g) {
        g.setColor(Theme.panel());
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    public void setOnPlay(Runnable r) {
        this.onPlay = r;
    }

    public void setOnPreview(Runnable r) {
        this.onPreview = r;
    }

    private void refresh() {
        if (!filling) {
            onPreview.run();
        }
    }

    /** Короткая сводка настроек для строки-кнопки. */
    public String summary() {
        Object f = fieldBox.getSelectedItem();
        return players.getSelectedItem() + " игрока · сид " + seed.getText().trim()
            + " · правила " + ruleset.getSelectedItem()
            + (f instanceof FieldOption fo && fo.id() != null ? " · " + fo.id() : "");
    }

    public void setBusy(boolean busy) {
        playButton.setEnabled(!busy);
        playButton.setText(busy ? "считаю партию…" : "<html><center>Сыграть<br>и показать</center></html>");
        playButton.setIcon(busy ? playSpinner : TransportIcons.of("PLAY", Theme.px(22)));
        if (busy) {
            playSpinner.start();
        } else {
            playSpinner.stop();
        }
        players.setEnabled(!busy);
        seed.setEnabled(!busy);
        ruleset.setEnabled(!busy);
        fieldBox.setEnabled(!busy);
        for (int i = 0; i < 4; i++) {
            seats[i].setEnabled(!busy && enabled(i));
            levels[i].setEnabled(!busy && enabled(i));
            cuFacing[i].setEnabled(!busy && enabled(i));
        }
    }

    private boolean enabled(int seat) {
        int n = players.getSelectedItem() instanceof Integer i ? i : 4;
        return seat < n;
    }

    private void enableSeats() {
        for (int i = 0; i < 4; i++) {
            boolean on = enabled(i);
            seats[i].setEnabled(on);
            levels[i].setEnabled(on);
            cuFacing[i].setEnabled(on);
            if (seatChip[i] != null) {
                seatChip[i].setStrong(on);      // лишнее место — плашка бледнеет
            }
        }
    }

    // ==================== прогон ====================
    private int playerCount() {
        return players.getSelectedItem() instanceof Integer i ? i : 4;
    }

    private long seedValue() {
        return Long.parseLong(seed.getText().trim());
    }

    private List<String> seatIds() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < playerCount(); i++) {
            // ИМЯ БОТА — ДВЕ ПОЛОВИНЫ: характер и уровень выбираются отдельно.
            ids.add(GameRecorder.botId(
                ((GameRecorder.SeatOption) seats[i].getSelectedItem()).id(),
                ((GameRecorder.SeatOption) levels[i].getSelectedItem()).id()));
        }
        return ids;
    }

    /**
     * СТОРОНА СВЕТА, куда смотрит ЦУ, вставшее на стороны {@code side} и
     * {@code side+1}. Считается из геометрии, а не подписана вручную: поле
     * развёрнуто на {@link kelium.report.FieldGeometry#TILT}, и захардкоженный
     * список названий разошёлся бы с картинкой при первой же правке поворота.
     */
    static String compass(int side) {
        double a = kelium.report.FieldGeometry.meanEdgeAngle(
            List.of(side, (side + 1) % 6));
        // на экране y растёт вниз: переводим в привычное «вверх — север»
        double m = kelium.report.FieldGeometry.norm180(-a);
        int k = Math.floorMod((int) Math.round(m / 60.0), 6);
        return switch (k) {
            case 0 -> "восток";
            case 1 -> "северо-восток";
            case 2 -> "северо-запад";
            case 3 -> "запад";
            case 4 -> "юго-запад";
            default -> "юго-восток";
        };
    }

    /** Случайный характер бота на это место — из того же списка, что в выпадашке. */
    private void randomSeatBot(int seat) {
        JComboBox<GameRecorder.SeatOption> box = seats[seat];
        if (box.getItemCount() == 0 || !box.isEnabled()) {
            return;
        }
        box.setSelectedIndex(rng.nextInt(box.getItemCount()));
        // КУБИК КИДАЕТ И УРОВЕНЬ ТОЖЕ: он стоит в той же строке места, и бросок
        // «характера отдельно от силы» оставлял бы половину места неслучайной.
        JComboBox<GameRecorder.SeatOption> lvl = levels[seat];
        if (lvl.getItemCount() > 0) {
            lvl.setSelectedIndex(rng.nextInt(lvl.getItemCount()));
        }
        GameRecorder.SeatOption o = (GameRecorder.SeatOption) box.getSelectedItem();
        GameRecorder.SeatOption l = (GameRecorder.SeatOption) lvl.getSelectedItem();
        if (o != null) {
            say.accept("Место " + (seat + 1) + ": выпал «" + o.label()
                + (l == null ? "" : " · " + l.label()) + "».");
        }
        refresh();
    }

    private List<Integer> facing() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < playerCount(); i++) {
            int fi = cuFacing[i].getSelectedIndex();
            out.add(fi <= 0 ? null : fi - 1);     // «авто» = null, «1» = сторона 0
        }
        return out;
    }

    /** Стартовая расстановка без прогона — быстро. */
    public ReplayRecord buildPreview() {
        applyCardSets();
        FieldOption fo = (FieldOption) fieldBox.getSelectedItem();
        return GameRecorder.preview(String.valueOf(ruleset.getSelectedItem()),
            playerCount(), seedValue(), seatIds(), fo == null ? null : fo.id(), facing(),
            fo == null ? null : fo.file(), blockSeed);
    }

    /** Сыграть партию целиком (вызывается из фонового потока). */
    public ReplayRecord play(Consumer<String> progress) {
        applyCardSets();
        FieldOption fo = (FieldOption) fieldBox.getSelectedItem();
        return GameRecorder.play(String.valueOf(ruleset.getSelectedItem()), playerCount(),
            seedValue(), seatIds(), fo == null ? null : fo.id(), facing(),
            fo == null ? null : fo.file(), blockSeed, progress);
    }

    // ==================== списки ====================
    /**
     * КОЛОНКИ ПЕРВОЙ СТРОКИ НАСТРОЙКИ. Пар шесть: игроков · сид · правила ·
     * задания · арсенал · поле, плюс две кнопки в конце.
     *
     * <p>ТЯНУТСЯ ЧЕТЫРЕ СПИСКА СРАЗУ И ПОРОВНУ. Раньше тянулся один («поле»), и
     * пока настроек было мало, это было незаметно; с появлением выбора колод весь
     * запас ширины оказался в середине строки — арсенал и поле уехали к правому
     * краю, а слева зияла дыра (замечание дизайнера 13.08.2026). Теперь лишнее
     * место делится между списками, и строка заполняется вся.
     */
    /**
     * ОДНА НАСТРОЙКА — ОДНОЙ ЯЧЕЙКОЙ: подпись и её приборы стоят вместе и
     * переносятся на другую строку ЦЕЛИКОМ. Разрывать «поле:» и список полей
     * переносом нельзя — подпись без своего списка ничего не значит.
     */
    private static JPanel cell(Component... parts) {
        JPanel p = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets 0, gapx " + Theme.px(4) + ", novisualpadding, fillx"));
        p.setOpaque(false);
        // ЛИШНЮЮ ШИРИНУ ЗАБИРАЕТ ГЛАВНЫЙ ПРИБОР ЯЧЕЙКИ, А НЕ ПУСТОТА СПРАВА.
        // Ячейки в группе выравниваются по самой широкой (см. group), и без
        // этого выравнивание было бы фиктивным: панель-ячейка растянулась бы, а
        // список внутри остался прежним — правые края всё равно не совпали бы.
        // Растёт первый список или поле ввода; подписи и кнопки-кубики держат
        // свой размер, им ширина ничего не даёт.
        boolean grown = false;
        for (Component c : parts) {
            boolean growThis = !grown
                && (c instanceof JComboBox<?> || c instanceof javax.swing.JTextField);
            p.add(c, growThis ? "growx, pushx" : "");
            grown |= growThis;
        }
        return p;
    }

    /**
     * ГРУППА ЛЕНТЫ: кучка родственных настроек СТОЛБИКОМ, ВЫРОВНЕННЫМ ВЛЕВО, а
     * под ней — подпись заглавными, как «Шрифт» или «Абзац» на ленте Word
     * (правка 14.08.2026, третья: столбик по центру решил перенос строк, но
     * сама группа всё равно тянулась шире содержимого — «поле:» распухало в
     * пустой список на полстроки, хотя «другая сборка» под ним была вдвое
     * уже; замечание дизайнера — «не тяни его до правого края»). Каждая
     * строка и подпись прижаты к левому краю, а {@code setMaximumSize} на
     * саму группу и на каждую её строку не даёт BoxLayout растянуть их шире
     * собственного предпочтительного размера — группа занимает РОВНО
     * столько, сколько нужно её содержимому, ни больше.
     */
    /**
     * РАЗДЕЛ «ДОПОЛНЕНИЯ» — три тумблера (просьба дизайнера 17.08.2026: «два
     * таких красивых тумблера как с айфона», третий — супер-арсенал — того же
     * рода переключатель и просится в тот же раздел).
     *
     * <p>Каждый тумблер пишет своё значение сразу в {@link AppSettings} по клику
     * — как остальные настройки ленты, отдельной кнопки «применить» нет. При
     * сборке партии {@link kelium.gui.Expansions#applyTo} читает эти же ключи и
     * накладывает их на свод правил.
     */
    private JPanel expansionsGroup() {
        kelium.dataio.AppSettings settings = kelium.dataio.AppSettings.of("replay2");
        java.util.List<Component> toggles = new ArrayList<>();
        for (String name : new String[]{
                kelium.gui.Expansions.SUPER_OBJECTIVES,
                kelium.gui.Expansions.STARTING_OBJECTIVES,
                kelium.gui.Expansions.SUPER_ARSENAL,
                kelium.gui.Expansions.MARKET_CARDS}) {
            Toggle t = new Toggle(kelium.gui.Expansions.title(name),
                kelium.gui.Expansions.on(settings, name), kelium.gui.Expansions.tip(name));
            t.onChange(value -> {
                kelium.gui.Expansions.set(settings, name, value);
                onPreview.run();
                say.accept("Дополнения: " + kelium.gui.Expansions.summary(settings) + ".");
            });
            toggles.add(t);
        }
        // ДВА В РЯД, А НЕ ЧЕТЫРЕ СТОЛБИКОМ (просьба дизайнера 20.08.2026).
        // Тумблеры узкие, и четыре строки растягивали ленту настройки вниз без
        // всякой нужды: два ряда по два занимают вдвое меньше высоты, а читаются
        // так же — пары стоят рядом, а не вперемешку.
        java.util.List<Component> rows = new ArrayList<>();
        for (int i = 0; i < toggles.size(); i += 2) {
            JPanel row = new JPanel();
            row.setOpaque(false);
            row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.add(toggles.get(i));
            if (i + 1 < toggles.size()) {
                row.add(Box.createHorizontalStrut(Theme.px(10)));
                row.add(toggles.get(i + 1));
            }
            row.add(Box.createHorizontalGlue());
            rows.add(row);
        }
        return group("Дополнения", rows.toArray(new Component[0]));
    }

    private static JPanel group(String captionText, Component... parts) {
        JPanel g = new JPanel();
        g.setLayout(new BoxLayout(g, BoxLayout.Y_AXIS));
        g.setOpaque(false);
        // ОДНА ШИРИНА НА ВСЮ ГРУППУ, ЭТАЛОН — САМЫЙ ШИРОКИЙ ЕЁ ЭЛЕМЕНТ, плюс
        // запас (просьба дизайнера 15.08.2026). До этого каждая строка группы
        // была шириной ровно по себе, и правые края внутри одной кучки гуляли:
        // «сид:» с кубиком оказывался заметно уже «игроков:», «другая сборка» —
        // вдвое уже «поле:». Запас нужен, чтобы длинный пункт списка не упирался
        // в стрелку и группа не выглядела набитой впритык.
        int widest = 0;
        for (Component c : parts) {
            widest = Math.max(widest, c.getPreferredSize().width);
        }
        int target = (int) Math.round(widest * GROUP_WIDTH_SLACK);
        for (Component c : parts) {
            if (c instanceof JComponent jc) {
                jc.setAlignmentX(Component.LEFT_ALIGNMENT);
                fixWidth(jc, target);
            }
            g.add(c);
            g.add(Box.createVerticalStrut(Theme.px(5)));
        }
        JLabel cap = Ui2.caption(captionText);
        cap.setAlignmentX(Component.LEFT_ALIGNMENT);
        g.add(cap);
        capWidth(g);
        return g;
    }

    /**
     * ЗАПАС ШИРИНЫ ГРУППЫ ЛЕНТЫ. Группа шире самого широкого своего элемента на
     * эту долю — воздух по правому краю, чтобы кучка не выглядела набитой.
     */
    private static final double GROUP_WIDTH_SLACK = 1.15;

    /**
     * ЖЁСТКАЯ ШИРИНА ЭЛЕМЕНТА ГРУППЫ: и предпочтительная, и предельная. Одной
     * только предельной мало — BoxLayout растянет до неё лишь то, чья
     * предпочтительная и так больше соседей; остальные останутся узкими, и
     * выравнивания не выйдет. Высота не трогается, её каждый считает сам.
     */
    private static void fixWidth(JComponent c, int width) {
        Dimension pref = c.getPreferredSize();
        c.setPreferredSize(new Dimension(width, pref.height));
        c.setMaximumSize(new Dimension(width, Integer.MAX_VALUE));
    }

    /**
     * НЕ ДАВАТЬ КОМПОНЕНТУ РАСТЯНУТЬСЯ ШИРЕ СВОЕГО ЖЕ ПРЕДПОЧТИТЕЛЬНОГО
     * РАЗМЕРА. У большинства Swing-компонентов {@code getMaximumSize()} по
     * умолчанию огромный — и BoxLayout честно тянет их на всю доступную
     * ширину контейнера, если в том же столбике/строке нашёлся сосед пошире.
     * Именно так «поле:» и распухало. Высоту оставляем без ограничения:
     * запрет только по ширине.
     */
    private static void capWidth(JComponent c) {
        Dimension pref = c.getPreferredSize();
        c.setMaximumSize(new Dimension(pref.width, Integer.MAX_VALUE));
    }

    /** Тонкая линия между группами ленты — тот же приём, что в Word. */
    private static JComponent divider() {
        JPanel line = new JPanel() {
            @Override protected void paintComponent(java.awt.Graphics gr) {
                gr.setColor(Theme.border());
                int lineW = Theme.px(1);
                gr.fillRect((getWidth() - lineW) / 2, Theme.px(4), lineW,
                    getHeight() - Theme.px(8));
            }
        };
        line.setOpaque(false);
        // Ширина поднята с 9 до 20 (просьба дизайнера 17.08.2026: «больше
        // воздуха между разделами, они прям сильно плотно друг к другу») —
        // сама линия по-прежнему 1px по центру, просто пустого поля вокруг
        // неё вдвое больше.
        Dimension d = new Dimension(Theme.px(20), Theme.px(64));
        line.setPreferredSize(d);
        line.setMinimumSize(new Dimension(Theme.px(20), Theme.px(1)));
        line.setMaximumSize(new Dimension(Theme.px(20), Integer.MAX_VALUE));
        return line;
    }

    /** Ширина четырёх главных списков — общая, чтобы строка не выглядела случайной. */
    private static void sameWidth(JComboBox<?> box) {
        int h = box.getPreferredSize().height;
        box.setPreferredSize(new Dimension(Theme.px(150), h));
        // МИНИМУМ ЗАМЕТНО УЖЕ ПРЕДПОЧТИТЕЛЬНОГО (просьба дизайнера 14.08.2026,
        // третья): списку есть куда сжаться в тесном окне, прежде чем лента
        // вообще уедет в прокрутку — «сжимать, пока читаемо и видимо».
        box.setMinimumSize(new Dimension(Theme.px(96), h));
    }

    /** Список версий одного карточного набора и его подпись — готовой ячейкой. */
    private JPanel cardSet(String label, String type, String tip) {
        JComboBox<String> box = new JComboBox<>();
        box.setFont(Theme.body());
        box.setToolTipText(Ui2.tip(tip));
        box.addItem(AS_IN_RULES);
        for (String v : kelium.dataio.ContentSet.versionsOnDisk(type,
                GameConfig.resolveDataRoot(null))) {
            box.addItem(v);
        }
        box.setSelectedItem(AS_IN_RULES);
        box.addActionListener(e -> applyCardSets());
        cardSets.put(type, box);
        sameWidth(box);
        return cell(Ui2.label(label), box);
    }

    /**
     * ПЕРЕЧИТАТЬ ВСЁ, ЧТО ЗАВИСИТ ОТ ПАПКИ ДАННЫХ: версии правил, версии колод,
     * список полей. Зовётся после того, как папку данных сменили в окне настроек —
     * иначе в списках остались бы версии, которых в новой папке нет.
     */
    public void reloadEverything() {
        filling = true;
        try {
            Object keepRules = ruleset.getSelectedItem();
            ruleset.removeAllItems();
            for (String r : GameConfig.availableRulesets(null)) {
                ruleset.addItem(r);
            }
            if (ruleset.getItemCount() == 0) {
                ruleset.addItem(GameConfig.DEFAULT_RULESET);
            }
            ruleset.setSelectedItem(keepRules != null
                && ((javax.swing.DefaultComboBoxModel<String>) ruleset.getModel())
                    .getIndexOf(keepRules) >= 0
                ? keepRules : GameConfig.DEFAULT_RULESET);

            for (java.util.Map.Entry<String, JComboBox<String>> e : cardSets.entrySet()) {
                JComboBox<String> box = e.getValue();
                box.removeAllItems();
                box.addItem(AS_IN_RULES);
                for (String v : kelium.dataio.ContentSet.versionsOnDisk(e.getKey(),
                        GameConfig.resolveDataRoot(null))) {
                    box.addItem(v);
                }
                box.setSelectedItem(AS_IN_RULES);
            }
            for (int i = 0; i < 4; i++) {
                reloadSeatOptions(seats[i], i);
            }
        } finally {
            filling = false;
        }
        reloadFields();
        enableSeats();
        refreshTableButton();
    }

    /**
     * Передать выбор колод в сборку конфигурации. Пункт «как в правилах» снимает
     * выбор, и версия снова берётся из правил — иначе один раз выбранная колода
     * молча осталась бы во всех следующих партиях.
     */
    private void applyCardSets() {
        for (java.util.Map.Entry<String, JComboBox<String>> e : cardSets.entrySet()) {
            Object v = e.getValue().getSelectedItem();
            GameConfig.pickContentVersion(e.getKey(),
                v == null || AS_IN_RULES.equals(v) ? null : String.valueOf(v));
        }
    }

    private void reloadSeatOptions(JComboBox<GameRecorder.SeatOption> box, int seat) {
        filling = true;
        try {
            Object keep = box.getSelectedItem();
            box.removeAllItems();
            // ТОЛЬКО ХАРАКТЕРЫ: уровень выбирается своим списком, и мешать их
            // в одном означало бы вернуть те самые шестнадцать строк.
            List<GameRecorder.SeatOption> options = GameRecorder.characterOptions();
            for (GameRecorder.SeatOption o : options) {
                box.addItem(o);
            }
            int pick = Math.min(seat, options.size() - 1);
            if (keep instanceof GameRecorder.SeatOption prev) {
                for (int i = 0; i < options.size(); i++) {
                    if (options.get(i).id().equals(prev.id())) {
                        pick = i;
                        break;
                    }
                }
            }
            box.setSelectedIndex(Math.max(0, pick));
            GameRecorder.SeatOption o = (GameRecorder.SeatOption) box.getSelectedItem();
            box.setToolTipText(o == null ? null : Ui2.tip(o.tip()));
        } finally {
            filling = false;
        }
    }

    /**
     * ПЕРЕЧИТАТЬ СПИСОК ПОЛЕЙ — публично, для окна настроек. Раньше список
     * обновлялся только как побочный эффект других действий (смена числа
     * игроков, {@link #reloadEverything()}), и добавленная в настройках папка
     * раскладок не появлялась в выпадающем списке, пока пользователь случайно
     * не задевал что-то ещё (баг найден дизайнером 14.08.2026).
     */
    public void reloadFields() {
        filling = true;
        try {
            int n = playerCount();
            String version;
            try {
                version = GameConfig.buildCached(String.valueOf(ruleset.getSelectedItem()),
                    n, 0L, null, null).ruleset.getStr("content_versions.scenarios", "1.0.0");
            } catch (RuntimeException e) {
                version = "1.0.0";
            }
            Object keep = fieldBox.getSelectedItem();
            fieldBox.removeAllItems();
            fieldBox.addItem(new FieldOption(null, "любая (по сиду)", null));
            Set<String> authors = new LinkedHashSet<>();
            try {
                for (java.util.Map<String, Object> v : kelium.engine.Scenario.loadAllVariants(
                        n, version, GameConfig.resolveDataRoot(null))) {
                    String id = String.valueOf(v.get("id"));
                    authors.add(id);
                    fieldBox.addItem(new FieldOption(id, "авторская · " + id, null));
                }
            } catch (RuntimeException e) {
                say.accept("Список авторских раскладок не прочитан: " + e.getMessage());
            }
            List<String> problems = new ArrayList<>();
            for (kelium.engine.LayoutLibrary.Entry e
                    : kelium.engine.LayoutLibrary.scan(n, problems)) {
                if (authors.contains(e.id())) {
                    continue;
                }
                fieldBox.addItem(new FieldOption(e.id(),
                    "своя · " + e.id() + "  (" + e.file().getFileName() + ")", e.file()));
            }
            if (!problems.isEmpty()) {
                say.accept("Часть файлов в папках раскладок не прочитана: "
                    + String.join("; ", problems));
            }
            fieldBag.clear();
            if (keep instanceof FieldOption fo && fo.id() != null) {
                for (int i = 0; i < fieldBox.getItemCount(); i++) {
                    if (fo.id().equals(fieldBox.getItemAt(i).id())) {
                        fieldBox.setSelectedIndex(i);
                        return;
                    }
                }
            }
            fieldBox.setSelectedIndex(0);
        } finally {
            filling = false;
        }
    }

    /**
     * ВСЁ СЛУЧАЙНО РАЗОМ. Порядок важен: сперва число игроков, потому что от него
     * зависят и список полей, и список характеров, — иначе выбирали бы из старых
     * списков и выбор слетал бы следом за перестройкой.
     *
     * <p>Пока идёт раздача, {@code filling} держит расстановку невычисленной:
     * иначе поле пересобиралось бы на каждую из семи настроек подряд.
     */
    private void randomAll() {
        filling = true;
        try {
            players.setSelectedItem(2 + rng.nextInt(3));
            for (int i = 0; i < 4; i++) {
                reloadSeatOptions(seats[i], i);
            }
            reloadFields();
            enableSeats();
            seed.setText(String.valueOf(Math.abs(rng.nextInt(1_000_000))));
            blockSeed = (long) Math.abs(rng.nextInt(1_000_000));
            for (int i = 0; i < playerCount(); i++) {
                if (seats[i].getItemCount() > 0) {
                    seats[i].setSelectedIndex(rng.nextInt(seats[i].getItemCount()));
                }
                if (levels[i].getItemCount() > 0) {
                    levels[i].setSelectedIndex(rng.nextInt(levels[i].getItemCount()));
                }
                cuFacing[i].setSelectedIndex(rng.nextInt(cuFacing[i].getItemCount()));
            }
            TableDialog.randomise(String.valueOf(ruleset.getSelectedItem()),
                playerCount(), rng);
            refreshTableButton();
            pickRandomFieldQuiet();
        } finally {
            filling = false;
        }
        refresh();
        say.accept("Случайная партия: " + playerCount() + " игрока, сид "
            + seed.getText().trim() + ", поле «" + fieldBox.getSelectedItem()
            + "». Стол: " + TableDialog.summary(playerCount()));
    }

    /** Случайная раскладка из мешка: пока мешок не опустеет, поле не повторится. */
    private void pickRandomField() {
        if (!pickRandomFieldQuiet()) {
            say.accept("Выбирать не из чего: в списке только «любая (по сиду)».");
        } else {
            FieldOption fo = (FieldOption) fieldBox.getSelectedItem();
            say.accept("Случайная раскладка: " + (fo == null ? "?" : fo.label())
                + " (в мешке осталось " + fieldBag.size() + ")");
        }
    }

    /**
     * То же, но молча — нужно раздаче «всё случайно»: там про поле говорится в
     * общей строке, и отдельное сообщение о нём тут же затиралось бы.
     *
     * @return false, если выбирать было не из чего
     */
    private boolean pickRandomFieldQuiet() {
        int count = fieldBox.getItemCount();
        if (count <= 1) {
            return false;
        }
        if (fieldBag.isEmpty()) {
            for (int i = 1; i < count; i++) {
                fieldBag.add(i);
            }
            Collections.shuffle(fieldBag, rng);
        }
        int idx = fieldBag.remove(fieldBag.size() - 1);
        if (idx >= count) {
            fieldBag.clear();
            return pickRandomFieldQuiet();
        }
        fieldBox.setSelectedIndex(idx);
        return true;
    }


    /** Общий на все приложения диалог «где лежат раскладки». */
    public void editLibrary(java.awt.Component parent) {
        if (PlacesDialog.show(parent)) {
            reloadFields();
            refresh();
            say.accept("Места хранения обновлены — список полей перечитан.");
        }
    }

    /** Подставить настройки из открытой записи, ничего не перестраивая. */
    public void showFromRecord(ReplayRecord rec) {
        filling = true;
        try {
            players.setSelectedItem(rec.players);
            seed.setText(String.valueOf(rec.seed));
            if (rec.ruleset != null && !rec.ruleset.isBlank()) {
                boolean found = false;
                for (int i = 0; i < ruleset.getItemCount(); i++) {
                    if (rec.ruleset.equals(ruleset.getItemAt(i))) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    ruleset.addItem(rec.ruleset);
                }
                ruleset.setSelectedItem(rec.ruleset);
            }
            for (int i = 0; i < rec.players && i < 4; i++) {
                String id = rec.seatIds.get(i);
                for (int k = 0; k < seats[i].getItemCount(); k++) {
                    if (seats[i].getItemAt(k).id().equals(id)) {
                        seats[i].setSelectedIndex(k);
                        break;
                    }
                }
                Integer f = i < rec.cuFacing.size() ? rec.cuFacing.get(i) : null;
                cuFacing[i].setSelectedIndex(f == null ? 0 : Math.floorMod(f, 6) + 1);
            }
            enableSeats();
        } finally {
            filling = false;
        }
    }

    /**
     * СТРОКА ЛЕНТЫ, КОТОРАЯ УМЕЕТ СЖИМАТЬСЯ ПРЕЖДЕ, ЧЕМ ЕХАТЬ В ПРОКРУТКУ
     * (просьба дизайнера 14.08.2026, третья). Обычная {@link JScrollPane}
     * всегда рисует содержимое его же предпочтительным размером и просто
     * прячет лишнее за скроллбар — так строка ехала бы в прокрутку, даже
     * если ей не хватает всего десяти пикселей. {@code Scrollable} даёт
     * лазейку: пока в окне видимости ХВАТАЕТ места хотя бы на минимальный
     * размер строки, {@link #getScrollableTracksViewportWidth} возвращает
     * {@code true} — тогда {@link JViewport} заставляет строку принять ЕГО
     * ширину, и BoxLayout честно сжимает группы к их минимуму. Прокрутка
     * появляется только когда сжимать уже некуда.
     */
    private static final class RibbonRow extends JPanel implements javax.swing.Scrollable {
        RibbonRow() {
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect,
                                              int orientation, int direction) {
            return Theme.px(24);
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect,
                                               int orientation, int direction) {
            return Theme.px(160);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            java.awt.Container parent = getParent();
            return parent == null || parent.getWidth() >= getMinimumSize().width;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return true;
        }
    }
}
