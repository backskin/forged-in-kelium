package kelium.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import kelium.core.BuildingToken;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.engine.Scoring;
import kelium.engine.Storage;

/**
 * ReplayRecord — ЗАПИСЬ ПАРТИИ: всё, что нужно, чтобы показать партию заново.
 *
 * <p>Устройство подсказано заказом (§4.2): движок играет партию целиком и
 * синхронно, поставить его на паузу нельзя. Поэтому сначала партия прогоняется
 * один раз, а приёмник событий сохраняет НА КАЖДЫЙ ШАГ снимок состояния, само
 * событие и строку лога/мысли. Дальше проигрыватель просто листает готовый
 * список — отсюда и шаг назад, и перемотка, и сохранение в файл.
 *
 * <p>Снимок хранит ровно то, что рисуется: гексы (тайлы, контейнеры, нейтралы,
 * занятые стороны), жетоны (владелец, тип, гекс, урон, энергия) и полное
 * состояние каждого игрока. Топология поля (какие вообще есть гексы и где)
 * статична и лежит в шапке записи, а не в каждом кадре.
 *
 * <p>Формат файла — JSON в UTF-8 (кириллица в консоли Windows ломается, поэтому
 * кодировка задаётся явно везде, где пишется файл).
 */
public final class ReplayRecord {

    /** Версия формата записи — растёт при несовместимых изменениях. */
    public static final int FORMAT_VERSION = 1;
    /** Метка формата в файле. */
    public static final String FORMAT_TAG = "kelium-replay";

    public String ruleset = "";
    public int players;
    public long seed;
    /** Идентификаторы ботов по местам (как в RunnerGui: {@code strat:hawk} и т.п.). */
    public final List<String> seatIds = new ArrayList<>();
    /** Человеческие названия ботов по местам. */
    public final List<String> seatLabels = new ArrayList<>();
    /** Стороны планшетов войск по местам. */
    public final List<String> sides = new ArrayList<>();
    /** Раскладка, на которой играли (id варианта); null — выбрана по сиду. */
    public String scenarioId;
    /** Файл, из которого взята раскладка (поле из конструктора); null — авторская. */
    public String scenarioFile;
    /** Стартовый поворот ЦУ по местам (сторона 0..5); пусто — автоподбор. */
    public final List<Integer> cuFacing = new ArrayList<>();
    /**
     * ЦВЕТ МЕСТА: место → номер цветового гнезда 0..3. Пусто — цвет по номеру
     * места, как было всегда.
     *
     * <p>Цвет к правилам отношения не имеет: движок о нём не знает, и партия от
     * него не меняется. Но в записи он нужен — иначе журнал переиграется не в
     * тех красках, в каких игрок за столом сидел, и разбирать партию по нему
     * будет неудобно.
     */
    public final List<Integer> seatColors = new ArrayList<>();

    /** Цветовое гнездо места: из записи, иначе по номеру места. */
    public int seatColor(int seat) {
        if (seat >= 0 && seat < seatColors.size() && seatColors.get(seat) != null) {
            return Math.floorMod(seatColors.get(seat), 4);
        }
        return Math.floorMod(seat, 4);
    }
    /**
     * ВКЛЮЧЁННЫЕ ДОПОЛНЕНИЯ на момент партии: {@code expansions.<имя> → вкл}.
     *
     * <p>Добавлено 20.08.2026. Дополнения накладываются на свод ДО сборки партии
     * ({@code Expansions.applyTo}), но в запись не попадали — и по такой записи
     * партию было НЕ ПОВТОРИТЬ: тот же сид и свод при других тумблерах дают
     * другую игру. Для разбора багов это главное: дизайнер присылает лог, а из
     * лога должно быть видно всё, что партию породило.
     */
    public final Map<String, Boolean> expansions = new LinkedHashMap<>();
    public Integer winner;
    public String condition = "";
    /**
     * Если партия кончилась по келемию — сколько источников на поле осталось и
     * какой порог стоял в своде. Нужно, чтобы подпись конца не врала про
     * «кончился келемий», когда по правилам порог выше единицы. −1 у старых
     * записей и у партий, кончившихся иначе.
     */
    public int spawnLeft = -1;
    public int spawnThreshold = -1;
    public int rounds;
    /**
     * ПЕЧАТНЫЙ ЛИЧНЫЙ ЗАПАС: сколько жетонов КАЖДОГО вида войск у игрока всего.
     * Планшет игрока раньше считал этот предел сам — по числу жетонов, которые
     * успели появиться в записи, — и показывал «в запасе 0 из 1» у одного игрока и
     * «2 из 2» у другого, хотя у всех по 4 (ошибка, найдена 13.08.2026).
     */
    public final Map<String, Integer> unitStock = new LinkedHashMap<>();

    /** Печатный запас по роду войск; нет в записи — по правилам их четыре. */
    public int unitStockOf(String type) {
        Integer v = unitStock.get(type);
        return v == null || v <= 0 ? 4 : v;
    }
    /** Названия карт по идентификаторам — чтобы в зонах игроков был человеческий текст. */
    public final Map<String, String> cardNames = new LinkedHashMap<>();
    /** Статичная топология поля. */
    public final List<HexInfo> hexes = new ArrayList<>();
    /** Шаги партии: один кадр на одно событие движка. */
    public final List<Frame> frames = new ArrayList<>();

    // ==================== элементы записи ====================

    /** Неизменная часть гекса: где он и какого вида. */
    public static final class HexInfo {
        public String id;
        public int q;
        public int r;
        public String kind = "NORMAL";
    }

    /** Тайл зарождения на гексе. */
    public static final class Spawn {
        public boolean start;
        public int kelium;
        public int stack = 1;
        public boolean flipped;
    }

    /** Нейтральная постройка на рёбрах гекса. */
    public static final class Neutral {
        public boolean big;
        public final List<Integer> corners = new ArrayList<>();
        /**
         * ПРОЧНОСТЬ нейтральной постройки: сколько осталось и сколько было. Нужна,
         * чтобы рисовать её на поле сердечками — «сколько ещё бить» видно сразу,
         * без наведения мыши (просьба дизайнера 13.08.2026). Старые записи этих
         * полей не имеют; тогда берём печатное значение (большой 2, малый 1).
         */
        public int hp = 1;
        public int hpMax = 1;
    }

    /**
     * Жетон, лежащий на карте трофеев: чей он был, что это и сколько стоит. Номер
     * нужен, чтобы «небрежный» поворот на карте не дёргался при листании партии.
     */
    public static final class TrophyToken {
        public int uid;
        public int owner;
        public boolean building;
        public String type = "";
        public int value;
    }

    /**
     * КАРТА-СИМВОЛ ПОД ПЛАНШЕТОМ ВОЙСК (супер-модуль). В записи (всевидящей)
     * id карты пишется всегда — дизайнеру для разбора партии нужно видеть, ЧТО
     * подсунуто (заказ на цифровую версию, §3). Честный вид от лица места
     * ({@code kelium.observe.PublicView}) сам решает, что из этого показать:
     * закрытым соседям — только число и вскрытые.
     */
    public static final class Tucked {
        /** {@code container} или {@code arsenal}. */
        public String kind = "";
        public String cardId = "";
        public boolean revealed;
    }

    /** Изменяемая часть гекса на конкретном шаге. */
    public static final class HexState {
        public String id;
        /** Ячейка с ПЕЧАТНЫМ контейнером: −1 нет, 0..5 наземная, 6 воздушная. */
        public int containerCell = -1;
        /**
         * ЖЁЛТАЯ ЯЧЕЙКА (0..5, −1 если поле не размечено): только на ней
         * энергостанция даёт свой номинал. Смотреть на неё в проигрывателе
         * надо: она объясняет, почему станция №4 иногда кормит одним кубиком.
         */
        public int energyCell = -1;
        /**
         * Чей это гекс для слабой подкраски: место игрока или −1.
         * {@link #ownerBuilt} — стоит ли тут его здание (подкраска заметнее)
         * или это только зона стройки (еле-еле). Подкраска заменила цветную
         * подкладку под зданиями (просьба дизайнера 12.08.2026).
         */
        public int ownerTint = -1;
        public boolean ownerBuilt;
        public Spawn spawn;
        /** Владелец каждой из 6 сторон: uid жетона или −1. */
        public int[] sideOwner = new int[]{-1, -1, -1, -1, -1, -1};
        public final List<Neutral> neutrals = new ArrayList<>();
    }

