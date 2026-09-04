package kelium.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Choice;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.StorageSide;
import kelium.dataio.Ctx;

/**
 * Вместимость хранилища — предел склада на келемий, боеприпасы и трофеи.
 *
 * <p>У склада ограниченное число ЯЧЕЕК, общих под все три кубика. Ячейка бывает
 * трёх типов: U (универсальная — под келемий ИЛИ боеприпас), K (только келемий),
 * A (только боеприпас). Трофей — особый случай: он занимает ЛЮБУЮ из трёх
 * (K/A/U), но всегда ровно одну, поэтому у него нет своего типизированного
 * подпредела, только доля в общем бюджете. Отсюда:
 * <pre>
 *   kelium ≤ K + U ; ammo ≤ A + U ; kelium + ammo + trophy ≤ K + A + U
 * </pre>
 * Именно из-за этого «18 келемия» невозможно — экономика упирается в склад.
 *
 * <p><b>Когда ячейки открыты.</b> Печатные ячейки уровня добытчика/энергостанции
 * физически лежат ПОД жетоном этого здания на планшете хранилища. Пока жетон НЕ
 * лежит там — ячейки открыты (доступны под кубики): это верно, пока здание СТОИТ
 * НА ПОЛЕ (построено) и ПОКА ОНО У ДРУГОГО ИГРОКА СРЕДИ УНИЧТОЖЕННЫХ ЖЕТОНОВ (уничтожено, но ещё
 * не возвращено — вместимость от этого НЕ падает). Ячейки закрываются ровно в
 * момент, когда жетон возвращается владельцу В ЗАПАС — сносом Стройкой за монету
 * ({@link kelium.engine.Actions#returnOwnBuildingToReserve}) или возвратом с
 * чужой карты трофеев (Возврат конца раунда, чужое действие Науки) — тогда
 * правила ОБЯЗЫВАЮТ положить жетон на планшет хранилища на его место, и это
 * накрывает ячейки. Если на них в этот момент лежали кубики — они сгорают в
 * общий запас БЕЗ права игрока их переставить, см. {@link #forceEvictOnBuildingReturn}
 * (единственный момент, где превышение вместимости решается без участия игрока).
 *
 * <p><b>Правило 4 (перестановка/выброс перед поступлением).</b> По правилам это
 * свободное действие в СВОЙ ход, доступное сколько угодно раз, в любой момент —
 * игрок МОЖЕТ освободить ячейки, выбросив в общий запас сколько угодно кубиков
 * любого из трёх типов, прежде чем что-то поступит в хранилище. В движке хук
 * ({@link #offerStorageDiscard}) задаётся реактивно, прямо перед
 * {@link #addKeliumCapped}/{@link #addAmmoCapped}/{@link #addTrophyCapped} — но
 * СПРАШИВАЕТСЯ только тогда, когда без выброса поступление всё равно обрежется
 * нехваткой места (room &lt; amount). Это не сужение права игрока: расклад по
 * ячейкам ни на что не влияет, кроме вместимости при следующем поступлении, так
 * что предложение выбросить что-то, когда места и так достаточно, не имеет
 * стратегического смысла — а вот агентам (в частности, простым «жадным» ботам,
 * которые готовы взять любую предложенную небанальную опцию) может дать повод
 * ошибочно выбросить то, что выбрасывать незачем. Спрашивать только «когда
 * реально надо» устраняет этот ложный стимул, не убирая право игрока.
 * Перестановка между ячейками уважает тип ячейки: келемий и боеприпас можно
 * класть ТОЛЬКО в K/U и A/U соответственно, трофей — в любую; в этой модели
 * (агрегированные счётчики, а не индивидуальные ячейки) это ограничение уже
 * встроено в формулы {@link #keliumMax}/{@link #ammoMax} выше — переставлять
 * физически «конкретный кубик в конкретную ячейку» отдельно моделировать не нужно.
 */
public final class Storage {

    private Storage() {
    }

