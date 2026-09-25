package kelium.engine.ability;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.ability.Hint.Bottleneck;
import kelium.engine.ability.Hint.Horizon;

/**
 * АРСЕНАЛ 7.0.0 И СУПЕР-АРСЕНАЛ 3.0.0 — низы, которые дизайнер поправил на
 * печатных картах (экспорт «арсенал-2…34» от 13.09.2026, начальный и супер —
 * от 17.09.2026).
 *
 * <p>ПОЧЕМУ СВОИ НОМЕРА, А НЕ ПРАВКА ПРЕЖНИХ. Каждая способность ниже отличается
 * от старой ровно тем, что напечатано: ценой («−1 монета» у спец-действия, где
 * раньше было даром), родом войск («пехота» вместо «наземные войска»), сроком
 * («в фазу Возвращение» вместо «в конце боя») или валютой (боеприпасы вместо
 * монет). Старые способности стоят в наборах 5.0.0 и 6.0.0, на которых сыграны
 * замеры, и менять их задним числом нельзя.
 *
 * <p>ЧАСТЬ СПОСОБНОСТЕЙ — ПОМЕТКИ (как в 5.0 и 6.0): «в фазу Возвращение»,
 * «в действии Наука», «в Манёвре» срабатывают там, где это место движка, через
 * {@code Passives.hasPassive}.
 */
public final class Arsenal7Abilities {

    private Arsenal7Abilities() {
    }

    /** Цена печатных спец-действий с «−1 монета» в левой колонке карты. */
    private static final int МОНЕТА = 1;

    public static void install() {
        // спец-действие прежней карты, но с печатной ценой или с удалением карты
        Abilities.register(new ПлатноеСпец("spec_move_energy_cube_paid",
            "spec_move_energy_cube", МОНЕТА, false,
            new Hint(Bottleneck.ENERGY, 2.0, Horizon.NOW, null,
                "кубик энергии переезжает за монету", false)));
        Abilities.register(new ПлатноеСпец("spec_loot_enemy_building_hex_paid",
            "spec_loot_enemy_building_hex", МОНЕТА, false,
            new Hint(Bottleneck.AMMO, 1.0, Horizon.NOW, null,
                "за монету забрать боеприпас или келемий у соседа по гексу", false)));
        Abilities.register(new ПлатноеСпец("spec_kelium_rain_burn",
            "spec_kelium_rain", 0, true,
            new Hint(Bottleneck.KELIUM, 3.0, Horizon.NOW, null,
                "новый тайл зарождения, но карта уходит из игры", true)));
        Abilities.register(new SpecMoveEconomyAdjacent());
        Abilities.register(new TwoSpecWithSecurity());
        Abilities.register(new InfantryIgnoresBuildings());
        Abilities.register(new ScienceTrophyToAmmo());
        Abilities.register(new InfantryHp2ReturnsAtReturn());
        // супер-войска 3.0.0
        Abilities.register(new SuperAircraftCarryAdjacent());
        Abilities.register(new SuperTowerRing());
    }

    // ==================================================================
    //  ОБЩЕЕ
    // ==================================================================

    /**
     * СПОСОБНОСТЬ КАРТЫ СУПЕР-АРСЕНАЛА, которой держится жетон супер-войска, —
     * поле {@code passive} записи карты {@code superCardId}. Супер-войско
     * узнаётся по способности, а не по номеру карты: одна и та же способность
     * лежит на картах разных наборов.
     */
    public static String superPassive(GameState state, UnitToken u) {
        if (u == null || !u.superUnit || u.superCardId == null) {
            return null;
        }
        try {
            var card = kelium.dataio.Ctx.cards(state, "super_arsenal").find(u.superCardId);
            return card == null || card.get("passive") == null
                ? null : String.valueOf(card.get("passive"));
        } catch (RuntimeException нетНабора) {
            return null;
        }
    }

    /** Своё супер-войско с этой способностью на поле. */
    private static List<UnitToken> своиСупер(GameState state, int seat, String passive) {
        List<UnitToken> out = new ArrayList<>();
        for (UnitToken u : state.player(seat).unitsOnField()) {
            if (passive.equals(superPassive(state, u)) && u.hexId != null) {
                out.add(u);
            }
        }
        return out;
    }

    private static UnitToken своёСупер(GameState state, int seat, int uid, String passive) {
        for (UnitToken u : своиСупер(state, seat, passive)) {
            if (u.uid == uid) {
                return u;
            }
        }
        return null;
    }