    /** Жетон (здание или войско) на конкретном шаге. */
    public static final class Tok {
        public int uid;
        public int owner;
        public boolean building;
        /** Код типа: {@code command_center}/{@code miner}/… либо {@code infantry}/… */
        public String type = "";
        /** Гекс, где стоит; null — в резерве. */
        public String hexId;
        public int damage;
        public int hp;
        public int energySlots;
        public int energyPlaced;
        /**
         * Кубики, ПРОСТАИВАЮЩИЕ на источнике (энергостанция, ЦУ): они лежат на
         * жетоне и ждут Смены энергии. Рисуются в жёлтой зоне хранения, чтобы
         * было видно, сколько энергии ещё не пущено в дело.
         */
        public int energyIdle;
        public Integer level;
        public boolean alive = true;
        /** Кто держит жетон на трофейном месте (уничтожен в этом раунде). */
        public Integer capturedBy;
        /**
         * Войско ВСТАВЛЕНО В ЗДАНИЕ (uid здания) — рисуется значком внутри, ячейку
         * гекса не занимает и не атакуемо, пока здание живо. Раньше проигрыватель
         * ДОГАДЫВАЛСЯ об укрытии по совпадению рода войск и типа здания на гексе и
         * показывал спрятанными войска, которые ими не были.
         */
        public Integer insideBuildingUid;
    }

    /** Полное состояние одного игрока (всё, что показывает его зона). */
    public static final class Player {
        public int seat;
        /** Сторона планшета ВОЙСК: А, Б1… — ею определяются роды войск игрока. */
        public String side = "";
        /**
         * Сторона планшета ХРАНИЛИЩА. Пишется с 14.08.2026: сторону войск запись
         * несла давно, а хранилище — нет, и в полосе игрока с планшетом было
         * видно только половину его расклада. В старых записях поле пустое, и
         * тогда сторона просто не показывается — выдумывать её нельзя.
         */
        public String storageSide = "";
        public int coin;
        public int kelium;
        public int ammo;
        /** Обломки (бывшие «трофейные очки») в хранилище — черные кубики. */
        public int debris;
        /** Вместимость обломков на складе (см. {@code Storage.debrisMax}). */
        public int debrisCap;
        /** Жетонов на трофейном месте и суммарная их ценность. */
        /**
         * КАКИЕ ЯЧЕЙКИ СКЛАДА ОТКРЫВАЕТ КАЖДОЕ СКЛАДСКОЕ ЗДАНИЕ. Ключ — «miner-2»
         * или «plant-4», значение — строка вида «UK»: U универсальная, K под
         * келемий, A под боеприпасы.
         *
         * <p>Нужно, чтобы показать планшет как он лежит на столе: пока здание не
         * построено, оно СВОИМ ЖЕТОНОМ закрывает эти ячейки; построил — жетон ушёл
         * на поле, ячейки открылись (просьба дизайнера 13.08.2026). Раньше запись
         * несла только итоговый предел, и связь «здание ↔ его ячейки» показать было
         * нечем.
         */
        public final Map<String, String> storageCells = new LinkedHashMap<>();
        public int trophyTokens;
        public int trophyPoints;
        /**
         * ЖЕТОНЫ НА КАРТЕ ТРОФЕЕВ — что именно снесли и по сколько очков. Раньше в
         * записи было только их число, и показать карту трофеев «как на столе» было
         * нечем (просьба дизайнера 13.08.2026).
         */
        public final List<TrophyToken> trophyCard = new ArrayList<>();
        /**
         * ТРОФЕИ, ЗАДЕРЖАННЫЕ НА КАРТЕ «Трофейный склад» (b13) — ОТДЕЛЬНО от
         * обычного трофейного пространства: за столом это разные места, и
         * жетон отсюда не вернётся владельцу в ближайший Возврат. Раньше
         * запись их не различала (дыра §8 п.6 концепта «Командный пункт»).
         */
        public final List<TrophyToken> trophyHeld = new ArrayList<>();
        /**
         * КАРТЫ-СИМВОЛЫ ПОД ПЛАНШЕТОМ — раньше в запись не попадали вовсе
         * (дыра §8 п.3 концепта): ни факт (публичен), ни лица (нужны журналу).
         */
        public final List<Tucked> tucked = new ArrayList<>();
        /**
         * ЖЕТОНЫ ЩИТА на строках родов войск — лежат на планшете открыто,
         * в запись не писались (дыра §8 п.5 концепта). Коды родов войск.
         */
        public final List<String> shieldedKinds = new ArrayList<>();
        public int containers;
        public int containerCap;
        public int keliumCap;
        public int ammoCap;
        public int storeCap;
        public final Map<String, Integer> tech = new LinkedHashMap<>();
        public int redModules;
        public int blueModules;
        public int goldModules;
        public int redHalves;
        public int blueHalves;
        /**
         * КУДА ПОСТАВЛЕН КРАСНЫЙ МОДУЛЬ: код рода войск → сам жетон. Красный
         * ложится на вторичный ряд атаки РОДА, а не на здание, поэтому ключ —
         * {@code infantry/vehicle/aircraft/tower}. Рода без модуля в карте нет.
         */
        public final Map<String, Module> redPlaced = new LinkedHashMap<>();
        /**
         * КУДА ПОСТАВЛЕН СИНИЙ МОДУЛЬ: код военного здания → сам жетон. Синий
         * ложится на сборочную «1» ЗДАНИЯ, ключ — {@code barracks/factory/airbase}.
         */
        public final Map<String, Module> bluePlaced = new LinkedHashMap<>();
        public final List<String> arsenalHand = new ArrayList<>();
        public final List<String> arsenalInstalled = new ArrayList<>();
        /**
         * «МАНДАТ СОВЕТА» (супер-арсенал sa8): отдельное место под картой —
         * либо ОДНА карта арсенала (тогда {@code mandateContainers == 0}),
         * либо {@code mandateContainers} (0..2) мест под контейнеры. Null/0 —
         * место свободно или карты sa8 нет вовсе.
         */
        public String mandateArsenalCard = null;
        public int mandateContainers = 0;
        public final List<String> superArsenal = new ArrayList<>();
        public final List<String> objectiveHand = new ArrayList<>();
        public final List<String> orderHand = new ArrayList<>();
        public final List<String> orderPlayed = new ArrayList<>();
        public String orderSetAside;
        public String orderColor;
        public final List<String> storageTokens = new ArrayList<>();
        public String superObjective;
        public int superProgress;
        /**
         * Прогресс супер-задания ПО ЧАСТЯМ: вид части → сколько внесено. Именно
         * это показывает вкладка супер-заданий: что собралось, а что нет
         * (просьба дизайнера 12.08.2026); одного числа для этого мало.
         */
        public final Map<String, Integer> superParts = new LinkedHashMap<>();
        public boolean superComplete;

        /**
         * ВТОРАЯ ПОЛОВИНА СУПЕР-ЗАДАНИЯ (версия правил 3.0, 17.08.2026).
         *
         * <p>Прежние поля {@code superProgress} и {@code superParts} остались от
         * той версии, где вклад вносили ЧАСТЯМИ. Теперь вносят всё разом, а после
         * вскрытия на карте живёт СЧЁТЧИК ЗАПУСКА: сколько ячеек ещё занято и
         * какой символ напечатан на каждой. Без этих двух полей проигрыватель
         * физически не мог показать вторую половину — он показывал «рубашку»,
         * которой в правилах уже нет.
         */
        public int superCells = -1;
        public final List<String> superCellSymbols = new ArrayList<>();
        public int cuTokens;
        public boolean ownCuToken = true;
        public String startHex = "";
        /** Разбивка победных очков (ключ {@code total} — итог). */
        public final Map<String, Integer> vp = new LinkedHashMap<>();
    }

    /**
     * ПОСТАВЛЕННЫЙ МОДУЛЬ — что именно за жетон и какой стороной он лежит.
     * Одного цвета мало: у красных и синих по четыре разных жетона, и без id
     * планшет показывает «модуль стоит», не отвечая какой. Сторона важна не
     * меньше: позолочённый работает сильнее, а перевернуть его назад нельзя.
     */
    public static final class Module {
        /** Id жетона из комплекта: {@code M1…M4} у красных, {@code C1…C4} у синих. */
        public String id = "";
        /** Лежит позолочённой стороной. */
        public boolean gold;
        /** Цели вторичной атаки, которые открывает красный жетон. */
        public final List<String> targets = new ArrayList<>();
        /** Характеристика, которую красный жетон поднимает вместо целей. */
        public String stat;
        public int plus;
        /** Цена вторичной атаки в боеприпасах (красный) или сборки (синий). */
        public int ammo;
        /** Сколько войск за сборку даёт синий жетон. */
        public int units;
    }