    /**
     * Вместимость КОНТЕЙНЕРОВ (правило 2026-08-10): карты контейнеров лежат в
     * ячейках арсенала под планшетом. Каждая СВОБОДНАЯ ячейка арсенала = 2 места;
     * закрытая карта арсенала занимает ячейку целиком (0 мест); открытая
     * (установленная) карта с {@code container_slot: true} даёт 1 место, без —
     * 0. При выключенном правиле — без лимита.
     */
    /**
     * СВОБОДНА ЛИ ЯЧЕЙКА ПОД ЕЩЁ ОДНУ КАРТУ АРСЕНАЛА.
     *
     * <p>Ячейки общие: карта арсенала занимает ячейку целиком, контейнеры лежат
     * по два в свободной ячейке. Значит взять карту арсенала можно, только если
     * после неё контейнерам ещё хватит места — иначе занятых ячеек станет
     * больше трёх.
     *
     * <p>Прежде проверка шла в одну сторону: контейнеры считались по свободным
     * ячейкам, а карта арсенала приходила молча и сверх того. Замер на двенадцати
     * партиях показал занятых ячеек ЧЕТЫРЕ при пределе три — две карты арсенала
     * и три контейнера.
     */
    public static boolean arsenalCellFree(kelium.core.GameState s, PlayerState p) {
        var rs = Ctx.rules(s);
        if (!Boolean.TRUE.equals(rs.get("containers_storage.open_is_spec", Boolean.FALSE))) {
            return true;                    // правило ячеек выключено
        }
        int cells = ((Number) rs.get("containers_storage.arsenal_cells", 3)).intValue();
        return cellsUsed(s, p) + 1 <= cells;
    }

    /**
     * СКОЛЬКО ЯЧЕЕК ПОД ПЛАНШЕТОМ ЗАНЯТО СЕЙЧАС: каждая карта арсенала — целая
     * ячейка, контейнеры — по {@code slots_per_free_cell} в ячейке.
     */
    public static int cellsUsed(kelium.core.GameState s, PlayerState p) {
        var rs = Ctx.rules(s);
        int perFree = ((Number) rs.get("containers_storage.slots_per_free_cell", 2)).intValue();
        int onCard = ((Number) rs.get("containers_storage.slots_on_open_card_with_slot", 1))
            .intValue();
        int арсенал = p.arsenalHand.size() + p.arsenalInstalled.size();
        // ЛЕЖАЩИЕ НЕ В ЯЧЕЙКАХ НЕ СЧИТАЮТСЯ: контейнеры под «мандатом» лежат на
        // своём отдельном месте, а у установленной карты с container_slot есть
        // место НА САМОЙ КАРТЕ. Без этой поправки счёт завышал занятость и
        // отказывал в карте арсенала там, где место на деле было.
        int наКартах = 0;
        var lib = Ctx.cards(s, "arsenal");
        for (String cid : p.allInstalledArsenal()) {
            var card = lib.find(cid);
            if (card != null && Boolean.TRUE.equals(card.get("container_slot"))) {
                наКартах += onCard;
            }
        }
        int вЯчейках = Math.max(0, p.containers - p.mandateContainers - наКартах);
        int подКонтейнеры = perFree <= 0 ? 0 : (вЯчейках + perFree - 1) / perFree;
        return арсенал + подКонтейнеры;
    }

    /**
     * ВЗЯТЬ КАРТУ АРСЕНАЛА В ЯЧЕЙКУ. Места нет — карта не берётся вовсе и
     * уходит в сброс своей колоды: держать её негде, руки для карт арсенала не
     * существует.
     *
     * @return взяли ли карту
     */
    public static boolean takeArsenalCard(kelium.core.GameState s, PlayerState p,
                                          String cardId) {
        if (cardId == null) {
            return false;
        }
        if (!arsenalCellFree(s, p)) {
            var deck = s.decks.get("arsenal");
            if (deck != null) {
                deck.discard(cardId);
            }
            return false;
        }
        p.arsenalHand.add(cardId);
        return true;
    }

