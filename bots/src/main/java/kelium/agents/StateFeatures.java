package kelium.agents;

import java.util.List;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.Scoring;
import kelium.engine.Storage;

/**
 * ПРИЗНАКИ ПОЗИЦИИ — единственное описание «как у меня дела», которым пользуются
 * ВСЕ обучаемые части системы.
 *
 * <p>Зачем один класс на всех. Раньше оценка позиции жила в одном месте
 * (семь слагаемых внутри бота), признаки для нейросети — в другом, а отчёты
 * считали своё третье. Три описания расходились, и обучение одного не помогало
 * другому. Теперь описание одно: {@link #of} возвращает вектор, а
 * <ul>
 *   <li>геном ({@link Genome}) даёт ЛИНЕЙНЫЕ веса к этим признакам — читаемая
 *       оценочная функция, которую настраивает отбор;</li>
 *   <li>нейросеть ({@link ValueNet}) учится НЕЛИНЕЙНОЙ функции от тех же
 *       признаков — и её можно честно сравнить с линейной.</li>
 * </ul>
 *
 * <p>Признаки НАМЕРЕННО «человеческие»: каждый можно назвать словами и объяснить
 * дизайнеру, почему бот считает позицию хорошей. Это условие того, что из игры
 * ботов можно извлечь понимание, а не только проценты побед.
 */
public final class StateFeatures {

    private StateFeatures() {
    }

    /** Имена признаков. Порядок = порядок значений в векторе {@link #of}. */
    public static final List<String> NAMES = List.of(
        "vp",                  // победные очки сейчас
        "margin",              // отрыв от сильнейшего соперника
        "coin",                // монеты (топливо стройки)
        "kelium",              // келемий (и очко, и товар)
        "ammo",                // боеприпасы (топливо войны)
        "trophy_pool",         // трофеи: жетоны под трофеи + чёрные кубы
        "miners_working",      // запитанные добытчики У ЖИВОЙ жилы
        "kelium_reachable",    // келемий на жилах, до которых я дотягиваюсь
        "storage_room",        // свободное место в хранилище
        "power_plants",        // энергостанций
        "energy_idle",         // простаивающие кубики энергии (запас гибкости)
        "energy_hungry",       // незапитанные ячейки (обратный признак: беда)
        "military_powered",    // запитанные военные здания
        "strike_buildings",    // заводы+авиабазы (только они дают бьющих здания)
        "units",               // войск на поле
        "strike_units",        // войск, способных бить здания (техника/авиация/вышка)
        "tech_steps",          // сумма шагов по трекам науки
        "tech_peaks",          // вершины треков, занятые мной
        "objectives_hand",     // карт заданий в руке
        "super_progress",      // прогресс супер-задания
        "arsenal_installed",   // установленных карт арсенала
        "containers",          // контейнеров на руках
        "cu_tokens",           // жетонов разрушения ЦУ (2 = мгновенная победа)
        "enemy_cu_damage",     // урон, накопленный на чужих ЦУ (осада идёт)
        "killable_in_range",   // чужих жетонов, которых я могу убить прямо сейчас
        "my_exposed",          // моих жетонов под ударом соперника (риск)
        "tiles_flipped",       // выработанных тайлов зарождения (это ПО)
        "tempo_economy",       // экономика, взвешенная по РАННОСТИ раунда
        "buildings");          // зданий на поле

    /** Масштабы для нормировки (нейросети нужен вход около [0..1]). */
    public static final double[] SCALES = {
        12, 10, 15, 8, 8, 10, 3, 12, 6, 3, 4, 5, 3, 2, 10, 6,
        12, 3, 3, 5, 3, 4, 2, 4, 6, 6, 4, 20, 8
    };

    /** Число признаков. */
    public static final int DIM = NAMES.size();