    /** Снимок всего, что рисуется, на одном шаге. */
    public static final class Snapshot {
        public int round;
        public int circle;
        public int firstPlayer;
        /** Место, чей сейчас ход (null — общая фаза раунда). */
        public Integer active;
        public String market;
        /** Открытые карты СУПЕР-АРСЕНАЛА: трек (left/middle/right) → id карты. */
        public final Map<String, String> superArsenalOffer = new LinkedHashMap<>();
        /**
         * ЯЧЕЙКИ ПРЕДЛОЖЕНИЙ КАРТЫ РЫНКА: [сторона][ячейка] = место игрока, чей
         * кубик келемия там стоит, либо −1. Сторона 0 — левое предложение, 1 —
         * правое. Нужно, чтобы на планшете рынка было видно занятые ячейки и
         * ЧЬИ они (просьба дизайнера 12.08.2026).
         */
        public int[][] marketCells = {{-1, -1}, {-1, -1}};
        /**
         * КТО СТОИТ НА ТРЕКАХ НАУКИ: трек → по шагам список мест в порядке
         * прихода. Прежде проигрыватель знал только «на каком шаге игрок» и
         * поэтому не мог показать ни ячейки, ни очередь прихода.
         */
        public final Map<String, List<List<Integer>>> techOccupancy = new LinkedHashMap<>();
        public final List<HexState> hexes = new ArrayList<>();
        public final List<Tok> tokens = new ArrayList<>();
        public final List<Player> players = new ArrayList<>();

        /**
         * КОЛОДЫ НА СТОЛЕ на этом шаге: имя набора → что в ней лежит.
         *
         * <p>Лежит в КАЖДОМ кадре нарочно. Просьба дизайнера — крутить ленту
         * времени и видеть, как колоды и сбросы меняются по ходу партии; а для
         * этого состояние колоды обязано быть частью шага, а не шапки записи.
         * Гексы лежат в шапке потому, что за партию не меняются, — колоды меняются
         * каждый ход.
         */
        public final Map<String, DeckState> decks = new LinkedHashMap<>();

        /**
         * ДВЕ ОТКРЫТЫЕ КАРТЫ АРСЕНАЛА рядом с колодой (витрина обмена в Науке).
         * Первая в списке лежит слева.
         */
        public final List<String> arsenalDisplay = new ArrayList<>();
    }

    /**
     * ОДНА КОЛОДА И ЕЁ СБРОС, оба — В ПОРЯДКЕ СВЕРХУ ВНИЗ.
     *
     * <p>ВНИМАНИЕ НА ПОРЯДОК. В движке {@code Deck.draw} снимает карту с КОНЦА
     * списка {@code drawPile}, то есть верх колоды — это последний элемент. Здесь
     * порядок развёрнут в человеческий: {@code draw.get(0)} — та карта, которая
     * уйдёт следующей. Иначе список в проигрывателе показывал бы колоду вверх
     * ногами, и заметить это было бы нечем.
     *
     * <p>Сброс тоже сверху вниз: {@code discard.get(0)} — последняя сброшенная
     * кем-либо карта, она и лежит лицом вверх на виду.
     */
    public static final class DeckState {
        /** Карты в колоде, СВЕРХУ ВНИЗ: нулевая уйдёт следующей. */
        public final List<String> draw = new ArrayList<>();
        /** Карты в сбросе, СВЕРХУ ВНИЗ: нулевая сброшена последней. */
        public final List<String> discard = new ArrayList<>();
    }

    /** Подсветка происходящего на этом шаге (§4.4 заказа). */
    public static final class Highlight {
        /** Перемещения: пары «откуда, куда». */
        public final List<String[]> moves = new ArrayList<>();
        /** Удары: пары «откуда, куда». */
        public final List<String[]> attacks = new ArrayList<>();
        /** Гексы, где что-то построено/выставлено. */
        public final List<String> builds = new ArrayList<>();
        /** Гексы, где получен урон. */
        public final List<String> damaged = new ArrayList<>();
        /** Гексы, где жетон уничтожен. */
        public final List<String> destroyed = new ArrayList<>();

        public boolean isEmpty() {
            return moves.isEmpty() && attacks.isEmpty() && builds.isEmpty()
                && damaged.isEmpty() && destroyed.isEmpty();
        }
    }

    /** Мысль бота от первого лица. */
    public static final class Thought {
        public int seat;
        public String text = "";

        public Thought() {
        }

        public Thought(int seat, String text) {
            this.seat = seat;
            this.text = text;
        }
    }

    /** Один шаг партии = одно событие движка. */
    /**
     * РАЗЫГРАННАЯ КАРТА ПРИКАЗА одного игрока — для наглядной «руки на столе»
     * в проигрывателе (просьба дизайнера 12.08.2026). Хранит не только сам
     * приказ, но и то, что с ним стало: срезало ли совпадение два действия до
     * одного, открылась ли нижняя половина, какие действия уже сыграны.
     */
    public static final class OrderPlay {
        public int seat;
        public int round;
        public int circle;
        public String card = "";
        /** Код верхнего приказа; для карты БЕЗОПАСНОСТЬ — {@code joker}. */
        public String top = "";
        public final List<String> topActions = new ArrayList<>();
        /** Сколько действий сверху разрешено: 2 обычно, 1 при совпадении. */
        public int topAllowed = 2;
        public boolean coincided;
        /** Код нижнего приказа или null. */
        public String bottom;
        public final List<String> bottomActions = new ArrayList<>();
        public boolean bottomOpen;
        public boolean maneuver;
        /**
         * Кадр, на котором карту ВСКРЫЛИ. Порядок в круге такой: сначала все
         * одновременно ВЫБИРАЮТ карту, потом все одновременно её ОТКРЫВАЮТ, и
         * только потом ходят ПО ОЧЕРЕДИ. Поэтому на этом кадре карты всех
         * игроков должны появиться на столе разом.
         */
        public int revealFrame;
        /**
         * Кадр, на котором начался ХОД по этой карте, или −1, если до него ещё
         * не дошло. Что игрок успел сыграть, проигрыватель считает сам — пробегая
         * кадры от этого и до текущего: так подсветка «сыграно/осталось» честно
         * живёт во времени и не забегает вперёд даже при перемотке назад.
         */
        public int turnFrame = -1;
    }

    /** Все карты приказов, разыгранные к этому шагу (в порядке розыгрыша). */
    public final List<OrderPlay> orderPlays = new ArrayList<>();

    public static final class Frame {
        public String type = "";
        public int round;
        public int circle;
        /** Место, к которому относится событие (null — общее). */
        public Integer seat;
        /** Строка лога по-русски. */
        public String log = "";
        /** Мысли ботов, прозвучавшие перед этим событием. */
        public final List<Thought> thoughts = new ArrayList<>();
        public Highlight highlight = new Highlight();
        public Snapshot snapshot;
        /** Шаг относится к бою (для перехода «к следующему бою»). */
        public boolean combat;
    }

    // ==================== удобные выборки ====================

    /** Кадр по номеру (с зажимом в границы); null — записи нет. */
    public Frame frame(int index) {
        if (frames.isEmpty()) {
            return null;
        }
        return frames.get(Math.max(0, Math.min(frames.size() - 1, index)));
    }

    /** Человеческое имя игрока: «Игрок 1 · Стратег · ястреб». */
    public String playerName(int seat) {
        String bot = seat < seatLabels.size() ? seatLabels.get(seat) : "бот";
        return "Игрок " + (seat + 1) + " · " + bot;
    }

    /**
     * Название карты по идентификатору (или сам идентификатор).
     *
     * <p>ЭТО ДАННЫЕ, А НЕ ПОДПИСЬ ДЛЯ ЭКРАНА: запасной вариант здесь — сам код
     * карты. Всё, что видит человек, обязано идти через
     * {@code kelium.gui.replay2.Names.card}, иначе на планшет попадёт
     * {@code blue_acq}.
     */
    public String cardName(String id) {
        if (id == null) {
            return "—";
        }
        String n = cardNames.get(id);
        return n == null || n.isBlank() ? id : n;
    }

