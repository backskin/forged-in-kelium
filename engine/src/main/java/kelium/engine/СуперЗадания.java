package kelium.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.dataio.Ctx;

/**
 * СУПЕР-ЗАДАНИЯ 8.0 — ТОЛЬКО ПОБЕДНЫЕ ОЧКИ (правило дизайнера 14.09.2026).
 *
 * <p>Карта раздаётся по одной втайне на подготовке и ещё приходит за третью
 * ступень трека технологий. Её НЕ выполняют, не сжигают и не сбрасывают: она
 * лежит в руке до конца партии и в подсчёте очков даёт очки за ДВЕ свои
 * категории счёта. Каждая категория платит 1 ПО за свою единицу счёта.
 *
 * <p>ЧТО УДАЛЕНО ВМЕСТЕ С РЕДАКЦИЕЙ 7.0: множитель верха, жёсткое требование
 * низа с разовой наградой, СПЕЦ-действие {@code spec_super6_claim}, режимы
 * {@code solo5}/{@code solo6}, раздача «две на выбор» и вкладка супер-заданий
 * в проигрывателе. Базовые источники очков (монеты, келемий, трофеи, здания и
 * войска на поле) из общих правил вынесены в эти самые категории.
 *
 * <p>Категории и их пары живут в данных ({@code data/cards/super_objectives.*}),
 * здесь — только счёт.
 */
public final class СуперЗадания {

    private СуперЗадания() {
    }

    /**
     * РАЗДАТЬ ПО ОДНОЙ КАРТЕ ВТАЙНЕ. Карта ложится игроку в руку и остаётся там
     * до конца партии; соперники её не видят.
     */
    public static void deal(GameState s, List<String> ids, Random rng) {
        List<String> колода = new ArrayList<>(ids);
        Collections.shuffle(колода, rng);
        int at = 0;
        for (PlayerState p : s.players) {
            if (at >= колода.size()) {
                return;               // карт не хватило — играем без них
            }
            p.superObjectives.add(колода.get(at++));
        }
    }

    /**
     * ОЧКИ ЗА ВСЕ СУПЕР-ЗАДАНИЯ ИГРОКА. Карт может быть несколько (одна с
     * подготовки, ещё по одной за третьи ступени треков), и каждая платит за
     * обе свои категории.
     */
    public static int vp(GameState s, int seat) {
        PlayerState p = s.player(seat);
        int всего = 0;
        for (String id : p.superObjectives) {
            for (String категория : категорииКарты(s, id)) {
                всего += очкиКатегории(s, p, категория);
            }
        }
        return всего;
    }