    public static int containerCapacity(kelium.core.GameState s, PlayerState p) {
        var rs = Ctx.rules(s);
        if (!Boolean.TRUE.equals(rs.get("containers_storage.open_is_spec", Boolean.FALSE))) {
            return Integer.MAX_VALUE;
        }
        int cells = ((Number) rs.get("containers_storage.arsenal_cells", 3)).intValue();
        int perFree = ((Number) rs.get("containers_storage.slots_per_free_cell", 2)).intValue();
        int onCard = ((Number) rs.get("containers_storage.slots_on_open_card_with_slot", 1)).intValue();
        int occupied = Math.min(cells, p.arsenalHand.size() + p.arsenalInstalled.size());
        int freeCells = cells - occupied;
        int slotCards = 0;
        var lib = Ctx.cards(s, "arsenal");
        // allInstalledArsenal(): карта под мандатом (sa8) — тоже ОТКРЫТАЯ
        // установленная карта, «container_slot» с неё считается наравне с
        // обычными тремя слотами.
        for (String cid : p.allInstalledArsenal()) {
            var card = lib.find(cid);
            if (card != null && Boolean.TRUE.equals(card.get("container_slot"))) {
                slotCards++;
            }
        }
        int base = Math.max(0, freeCells) * perFree + slotCards * onCard;
        // «МАНДАТ СОВЕТА» (супер-арсенал sa8): МЕСТО ПОД МАНДАТОМ — до 2
        // контейнеров, НА СВОЁМ отдельном месте, не в ячейках арсенала. Не
        // флат-бонус за одно удержание карты: именно СТОЛЬКО, сколько игрок
        // туда фактически отвёл (p.mandateContainers, 0/1/2 — см.
        // GameEngine.mandateAllocateContainers), и не одновременно с картой
        // арсенала в том же месте (взаимоисключение проверяется там же).
        base += p.mandateContainers;
        return base;
    }

    /** Добавить контейнеры с учётом вместимости ячеек; вернуть, сколько влезло. */
    public static int addContainersCapped(kelium.core.GameState s, PlayerState p, int n) {
        return addContainersCapped(s, p, n, "прочее");
    }

    /**
     * То же, но с ПОМЕТКОЙ ИСТОЧНИКА — для балансовых пробников.
     *
     * <p>Зачем. Замер показал, что за партию вскрывается 27–73 карты контейнеров
     * при 12–18 печатных ячейках на поле, то есть контейнеры откуда-то сыплются
     * без конца. Понять откуда, не помечая источники, невозможно: выдач в движке
     * одиннадцать штук в семи файлах — печатные ячейки, Добыча, снос нейтрала,
     * компенсации за снесённые здания и ЦУ, карты, задания, подготовка.
     */
    public static int addContainersCapped(kelium.core.GameState s, PlayerState p, int n,
                                          String source) {
        int cap = containerCapacity(s, p);
        int add;
        if (cap == Integer.MAX_VALUE) {
            p.containers += n;
            add = n;
        } else {
            add = Math.min(n, Math.max(0, cap - p.containers));
            p.containers += add;
        }
        if (add > 0) {
            SOURCE_STATS.computeIfAbsent(source,
                k -> new java.util.concurrent.atomic.LongAdder()).add(add);
        }
        return add;
    }

    /** Сколько контейнеров выдано по каждому источнику (телеметрия пробников). */
    private static final java.util.Map<String, java.util.concurrent.atomic.LongAdder>
        SOURCE_STATS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Обнулить счётчики источников контейнеров. */
    public static void resetContainerStats() {
        SOURCE_STATS.clear();
    }

    /** Выдачи контейнеров по источникам с последнего обнуления. */
    public static java.util.Map<String, Long> containerStats() {
        java.util.Map<String, Long> out = new java.util.LinkedHashMap<>();
        SOURCE_STATS.forEach((k, v) -> out.put(k, v.sum()));
        return out;
    }

    private record Cells(int u, int k, int a) {
    }

    private static Cells countCells(String cellstr) {
        int u = 0, k = 0, a = 0;
        if (cellstr != null) {
            for (char ch : cellstr.toCharArray()) {
                if (ch == 'U') {
                    u++;
                } else if (ch == 'K') {
                    k++;
                } else if (ch == 'A' || ch == 'B') {  // в данных боеприпасы = 'A'
                    a++;
                }
            }
        }
        return new Cells(u, k, a);
    }