    /** Номер первого кадра указанного раунда (или 0). */
    public int firstFrameOfRound(int round) {
        for (int i = 0; i < frames.size(); i++) {
            if (frames.get(i).round >= round) {
                return i;
            }
        }
        return Math.max(0, frames.size() - 1);
    }

    /**
     * Снять СНИМОК с живого состояния движка. Живёт здесь, а не в приложении,
     * потому что этим же снимком рисует картинки отчётов {@link SvgFieldRenderer}
     * — рендер у обоих один.
     */
    public static Snapshot snapshotOf(GameState s, Integer active) {
        Snapshot snap = new Snapshot();
        snap.round = s.round;
        snap.circle = s.circle;
        snap.firstPlayer = s.firstPlayer;
        snap.active = active;
        snap.market = s.marketActive;
        snap.superArsenalOffer.putAll(new TreeMap<>(s.superArsenalOffer));
        for (int side = 0; side < s.marketCells.length; side++) {
            snap.marketCells[side] = s.marketCells[side].clone();
        }
        for (String track : s.tech.tracks) {
            List<List<Integer>> perStep = new ArrayList<>();
            for (List<Integer> seats : s.tech.occupancy.get(track)) {
                perStep.add(new ArrayList<>(seats));
            }
            snap.techOccupancy.put(track, perStep);
            // (колоды заполняются ниже, после игроков)
        }

        // Чей гекс и чья зона стройки — для слабой подкраски в рисовальщике.
        // Гексы со своим зданием красятся заметнее, зона стройки — еле-еле.
        Map<String, Integer> builtBy = new java.util.HashMap<>();
        Map<String, Integer> zoneOf = new java.util.HashMap<>();
        for (int seat = 0; seat < s.players.size(); seat++) {
            for (kelium.core.BuildingToken b : s.player(seat).buildingsOnField()) {
                builtBy.putIfAbsent(b.hexId, seat);
            }
            for (String hid : kelium.engine.Actions.buildableHexes(s, seat)) {
                zoneOf.putIfAbsent(hid, seat);
            }
        }

        for (Hex h : s.field.hexes.values()) {
            HexState hs = new HexState();
            hs.id = h.id;
            hs.containerCell = h.containerCell;
            hs.energyCell = h.energyCell;
            Integer built = builtBy.get(h.id);
            if (built != null) {
                hs.ownerTint = built;
                hs.ownerBuilt = true;
            } else if (zoneOf.get(h.id) != null) {
                hs.ownerTint = zoneOf.get(h.id);
            }
            if (h.spawnTile != null) {
                Spawn sp = new Spawn();
                sp.start = h.spawnTile.isStart;
                sp.kelium = Math.max(0, h.spawnTile.kelium);
                sp.stack = h.spawnTile.stack;
                sp.flipped = h.spawnTile.flipped;
                hs.spawn = sp;
            }
            for (int i = 0; i < 6; i++) {
                hs.sideOwner[i] = h.sideOwner[i] == null ? -1 : h.sideOwner[i];
            }
            for (Hex.NeutralBuilding nb : h.neutrals) {
                Neutral n = new Neutral();
                n.big = nb.big;
                n.hp = nb.hp;
                n.hpMax = nb.big ? 2 : 1;      // печатная прочность постройки
                if (nb.corners != null) {
                    n.corners.addAll(nb.corners);
                }
                hs.neutrals.add(n);
            }
            snap.hexes.add(hs);
        }

        for (PlayerState p : s.players) {
            for (BuildingToken b : p.buildings) {
                Tok t = new Tok();
                t.uid = b.uid;
                t.owner = b.owner;
                t.building = true;
                t.type = b.type.code;
                t.hexId = b.hexId;
                t.damage = b.damage;
                t.hp = b.hp;
                t.energySlots = b.energySlots;
                t.energyPlaced = b.energyPlaced;
                t.energyIdle = b.energyIdle;
                t.level = b.level;
                t.alive = b.alive();
                t.capturedBy = b.capturedBy;
                snap.tokens.add(t);
            }
            for (UnitToken u : p.units) {
                Tok t = new Tok();
                t.uid = u.uid;
                t.owner = u.owner;
                t.building = false;
                t.type = u.type.code;
                t.hexId = u.hexId;
                t.damage = u.damage;
                t.hp = u.hp;
                t.alive = u.alive();
                t.capturedBy = u.capturedBy;
                t.insideBuildingUid = u.insideBuildingUid;
                snap.tokens.add(t);
            }
            snap.players.add(playerView(s, p));
        }
        // КОЛОДЫ И СБРОСЫ — с развортом в человеческий порядок «сверху вниз».
        // Движок снимает карту с КОНЦА drawPile, значит верх колоды — последний
        // элемент; в сбросе последняя положенная карта тоже в конце, а лежит она
        // сверху. Разворачиваем оба списка здесь, один раз, чтобы каждый читатель
        // записи не делал этого сам и не забыл.
        if (s.decks != null) {
            for (var e : s.decks.entrySet()) {
                kelium.core.Deck d = e.getValue();
                if (d == null) {
                    continue;
                }
                DeckState ds = new DeckState();
                for (int i = d.drawPile.size() - 1; i >= 0; i--) {
                    ds.draw.add(d.drawPile.get(i));
                }
                for (int i = d.discardPile.size() - 1; i >= 0; i--) {
                    ds.discard.add(d.discardPile.get(i));
                }
                snap.decks.put(e.getKey(), ds);
            }
        }
        snap.arsenalDisplay.addAll(s.arsenalDisplay);
        return snap;
    }

    private static Player playerView(GameState s, PlayerState p) {
        Player v = new Player();
        v.seat = p.seat;
        v.side = p.board.troop.side;
        v.storageSide = p.board.storage.side;
        v.coin = p.resources.coin();
        v.kelium = p.resources.kelium();
        v.ammo = p.resources.ammo();
        v.debris = p.resources.debris();
        v.debrisCap = kelium.engine.Storage.debrisMax(s, p);
        v.trophyTokens = p.trophySpace.size();
        v.trophyPoints = p.trophySpacePoints();
        // Сами жетоны на карте трофеев: их надо показать «как на столе»
        for (kelium.core.Token t : p.trophySpace) {
            TrophyToken tt = new TrophyToken();
            tt.uid = t.uid();
            tt.owner = t.owner();
            tt.building = t instanceof kelium.core.BuildingToken;
            tt.type = t instanceof kelium.core.BuildingToken bt ? bt.type.code
                : ((kelium.core.UnitToken) t).type.code;
            tt.value = t.trophyValue();
            v.trophyCard.add(tt);
        }
        for (kelium.core.Token t : p.trophyHeldOnCards) {
            TrophyToken tt = new TrophyToken();
            tt.uid = t.uid();
            tt.owner = t.owner();
            tt.building = t instanceof kelium.core.BuildingToken;
            tt.type = t instanceof kelium.core.BuildingToken bt ? bt.type.code
                : ((kelium.core.UnitToken) t).type.code;
            tt.value = t.trophyValue();
            v.trophyHeld.add(tt);
        }
        for (kelium.core.PlayerState.TuckedCard t : p.tucked) {
            Tucked x = new Tucked();
            x.kind = t.kind;
            x.cardId = t.cardId;
            x.revealed = t.revealed;
            v.tucked.add(x);
        }
        for (kelium.core.UnitType u : p.shieldedKinds) {
            v.shieldedKinds.add(u.code);
        }
        v.containers = p.containers;
        int cap = Storage.containerCapacity(s, p);
        v.containerCap = cap == Integer.MAX_VALUE ? -1 : cap;
        // раскладка ячеек склада по складским зданиям — см. Player.storageCells
        for (int lv = 1; lv <= 4; lv++) {
            try {
                v.storageCells.put("miner-" + lv, p.board.storage.minerCells(lv));
                v.storageCells.put("plant-" + lv, p.board.storage.plantCells(lv));
            } catch (RuntimeException e) {
                break;      // сторона планшета без такой раскладки — не беда
            }
        }
        // ПРЕДЕЛЫ СКЛАДА СЧИТАЮТСЯ С ПАРТИЕЙ В РУКАХ, как их считает движок:
        // способности арсенала добавляют ячейки, и без них запись показывала
        // меньший склад, чем игра разрешала. В проигрывателе это выглядело как
        // «занято 13 из 11», а предел обломков (он-то считался правильно, через
        // partию) уходил в минус: «обломки 0 из −2».
        v.keliumCap = Storage.keliumMax(s, p);
        v.ammoCap = Storage.ammoMax(s, p);
        v.storeCap = Storage.totalMax(s, p);
        v.tech.putAll(new TreeMap<>(p.techSteps));
        v.redModules = p.redModules;
        v.blueModules = p.blueModules;
        v.goldModules = p.goldModules;
        // Половинок модулей в игре больше нет (решение дизайнера 13.08.2026):
        // поля записи оставлены нулями, чтобы старые файлы записей читались.
        v.redHalves = 0;
        v.blueHalves = 0;
        p.redPlacements.forEach((unit, spec) ->
            v.redPlaced.put(unit.code, moduleOf(spec)));
        p.bluePlacements.forEach((building, spec) ->
            v.bluePlaced.put(building.code, moduleOf(spec)));
        v.arsenalHand.addAll(p.arsenalHand);
        v.arsenalInstalled.addAll(p.arsenalInstalled);
        v.mandateArsenalCard = p.mandateArsenalCard;
        v.mandateContainers = p.mandateContainers;
        v.superArsenal.addAll(p.superArsenalCards);
        v.objectiveHand.addAll(p.objectiveHand);
        v.orderHand.addAll(p.orderHand);
        v.orderPlayed.addAll(p.orderPlayed);
        v.orderSetAside = p.orderSetAside;
        v.orderColor = p.orderColor;
        v.storageTokens.addAll(p.storageTokens);
        v.superObjective = p.superObjective;
        v.superProgress = p.superObjectiveProgress;
        v.superParts.putAll(new TreeMap<>(p.superPartProgress));
        v.superComplete = p.superObjectiveComplete;
        v.superCells = p.superCells;
        v.superCellSymbols.addAll(p.superCellSymbols);
        v.cuTokens = p.cuDestructionTokens;
        v.ownCuToken = p.ownCuTokenAvailable;
        v.startHex = p.startHex;
        v.vp.putAll(Scoring.scorePlayer(s, p.seat));
        return v;
    }

