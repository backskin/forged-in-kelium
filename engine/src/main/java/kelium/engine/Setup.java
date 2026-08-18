package kelium.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Deck;
import kelium.core.Field;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.HexKind;
import kelium.core.PlayerBoard;
import kelium.core.PlayerState;
import kelium.core.Resources;
import kelium.core.TechBoard;
import kelium.core.TokenStats;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.rules.Ruleset;

/**
 * Подготовка партии — сборка {@link GameState} из {@link GameConfig}.
 *
 * <p>Связывает версионный слой данных (ruleset + content) с незыблемым ядром.
 * Значения подготовки (стартовые ресурсы, стороны планшетов) берутся из
 * ruleset/content. Геометрия поля: настоящий сценарий, если он есть для данного
 * числа игроков; иначе — простое связное гексовое кольцо-заглушка.
 */
public final class Setup {

    private Setup() {
    }

    /**
     * Стартовые монеты. Решение дизайнера 2026-08-12: **5 монет ВСЕМ** (плюс
     * 1 келемий и 1 боеприпас). Прежняя лесенка 3/4/4/5 «по месту» отменена —
     * ruleset может переопределить через {@code setup.start_coins}.
     */
    /** О каких отсеянных картах уже сообщили (чтобы не повторяться каждую партию). */
    private static final java.util.Set<String> REPORTED_CULLS =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static final int[] START_COINS = {5, 5, 5, 5};
    public static final int START_KELIUM = 1;
    public static final int START_AMMO = 1;