    /** Сколько ячеек склада ОТКРЫТО у игрока (по типам U/K/A). */
    private static Cells openCells(kelium.core.GameState s, PlayerState player) {
        StorageSide storage = player.board.storage;
        // На планшете хранилища ВСЕГДА открыты 2 центральные универсальные
        // ячейки (стартовые слоты), независимо от построенных зданий.
        int u = 2, k = 0, a = 0;
        java.util.Set<Integer> minerLevels = new java.util.HashSet<>();
        java.util.Set<Integer> plantLevels = new java.util.HashSet<>();
        // ЖЕТОН НА ГЕКСЕ — ЗНАЧИТ НЕ НА ПЛАНШЕТЕ ХРАНИЛИЩА, а только лежащий на
        // планшете накрывает свои ячейки. Поэтому здесь спрашивается «стоит ли на
        // гексе», а не «жив ли»: здание, у которого прочность уже кончилась, но
        // жетон ещё не уехал на место уничтоженных жетонов, физически со планшета не убран.
        for (BuildingToken b : player.buildings) {
            if (b.hexId == null) {
                continue;
            }
            if (b.type == BuildingType.MINER) {
                minerLevels.add(b.level);
            } else if (b.type == BuildingType.POWER_PLANT) {
                plantLevels.add(b.level);
            }
        }
        // Уничтоженное, но ЕЩЁ НЕ ВОЗВРАЩЁННОЕ здание (лежит трофеем у другого
        // игрока) физически ОТСУТСТВУЕТ на планшете хранилища владельца — значит
        // не накрывает свои печатные ячейки, и они остаются открытыми, как если
        // бы здание всё ещё стояло на поле. Ячейки закрываются ровно в момент
        // возврата, когда владелец обязан положить жетон на планшет хранилища на
        // отведённое ему место (см. {@link #forceEvictOnBuildingReturn}).
        if (s != null) {
            for (PlayerState holder : s.players) {
                for (kelium.core.Token t : holder.destroyedTokens) {
                    if (t.owner() != player.seat || !(t instanceof BuildingToken b)) {
                        continue;
                    }
                    if (b.type == BuildingType.MINER) {
                        minerLevels.add(b.level);
                    } else if (b.type == BuildingType.POWER_PLANT) {
                        plantLevels.add(b.level);
                    }
                }
            }
        }
        for (int lv : minerLevels) {
            Cells c = countCells(storage.minerCells(lv));
            u += c.u; k += c.k; a += c.a;
        }
        for (int lv : plantLevels) {
            Cells c = countCells(storage.plantCells(lv));
            u += c.u; k += c.k; a += c.a;
        }
        for (String tok : player.storageTokens) {
            if ("+1_universal_cell".equals(tok)) {
                u++;
            }
        }
        return new Cells(u, k, a);
    }

    /**
     * ПРИБАВКА ЯЧЕЕК ОТ СПОСОБНОСТЕЙ — единственное место, где склад спрашивает
     * точку правил {@link kelium.engine.ability.Hook#STORAGE_CELLS}. Прибавка идёт
     * к УНИВЕРСАЛЬНЫМ ячейкам, потому что «+1 ячейка склада» годится и под келемий,
     * и под боеприпас.
     *
     * <p>Состояние партии нужно, чтобы узнать установленные карты игрока. Там, где
     * его нет (чтения бота, отчёты), берётся печатное число без прибавки — врать
     * в сторону занижения безопасно.
     */
    private static int abilityCells(kelium.core.GameState s, PlayerState p) {
        if (s == null) {
            return 0;
        }
        return kelium.engine.ability.RuleQuery
            .of(s, p.seat, kelium.engine.ability.Hook.STORAGE_CELLS)
            .base(0).ask();
    }

    /** Предел келемия на складе (ячейки K + универсальные). */
    public static int keliumMax(PlayerState p) {
        return keliumMax(null, p);
    }

    /** Предел келемия с учётом способностей арсенала. */
    public static int keliumMax(kelium.core.GameState s, PlayerState p) {
        Cells c = openCells(s, p);
        return c.k + c.u + abilityCells(s, p);
    }

    /** Предел боеприпасов на складе (ячейки A + универсальные). */
    public static int ammoMax(PlayerState p) {
        return ammoMax(null, p);
    }

    /** Предел боеприпасов с учётом способностей арсенала. */
    public static int ammoMax(kelium.core.GameState s, PlayerState p) {
        Cells c = openCells(s, p);
        return c.a + c.u + abilityCells(s, p);
    }

    /** Общее число открытых ячеек склада (келемий, боеприпасы и трофеи делят их). */
    public static int totalMax(PlayerState p) {
        return totalMax(null, p);
    }