    /**
     * ЖЕТОН МОДУЛЯ ИЗ СЫРОГО РАЗМЕЩЕНИЯ ДВИЖКА. Внутри движка размещение — это
     * свободная карта {@code Map<String,Object>}, набор ключей у которой зависит
     * от вида жетона: у красного либо {@code targets} (открывает цели), либо
     * {@code stat}+{@code plus} (поднимает характеристику рода), у синего —
     * {@code units} и {@code ammo}. Здесь всё это раскладывается по полям, чтобы
     * рисующей стороне не пришлось разбирать карту заново и гадать о типах.
     */
    private static Module moduleOf(Map<String, Object> spec) {
        Module m = new Module();
        if (spec == null) {
            return m;
        }
        m.id = String.valueOf(spec.getOrDefault("id", ""));
        m.gold = Boolean.TRUE.equals(spec.get("gold"));
        Object tg = spec.get("targets");
        if (tg instanceof String[] arr) {
            m.targets.addAll(java.util.Arrays.asList(arr));
        } else if (tg instanceof List<?> list) {
            for (Object o : list) {
                m.targets.add(String.valueOf(o));
            }
        }
        if (spec.get("stat") != null) {
            m.stat = String.valueOf(spec.get("stat"));
        }
        m.plus = spec.get("plus") instanceof Number n ? n.intValue() : 0;
        m.ammo = spec.get("ammo") instanceof Number n ? n.intValue() : 0;
        m.units = spec.get("units") instanceof Number n ? n.intValue() : 0;
        return m;
    }

    // ==================== сохранение и загрузка ====================