    /** Убрать из игры установленную карту арсенала, несущую эту способность. */
    static void снятьКарту(GameState state, int seat, String passive) {
        PlayerState me = state.player(seat);
        var lib = kelium.dataio.Ctx.cards(state, "arsenal");
        for (String cid : new ArrayList<>(me.arsenalInstalled)) {
            var card = lib.find(cid);
            if (card != null && card.get("bottom") instanceof Map<?, ?> bm
                    && passive.equals(String.valueOf(bm.get("passive")))) {
                me.arsenalInstalled.remove(cid);
                return;
            }
        }
    }

    /**
     * СПЕЦ-ДЕЙСТВИЕ ПРЕЖНЕЙ КАРТЫ С ПЕЧАТНОЙ ЦЕНОЙ ИЛИ С УДАЛЕНИЕМ КАРТЫ.
     *
     * <p>Три низа 7.0.0 делают ровно то же, что прежние, и отличаются только
     * тем, что напечатано рядом со знаком спец-действия: «−1 монета»
     * («Диспетчерская», начальная «Мародёрка») или «Затем удали эту карту»
     * («Келемиевый дождь»). Исполнение отдаётся прежней способности, чтобы
     * правило жило в одном месте; монета списывается только при успехе.
     */
    private static final class ПлатноеСпец implements Ability, OptionSource {

        private final String id;
        private final String основа;
        private final int монет;
        private final boolean удалитьКарту;
        private final Hint hint;

        ПлатноеСпец(String id, String основа, int монет, boolean удалитьКарту, Hint hint) {
            this.id = id;
            this.основа = основа;
            this.монет = монет;
            this.удалитьКарту = удалитьКарту;
            this.hint = hint;
        }

        private OptionSource база() {
            return Abilities.byId(основа) instanceof OptionSource src ? src : null;
        }

        @Override public String id() {
            return id;
        }

        @Override public Trigger trigger() {
            return Trigger.SPEC;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            OptionSource src = база();
            if (slot != Slot.SPEC || src == null
                    || !state.player(seat).resources.canPay(Resource.COIN, монет)) {
                return List.of();
            }
            List<Choice> out = new ArrayList<>();
            String цена = монет > 0 ? " (−" + монет + " монета)" : "";
            String хвост = удалитьКарту ? ", затем карта уходит из игры" : "";
            for (Choice c : src.options(state, seat, slot)) {
                out.add(new Choice("ability:" + id, c.payload(), c.label() + цена + хвост));
            }
            return out;
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen, Agent agent) {
            OptionSource src = база();
            PlayerState me = state.player(seat);
            if (src == null || !me.resources.canPay(Resource.COIN, монет)) {
                return false;
            }
            Choice какОснова = new Choice("ability:" + основа,
                chosen == null ? null : chosen.payload(),
                chosen == null ? "" : chosen.label());
            if (!src.perform(state, seat, какОснова, agent)) {
                return false;
            }
            if (монет > 0) {
                me.resources.pay(Resource.COIN, монет);
            }
            if (удалитьКарту) {
                снятьКарту(state, seat, id);
            }
            return true;
        }

        @Override public Hint hint() {
            return hint;
        }
    }

    // ==================================================================
    //  СПЕЦ-ДЕЙСТВИЯ
    // ==================================================================

    /**
     * «ГЕОЛОГОРАЗВЕДКА»: СПЕЦ, −1 монета — «перемести по полю 1 свой добытчик
     * или энергостанцию на любой соседний гекс».
     *
     * <p>Прежний низ («на любой гекс, где доступна стройка», даром и только
     * добытчик) переписан дизайнером: дальше соседнего гекса здание не
     * уезжает, зато переезжает и энергостанция, и за это платят монету.
     *
     * <p>Куда можно: гекс проходимый (не запретный и без тайла зарождения), со
     * свободной наземной ячейкой и без чужих войск — то же ограничение, что у
     * любой постановки жетона на поле. Ячейку выбирает игрок: у добытчика от неё
     * зависит, к какой жиле он примыкает.
     */
    private static final class SpecMoveEconomyAdjacent implements Ability, OptionSource {

        @Override public String id() {
            return "spec_move_economy_building_adjacent";
        }