    /** Определить сторону планшета для каждого места из модуля асимметрии. */
    @SuppressWarnings("unchecked")
    private static List<String> resolveBoardSides(Ruleset ruleset, int numPlayers, List<String> configOverride) {
        if (configOverride != null) {
            return configOverride;
        }
        Object explicit = ruleset.get("asymmetry.board_sides", null);
        if (explicit instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                out.add(o.toString());
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        String mode = ruleset.getStr("asymmetry.mode", "A");
        List<String> out = new ArrayList<>();
        if ("B".equals(mode)) {
            for (int i = 0; i < numPlayers; i++) {
                out.add("B" + (i + 1));
            }
        } else if ("V".equals(mode)) {
            // НАБОР «В»: один общий планшет на всех, как сторона А, но с таблицей
            // атак, где ни один род не бьёт себя.
            for (int i = 0; i < numPlayers; i++) {
                out.add("V");
            }
        } else if ("G".equals(mode)) {
            // НАБОР «Г»: четыре варианта того же принципа, каждому игроку свой —
            // различаются столпом, порядком целей и МЕСТАМИ ПОД КРАСНЫЕ МОДУЛИ.
            for (int i = 0; i < numPlayers; i++) {
                out.add("G" + (i % 4 + 1));
            }
        } else {
            for (int i = 0; i < numPlayers; i++) {
                out.add("A");
            }
        }
        return out;
    }

    /**
     * Минимальное связное поле-заглушка: центральное кольцо узлов со стартовым
     * гексом и грядкой на каждого игрока.
     */
    private static Field buildRingField(int numPlayers) {
        Field f = new Field();
        List<String> hubIds = new ArrayList<>();
        for (int i = 0; i < numPlayers; i++) {
            String hid = "hub" + i;
            hubIds.add(hid);
            Hex h = new Hex(hid);
            h.kind = HexKind.NORMAL;
            f.addHex(h);
        }
        for (int i = 0; i < numPlayers; i++) {
            f.link(hubIds.get(i), hubIds.get((i + 1) % numPlayers));
        }
        for (int i = 0; i < numPlayers; i++) {
            Hex start = new Hex("start" + i);
            start.kind = HexKind.START;
            Hex spawn = new Hex("spawn" + i);
            spawn.sectors = 2;
            // тайл зарождения — физический жетон НА гексе (стартовый: 3/2)
            spawn.spawnTile = new kelium.core.SpawnTile(true, 3, 2, 1);
            f.addHex(start);
            f.addHex(spawn);
            f.link("start" + i, hubIds.get(i));
            f.link("spawn" + i, hubIds.get(i));
        }
        return f;
    }

    /**
     * Пара сторон стартового гекса под ЦУ по заданному повороту, либо null —
     * если поворот не задан или запрошенные стороны заняты (тогда сработает
     * автоподбор {@link #smartCuFacing}).
     */
    /**
     * ДОПОЛНЕНИЯ — ТРИ НЕЗАВИСИМЫХ ТУМБЛЕРА (решение дизайнера 17.08.2026).
     *
     * <p>Прежде это был ОДИН режим на три значения ({@code super} | {@code
     * starters} | {@code none}), то есть супер задания и начальные задания
     * исключали друг друга. Дизайнер это отменил: «можно включать и то и другое
     * в партию пускай». Поэтому теперь три отдельных ключа, каждый со своим
     * выключателем:
     *
     * <ul>
     *   <li>{@code expansions.super_objectives} — супер задания и супероружие;</li>
     *   <li>{@code expansions.starting_objectives} — начальные задания;</li>
     *   <li>{@code expansions.super_arsenal} — карты на вершинах треков;</li>
     *   <li>{@code expansions.market_cards} — карты предложений рынка. Выключено —
     *       остаётся только напечатанный обмен на планшете маркета, колода рынка
     *       не раздаётся, а карты, требующие предложение С КАРТЫ, изымаются.</li>
     * </ul>
     *
     * <p>СТАРЫЕ ВЕРСИИ ПРАВИЛ не знают этих ключей, поэтому значение по
     * умолчанию выводится из прежнего {@code super_objectives.mode}: партии на
     * старых сводах играются ровно так же, как играли.
     */
    public static boolean expansionOn(Ruleset ruleset, String name) {
        Object direct = ruleset.get("expansions." + name, null);
        if (direct != null) {
            return Boolean.TRUE.equals(direct);
        }
        String mode = legacyMode(ruleset);
        return switch (name) {
            case "super_objectives" -> "super".equals(mode)
                && Boolean.TRUE.equals(ruleset.get("super_objectives.enabled", Boolean.TRUE));
            case "starting_objectives" -> "starters".equals(mode);
            case "super_arsenal" -> true;   // прежде выключателя не было вовсе
            // КАРТЫ РЫНКА: на старых сводах их выключателя не было, и партии
            // играли С НИМИ — значит по умолчанию включены, иначе прежние
            // замеры поменяли бы смысл.
            case "market_cards" -> true;
            default -> false;
        };
    }

    /** Прежний трёхзначный режим — только для сводов, не знающих про дополнения. */
    private static String legacyMode(Ruleset ruleset) {
        Object m = ruleset.get("super_objectives.mode", null);
        if (m != null) {
            return String.valueOf(m);
        }
        return Boolean.TRUE.equals(ruleset.get("super_objectives.enabled", Boolean.TRUE))
            ? "super" : "none";
    }

    private static List<Integer> requestedCuFacing(GameConfig config, int seat, Hex startHex) {
        if (config.cuFacing == null || seat >= config.cuFacing.size()) {
            return null;
        }
        Integer face = config.cuFacing.get(seat);
        if (face == null) {
            return null;
        }
        int a = Math.floorMod(face, 6);
        int b = (a + 1) % 6;
        if (startHex.sideOwner[a] != null || startHex.sideOwner[b] != null) {
            return null;
        }
        return List.of(a, b);
    }

    /**
     * УМНЫЙ автоповорот ЦУ (решение дизайнера 2026-08-12): «нос» центра
     * управления — острый угол между его двумя длинными стенками — смотрит
     * В СТОРОНУ ЦЕНТРА ПОЛЯ, туда, куда игрок будет развиваться.
     *
     * <p>Геометрия: ЦУ занимает пару соседних сторон f и f+1; общий угол этих
     * сторон (тот самый «нос») направлен наружу под углом {@code −60·f − 30}.
     * Значит нужно выбрать f, у которого этот угол ближе всего к направлению на
     * центр поля. Если пара занята — берём следующую по близости.
     *
     * @return пара сторон, либо null (все пары заняты — редкий случай)
     */
    private static List<Integer> smartCuFacing(Field field, Hex startHex) {
        double[] me = hexCenter(startHex.id);
        if (me == null) {
            return null;                   // поле без осевых координат (кольцо-заглушка)
        }
        double sx = 0;
        double sy = 0;
        int count = 0;
        for (Hex h : field.hexes.values()) {
            double[] c = hexCenter(h.id);
            if (c != null) {
                sx += c[0];
                sy += c[1];
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        double toCentre = Math.toDegrees(Math.atan2(sy / count - me[1], sx / count - me[0]));
        List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5));
        order.sort((a, b) -> Double.compare(angleGap(-60.0 * a - 30, toCentre),
                                            angleGap(-60.0 * b - 30, toCentre)));
        // ОДНО ПРАВИЛО, И БОЛЬШЕ НИКАКИХ (решение дизайнера 13.08.2026): стенки ЦУ
        // выходят на стороны, обращённые К ЦЕНТРУ ПОЛЯ — туда, куда игрок будет
        // развиваться. Всё остальное — свобода игрока.
        //
        // Раньше здесь стояли ещё два запрета: не занимать сторону, за которой
        // лежит живая грядка (иначе стартовый добытчик до неё не дотянется), и не
        // накрывать печатную ячейку контейнера. Оба УБРАНЫ по прямому указанию
        // дизайнера: «не надо ограничивать игроков в размещении ЦУ на старте».
        // Побочный итог тоже важен: запреты разворачивали часть мест иначе, чем
        // остальные, и расстановка выглядела непоследовательно — теперь все места
        // повёрнуты по одному и тому же правилу.
        for (int f : order) {
            int g = (f + 1) % 6;
            if (startHex.sideOwner[f] != null || startHex.sideOwner[g] != null) {
                continue;                      // сторона уже занята — физика, не запрет
            }
            return List.of(f, g);
        }
        return null;
    }

    /**
     * Расхождение двух направлений в градусах: 0 — смотрят одинаково,
     * 180 — строго навстречу.
     */
    private static double angleGap(double a, double b) {
        double d = ((a - b) % 360 + 360) % 360;
        return d > 180 ? 360 - d : d;
    }

    /**
     * Центр гекса в пикселях по его id {@code h<q>_<r>} (гекс «остриём вверх»:
     * cx = √3·(q + r/2), cy = 1,5·r). null — id не в осевом формате.
     */
    private static double[] hexCenter(String id) {
        if (id == null || !id.startsWith("h") || !id.contains("_")) {
            return null;
        }
        try {
            String body = id.substring(1);
            int us = body.indexOf('_');
            int q = Integer.parseInt(body.substring(0, us));
            int r = Integer.parseInt(body.substring(us + 1));
            return new double[]{Math.sqrt(3) * (q + r / 2.0), 1.5 * r};
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Вернуть (Field, {seat: id стартового гекса}) — из сценария или кольцо-заглушку. */
    private static Scenario.FieldWithStarts loadField(GameConfig config, int n) {
        // B8: у сценариев СВОЙ ключ версии (независимый от досок).
        String version = config.ruleset.getStr("content_versions.scenarios",
            config.ruleset.getStr("content_versions.boards", "1.0.0"));
        String problem = null;
        try {
            // Если раскладка выбрана явно (config.scenarioId) — берём именно её,
            // иначе вариант подбирается по сиду, как раньше.
            Map<String, Object> scn = null;
            if (config.scenarioFile != null) {
                // Раскладка из своего файла (нарисована конструктором): берём
                // вариант по имени, а если имя не задано — первый в файле.
                List<Map<String, Object>> variants =
                    Scenario.loadVariantsFromFile(config.scenarioFile);
                for (Map<String, Object> v : variants) {
                    if (config.scenarioId == null
                            || config.scenarioId.equals(String.valueOf(v.get("id")))) {
                        scn = v;
                        break;
                    }
                }
                if (scn == null) {
                    throw new Scenario.ScenarioError("в файле " + config.scenarioFile
                        + " нет раскладки «" + config.scenarioId + "»");
                }
            } else if (config.scenarioId != null && !config.scenarioId.isBlank()) {
                for (Map<String, Object> v : Scenario.loadAllVariants(n, version, config.dataRoot)) {
                    if (config.scenarioId.equals(String.valueOf(v.get("id")))) {
                        scn = v;
                        break;
                    }
                }
                if (scn == null) {
                    throw new Scenario.ScenarioError("раскладка «" + config.scenarioId
                        + "» не найдена среди вариантов на " + n + " игроков");
                }
            } else {
                scn = Scenario.loadScenario(n, version, config.dataRoot, config.seed);
            }
            Scenario.FieldWithStarts fw = Scenario.buildFieldFromScenario(scn);
            boolean allSeats = true;
            for (int seat = 0; seat < n; seat++) {
                if (!fw.starts().containsKey(seat)) {
                    allSeats = false;
                    break;
                }
            }
            if (allSeats) {
                return fw;
            }
            problem = "в сценарии нет стартов для всех мест";
        } catch (Scenario.ScenarioError e) {
            problem = e.getMessage();
        }
        // B8: заглушка больше НЕ тихая — партия на кольце вместо реального поля
        // это другая игра; сигналим громко в stderr.
        System.err.println("[SETUP] ВНИМАНИЕ: сценарий " + n + "p v" + version
            + " не загружен (" + problem + ") — играем на КОЛЬЦЕ-ЗАГЛУШКЕ!");
        Field field = buildRingField(n);
        Map<Integer, String> starts = new HashMap<>();
        for (int seat = 0; seat < n; seat++) {
            starts.put(seat, "start" + seat);
        }
        return new Scenario.FieldWithStarts(field, starts);
    }

    /**
     * Собрать полное начальное {@link GameState} из конфигурации.
     *
     * <p>Готовит характеристики жетонов (с бонусом HP), назначает стороны
     * планшетов, строит поле, расставляет стартовую раскладку каждого игрока
     * (ЦУ, добытчик №1, 1 пехота), раздаёт по одному супер-заданию, создаёт
     * тех-планшет и колоды (с отбраковкой карт под число игроков).
     */
    public static GameState buildGame(GameConfig config) {
        return buildGame(config, null);
    }

    /**
     * То же, но со ОТДЕЛЬНЫМ сидом РАСКЛАДКИ БЛОКОВ.
     *
     * <p>Поле собирается из картонных блоков, и на них напечатаны контейнеры
     * (см. {@link BlockStamp}). За столом игроки каждый раз кладут блоки иначе,
     * поэтому полезно уметь перекатывать ТОЛЬКО их: то же поле, те же колоды,
     * те же боты — другое расположение контейнеров. Проигрыватель этим и
     * пользуется в кнопке «другая сборка блоков».
     *
     * @param blockSeed сид раскладки блоков; null — брать общий сид партии
     */
    @SuppressWarnings("unchecked")
    public static GameState buildGame(GameConfig config, Long blockSeed) {
        Random rng = config.seed != null ? new Random(config.seed) : new Random();
        Ruleset ruleset = config.ruleset;
        var content = config.content;
        int n = config.numPlayers;

        // ПРИВЯЗАТЬ ДАННЫЕ К КАРТАМ-ОБЪЕКТАМ (заказ дизайнера 15.08.2026, модуль
        // cards). До этой строки CardRegistry.bindAll не вызывался НИГДЕ, кроме
        // тестов: карта-объект существовала, но играла на пустых данных — её
        // пороги брались из запасных значений в коде, а не из YAML партии. Без
        // привязки Objective.progress()/needed() нельзя было спросить осмысленно
        // ни разу за всю живую партию.
        kelium.engine.cards.CardRegistry.bindAll("objectives", content.get("objectives").entries);
        kelium.engine.cards.CardRegistry.bindAll("arsenal", content.get("arsenal").entries);
        kelium.engine.cards.CardRegistry.bindAll("containers", content.get("containers").entries);
        kelium.engine.cards.CardRegistry.bindAll("market", content.get("market").entries);
        kelium.engine.cards.CardRegistry.bindAll("super_objectives",
            content.get("super_objectives").entries);
        kelium.engine.cards.CardRegistry.bindAll("super_arsenal",
            content.get("super_arsenal").entries);

        List<Map<String, Object>> boardsEntries = content.get("boards").entries;
        // Запись о жетонах — печатная, если опыт не подменил её копией с правками
        // (см. GameConfig.tokenStatsOverride).
        TokenStats stats = TokenStats.fromContent(
            config.tokenStatsOverride != null ? config.tokenStatsOverride
                : PlayerBoard.tokensEntry(boardsEntries),
            ruleset.tokenHpBonusAll());

        List<String> sides = resolveBoardSides(ruleset, n, config.boardSides);

        Scenario.FieldWithStarts fw = loadField(config, n);
        Field field = fw.field();
        Map<Integer, String> startHexes = fw.starts();

        // КОНТЕЙНЕРЫ 2.0: поле раскладывается картонными блоками, и с них на
        // ячейки гексов переносятся ПЕЧАТНЫЕ контейнеры. Жетон, вставший на
        // такую ячейку, немедленно берёт карту контейнера из запаса.
        // Отдельный сид, если он задан: тогда перекатывается ТОЛЬКО раскладка
        // блоков, а всё остальное в партии остаётся прежним.
        Random blockRng = blockSeed != null ? new Random(blockSeed) : rng;
        BlockStamp.stamp(field, GameConfig.resolveDataRoot(null), blockRng, ruleset);

        List<PlayerState> players = new ArrayList<>();
        int uid = 0;
        for (int seat = 0; seat < n; seat++) {
            // СТОРОНЫ ПЛАНШЕТОВ — ПО ОТДЕЛЬНОСТИ. На столе игрок кладёт планшет
            // войск одной стороной, а хранилища — какой захочет, и просьба
            // дизайнера (13.08.2026) выбирать их независимо. Ничего не выбрано —
            // обе стороны прежние, из правил.
            String side = sides.get(seat);
            GameConfig.SeatPick pick = config.seatPick(seat);
            String troopSide = pick.troopSide() == null ? side : pick.troopSide();
            // СКЛАДСКИЕ СТОРОНЫ ЖИВУТ ОТДЕЛЬНО. Наборы «В» и «Г» (15.08.2026)
            // меняют ТОЛЬКО планшет войск — таблицы атак и места под красные
            // модули. Складских сторон с такими кодами не существует и не
            // задумано, поэтому склад остаётся стороной А.
            String storageSide = pick.storageSide() != null ? pick.storageSide()
                : (side.startsWith("V") || side.startsWith("G") ? "A" : side);
            PlayerBoard board = PlayerBoard.fromContent(boardsEntries, troopSide, storageSide);
            // Стартовые монеты: из ruleset (setup.start_coins), иначе умолчание —
            // 5 всем (решение 2026-08-12).
            int startCoins;
            Object coinsCfg = ruleset.get("setup.start_coins", null);
            if (coinsCfg instanceof List<?> lst && seat < lst.size()
                    && lst.get(seat) instanceof Number cn) {
                startCoins = cn.intValue();
            } else {
                startCoins = seat < START_COINS.length ? START_COINS[seat] : 4;
            }
            Resources res = new Resources(startCoins, START_KELIUM, START_AMMO, 0);
            String startHex = startHexes.get(seat);
            PlayerState ps = new PlayerState(seat, board, res, startHex);
            Hex sh = field.get(startHex);

            // ЦУ (2 смежные стороны, +2 энергии), добытчик №1 (1 сторона), 1 пехота.
            BuildingToken cu = stats.makeBuilding(BuildingType.COMMAND_CENTER, seat, uid++, null);
            cu.hexId = startHex;
            // ЦУ — САМ СЕБЕ ИСТОЧНИК И ПОТРЕБИТЕЛЬ: он приходит с двумя своими
            // кубиками, но ЯЧЕЙКА У НЕГО ОДНА. Первый кубик встаёт в ячейку и
            // запитывает ЦУ, второй ОСТАЁТСЯ ЛЕЖАТЬ в его зоне свободной энергии
            // (правило дизайнера, подтверждено 13.08.2026).
            //
            // Раньше здесь оба кубика запихивались в ячейки — получалось «занято 2
            // из 1», свободных 0. На поле второй кубик было негде показать, и он
            // просто исчезал из виду; проверка по записи: свободная энергия у ЦУ
            // была 0 из 3008 случаев. Стройка ЦУ по ходу партии делает это верно
            // (см. Actions), расходилась только стартовая расстановка.
            int cuGives = stats.buildingEnergyGives(BuildingType.COMMAND_CENTER);
            int cuSelf = Math.min(cuGives, cu.energySlots);
            cu.addEnergyFrom(cu.uid, cuSelf);
            cu.energyIdle = cuGives - cuSelf;
            // ПОВОРОТ ЦУ на старте: игрок (или дизайнер в проигрывателе) может
            // задать, с какой стороны гекса стоит ЦУ — от этого зависит, какие
            // стенки закрыты и куда открыт выход. Не задан — как раньше, первая
            // свободная пара сторон.
            List<Integer> cuFp = requestedCuFacing(config, seat, sh);
            if (cuFp == null) {
                cuFp = smartCuFacing(field, sh);          // носом к центру поля
            }
            if (cuFp == null) {
                // поле без осевых координат — обычный подбор следа
                cuFp = sh.chooseFootprint(
                    Placement.footprint(BuildingType.COMMAND_CENTER), 0, 0);
            }
            // КОНТЕЙНЕРЫ 2.0: запрета накрывать ячейку с печатным контейнером
            // НЕТ. Как бы ЦУ ни встало, стартовый контейнер игрок получит:
            // накрыло ячейку — контейнер берёт САМО ЦУ (оно тоже жетон,
            // вставший на ячейку), не накрыло — его возьмёт пехота. Выдача
            // идёт ниже, после расстановки жетонов.
            sh.occupySides(cu.uid, cuFp != null ? cuFp : List.of(0, 1));
            ps.buildings.add(cu);

            // Стартовый добытчик №1 — только если версия правил его кладёт.
            // СВОД: «На старте у игрока ЦУ, две энергии и одна пехота» — в 1.5.0
            // setup.start_miner=false (решение дизайнера 2026-08-11).
            if (ruleset.getBool("setup.start_miner", true)) {
                BuildingToken miner = stats.makeBuilding(BuildingType.MINER, seat, uid++, 1);
                miner.hexId = startHex;
                List<Integer> fp = sh.chooseFootprint(
                    Placement.footprint(BuildingType.MINER), 0, 0);
                if (fp != null) {
                    sh.occupySides(miner.uid, fp);
                }
                ps.buildings.add(miner);
            }

            UnitToken inf = stats.makeUnit(UnitType.INFANTRY, seat, uid++);
            inf.hexId = startHex;
            ps.units.add(inf);
            // КОНТЕЙНЕРЫ 2.0: стартовая пехота ставится на ячейку с ПЕЧАТНЫМ
            // контейнером своего гекса, поэтому каждый игрок начинает партию с
            // одной случайной картой контейнера. ЦУ поставлено раньше и ячейку
            // не занимает; если контейнер стартового гекса оказался воздушным,
            // пехоте до него не дотянуться — стартового контейнера не будет.


            players.add(ps);
        }

        // СУПЕР ЗАДАНИЯ. Раздаются открытыми с подготовки. С версии 2.0 правил
        // (12.08.2026) игроку раздаётся НЕСКОЛЬКО карт (super_objectives.deal) и
        // он оставляет одну (choose); прочие уходят в коробку. Выбор делает сам
        // игрок — Setup агента не знает, поэтому здесь фиксируется ПРЕДЛОЖЕНИЕ,
        // а выбор совершает движок первым делом партии (GameEngine.offerSuperPick).
        // РЕЖИМ СТАРТОВЫХ ЗАДАНИЙ (правило дизайнера 12.08.2026), три варианта:
        //   super    — супер задания: 2 карты на выбор, оставляешь 1;
        //   starters — НАЧАЛЬНЫЕ задания: ровно так же, 2 карты на выбор, 1 себе;
        //   none     — без стартовых заданий вообще.
        // Начальные задания (kind: starting) в любом режиме изымаются из ОБЩЕЙ
        // колоды заданий: это отдельный модуль старта, а не обычные карты.
        int deal = Math.max(1, ((Number) ruleset.get("super_objectives.deal", 1)).intValue());
        if (expansionOn(ruleset, "super_objectives")) {
            List<String> superIds = new ArrayList<>(content.get("super_objectives").ids());
            Collections.shuffle(superIds, rng);
            int at = 0;
            for (PlayerState ps : players) {
                for (int k = 0; k < deal && at < superIds.size(); k++, at++) {
                    ps.superObjectiveOffer.add(superIds.get(at));
                }
                // Одна карта в предложении = выбора нет, ставим сразу.
                if (ps.superObjectiveOffer.size() == 1) {
                    ps.superObjective = ps.superObjectiveOffer.get(0);
                }
            }
        }
        // НЕЗАВИСИМЫЙ ТУМБЛЕР: начальные задания включаются отдельно и МОГУТ
        // играться вместе с супер заданиями (решение дизайнера 17.08.2026).
        if (expansionOn(ruleset, "starting_objectives")) {
            List<String> starters = new ArrayList<>();
            for (Map<String, Object> e : content.get("objectives").entries) {
                if ("starting".equals(e.get("kind"))) {
                    starters.add((String) e.get("id"));
                }
            }
            Collections.shuffle(starters, rng);
            int at = 0;
            for (PlayerState ps : players) {
                for (int k = 0; k < deal && at < starters.size(); k++, at++) {
                    ps.startObjectiveOffer.add(starters.get(at));
                }
                if (ps.startObjectiveOffer.size() == 1) {
                    ps.objectiveHand.add(ps.startObjectiveOffer.get(0));
                }
            }
        }

        // МЕШКИ ЖЕТОНОВ МОДУЛЕЙ («Модули 2.0», 12.08.2026): в мешок кладутся
        // полные наборы по числу игроков; награда «модуль» тянет случайный жетон.
        // Мешки заполняются ниже, когда GameState уже собран (нужен его rng).
        // Доска науки.
        Map<String, Object> te = PlayerBoard.techEntry(boardsEntries);
        List<String> trackIds = new ArrayList<>();
        for (Object tObj : (List<Object>) te.get("tracks")) {
            trackIds.add((String) ((Map<String, Object>) tObj).get("id"));
        }
        TechBoard tech = TechBoard.create(trackIds, ruleset.getInt("tech.steps_per_track"));

        // Колоды (с отбраковкой по числу игроков).
        Map<String, Deck> decks = new HashMap<>();
        for (String ctype : new String[]{"objectives", "arsenal", "containers", "orders", "market"}) {
            List<String> ids = Deck.cullForPlayers(content.get(ctype).entries, n);
            // ОТСЕВ НЕРЕАЛИЗОВАННЫХ КАРТ ОТМЕНЁН (решение дизайнера 17.08.2026).
            // Это был артефакт симуляции, а не правило игры: за столом карту из
            // колоды никто не вынимает, и «карта молча исчезла из партии» —
            // худший из возможных способов сообщить о недоделке. Теперь колода
            // играется целиком, а недоделки ловит тест каталога (CardCatalogTest)
            // — громко и до партии, а не тихо и во время неё.
            //
            // cullUnimplemented оставлен как ДИАГНОСТИКА: он больше ничего не
            // изымает, но по-прежнему называет карты, у которых эффект или
            // пассивка не реализованы.
            reportUnimplemented(ctype, ids, content.get(ctype));

            // КАРТЫ, ЗАВИСЯЩИЕ ОТ ВЫКЛЮЧЕННОГО ДОПОЛНЕНИЯ, изымаются из колоды.
            // Признак стоит В ДАННЫХ карты (needs_expansion), а не списком
            // номеров в коде: иначе следующая такая карта потеряется молча — так
            // уже было с шестью картами, которые движок выбрасывал за спиной.
            // Это ЕДИНСТВЕННЫЙ законный повод убрать карту из колоды: дополнение
            // выключено целиком, и без него условие карты невыполнимо.
            {
                List<String> kept = new ArrayList<>();
                List<String> dropped = new ArrayList<>();
                for (String id : ids) {
                    Map<String, Object> card = content.get(ctype).find(id);
                    Object need = card == null ? null : card.get("needs_expansion");
                    if (need != null && !expansionOn(ruleset, String.valueOf(need))) {
                        dropped.add(id + " (нужно дополнение «" + need + "»)");
                    } else {
                        kept.add(id);
                    }
                }
                if (!dropped.isEmpty()) {
                    System.err.println("[SETUP] " + ctype
                        + ": изъято по выключенному дополнению — " + dropped);
                    ids = kept;
                }
            }

            // СПОРНЫЕ КАРТЫ (backlog E12/E13/E14). НАЙДЕНО 18.08.2026: признак
            // contested в данных карты и переключатели contested_cards.*_enabled
            // в правилах существовали годами, но НИКТО и НИКОГДА их не
            // сопоставлял — контейнеры c26 «Резервный генератор» (energy_
            // without_source) и c28 «Шифровка» (effect_survives_round) играли
            // ВСЕГДА, хотя по умолчанию оба переключателя стоят false. Тот же
            // класс ошибки, что и с needs_expansion: заготовленный выключатель
            // без единого провода к нему.
            {
                List<String> kept = new ArrayList<>();
                List<String> dropped = new ArrayList<>();
                for (String id : ids) {
                    Map<String, Object> card = content.get(ctype).find(id);
                    Object contested = card == null ? null : card.get("contested");
                    if (contested != null && !Boolean.TRUE.equals(
                            ruleset.get("contested_cards." + contested + "_enabled", Boolean.FALSE))) {
                        dropped.add(id + " (спорная карта «" + contested + "» выключена)");
                    } else {
                        kept.add(id);
                    }
                }
                if (!dropped.isEmpty()) {
                    System.err.println("[SETUP] " + ctype
                        + ": изъято как спорная карта с выключенным правилом — " + dropped);
                    ids = kept;
                }
            }
            // РЕЖИМ ИГРЫ (правило дизайнера 12.08.2026): супер задания и
            // НАЧАЛЬНЫЕ задания вместе не играются. В режиме super начальные
            // задания из колоды изымаются целиком.
            if ("objectives".equals(ctype)) {
                List<String> kept = new ArrayList<>();
                for (String id : ids) {
                    Map<String, Object> card = content.get(ctype).find(id);
                    if (card == null || !"starting".equals(card.get("kind"))) {
                        kept.add(id);
                    }
                }
                if (kept.size() != ids.size()) {
                    System.err.println("[SETUP] режим супер заданий: изъято начальных заданий "
                        + (ids.size() - kept.size()));
                }
                ids = kept;
            }
            // ==============================================================
            //  ЛАБОРАТОРНЫЕ РУЧКИ (только для балансовых стендов)
            // ==============================================================
            // Средние по колоде ничего не говорят о КОНКРЕТНОЙ карте: слабая
            // карта тонет в общей статистике. Эти два ключа ставят карту в
            // условия, где её нельзя не увидеть. В обычной партии оба не заданы.
            if ("market".equals(ctype)) {
                // Вся колода рынка — из одной карты: она действует все раунды.
                String only = ruleset.getStr("market.only_card", null);
                if (only != null && !only.isBlank()) {
                    List<String> same = new ArrayList<>();
                    for (int i = 0; i < ids.size(); i++) {
                        same.add(only);
                    }
                    ids = same;
                }
            }
            if ("objectives".equals(ctype)) {
                // Копии одной карты задания по числу игроков — чтобы она
                // гарантированно дошла до руки.
                String extra = ruleset.getStr("objectives.extra_copies_card", null);
                int copies = ((Number) ruleset.get("objectives.extra_copies_count", 0))
                    .intValue();
                if (extra != null && !extra.isBlank() && copies > 0) {
                    List<String> more = new ArrayList<>(ids);
                    for (int i = 0; i < copies; i++) {
                        more.add(extra);
                    }
                    ids = more;
                }
            }
            if ("market".equals(ctype) && !expansionOn(ruleset, "market_cards")) {
                // КАРТЫ РЫНКА ВЫКЛЮЧЕНЫ: колода не собирается вовсе, активной
                // карты в партии нет, и на планшете маркета остаётся только
                // напечатанный обмен. Пустая колода здесь — законное состояние,
                // а не сбой: движок уже умеет жить без активной карты.
                decks.put(ctype, Deck.fromIds(ctype, List.of(), rng));
                continue;
            }
            decks.put(ctype, Deck.fromIds(ctype, ids, rng));
        }

        // (метод cullUnimplemented — внизу файла)

        // Стартовые карты арсенала: по одной каждому игроку (kind == "starting").
        // Кладём взакрытую в руку арсенала, из колоды удаляем, обычные (24) остаются.
        List<String> startingArsenal = new ArrayList<>();
        for (Map<String, Object> e : content.get("arsenal").entries) {
            if ("starting".equals(e.get("kind"))) {
                startingArsenal.add((String) e.get("id"));
            }
        }
        Collections.shuffle(startingArsenal, rng);
        for (int seat = 0; seat < players.size() && seat < startingArsenal.size(); seat++) {
            String cid = startingArsenal.get(seat);
            players.get(seat).arsenalHand.add(cid);
            decks.get("arsenal").removeCard(cid);   // не должна выпасть повторно
        }

        GameState s = new GameState(config, players, field, stats, tech, decks, rng, 0);
        s.journal = new kelium.core.TurnJournal(n);

        // ВИТРИНА АРСЕНАЛА (правило дизайнера 15.08.2026): две открытые карты
        // рядом с планшетом науки. Карты СНИМАЮТСЯ С КОЛОДЫ — вытянуть их
        // вслепую, пока они лежат на витрине, нельзя, ровно как за столом.
        refillArsenalDisplay(s);

        // МЕШКИ МОДУЛЕЙ («Модули 2.0», 12.08.2026): по полному набору жетонов на
        // каждого игрока — 8/12/16 жетонов на 2/3/4 игроков для мешка из одного
        // набора. Выключено правилами — списки остаются пустыми, и модули
        // выдаются прежним счётчиком.
        if (Boolean.TRUE.equals(ruleset.get("modules.from_bag", Boolean.FALSE))) {
            Object mv = ruleset.get("content_versions.modules", null);
            ModuleSets.Library lib = ModuleSets.load(config.dataRoot,
                mv == null ? null : mv.toString());
            String redBagId = String.valueOf(ruleset.get("modules.red_bag", "bag_R1"));
            String blueBagId = String.valueOf(ruleset.get("modules.blue_bag", "bag_C"));
            s.redBag.addAll(ModuleSets.buildBag(lib, lib.redSets(), redBagId, n, rng));
            s.blueBag.addAll(ModuleSets.buildBag(lib, lib.blueSets(), blueBagId, n, rng));
            if (s.redBag.isEmpty() && s.blueBag.isEmpty()) {
                System.err.println("[SETUP] ВНИМАНИЕ: мешки модулей включены, но пусты "
                    + "(наборы " + redBagId + "/" + blueBagId + " не найдены) — "
                    + "модули не будут выдаваться!");
            }
        }

        // СТАРЫЙ РЕЖИМ КОНТЕЙНЕРОВ (ruleset 1.6.0-c1): жетоны раскладываются по
        // пустым гексам, печатные ячейки гасятся. В основном режиме ничего не
        // делает.
        int laidTokens = TokenContainers.layoutAtSetup(s);
        if (laidTokens > 0) {
            System.err.println("[SETUP] режим ЖЕТОНОВ контейнеров: разложено "
                + laidTokens + " жетонов по пустым гексам");
        }

        // СТАРТОВЫЙ КОНТЕЙНЕР — РОВНО ОДИН КАЖДОМУ, из запаса.
        //
        // Печатные контейнеры на блоках ложатся случайно, и стартовому гексу
        // могло не достаться ни одного (малый блок несёт 4 контейнера на 5
        // гексов), а мог достаться воздушный, до которого наземным жетонам не
        // дотянуться. Привязывать стартовую карту к раскладке — значит делать
        // старт неравным на ровном месте, поэтому она выдаётся безусловно.
        for (PlayerState p : players) {
            Storage.addContainersCapped(s, p, 1, "подготовка");
        }

        // Супер-арсенал (треки 2.0): из 9 карт выложить В ОТКРЫТУЮ по одной на
        // вершину каждого трека; остальные — «в коробку» (не участвуют).
        // ДОПОЛНЕНИЕ: выключается тумблером, и тогда вершина трека даёт другой
        // приз (см. Actions, ветка super_arsenal_card).
        if (expansionOn(ruleset, "super_arsenal")) {
            try {
                List<String> saIds = new ArrayList<>(content.get("super_arsenal").ids());
                Collections.shuffle(saIds, rng);
                for (int i = 0; i < tech.tracks.size() && i < saIds.size(); i++) {
                    s.superArsenalOffer.put(tech.tracks.get(i), saIds.get(i));
                }
            } catch (RuntimeException e) {
                // контента может не быть в старых версиях правил — играем без вершин
            }
        }
        return s;
    }

    /**
     * E1/E2: изъять из колоды карты, чьи эффекты/пассивки ещё не реализованы
     * движком (решение ревизии 2026-08-11: «реализовать либо изъять»). Изъятие
     * ГРОМКОЕ — каждый отсев пишется в stderr. Стартовые карты арсенала не
     * проходят через колоду и не отсеиваются (их мёртвые низы — вопрос дизайнеру).
     */
    @SuppressWarnings("unchecked")
    private static List<String> reportUnimplemented(String ctype, List<String> ids,
                                                    kelium.dataio.ContentSet set) {
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            Map<String, Object> card = set.byId(id);
            String bad = null;
            switch (ctype) {
                case "arsenal" -> {
                    Map<String, Object> top = (Map<String, Object>) card.get("top");
                    Map<String, Object> bottom = (Map<String, Object>) card.get("bottom");
                    if (top != null && !Effects.isImplemented((String) top.get("effect"))) {
                        bad = "top effect " + top.get("effect");
                    } else if (bottom != null && bottom.get("scoring") != null) {
                        // КАРТА-ЦЕЛЬ (арсенал 2.1.0): низ не меняет правил, а
                        // считает очки в конце партии. Способности у неё нет и не
                        // должно быть — отсев по «нереализованной пассивке» здесь
                        // выбросил бы совершенно рабочую карту.
                        bad = null;
                    } else if (bottom != null
                            && !Passives.isImplemented((String) bottom.get("passive"))) {
                        bad = "passive " + bottom.get("passive");
                    }
                }
                case "containers" -> {
                    for (String v : new String[]{"a", "b"}) {
                        Map<String, Object> var = (Map<String, Object>) card.get(v);
                        if (var != null && !Effects.isImplemented((String) var.get("effect"))) {
                            bad = "variant " + v + " effect " + var.get("effect");
                            break;
                        }
                    }
                }
                case "market" -> {
                    for (String side : new String[]{"left", "right"}) {
                        Map<String, Object> offer = (Map<String, Object>) card.get(side);
                        if (offer != null && !Effects.isImplemented((String) offer.get("effect"))) {
                            bad = side + " effect " + offer.get("effect");
                            break;
                        }
                    }
                }
                default -> {
                    // objectives/orders: эффектных заглушек нет, не отсеиваем
                }
            }
            // КАРТА ОСТАЁТСЯ В КОЛОДЕ ВСЕГДА: отсева больше нет, есть только
            // сообщение о недоделке.
            out.add(id);
            if (bad != null && REPORTED_CULLS.add(ctype + "/" + id)) {
                // Один раз на процесс, а не на каждую партию: в батче это были
                // десятки тысяч строк в консоль (92% всего лога прогона).
                System.err.println("[SETUP] НЕ РЕАЛИЗОВАНО в карте " + ctype + "/" + id
                    + " (" + card.getOrDefault("name", "?") + "): " + bad);
            }
        }
        return out;
    }

    /**
     * ПОПОЛНИТЬ ВИТРИНУ АРСЕНАЛА до двух открытых карт.
     *
     * <p>Вызывается на подготовке и сразу после того, как игрок забрал карту с
     * витрины. Карты берутся С ВЕРХА КОЛОДЫ и физически уходят из неё; если
     * колода и сброс исчерпаны, витрина остаётся неполной — это законное
     * состояние партии, а не ошибка.
     */
    public static void refillArsenalDisplay(GameState s) {
        kelium.core.Deck deck = s.decks.get("arsenal");
        if (deck == null) {
            return;
        }
        while (s.arsenalDisplay.size() < 2) {
            String card = deck.draw(s.rng);
            if (card == null) {
                break;                    // карт больше нет — витрина неполная
            }
            s.arsenalDisplay.add(card);
        }
    }

}