    /** Записать в файл JSON (UTF-8). */
    public void save(Path file) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, Json.write(toMap()), StandardCharsets.UTF_8);
    }

    /** Прочитать запись из файла JSON. */
    public static ReplayRecord load(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        Object root = Json.parse(text);
        if (!(root instanceof Map<?, ?>)) {
            throw new IOException("это не запись партии: в файле не объект JSON");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) root;
        if (!FORMAT_TAG.equals(Json.s(m, "format"))) {
            throw new IOException("это не запись партии «Кристаллов Раздора» "
                + "(ожидался формат " + FORMAT_TAG + ")");
        }
        int ver = Json.i(m, "version");
        if (ver > FORMAT_VERSION) {
            throw new IOException("запись сделана более новой версией приложения "
                + "(формат " + ver + ", эта версия понимает " + FORMAT_VERSION + ")");
        }
        try {
            return fromMap(m);
        } catch (RuntimeException e) {
            throw new IOException("запись читается, но она неполная: " + e.getMessage(), e);
        }
    }

    // -------------------- запись в структуру --------------------
    /** Представить запись как вложенные карты/списки — прямо под {@link Json}. */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("format", FORMAT_TAG);
        m.put("version", FORMAT_VERSION);
        m.put("ruleset", ruleset);
        m.put("players", players);
        m.put("seed", seed);
        m.put("seatIds", seatIds);
        m.put("expansions", expansions);
        m.put("seatLabels", seatLabels);
        m.put("sides", sides);
        m.put("scenarioId", scenarioId);
        m.put("scenarioFile", scenarioFile);
        m.put("cuFacing", cuFacing);
        m.put("seatColors", seatColors);
        m.put("winner", winner);
        m.put("condition", condition);
        m.put("spawnLeft", spawnLeft);
        m.put("spawnThreshold", spawnThreshold);
        m.put("rounds", rounds);
        m.put("unitStock", unitStock);
        m.put("cardNames", cardNames);
        List<Object> hs = new ArrayList<>();
        for (HexInfo h : hexes) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", h.id);
            o.put("q", h.q);
            o.put("r", h.r);
            o.put("kind", h.kind);
            hs.add(o);
        }
        m.put("hexes", hs);

        // РАЗЫГРАННЫЕ ПРИКАЗЫ — их показывает полоска карт у зоны игрока,
        // поэтому они обязаны сохраняться вместе с записью партии.
        List<Object> ops = new ArrayList<>();
        for (OrderPlay op : orderPlays) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("seat", op.seat);
            o.put("round", op.round);
            o.put("circle", op.circle);
            o.put("revealFrame", op.revealFrame);
            o.put("turnFrame", op.turnFrame);
            o.put("card", op.card);
            o.put("top", op.top);
            o.put("topActions", op.topActions);
            o.put("topAllowed", op.topAllowed);
            o.put("coincided", op.coincided ? 1 : 0);
            o.put("bottom", op.bottom);
            o.put("bottomActions", op.bottomActions);
            o.put("bottomOpen", op.bottomOpen ? 1 : 0);
            o.put("maneuver", op.maneuver ? 1 : 0);
            ops.add(o);
        }
        m.put("orderPlays", ops);

        List<Object> fs = new ArrayList<>();
        String prevSnap = null;
        Map<String, Object> prevSnapMap = null;
        Snapshot prevSnapshot = null;
        for (Frame f : frames) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("type", f.type);
            o.put("round", f.round);
            o.put("circle", f.circle);
            o.put("seat", f.seat);
            o.put("log", f.log);
            o.put("combat", f.combat);
            List<Object> th = new ArrayList<>();
            for (Thought t : f.thoughts) {
                Map<String, Object> to = new LinkedHashMap<>();
                to.put("seat", t.seat);
                to.put("text", t.text);
                th.add(to);
            }
            o.put("thoughts", th);
            o.put("hl", highlightToMap(f.highlight));
            // Снимок повторяется дословно — не дублируем: null значит «как в
            // предыдущем кадре». На длинной партии это заметно уменьшает файл.
            // Карту снимка строим ОДИН раз (раньше собиралась дважды: на
            // сравнение и на запись — лишняя работа на каждом из сотен кадров).
            Map<String, Object> snapMap = null;
            String snapJson = null;
            if (f.snapshot != null) {
                snapMap = f.snapshot == prevSnapshot && prevSnapMap != null
                    ? prevSnapMap : snapshotToMap(f.snapshot);
                snapJson = Json.write(snapMap);
            }
            if (snapJson != null && snapJson.equals(prevSnap)) {
                o.put("snap", null);
            } else {
                o.put("snap", snapMap);
                prevSnap = snapJson;
            }
            prevSnapMap = snapMap;
            prevSnapshot = f.snapshot;
            fs.add(o);
        }
        m.put("frames", fs);
        return m;
    }

    private static Map<String, Object> highlightToMap(Highlight h) {
        if (h == null || h.isEmpty()) {
            return null;
        }
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("moves", pairs(h.moves));
        o.put("attacks", pairs(h.attacks));
        o.put("builds", h.builds);
        o.put("damaged", h.damaged);
        o.put("destroyed", h.destroyed);
        return o;
    }

    private static List<Object> pairs(List<String[]> src) {
        List<Object> out = new ArrayList<>();
        for (String[] p : src) {
            out.add(List.of(p[0], p[1]));
        }
        return out;
    }

    private static Map<String, Object> snapshotToMap(Snapshot s) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("round", s.round);
        o.put("circle", s.circle);
        o.put("first", s.firstPlayer);
        o.put("active", s.active);
        o.put("market", s.market);
        o.put("superArs", s.superArsenalOffer);
        List<Object> mc = new ArrayList<>();
        for (int[] side : s.marketCells) {
            List<Object> row = new ArrayList<>();
            for (int seat : side) {
                row.add(seat);
            }
            mc.add(row);
        }
        o.put("marketCells", mc);
        Map<String, Object> occ = new LinkedHashMap<>();
        for (var e : s.techOccupancy.entrySet()) {
            List<Object> steps = new ArrayList<>();
            for (List<Integer> seats : e.getValue()) {
                steps.add(new ArrayList<>(seats));
            }
            occ.put(e.getKey(), steps);
        }
        o.put("techOcc", occ);
        // КОЛОДЫ: короткие ключи, потому что этот блок лежит в КАЖДОМ кадре.
        // Замер: три колоды дают около килобайта на кадр — против семи мегабайт
        // всей записи это терпимо, а иначе ленту времени по колодам не покрутить.
        Map<String, Object> dk = new LinkedHashMap<>();
        for (var e : s.decks.entrySet()) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("d", new ArrayList<>(e.getValue().draw));
            one.put("s", new ArrayList<>(e.getValue().discard));
            dk.put(e.getKey(), one);
        }
        o.put("decks", dk);
        o.put("arsDisplay", new ArrayList<>(s.arsenalDisplay));
        List<Object> hx = new ArrayList<>();
        for (HexState h : s.hexes) {
            Map<String, Object> ho = new LinkedHashMap<>();
            ho.put("id", h.id);
            ho.put("cont", h.containerCell);
            ho.put("ecell", h.energyCell);
            ho.put("own", h.ownerTint);
            ho.put("ownb", h.ownerBuilt ? 1 : 0);
            if (h.spawn != null) {
                Map<String, Object> sp = new LinkedHashMap<>();
                sp.put("start", h.spawn.start);
                sp.put("kelium", h.spawn.kelium);
                sp.put("stack", h.spawn.stack);
                sp.put("flipped", h.spawn.flipped);
                ho.put("spawn", sp);
            }
            ho.put("sides", h.sideOwner);
            if (!h.neutrals.isEmpty()) {
                List<Object> ns = new ArrayList<>();
                for (Neutral n : h.neutrals) {
                    Map<String, Object> no = new LinkedHashMap<>();
                    no.put("big", n.big);
                    no.put("corners", n.corners);
                    no.put("hp", n.hp);
                    no.put("hpMax", n.hpMax);
                    ns.add(no);
                }
                ho.put("neutrals", ns);
            }
            hx.add(ho);
        }
        o.put("hexes", hx);
        List<Object> tk = new ArrayList<>();
        for (Tok t : s.tokens) {
            Map<String, Object> to = new LinkedHashMap<>();
            to.put("uid", t.uid);
            to.put("owner", t.owner);
            to.put("bld", t.building);
            to.put("type", t.type);
            to.put("hex", t.hexId);
            to.put("dmg", t.damage);
            to.put("hp", t.hp);
            to.put("es", t.energySlots);
            to.put("ep", t.energyPlaced);
            to.put("ei", t.energyIdle);
            to.put("lvl", t.level);
            to.put("alive", t.alive);
            to.put("cap", t.capturedBy);
            to.put("in", t.insideBuildingUid);   // войско внутри этого здания
            tk.add(to);
        }
        o.put("tokens", tk);
        List<Object> ps = new ArrayList<>();
        for (Player p : s.players) {
            ps.add(playerToMap(p));
        }
        o.put("players", ps);
        return o;
    }

    private static Map<String, Object> playerToMap(Player p) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("seat", p.seat);
        o.put("side", p.side);
        o.put("storeSide", p.storageSide);
        o.put("coin", p.coin);
        o.put("kelium", p.kelium);
        o.put("ammo", p.ammo);
        o.put("debris", p.debris);
        o.put("debrisCap", p.debrisCap);
        o.put("storageCells", p.storageCells);
        o.put("trophyTokens", p.trophyTokens);
        o.put("trophyPoints", p.trophyPoints);
        if (!p.trophyCard.isEmpty()) {
            List<Object> tc = new ArrayList<>();
            for (TrophyToken t : p.trophyCard) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("uid", t.uid);
                m.put("owner", t.owner);
                m.put("building", t.building);
                m.put("type", t.type);
                m.put("value", t.value);
                tc.add(m);
            }
            o.put("trophyCard", tc);
        }
        if (!p.trophyHeld.isEmpty()) {
            List<Object> th = new ArrayList<>();
            for (TrophyToken t : p.trophyHeld) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("uid", t.uid);
                m.put("owner", t.owner);
                m.put("building", t.building);
                m.put("type", t.type);
                m.put("value", t.value);
                th.add(m);
            }
            o.put("trophyHeld", th);
        }
        if (!p.tucked.isEmpty()) {
            List<Object> tu = new ArrayList<>();
            for (Tucked t : p.tucked) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("kind", t.kind);
                m.put("card", t.cardId);
                m.put("rev", t.revealed);
                tu.add(m);
            }
            o.put("tucked", tu);
        }
        if (!p.shieldedKinds.isEmpty()) {
            o.put("shields", p.shieldedKinds);
        }
        o.put("containers", p.containers);
        o.put("containerCap", p.containerCap);
        o.put("keliumCap", p.keliumCap);
        o.put("ammoCap", p.ammoCap);
        o.put("storeCap", p.storeCap);
        o.put("tech", p.tech);
        o.put("red", p.redModules);
        o.put("blue", p.blueModules);
        o.put("gold", p.goldModules);
        o.put("redHalves", p.redHalves);
        o.put("blueHalves", p.blueHalves);
        if (!p.redPlaced.isEmpty()) {
            o.put("redPlaced", modulesToMap(p.redPlaced));
        }
        if (!p.bluePlaced.isEmpty()) {
            o.put("bluePlaced", modulesToMap(p.bluePlaced));
        }
        o.put("arsHand", p.arsenalHand);
        o.put("arsInst", p.arsenalInstalled);
        if (p.mandateArsenalCard != null) {
            o.put("mandCard", p.mandateArsenalCard);
        }
        if (p.mandateContainers != 0) {
            o.put("mandCont", p.mandateContainers);
        }
        o.put("superArs", p.superArsenal);
        o.put("objHand", p.objectiveHand);
        o.put("ordHand", p.orderHand);
        o.put("ordPlayed", p.orderPlayed);
        o.put("ordAside", p.orderSetAside);
        o.put("ordColor", p.orderColor);
        o.put("store", p.storageTokens);
        o.put("super", p.superObjective);
        o.put("superProgress", p.superProgress);
        o.put("superParts", p.superParts);
        o.put("superComplete", p.superComplete);
        o.put("cuTokens", p.cuTokens);
        o.put("ownCu", p.ownCuToken);
        o.put("startHex", p.startHex);
        o.put("vp", p.vp);
        return o;
    }

    // -------------------- чтение из структуры --------------------
    @SuppressWarnings("unchecked")
    private static ReplayRecord fromMap(Map<String, Object> m) {
        ReplayRecord r = new ReplayRecord();
        r.ruleset = orEmpty(Json.s(m, "ruleset"));
        r.players = Json.i(m, "players");
        Object sd = m.get("seed");
        r.seed = sd instanceof Number n ? n.longValue() : 0L;
        r.seatIds.addAll(Json.strings(m, "seatIds"));
        if (m.get("expansions") instanceof Map<?, ?> ex) {
            for (Map.Entry<?, ?> e : ex.entrySet()) {
                r.expansions.put(String.valueOf(e.getKey()),
                    Boolean.TRUE.equals(e.getValue()) || "true".equals(String.valueOf(e.getValue())));
            }
        }
        r.seatLabels.addAll(Json.strings(m, "seatLabels"));
        r.sides.addAll(Json.strings(m, "sides"));
        r.scenarioId = Json.s(m, "scenarioId");
        r.scenarioFile = Json.s(m, "scenarioFile");
        for (Object o : Json.list(m, "seatColors")) {
            r.seatColors.add(o instanceof Number n3 ? n3.intValue() : null);
        }
        for (Object o : Json.list(m, "cuFacing")) {
            r.cuFacing.add(o instanceof Number n2 ? n2.intValue() : null);
        }
        r.winner = Json.io(m, "winner");
        r.condition = orEmpty(Json.s(m, "condition"));
        // Старые записи этих полей не несут: Json.i вернёт 0, а «ноль
        // источников» - осмысленное значение, поэтому отсутствие читаем как -1.
        r.spawnLeft = m.containsKey("spawnLeft") ? Json.i(m, "spawnLeft") : -1;
        r.spawnThreshold = m.containsKey("spawnThreshold") ? Json.i(m, "spawnThreshold") : -1;
        r.rounds = Json.i(m, "rounds");
        // старые записи этого поля не несут — тогда печатный запас по правилам (4)
        Map<String, Object> stock = Json.map(m, "unitStock");
        if (stock != null) {
            for (Map.Entry<String, Object> e : stock.entrySet()) {
                if (e.getValue() instanceof Number n) {
                    r.unitStock.put(e.getKey(), n.intValue());
                }
            }
        }
        Map<String, Object> names = Json.map(m, "cardNames");
        if (names != null) {
            for (Map.Entry<String, Object> e : names.entrySet()) {
                r.cardNames.put(e.getKey(), String.valueOf(e.getValue()));
            }
        }
        for (Object o : Json.list(m, "hexes")) {
            Map<String, Object> ho = (Map<String, Object>) o;
            HexInfo h = new HexInfo();
            h.id = Json.s(ho, "id");
            h.q = Json.i(ho, "q");
            h.r = Json.i(ho, "r");
            h.kind = orDefault(Json.s(ho, "kind"), "NORMAL");
            r.hexes.add(h);
        }
        for (Object o : Json.list(m, "orderPlays")) {
            Map<String, Object> oo = (Map<String, Object>) o;
            OrderPlay op = new OrderPlay();
            op.seat = Json.i(oo, "seat");
            op.round = Json.i(oo, "round");
            op.circle = Json.i(oo, "circle");
            op.revealFrame = Json.i(oo, "revealFrame");
            op.turnFrame = oo.containsKey("turnFrame") ? Json.i(oo, "turnFrame") : -1;
            op.card = orEmpty(Json.s(oo, "card"));
            op.top = orEmpty(Json.s(oo, "top"));
            op.topActions.addAll(Json.strings(oo, "topActions"));
            op.topAllowed = oo.containsKey("topAllowed") ? Json.i(oo, "topAllowed") : 2;
            op.coincided = Json.i(oo, "coincided") != 0;
            op.bottom = oo.get("bottom") == null ? null : Json.s(oo, "bottom");
            op.bottomActions.addAll(Json.strings(oo, "bottomActions"));
            op.bottomOpen = Json.i(oo, "bottomOpen") != 0;
            op.maneuver = Json.i(oo, "maneuver") != 0;
            r.orderPlays.add(op);
        }
        Snapshot prev = null;
        int index = 0;
        for (Object o : Json.list(m, "frames")) {
            Map<String, Object> fo = (Map<String, Object>) o;
            Frame f = new Frame();
            index++;
            f.type = orEmpty(Json.s(fo, "type"));
            f.round = Json.i(fo, "round");
            f.circle = Json.i(fo, "circle");
            f.seat = Json.io(fo, "seat");
            f.log = orEmpty(Json.s(fo, "log"));
            f.combat = Json.b(fo, "combat");
            for (Object t : Json.list(fo, "thoughts")) {
                Map<String, Object> to = (Map<String, Object>) t;
                f.thoughts.add(new Thought(Json.i(to, "seat"), orEmpty(Json.s(to, "text"))));
            }
            f.highlight = highlightFromMap(Json.map(fo, "hl"));
            Map<String, Object> so = Json.map(fo, "snap");
            if (so != null) {
                prev = snapshotFromMap(so);
            }
            if (prev == null) {
                // Первый кадр ОБЯЗАН нести снимок: без него рисовать нечего, а
                // дальше по записи всё держится на «как в предыдущем кадре».
                throw new IllegalStateException("шаг " + index
                    + " остался без снимка состояния — запись повреждена");
            }
            f.snapshot = prev;
            r.frames.add(f);
        }
        if (r.frames.isEmpty()) {
            throw new IllegalStateException("в записи нет ни одного шага");
        }
        return r;
    }

    @SuppressWarnings("unchecked")
    private static Highlight highlightFromMap(Map<String, Object> o) {
        Highlight h = new Highlight();
        if (o == null) {
            return h;
        }
        for (Object p : Json.list(o, "moves")) {
            List<Object> pair = (List<Object>) p;
            if (pair.size() >= 2) {
                h.moves.add(new String[]{String.valueOf(pair.get(0)), String.valueOf(pair.get(1))});
            }
        }
        for (Object p : Json.list(o, "attacks")) {
            List<Object> pair = (List<Object>) p;
            if (pair.size() >= 2) {
                h.attacks.add(new String[]{String.valueOf(pair.get(0)), String.valueOf(pair.get(1))});
            }
        }
        h.builds.addAll(Json.strings(o, "builds"));
        h.damaged.addAll(Json.strings(o, "damaged"));
        h.destroyed.addAll(Json.strings(o, "destroyed"));
        return h;
    }

    @SuppressWarnings("unchecked")
    private static Snapshot snapshotFromMap(Map<String, Object> o) {
        Snapshot s = new Snapshot();
        s.round = Json.i(o, "round");
        s.circle = Json.i(o, "circle");
        s.firstPlayer = Json.i(o, "first");
        s.active = Json.io(o, "active");
        s.market = Json.s(o, "market");
        s.superArsenalOffer.putAll(Json.strMap(o, "superArs"));
        List<Object> mc = Json.list(o, "marketCells");
        for (int side = 0; side < Math.min(2, mc.size()); side++) {
            if (mc.get(side) instanceof List<?> row) {
                for (int i = 0; i < Math.min(2, row.size()); i++) {
                    s.marketCells[side][i] = row.get(i) instanceof Number n ? n.intValue() : -1;
                }
            }
        }
        Map<String, Object> occ = Json.map(o, "techOcc");
        if (occ != null) {
            for (var e : occ.entrySet()) {
                List<List<Integer>> steps = new ArrayList<>();
                if (e.getValue() instanceof List<?> list) {
                    for (Object so : list) {
                        List<Integer> seats = new ArrayList<>();
                        if (so instanceof List<?> sl) {
                            for (Object x : sl) {
                                if (x instanceof Number n) {
                                    seats.add(n.intValue());
                                }
                            }
                        }
                        steps.add(seats);
                    }
                }
                s.techOccupancy.put(e.getKey(), steps);
            }
        }
        Map<String, Object> dk = Json.map(o, "decks");
        if (dk != null) {
            for (var e : dk.entrySet()) {
                DeckState ds = new DeckState();
                if (e.getValue() instanceof Map<?, ?> m) {
                    for (Object x : Json.list((Map<String, Object>) m, "d")) {
                        ds.draw.add(String.valueOf(x));
                    }
                    for (Object x : Json.list((Map<String, Object>) m, "s")) {
                        ds.discard.add(String.valueOf(x));
                    }
                }
                s.decks.put(e.getKey(), ds);
            }
        }
        for (Object x : Json.list(o, "arsDisplay")) {
            s.arsenalDisplay.add(String.valueOf(x));
        }
        for (Object ho : Json.list(o, "hexes")) {
            Map<String, Object> h = (Map<String, Object>) ho;
            HexState st = new HexState();
            st.id = Json.s(h, "id");
            st.containerCell = h.containsKey("cont") ? Json.i(h, "cont") : -1;
            st.energyCell = h.containsKey("ecell") ? Json.i(h, "ecell") : -1;
            st.ownerTint = h.containsKey("own") ? Json.i(h, "own") : -1;
            st.ownerBuilt = h.containsKey("ownb") && Json.i(h, "ownb") != 0;
            Map<String, Object> sp = Json.map(h, "spawn");
            if (sp != null) {
                Spawn spawn = new Spawn();
                spawn.start = Json.b(sp, "start");
                spawn.kelium = Json.i(sp, "kelium");
                spawn.stack = Math.max(1, Json.i(sp, "stack"));
                spawn.flipped = Json.b(sp, "flipped");
                st.spawn = spawn;
            }
            List<Object> sides = Json.list(h, "sides");
            for (int i = 0; i < 6 && i < sides.size(); i++) {
                st.sideOwner[i] = sides.get(i) instanceof Number n ? n.intValue() : -1;
            }
            for (Object no : Json.list(h, "neutrals")) {
                Map<String, Object> n = (Map<String, Object>) no;
                Neutral nb = new Neutral();
                nb.big = Json.b(n, "big");
                // Старые записи прочности не несут — берём печатную
                nb.hpMax = n.get("hpMax") instanceof Number m ? m.intValue()
                    : (nb.big ? 2 : 1);
                nb.hp = n.get("hp") instanceof Number m ? m.intValue() : nb.hpMax;
                for (Object c : Json.list(n, "corners")) {
                    if (c instanceof Number num) {
                        nb.corners.add(num.intValue());
                    }
                }
                st.neutrals.add(nb);
            }
            s.hexes.add(st);
        }
        for (Object to : Json.list(o, "tokens")) {
            Map<String, Object> t = (Map<String, Object>) to;
            Tok tok = new Tok();
            tok.uid = Json.i(t, "uid");
            tok.owner = Json.i(t, "owner");
            tok.building = Json.b(t, "bld");
            tok.type = orEmpty(Json.s(t, "type"));
            tok.hexId = Json.s(t, "hex");
            tok.damage = Json.i(t, "dmg");
            tok.hp = Json.i(t, "hp");
            tok.energySlots = Json.i(t, "es");
            tok.energyPlaced = Json.i(t, "ep");
            tok.energyIdle = t.containsKey("ei") ? Json.i(t, "ei") : 0;
            tok.level = Json.io(t, "lvl");
            tok.alive = Json.b(t, "alive");
            tok.capturedBy = Json.io(t, "cap");
            tok.insideBuildingUid = Json.io(t, "in");
            s.tokens.add(tok);
        }
        for (Object po : Json.list(o, "players")) {
            s.players.add(playerFromMap((Map<String, Object>) po));
        }
        return s;
    }

    private static Player playerFromMap(Map<String, Object> o) {
        Player p = new Player();
        p.seat = Json.i(o, "seat");
        p.side = orEmpty(Json.s(o, "side"));
        p.storageSide = orEmpty(Json.s(o, "storeSide"));
        p.coin = Json.i(o, "coin");
        p.kelium = Json.i(o, "kelium");
        p.ammo = Json.i(o, "ammo");
        p.debris = Json.i(o, "debris");
        p.debrisCap = Json.i(o, "debrisCap");
        Map<String, Object> cells = Json.map(o, "storageCells");
        if (cells != null) {
            for (Map.Entry<String, Object> e : cells.entrySet()) {
                p.storageCells.put(e.getKey(), String.valueOf(e.getValue()));
            }
        }
        p.trophyTokens = Json.i(o, "trophyTokens");
        p.trophyPoints = Json.i(o, "trophyPoints");
        for (Object to : Json.list(o, "trophyCard")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) to;
            TrophyToken t = new TrophyToken();
            t.uid = Json.i(m, "uid");
            t.owner = Json.i(m, "owner");
            t.building = Json.b(m, "building");
            t.type = Json.s(m, "type");
            t.value = Json.i(m, "value");
            p.trophyCard.add(t);
        }
        for (Object to : Json.list(o, "trophyHeld")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) to;
            TrophyToken t = new TrophyToken();
            t.uid = Json.i(m, "uid");
            t.owner = Json.i(m, "owner");
            t.building = Json.b(m, "building");
            t.type = Json.s(m, "type");
            t.value = Json.i(m, "value");
            p.trophyHeld.add(t);
        }
        for (Object to : Json.list(o, "tucked")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) to;
            Tucked t = new Tucked();
            t.kind = Json.s(m, "kind");
            t.cardId = Json.s(m, "card");
            t.revealed = Json.b(m, "rev");
            p.tucked.add(t);
        }
        p.shieldedKinds.addAll(Json.strings(o, "shields"));
        p.containers = Json.i(o, "containers");
        p.containerCap = Json.i(o, "containerCap");
        p.keliumCap = Json.i(o, "keliumCap");
        p.ammoCap = Json.i(o, "ammoCap");
        p.storeCap = Json.i(o, "storeCap");
        p.tech.putAll(Json.ints(o, "tech"));
        p.redModules = Json.i(o, "red");
        p.blueModules = Json.i(o, "blue");
        p.goldModules = Json.i(o, "gold");
        p.redHalves = Json.i(o, "redHalves");
        p.blueHalves = Json.i(o, "blueHalves");
        modulesFromMap(o, "redPlaced", p.redPlaced);
        modulesFromMap(o, "bluePlaced", p.bluePlaced);
        p.arsenalHand.addAll(Json.strings(o, "arsHand"));
        p.arsenalInstalled.addAll(Json.strings(o, "arsInst"));
        p.mandateArsenalCard = Json.s(o, "mandCard");
        p.mandateContainers = Json.i(o, "mandCont");
        p.superArsenal.addAll(Json.strings(o, "superArs"));
        p.objectiveHand.addAll(Json.strings(o, "objHand"));
        p.orderHand.addAll(Json.strings(o, "ordHand"));
        p.orderPlayed.addAll(Json.strings(o, "ordPlayed"));
        p.orderSetAside = Json.s(o, "ordAside");
        p.orderColor = Json.s(o, "ordColor");
        p.storageTokens.addAll(Json.strings(o, "store"));
        p.superObjective = Json.s(o, "super");
        p.superProgress = Json.i(o, "superProgress");
        p.superParts.putAll(Json.ints(o, "superParts"));
        p.superComplete = Json.b(o, "superComplete");
        p.cuTokens = Json.i(o, "cuTokens");
        p.ownCuToken = Json.b(o, "ownCu");
        p.startHex = orEmpty(Json.s(o, "startHex"));
        p.vp.putAll(Json.ints(o, "vp"));
        return p;
    }

    /** Карта поставленных модулей в вид, пригодный для JSON. */
    private static Map<String, Object> modulesToMap(Map<String, Module> src) {
        Map<String, Object> out = new LinkedHashMap<>();
        src.forEach((slot, m) -> {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("id", m.id);
            one.put("gold", m.gold);
            if (!m.targets.isEmpty()) {
                one.put("targets", m.targets);
            }
            if (m.stat != null) {
                one.put("stat", m.stat);
                one.put("plus", m.plus);
            }
            if (m.ammo != 0) {
                one.put("ammo", m.ammo);
            }
            if (m.units != 0) {
                one.put("units", m.units);
            }
            out.put(slot, one);
        });
        return out;
    }

    /** Обратное чтение. Ключа нет — запись старая, модулей просто не будет. */
    private static void modulesFromMap(Map<String, Object> o, String key,
                                       Map<String, Module> into) {
        if (!(o.get(key) instanceof Map<?, ?> raw)) {
            return;
        }
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            if (!(e.getValue() instanceof Map<?, ?> mm)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> one = (Map<String, Object>) mm;
            Module m = new Module();
            m.id = orEmpty(Json.s(one, "id"));
            m.gold = Json.b(one, "gold");
            for (String t : Json.strings(one, "targets")) {
                m.targets.add(t);
            }
            m.stat = Json.s(one, "stat");
            m.plus = Json.i(one, "plus");
            m.ammo = Json.i(one, "ammo");
            m.units = Json.i(one, "units");
            into.put(String.valueOf(e.getKey()), m);
        }
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String orDefault(String s, String def) {
        return s == null || s.isEmpty() ? def : s;
    }
}