    /** Общее число ячеек с учётом способностей арсенала. */
    public static int totalMax(kelium.core.GameState s, PlayerState p) {
        Cells c = openCells(s, p);
        return c.k + c.a + c.u + abilityCells(s, p);
    }

    /**
     * Предел трофеев на складе. Трофей не привязан к типу ячейки (K/A/U все
     * подходят), поэтому у него нет типизированного подпредела — только общий
     * бюджет за вычетом уже занятого келемием и боеприпасом.
     */
    public static int trophyMax(kelium.core.GameState s, PlayerState p) {
        return totalMax(s, p) - p.resources.kelium() - p.resources.ammo();
    }


    /**
     * Правило 4: перед добавлением ЛЮБОГО кубика в хранилище (келемий,
     * боеприпас, трофей) спросить агента, не хочет ли он сначала переставить/
     * выбросить содержимое хранилища. «Переставить между ячейками» не имеет
     * отдельного игрового эффекта в этой модели (ячейки взаимозаменяемы в
     * пределах формул выше, конкретная ячейка для конкретного кубика не
     * отслеживается) — значимое действие тут одно: добровольно выбросить любое
     * число кубиков любого типа в общий запас ДО расчёта вместимости. Вызывается
     * из {@link #addKeliumCapped}/{@link #addAmmoCapped}/{@link #addTrophyCapped}
     * — единственных каналов пополнения склада, так что все действия движка
     * покрываются автоматически без правки мест вызова. Вызывающий код спрашивает
     * это ТОЛЬКО когда без выброса поступление обрежется нехваткой места (см.
     * javadoc класса) — иначе агент, готовый взять любую небанальную опцию,
     * выбрасывал бы кубики без всякой нужды.
     *
     * <p>Без агента (боты в режиме только чтения, отчёты, символьные прогоны) —
     * хук пропускается: излишек просто не добавится, как и раньше.
     */
    private static void offerStorageDiscard(kelium.core.GameState s, PlayerState player, int needed) {
        if (s == null || s.agents == null || player.seat >= s.agents.size()) {
            return;
        }
        Agent agent = s.agents.get(player.seat);
        if (agent == null) {
            return;
        }
        // Ограничение итераций — защита от зацикливания плохо написанного бота.
        // "pass" — ЛИТЕРАЛЬНО эта строка, а не "storage_discard_pass": по
        // конвенции движка (see sci_exchange/MiningAction) именно "pass" узнают
        // простые/эвристические агенты как «отказаться» — иное имя означало бы
        // для них «первая доступная НЕ-pass опция», то есть ошибочный выброс
        // ресурса на КАЖДОМ поступлении (баг найден на MiningReachTest).
        // "needed" в контексте — сколько ячеек НЕ ХВАТАЕТ для полного поступления
        // (не жёсткое ограничение, просто подсказка агенту, когда МОЖНО
        // остановиться — выбросить больше по-прежнему можно, это его выбор).
        for (int i = 0; i < 16; i++) {
            List<Choice> opts = new ArrayList<>();
            opts.add(new Choice("pass", null, "ничего не выбрасывать"));
            if (player.resources.kelium() > 0) {
                opts.add(new Choice("storage_discard", Resource.KELIUM,
                    "выбросить 1 келемий из хранилища"));
            }
            if (player.resources.ammo() > 0) {
                opts.add(new Choice("storage_discard", Resource.AMMO,
                    "выбросить 1 боеприпас из хранилища"));
            }
            if (player.resources.trophy() > 0) {
                opts.add(new Choice("storage_discard", Resource.TROPHY,
                    "выбросить 1 трофей из хранилища"));
            }
            if (opts.size() == 1) {
                return;   // хранилище пусто — нечего выбрасывать
            }
            Choice pick = agent.choose(s, opts,
                Map.of("kind", "storage_discard", "needed", Math.max(0, needed)));
            if (pick == null || !"storage_discard".equals(pick.kind())) {
                return;
            }
            player.resources.pay((Resource) pick.payload(), 1);
            needed--;
        }
    }

    /** Добавить келемий в пределах вместимости; вернуть фактически добавленное. */
    public static int addKeliumCapped(PlayerState player, int amount) {
        return addKeliumCapped(null, player, amount);
    }