        @Override public Trigger trigger() {
            return Trigger.SPEC;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        private static boolean чужиеВойска(GameState state, String hexId, int seat) {
            for (PlayerState o : state.players) {
                if (o.seat == seat) {
                    continue;
                }
                for (UnitToken u : o.unitsOnField()) {
                    if (hexId.equals(u.hexId)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            if (slot != Slot.SPEC || !state.player(seat).resources.canPay(Resource.COIN, МОНЕТА)) {
                return List.of();
            }
            List<Choice> out = new ArrayList<>();
            for (BuildingToken b : state.player(seat).buildingsOnField()) {
                if (b.type != BuildingType.MINER && b.type != BuildingType.POWER_PLANT) {
                    continue;
                }
                for (String nb : state.field.neighborsView(b.hexId)) {
                    Hex h = state.field.get(nb);
                    if (h == null || !kelium.engine.Movement.passable(state, nb)
                            || чужиеВойска(state, nb, seat)) {
                        continue;
                    }
                    for (int side : h.freeSideIndices()) {
                        out.add(new Choice("ability:" + id(), new Object[]{b.uid, nb, side},
                            "СПЕЦ (−1 монета): " + b.type.code + " @" + b.hexId
                                + " -> " + nb + ", ячейка " + side));
                    }
                }
            }
            return out;
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen, Agent agent) {
            if (!(chosen != null && chosen.payload() instanceof Object[] pick) || pick.length != 3) {
                return false;
            }
            int uid = (Integer) pick[0];
            String куда = String.valueOf(pick[1]);
            int side = (Integer) pick[2];
            PlayerState me = state.player(seat);
            if (!me.resources.canPay(Resource.COIN, МОНЕТА)) {
                return false;
            }
            for (BuildingToken b : me.buildingsOnField()) {
                if (b.uid != uid) {
                    continue;
                }
                Hex было = state.field.get(b.hexId);
                Hex стало = state.field.get(куда);
                if (было == null || стало == null
                        || !state.field.neighborsView(b.hexId).contains(куда)
                        || стало.sideOwner[side] != null) {
                    return false;
                }
                for (int i = 0; i < 6; i++) {
                    if (было.sideOwner[i] != null && было.sideOwner[i] == b.uid) {
                        было.sideOwner[i] = null;
                    }
                }
                b.hexId = куда;
                стало.sideOwner[side] = b.uid;
                me.resources.pay(Resource.COIN, МОНЕТА);
                return true;
            }
            return false;
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.KELIUM, 1.5, Horizon.REST_OF_GAME, null,
                "добытчик или энергостанция переезжает на соседний гекс", false);
        }
    }

    // ==================================================================
    //  ПРАВКИ ЗНАЧЕНИЙ И ПОМЕТКИ
    // ==================================================================

    /**
     * «ОПЕРАТИВНЫЙ ОТДЕЛ» 7.0.0: «У тебя два спец. действия в ход, ЕСЛИ ты
     * сыграл приказ БЕЗОПАСНОСТЬ». Условие на печатной карте обратное прежнему
     * («если НЕ сыграл», {@code two_spec_without_security}): второе
     * спец-действие теперь идёт к карте-джокеру, а не против неё.
     *
     * <p>Карта «Безопасность» в колоде приказов 4.0.0 называется «Затаиться»
     * (решение 23.09.2026), номер у неё прежний — {@code security_*}.
     */
    private static final class TwoSpecWithSecurity implements Ability {

        @Override public String id() {
            return "two_spec_with_security";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.ORDER_SPEC_COUNT);
        }

        @Override public void modify(RuleQuery q) {
            for (String cid : q.state().player(q.seat()).orderPlayed) {
                if (cid != null && cid.toLowerCase(java.util.Locale.ROOT).contains("security")) {
                    q.atLeast(2);
                    return;
                }
            }
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.ACTIONS, 1.5, Horizon.THIS_ROUND,
                (s, seat) -> {
                    for (String cid : s.player(seat).orderHand) {
                        if (cid != null && cid.contains("security")) {
                            return true;
                        }
                    }
                    return false;
                },
                "второе спец-действие в ход с картой Безопасность", false);
        }
    }

    /**
     * «РЕАКТИВНЫЕ РАНЦЫ»: «В действие Маневр твоя пехота игнорирует здания как
     * препятствие». Прежний низ снимал стенки для всех наземных войск; на
     * печатной карте — только пехота. Пометка, работает в
     * {@code Actions.MovementAction.canEnterHex}.
     */
    private static final class InfantryIgnoresBuildings implements Ability {

        @Override public String id() {
            return "infantry_ignores_buildings_in_maneuver";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.REACH, 1.0, Horizon.REST_OF_GAME, null,
                "стенки не держат пехоту", false);
        }
    }

    /**
     * «КОНВЕРСИЯ СНАРЯДОВ»: «В действие Наука можешь обменивать трофеи на
     * боеприпасы по курсу −1 / −2 трофея за +1 / +3 боеприпаса». Пометка,
     * обмен предлагает {@code Actions.ScienceAction.maybeExchange}.
     */
    private static final class ScienceTrophyToAmmo implements Ability {