    /** Пара категорий карты; пусто — карты нет или она старой редакции. */
    @SuppressWarnings("unchecked")
    public static List<String> категорииКарты(GameState s, String id) {
        Map<String, Object> card = Ctx.cards(s, "super_objectives").find(id);
        if (card == null || !(card.get("categories") instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            out.add(String.valueOf(o));
        }
        return out;
    }

    /** ВСЕ ДВЕНАДЦАТЬ КАТЕГОРИЙ СЧЁТА — единственный список, по которому платит игра. */
    public static final List<String> КАТЕГОРИИ = List.of(
        "buildings_3", "units_4", "kelium_4", "trophy_tokens_4",
        "tech_steps_4", "red_modules", "blue_modules", "units_at_enemy",
        "coins_4", "ammo_kelium_pairs", "plants_off_cell", "miners_on_container");

    /**
     * Знает ли движок эту категорию. Нужно сторожу каталога: неизвестная
     * категория молча платила бы ноль и выглядела рабочей.
     */
    public static boolean знаетКатегорию(String категория) {
        return КАТЕГОРИИ.contains(категория);
    }

    /**
     * СКОЛЬКО ПЛАТИТ ОДНА КАТЕГОРИЯ. Считается по состоянию на конец партии:
     * все двенадцать категорий смотрят на то, что лежит на столе и в хранилище,
     * и ни одна не зависит от истории партии.
     */
    public static int очкиКатегории(GameState s, PlayerState p, String категория) {
        return switch (категория) {
            case "buildings_3" -> p.buildingsOnField().size() / 3;
            case "units_4" -> p.unitsOnField().size() / 4;
            case "kelium_4" -> Math.min(4, p.resources.kelium());
            case "trophy_tokens_4" -> Math.min(4, p.destroyedTokens.size());
            case "tech_steps_4" -> Math.min(4, занятыеСтупени(p));
            case "red_modules" -> жетоновНаПланшете(p.redPlacements.values());
            case "blue_modules" -> жетоновНаПланшете(p.bluePlacements.values());
            case "units_at_enemy" -> войскаУЧужихЗданий(s, p);
            case "coins_4" -> p.resources.coin() / 4;
            case "ammo_kelium_pairs" -> Math.min(p.resources.ammo(), p.resources.kelium());
            case "plants_off_cell" -> энергостанцииВнеКруга(s, p);
            case "miners_on_container" -> добытчикиНаКонтейнере(s, p);
            default -> 0;
        };
    }

    /**
     * Сколько жетонов модуля лежит на планшете. Жетонов модулей в запасе не
     * бывает (правило дизайнера 14.09.2026): вытянутый жетон сразу ложится на
     * ячейку, поэтому считать больше нечего. Глухой жетон уничтожения ЦУ —
     * не модуль и в счёт не идёт.
     */
    private static int жетоновНаПланшете(java.util.Collection<Map<String, Object>> раскладка) {
        int n = 0;
        for (Map<String, Object> pl : раскладка) {
            if (!Boolean.TRUE.equals(pl.get("blocks"))) {
                n++;
            }
        }
        return n;
    }

    /**
     * СКОЛЬКО РАЗНЫХ СТУПЕНЕЙ занято кубиками игрока. Кубик остаётся на ступени
     * навсегда, поэтому игрок, дошедший на треке до третьей ступени, занимает
     * ступени 1, 2 и 3; разные треки на одной ступени считаются за одну.
     */
    private static int занятыеСтупени(PlayerState p) {
        Set<Integer> ступени = new HashSet<>();
        for (Integer дошёл : p.techSteps.values()) {
            for (int step = 1; step <= (дошёл == null ? 0 : дошёл); step++) {
                ступени.add(step);
            }
        }
        return ступени.size();
    }

    /** Свои войска, стоящие на гексе, где есть здание другого игрока. */
    private static int войскаУЧужихЗданий(GameState s, PlayerState p) {
        Set<String> чужиеГексы = new HashSet<>();
        for (PlayerState other : s.players) {
            if (other.seat == p.seat) {
                continue;
            }
            for (BuildingToken b : other.buildingsOnField()) {
                if (b.hexId != null) {
                    чужиеГексы.add(b.hexId);
                }
            }
        }
        int n = 0;
        for (UnitToken u : p.unitsOnField()) {
            if (u.hexId != null && чужиеГексы.contains(u.hexId)) {
                n++;
            }
        }
        return n;
    }

    /** Свои энергостанции, стоящие НЕ на круге энергии своего гекса. */
    private static int энергостанцииВнеКруга(GameState s, PlayerState p) {
        int n = 0;
        for (BuildingToken b : p.buildingsOnField()) {
            if (b.type == BuildingType.POWER_PLANT && !Power.onEnergyCell(s, b)) {
                n++;
            }
        }
        return n;
    }

    /** Свои добытчики, занявшие ячейку печатного контейнера своего гекса. */
    private static int добытчикиНаКонтейнере(GameState s, PlayerState p) {
        int n = 0;
        for (BuildingToken b : p.buildingsOnField()) {
            if (b.type != BuildingType.MINER || b.hexId == null) {
                continue;
            }
            Hex h = s.field.get(b.hexId);
            // Ячейка контейнера бывает не размечена (−1) и бывает НЕ НАЗЕМНОЙ
            // (6 — небо): наземных сторон у гекса шесть, и только они имеют
            // владельца.
            if (h == null || h.containerCell < 0 || h.containerCell >= h.sideOwner.length) {
                continue;
            }
            Integer owner = h.sideOwner[h.containerCell];
            if (owner != null && owner == b.uid) {
                n++;
            }
        }
        return n;
    }
}