    /** Добавить келемий в пределах вместимости, зная партию (с прибавками карт). */
    public static int addKeliumCapped(kelium.core.GameState s, PlayerState player, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int room = keliumRoom(s, player);
        if (room < amount) {
            offerStorageDiscard(s, player, amount - room);
            room = keliumRoom(s, player);
        }
        int add = Math.min(amount, room);
        player.resources.add(Resource.KELIUM, add);
        return add;
    }

    /**
     * Сколько кубиков этого типа ещё поместится на складе.
     *
     * <p>Нужно тем, кто ДОЛЖЕН узнать про место ДО того, как что-то сделает:
     * способность, забирающая кубик у противника, не может забрать больше, чем
     * готова принять твоя собственная полка.
     */
    public static int roomFor(kelium.core.GameState s, PlayerState player, Resource what) {
        return switch (what) {
            case KELIUM -> keliumRoom(s, player);
            case AMMO -> ammoRoom(s, player);
            case TROPHY -> trophyRoom(s, player);
            default -> Integer.MAX_VALUE;      // монеты и очки склад не занимают
        };
    }

    private static int keliumRoom(kelium.core.GameState s, PlayerState player) {
        int curK = player.resources.kelium();
        int curA = player.resources.ammo();
        int curD = player.resources.trophy();
        int roomTyped = keliumMax(s, player) - curK;
        int roomTotal = totalMax(s, player) - (curK + curA + curD);
        return Math.max(0, Math.min(roomTyped, roomTotal));
    }

    /** Добавить боеприпасы в пределах вместимости; вернуть добавленное. */
    public static int addAmmoCapped(PlayerState player, int amount) {
        return addAmmoCapped(null, player, amount);
    }

    /** Добавить боеприпасы в пределах вместимости, зная партию. */
    public static int addAmmoCapped(kelium.core.GameState s, PlayerState player, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int room = ammoRoom(s, player);
        if (room < amount) {
            offerStorageDiscard(s, player, amount - room);
            room = ammoRoom(s, player);
        }
        int add = Math.min(amount, room);
        player.resources.add(Resource.AMMO, add);
        return add;
    }

    private static int ammoRoom(kelium.core.GameState s, PlayerState player) {
        int curK = player.resources.kelium();
        int curA = player.resources.ammo();
        int curD = player.resources.trophy();
        int roomTyped = ammoMax(s, player) - curA;
        int roomTotal = totalMax(s, player) - (curK + curA + curD);
        return Math.max(0, Math.min(roomTyped, roomTotal));
    }

    /** Добавить трофеи в пределах вместимости; вернуть фактически добавленное. */
    public static int addTrophyCapped(PlayerState player, int amount) {
        return addTrophyCapped(null, player, amount);
    }

    /**
     * Добавить трофеи в пределах вместимости, зная партию. Трофей не имеет
     * типизированного подпредела (см. {@link #trophyMax}) — ограничение только
     * по общему бюджету ячеек.
     */
    public static int addTrophyCapped(kelium.core.GameState s, PlayerState player, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int room = trophyRoom(s, player);
        if (room < amount) {
            offerStorageDiscard(s, player, amount - room);
            room = trophyRoom(s, player);
        }
        int add = Math.min(amount, room);
        player.resources.add(Resource.TROPHY, add);
        return add;
    }

    private static int trophyRoom(kelium.core.GameState s, PlayerState player) {
        int curK = player.resources.kelium();
        int curA = player.resources.ammo();
        int curD = player.resources.trophy();
        return Math.max(0, totalMax(s, player) - (curK + curA + curD));
    }