    /**
     * Посчитать признаки позиции глазами места {@code seat}. Только ОТКРЫТАЯ
     * информация: свои карты, поле, треки, ресурсы всех игроков — то, что видно
     * за столом. Руки соперников не читаются.
     */
    public static double[] of(GameState s, int seat) {
        double[] f = new double[DIM];
        PlayerState me = s.player(seat);
        WorldView wv = new WorldView(s, seat);

        int vp = Scoring.scorePlayer(s, seat).getOrDefault("total", 0);
        int rivalMax = Integer.MIN_VALUE;
        for (int st = 0; st < s.numPlayers(); st++) {
            if (st == seat) {
                continue;
            }
            rivalMax = Math.max(rivalMax, Scoring.scorePlayer(s, st).getOrDefault("total", 0));
        }
        f[0] = vp;
        f[1] = rivalMax == Integer.MIN_VALUE ? 0 : vp - rivalMax;
        f[2] = me.resources.coin();
        f[3] = me.resources.kelium();
        f[4] = me.resources.ammo();
        f[5] = me.trophySpacePoints() + me.resources.debris();

        java.util.Set<String> live = Plan.liveTileHexes(s);
        int minersWorking = 0;
        int plants = 0;
        int idle = 0;
        int hungry = 0;
        int milPowered = 0;
        int strikeBld = 0;
        int buildings = 0;
        for (BuildingToken b : me.buildingsOnField()) {
            buildings++;
            hungry += Math.max(0, b.energySlots - b.energyPlaced);
            switch (b.type) {
                case MINER -> {
                    if (b.powered() && Plan.touchesLiveTile(s, b.hexId, live)) {
                        minersWorking++;
                    }
                }
                case POWER_PLANT -> {
                    plants++;
                    idle += b.energyIdle;
                }
                case COMMAND_CENTER -> idle += b.energyIdle;
                default -> { }
            }
            if (b.type == BuildingType.BARRACKS || b.type == BuildingType.FACTORY
                    || b.type == BuildingType.AIRBASE) {
                if (b.powered()) {
                    milPowered++;
                }
                if (b.type != BuildingType.BARRACKS) {
                    strikeBld++;
                }
            }
        }
        f[6] = minersWorking;
        // Сколько келемия я реально могу достать: жилы, к которым примыкает хоть
        // одно моё здание. «Келемий на поле вообще» ботов только обманывал.
        int reachable = 0;
        for (Hex h : s.field.hexes.values()) {
            if (h.spawnTile == null || h.spawnTile.kelium <= 0) {
                continue;
            }
            for (String nb : s.field.neighborsView(h.id)) {
                boolean mine = false;
                for (BuildingToken b : me.buildingsOnField()) {
                    if (nb.equals(b.hexId)) {
                        mine = true;
                        break;
                    }
                }
                if (mine) {
                    reachable += h.spawnTile.kelium;
                    break;
                }
            }
        }
        f[7] = reachable;
        f[8] = Math.max(0, Storage.keliumMax(me) - me.resources.kelium());
        f[9] = plants;
        f[10] = idle;
        f[11] = hungry;
        f[12] = milPowered;
        f[13] = strikeBld;

        int units = 0;
        int strikeUnits = 0;
        for (UnitToken u : me.unitsOnField()) {
            units++;
            if (u.type == UnitType.VEHICLE || u.type == UnitType.AIRCRAFT
                    || u.type == UnitType.TOWER) {
                strikeUnits++;
            }
        }
        f[14] = units;
        f[15] = strikeUnits;

        int techSteps = 0;
        int peaks = 0;
        for (var e : me.techSteps.entrySet()) {
            techSteps += e.getValue();
            if (e.getValue() >= s.tech.steps) {
                peaks++;
            }
        }
        f[16] = techSteps;
        f[17] = peaks;
        f[18] = me.objectiveHand.size();
        f[19] = me.superObjectiveProgress;
        f[20] = me.arsenalInstalled.size();
        f[21] = me.containers;
        f[22] = me.cuDestructionTokens;

        int enemyCuDamage = 0;
        for (Token t : wv.enemyTokens) {
            if (t instanceof BuildingToken b && b.type == BuildingType.COMMAND_CENTER) {
                enemyCuDamage += b.damage;
            }
        }
        f[23] = enemyCuDamage;
        f[24] = wv.killableTargetsInStrikeRange();
        // Риск: сколько МОИХ жетонов стоит вплотную к чужим войскам. Раньше бот
        // не видел опасности вообще и лез под удар «потому что цель близко».
        f[25] = exposedTokens(s, seat);
        f[26] = me.flippedStartTiles + me.flippedNormalTiles;
        // ТЕМП: экономика в раунде 2 стоит куда больше, чем в раунде 7 — успеешь
        // ли ты её обналичить. Без этого признака бот строил добытчики в конце.
        double earliness = Math.max(0.0, (8.0 - s.round) / 8.0);
        f[27] = (minersWorking + plants + me.resources.kelium()) * earliness;
        f[28] = buildings;
        return f;
    }

    /** Сколько моих жетонов стоит на гексе, смежном с чужим войском. */
    private static int exposedTokens(GameState s, int seat) {
        PlayerState me = s.player(seat);
        java.util.Set<String> danger = new java.util.HashSet<>();
        for (PlayerState p : s.players) {
            if (p.seat == seat) {
                continue;
            }
            for (UnitToken u : p.unitsOnField()) {
                danger.add(u.hexId);
                danger.addAll(s.field.neighborsView(u.hexId));
            }
        }
        int n = 0;
        for (UnitToken u : me.unitsOnField()) {
            if (danger.contains(u.hexId)) {
                n++;
            }
        }
        for (BuildingToken b : me.buildingsOnField()) {
            if (danger.contains(b.hexId)) {
                n++;
            }
        }
        return n;
    }

    /** Нормированный вектор (для нейросети): каждый признак поделён на масштаб. */
    public static double[] normalized(GameState s, int seat) {
        double[] f = of(s, seat);
        double[] out = new double[DIM];
        for (int i = 0; i < DIM; i++) {
            double v = f[i] / SCALES[i];
            out[i] = Math.max(-2.0, Math.min(2.0, v));
        }
        return out;
    }

    /** Ключ веса генома для признака {@code i} — {@code eval.<имя>}. */
    public static String weightKey(int i) {
        return "eval." + NAMES.get(i);
    }
}
