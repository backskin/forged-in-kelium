package kelium.agents;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Token;
import kelium.core.UnitToken;

/**
 * ЗАЧЕМ — НАМЕРЕНИЕ БОТА СЛОВАМИ (заказ дизайнера 27.09.2026).
 *
 * <p>«Мерило готовности бота: по его ходам понятно, чего он хочет и против кого
 * играет». Планировщик выбирает ход сравнением позиций до и после — и ровно
 * это сравнение говорит, зачем ход: кого из соперников он ослабил, что построил,
 * куда продвинулся. Фраза короткая, простыми словами, без служебных кодов: её
 * читает человек в окне партии и в разборе.
 *
 * <p>Соперник называется так же, как в окне партии: «Игрок N».
 */
public final class Замысел {

    private Замысел() {
    }

    /** «Игрока 3» — соперник в винительном падеже. */
    public static String игрока(int seat) {
        return "Игрока " + (seat + 1);
    }

    /** Кого бьём: лидера или просто соседа. */
    public static String кого(int seat, int лидер) {
        return seat == лидер ? "лидера — " + игрока(seat) : игрока(seat);
    }

    /** Ради чего строится здание. */
    public static String здание(BuildingType t) {
        if (t == null) {
            return "строю здание";
        }
        return switch (t) {
            case MINER -> "строю добытчик ради келемия";
            case POWER_PLANT -> "строю энергостанцию ради питания зданий";
            case BARRACKS -> "строю казарму ради пехоты";
            case FACTORY -> "строю завод ради техники";
            case AIRBASE -> "строю авиабазу ради авиации";
            case COMMAND_CENTER -> "возвращаю ЦУ на поле";
        };
    }

    private static Map<BuildingType, Integer> здания(PlayerState p) {
        Map<BuildingType, Integer> m = new EnumMap<>(BuildingType.class);
        for (BuildingToken b : p.buildingsOnField()) {
            m.merge(b.type, 1, Integer::sum);
        }
        return m;
    }

    private static int шагиНауки(PlayerState p) {
        int n = 0;
        for (int v : p.techSteps.values()) {
            n += v;
        }
        return n;
    }

    /**
     * НАМЕРЕНИЕ ХОДА — по разнице позиций до и после выбранного сценария.
     *
     * @param удары  чьи жетоны бил сценарий (место → попаданий)
     * @param лидер  соперник, которого бот считает лидером на этот ход
     */
    public static String ход(GameState до, GameState после, int seat, int лидер,
                             Map<Integer, Integer> удары, Intents in, List<String> действия) {
        List<String> фразы = new ArrayList<>();
        PlayerState я0 = до.player(seat);
        PlayerState я1 = после.player(seat);
        // ---- против кого -------------------------------------------------------
        int битый = -1;
        int попаданий = 0;
        boolean нейтралы = удары != null && удары.containsKey(-1);
        if (удары != null) {
            for (var e : удары.entrySet()) {
                if (e.getKey() < 0) {
                    continue;
                }
                // лидер при равенстве — первым: по нему и видно, против кого игра
                if (e.getValue() > попаданий || (e.getValue() == попаданий && e.getKey() == лидер)) {
                    битый = e.getKey();
                    попаданий = e.getValue();
                }
            }
        }
        if (битый >= 0) {
            фразы.add("бью " + кого(битый, лидер));
        } else if (нейтралы) {
            фразы.add("бью нейтральную постройку ради трофеев");
        }
        if (лидер >= 0 && лидер < после.numPlayers()
                && шагиНауки(я1) > шагиНауки(я0)
                && Угрозы.наукаДоступно(после, после.player(лидер))
                    < Угрозы.наукаДоступно(до, до.player(лидер))) {
            фразы.add("занимаю ступень науки раньше " + (лидер == битый ? "него"
                : "лидера — " + игрока(лидер)));
        }
        // ---- что себе ----------------------------------------------------------
        List<String> себе = new ArrayList<>();
        if (я1.objectivesCompleted > я0.objectivesCompleted) {
            себе.add("выполняю задание");
        }
        Map<BuildingType, Integer> з0 = здания(я0);
        Map<BuildingType, Integer> з1 = здания(я1);
        for (BuildingType t : List.of(BuildingType.COMMAND_CENTER, BuildingType.MINER,
                BuildingType.POWER_PLANT, BuildingType.AIRBASE, BuildingType.FACTORY,
                BuildingType.BARRACKS)) {
            if (з1.getOrDefault(t, 0) > з0.getOrDefault(t, 0)) {
                себе.add(здание(t));
                break;
            }
        }
        if (шагиНауки(я1) > шагиНауки(я0) && фразы.stream().noneMatch(f -> f.contains("науки"))) {
            себе.add("поднимаюсь по науке");
        }
        if (я1.unitsOnField().size() > я0.unitsOnField().size()) {
            себе.add("набираю войска");
        }
        if (я1.allInstalledArsenal().size() > я0.allInstalledArsenal().size()) {
            себе.add("ставлю карту арсенала");
        }
        if (я1.resources.kelium() > я0.resources.kelium()) {
            себе.add("добываю келемий");
        }
        String подвожу = подвод(до, после, seat, in, лидер);
        if (подвожу != null && битый < 0) {
            себе.add(подвожу);
        }
        if (я1.resources.trophy() + я1.destroyedValue()
                > я0.resources.trophy() + я0.destroyedValue() && битый < 0 && !нейтралы) {
            себе.add("беру трофеи для науки");
        }
        if (я1.containers > я0.containers) {
            себе.add("беру контейнер");
        }
        if (я1.arsenalHand.size() > я0.arsenalHand.size()) {
            себе.add("беру карту арсенала");
        }
        if (себе.isEmpty() && я1.resources.coin() > я0.resources.coin()) {
            себе.add("запасаю монеты");
        }
        if (себе.isEmpty() && я1.resources.ammo() > я0.resources.ammo()) {
            себе.add("запасаю боеприпасы");
        }
        if (себе.isEmpty() && энергия(я1) > энергия(я0)) {
            себе.add("запитываю здания");
        }
        if (себе.isEmpty() && сдвинуты(я0, я1)) {
            себе.add(подвожу != null ? подвожу : "переставляю войска");
        }
        if (!себе.isEmpty()) {
            фразы.add(себе.get(0));
        }
        if (фразы.isEmpty()) {
            if (действия == null || действия.isEmpty()) {
                return "пропускаю: ни одно действие сейчас ничего не даёт";
            }
            return "держу позицию";
        }
        return String.join("; ", фразы.subList(0, Math.min(2, фразы.size())));
    }