    /**
     * Сгорание излишка при возврате здания владельцу — жетон добытчика/
     * энергостанции вернулся на планшет владельца, накрыв ранее открытые
     * ячейки, на которых в это время лежали кубики (набранные, пока склад был
     * шире, из-за временного отсутствия этого здания). Свободных ячеек, куда
     * переставить лишнее, физически нет: закрывшаяся ячейка уже вычтена из
     * {@link #totalMax}, так что «излишек» — это именно то, что нигде не
     * помещается, а не то, что кто-то не переставил.
     *
     * <p>Различается только ЧЬИМ решением горит лишнее (уточнение дизайнера
     * 17.08.2026, ответ на «внутри своего спец-действия игрок... может...
     * попробовать вытащить их... в другие свободные ячейки»):
     * <ul>
     *   <li>{@code ownTurnChoice=true} — возврат случился ВНУТРИ хода этого
     *   игрока, его собственным действием (снос Стройкой, оплата ячейки карты
     *   супероружия своим зданием). Правило 4 разрешает игроку в свой ход
     *   свободно перекладывать/выбрасывать содержимое склада — значит и здесь
     *   он выбирает, ЧТО именно сгорит, а не движок фиксированным порядком.</li>
     *   <li>{@code ownTurnChoice=false} — возврат случился НЕ в ход этого
     *   игрока (Возврат конца раунда, чужое действие Науки, чужой СПЕЦ вроде
     *   «Ядерного удара» по чужому гексу): игрок тут не действует, выбора нет,
     *   горит фиксированным порядком.</li>
     * </ul>
     *
     * <p>Типизированный излишек (келемий/боеприпас сверх СВОИХ K/A-пределов)
     * горит без вариантов в обоих случаях: чужой тип ячейки эти кубики
     * физически не примет. Выбор игрока касается только остатка ОБЩЕГО
     * излишка, где типы взаимозаменяемы.
     */
    public static void evictOnBuildingReturn(kelium.core.GameState s, PlayerState player,
                                              boolean ownTurnChoice) {
        int keliumOver = player.resources.kelium() - keliumMax(s, player);
        if (keliumOver > 0) {
            player.resources.pay(Resource.KELIUM, keliumOver);
        }
        int ammoOver = player.resources.ammo() - ammoMax(s, player);
        if (ammoOver > 0) {
            player.resources.pay(Resource.AMMO, ammoOver);
        }
        int totalOver = (player.resources.kelium() + player.resources.ammo()
            + player.resources.trophy()) - totalMax(s, player);
        if (totalOver > 0 && ownTurnChoice) {
            totalOver = offerBurnChoice(s, player, totalOver);
        }
        for (Resource r : new Resource[] {Resource.TROPHY, Resource.AMMO, Resource.KELIUM}) {
            if (totalOver <= 0) {
                break;
            }
            int cut = Math.min(totalOver, player.resources.get(r));
            if (cut > 0) {
                player.resources.pay(r, cut);
                totalOver -= cut;
            }
        }
    }

    /** Старое имя — БЕЗ выбора игрока, всегда фиксированный порядок. */
    public static void forceEvictOnBuildingReturn(kelium.core.GameState s, PlayerState player) {
        evictOnBuildingReturn(s, player, false);
    }

    /**
     * Даёт игроку самому выбрать, какие именно кубики из общего излишка сгорят
     * (см. {@link #evictOnBuildingReturn}). Без агента (боты в режиме чтения,
     * символьные прогоны) — молча пропускается, остаток дожигает вызывающий
     * код фиксированным порядком, как раньше.
     */
    private static int offerBurnChoice(kelium.core.GameState s, PlayerState player, int needed) {
        if (s == null || s.agents == null || player.seat >= s.agents.size()) {
            return needed;
        }
        Agent agent = s.agents.get(player.seat);
        if (agent == null) {
            return needed;
        }
        for (int i = 0; i < 16 && needed > 0; i++) {
            List<Choice> opts = new ArrayList<>();
            if (player.resources.kelium() > 0) {
                opts.add(new Choice("storage_burn_choice", Resource.KELIUM,
                    "сжечь 1 келемий из хранилища (ячейка закрылась)"));
            }
            if (player.resources.ammo() > 0) {
                opts.add(new Choice("storage_burn_choice", Resource.AMMO,
                    "сжечь 1 боеприпас из хранилища (ячейка закрылась)"));
            }
            if (player.resources.trophy() > 0) {
                opts.add(new Choice("storage_burn_choice", Resource.TROPHY,
                    "сжечь 1 трофей из хранилища (ячейка закрылась)"));
            }
            if (opts.isEmpty()) {
                return needed;
            }
            Choice pick = agent.choose(s, opts,
                Map.of("kind", "storage_burn_choice", "needed", needed));
            Resource r = pick != null && pick.payload() instanceof Resource res
                ? res : (Resource) opts.get(0).payload();
            player.resources.pay(r, 1);
            needed--;
        }
        return needed;
    }
}