        @Override public String id() {
            return "science_trophy_to_ammo";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.AMMO, 1.5, Horizon.REST_OF_GAME, null,
                "трофеи обращаются в боеприпасы", false);
        }
    }

    /**
     * «ШТУРМОВЫЕ ЩИТЫ»: «Твоя пехота имеет 2 прочности; в фазу Возвращение
     * верни с поля в запас всю свою пехоту, на которой есть урон».
     *
     * <p>Прочность — та же прибавка, что у «Ударной пехоты» 5.0 (пехоте с
     * печатной прочностью 1 — вторая единица). Отличается срок возврата: раненые
     * уходят не сразу после боя, а в фазу Возвращение ({@code
     * GameEngine.returnStep}), то есть успевают отыграть остаток раунда.
     */
    private static final class InfantryHp2ReturnsAtReturn implements Ability {

        @Override public String id() {
            return "infantry_hp2_returns_at_return";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.TOKEN_HP);
        }

        @Override public void modify(RuleQuery q) {
            if (q.subject() instanceof UnitToken u && u.type == UnitType.INFANTRY
                    && q.current() <= 1) {
                q.add(1).by(id());
            }
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.DEFENCE, 1.2, Horizon.REST_OF_GAME, null,
                "пехота переживает удар и доигрывает раунд", false);
        }
    }

    // ==================================================================
    //  СУПЕР-ВОЙСКА 3.0.0
    // ==================================================================

    /**
     * «СУПЕР-АВИАЦИЯ»: СПЕЦ (цены не напечатано) — «перемести супер-авиацию и
     * любые жетоны с её гекса на соседний».
     *
     * <p>Прежняя «Гроза» летела на всю свою скорость и брала только свои
     * наземные войска. Печатная карта: ровно на СОСЕДНИЙ гекс, и берёт ЛЮБЫЕ
     * жетоны — значит и чужие войска тоже: супер-авиация может унести с гекса
     * противника. Какие жетоны взять, выбирает владелец по одному. Каждый
     * переносимый жетон должен иметь право встать на новый гекс по обычным
     * правилам входа ({@code Movement.canEnter} от лица его хозяина), иначе он
     * остаётся на месте.
     *
     * <p>ЗДАНИЯ НЕ ПЕРЕНОСЯТСЯ: здание приколочено к ячейке гекса своей стенкой,
     * и «перенести его соседний гекс» — это уже другая механика (выбор ячейки,
     * зона стройки, энергия). Слово «жетоны» на карте здесь прочитано как
     * «жетоны войск» — вопрос дизайнеру в отчёте.
     */
    private static final class SuperAircraftCarryAdjacent implements Ability, OptionSource {

        @Override public String id() {
            return "super_aircraft_carry_adjacent";
        }

        @Override public Trigger trigger() {
            return Trigger.SPEC;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            if (slot != Slot.SPEC) {
                return List.of();
            }
            List<Choice> out = new ArrayList<>();
            for (UnitToken u : своиСупер(state, seat, id())) {
                for (String nb : state.field.neighborsView(u.hexId)) {
                    if (kelium.engine.Movement.canEnter(state, u, nb, seat)) {
                        out.add(new Choice("ability:" + id(), new Object[]{u.uid, nb},
                            "СПЕЦ: супер-авиация с жетонами своего гекса на " + nb));
                    }
                }
            }
            return out;
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen, Agent agent) {
            if (!(chosen != null && chosen.payload() instanceof Object[] pl) || pl.length != 2
                    || !(pl[0] instanceof Integer uid) || !(pl[1] instanceof String dest)) {
                return false;
            }
            UnitToken u = своёСупер(state, seat, uid, id());
            if (u == null || !state.field.neighborsView(u.hexId).contains(dest)
                    || !kelium.engine.Movement.canEnter(state, u, dest, seat)) {
                return false;
            }
            String origin = u.hexId;
            u.hexId = dest;
            java.util.Set<Integer> отказано = new java.util.HashSet<>();
            while (true) {
                List<Choice> opts = new ArrayList<>();
                for (PlayerState owner : state.players) {
                    for (UnitToken g : owner.unitsOnField()) {
                        if (g.uid == u.uid || !origin.equals(g.hexId)
                                || отказано.contains(g.uid)) {
                            continue;
                        }
                        if (!kelium.engine.Movement.canEnter(state, g, dest, owner.seat)) {
                            continue;
                        }
                        opts.add(new Choice("carry_token", g,
                            "унести " + g.type.code + " игрока " + owner.seat + " на " + dest));
                    }
                }
                if (opts.isEmpty()) {
                    break;
                }
                opts.add(new Choice("pass", null, "больше никого не брать"));
                Choice pick = agent == null ? opts.get(opts.size() - 1)
                    : agent.choose(state, opts, Map.of("kind", "carry_token"));
                if (pick == null || !(pick.payload() instanceof UnitToken g)) {
                    break;
                }
                g.hexId = dest;
                отказано.add(g.uid);
            }
            return true;
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.REACH, 1.5, Horizon.THIS_ROUND, null,
                "перенос отряда (или чужих войск) на соседний гекс", false);
        }
    }

    /**
     * «СУПЕР-ВЫШКА»: СПЕЦ, −1 боеприпас — «нанеси супер-вышкой по 1 урону на
     * каждый соседний гекс».
     *
     * <p>Прежняя «Цитадель» платила по боеприпасу за каждый гекс залпа. На
     * печатной карте цена одна — боеприпас, — а урон идёт на КАЖДЫЙ соседний
     * гекс. По одному урону на гекс: цель на гексе выбирает владелец вышки, из
     * ЧУЖИХ жетонов (свои под свой же залп не подставляются — вопрос дизайнеру
     * в отчёте). Гекс без чужих жетонов пропускается. Урон — обычный удар боя
     * ({@code CombatResolver.hit}): щиты, уничтожение, трофеи — по правилам боя.
     */
    private static final class SuperTowerRing implements Ability, OptionSource {

        @Override public String id() {
            return "super_tower_ring";
        }

        @Override public Trigger trigger() {
            return Trigger.SPEC;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        private static List<Token> чужиеНа(GameState state, String hexId, int seat) {
            List<Token> out = new ArrayList<>();
            for (PlayerState pl : state.players) {
                if (pl.seat == seat) {
                    continue;
                }
                for (UnitToken u : pl.unitsOnField()) {
                    if (hexId.equals(u.hexId)) {
                        out.add(u);
                    }
                }
                for (BuildingToken b : pl.buildingsOnField()) {
                    if (hexId.equals(b.hexId)) {
                        out.add(b);
                    }
                }
            }
            return out;
        }

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            if (slot != Slot.SPEC || !state.player(seat).resources.canPay(Resource.AMMO, 1)) {
                return List.of();
            }
            List<Choice> out = new ArrayList<>();
            for (UnitToken u : своиСупер(state, seat, id())) {
                boolean есть = false;
                for (String nb : state.field.neighborsView(u.hexId)) {
                    if (!чужиеНа(state, nb, seat).isEmpty()) {
                        есть = true;
                        break;
                    }
                }
                if (есть) {
                    out.add(new Choice("ability:" + id(), u.uid,
                        "СПЕЦ (−1 БПР): супер-вышка бьёт по 1 урону на каждый соседний гекс"));
                }
            }
            return out;
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen, Agent agent) {
            if (!(chosen != null && chosen.payload() instanceof Integer uid)) {
                return false;
            }
            PlayerState me = state.player(seat);
            UnitToken u = своёСупер(state, seat, uid, id());
            if (u == null || !me.resources.canPay(Resource.AMMO, 1)) {
                return false;
            }
            me.resources.pay(Resource.AMMO, 1);
            int ударов = 0;
            for (String nb : new ArrayList<>(state.field.neighborsView(u.hexId))) {
                List<Token> цели = чужиеНа(state, nb, seat);
                if (цели.isEmpty()) {
                    continue;
                }
                Token жертва = цели.get(0);
                if (цели.size() > 1 && agent != null) {
                    List<Choice> opts = new ArrayList<>();
                    for (Token t : цели) {
                        String label = t instanceof UnitToken ut ? ut.type.code
                            : ((BuildingToken) t).type.code;
                        opts.add(new Choice("ring_hit", t,
                            "урон по " + nb + ": " + label + " игрока " + t.owner()));
                    }
                    Choice pick = agent.choose(state, opts, Map.of("kind", "ring_hit"));
                    if (pick != null && pick.payload() instanceof Token t) {
                        жертва = t;
                    }
                }
                ((kelium.engine.CombatResolver) state.combat).hit(жертва, seat);
                ударов++;
            }
            return ударов > 0;
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.DEFENCE, 3.0, Horizon.REST_OF_GAME, null,
                "удар по всем соседним гексам за один боеприпас", false);
        }
    }
}