    /** Сколько кубиков энергии стоит на моих зданиях. */
    private static int энергия(PlayerState p) {
        int n = 0;
        for (BuildingToken b : p.buildingsOnField()) {
            n += b.energyPlaced;
        }
        return n;
    }

    /** Сдвинулся ли хоть один мой жетон войск. */
    private static boolean сдвинуты(PlayerState до, PlayerState после) {
        Map<Integer, String> где = new java.util.HashMap<>();
        for (UnitToken u : до.unitsOnField()) {
            где.put(u.uid, u.hexId);
        }
        for (UnitToken u : после.unitsOnField()) {
            String было = где.get(u.uid);
            if (было != null && !было.equals(u.hexId)) {
                return true;
            }
        }
        return false;
    }

    /** Войска стали ближе к целевому гексу намерений — «подвожу войска к Игроку N». */
    private static String подвод(GameState до, GameState после, int seat, Intents in, int лидер) {
        if (in == null || in.targetHex == null) {
            return null;
        }
        int чей = -1;
        for (PlayerState p : после.players) {
            if (p.seat == seat) {
                continue;
            }
            for (Token t : p.unitsOnField()) {
                if (in.targetHex.equals(t.hexId())) {
                    чей = p.seat;
                }
            }
            for (Token t : p.buildingsOnField()) {
                if (in.targetHex.equals(t.hexId())) {
                    чей = p.seat;
                }
            }
        }
        if (чей < 0) {
            return null;
        }
        int d0 = дальность(до, seat, in.targetHex);
        int d1 = дальность(после, seat, in.targetHex);
        return d1 < d0 ? "подвожу войска к " + (чей == лидер ? "лидеру — Игроку " + (чей + 1)
            : "Игроку " + (чей + 1)) : null;
    }

    private static int дальность(GameState s, int seat, String гекс) {
        int сумма = 0;
        for (UnitToken u : s.player(seat).unitsOnField()) {
            Integer d = kelium.engine.Movement.distance(s, u.hexId, java.util.Set.of(гекс));
            сумма += d == null ? 12 : Math.min(12, d);
        }
        return сумма;
    }

    /** Намерение удара: по чьему жетону пришёлся выбранный гекс. */
    public static String удар(GameState s, int seat, Choice o, int лидер) {
        if (!(o.payload() instanceof Map<?, ?> m)) {
            return null;
        }
        if (Boolean.TRUE.equals(m.get("neutral"))) {
            return "бью нейтральную постройку ради трофея";
        }
        Object hex = m.get("target");
        if (hex == null) {
            return null;
        }
        int чей = -1;
        for (PlayerState p : s.players) {
            if (p.seat == seat) {
                continue;
            }
            boolean есть = false;
            for (Token t : p.unitsOnField()) {
                есть |= hex.equals(t.hexId());
            }
            for (Token t : p.buildingsOnField()) {
                есть |= hex.equals(t.hexId());
            }
            if (есть && (чей < 0 || p.seat == лидер)) {
                чей = p.seat;
            }
        }
        return чей < 0 ? null : "бью " + кого(чей, лидер);
    }
}
