package kelium.engine.cards;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.engine.Passability;
import kelium.engine.Scoring;
import kelium.engine.Storage;

/**
 * РЕАЛИЗАЦИЯ ДОГОВОРА КАРТЫ — недостающее звено между {@code Objective}/
 * {@code ArsenalCard} и настоящей партией.
 *
 * <p>ПОЧЕМУ ЭТОГО НЕ БЫЛО И ПОЧЕМУ ЭТО ВАЖНО. {@link CardContext} был объявлен
 * 15.08.2026 вместе с модулем карт, и javadoc обещал: «кто реализует —
 * {@code kelium.engine.EngineCardContext}». Реализации не было НИКОГДА. Значит
 * {@code Objective.progress()} и {@code needed()} — код, который физически
 * НЕЛЬЗЯ было вызвать в живой партии: ни бот, ни движок не имели способа
 * передать карте настоящее состояние игры. Каждая карта задания честно умела
 * сказать «чего не хватает», а спросить её было некому. Это и есть прямая
 * причина жалобы «боты не знают, как играть задания»: знание было написано и
 * заперто без двери.
 *
 * <p>Один объект на вызов, лёгкий: оборачивает {@link GameState} + место, ничего
 * не кэширует, создаётся и выбрасывается на каждый вопрос к карте.
 */
public final class EngineCardContext implements CardContext {

    private final GameState state;
    private final int seat;
    private final Agent agent;   // может быть null — тогда ask() берёт первый вариант

    public EngineCardContext(GameState state, int seat) {
        this(state, seat, null);
    }

    public EngineCardContext(GameState state, int seat, Agent agent) {
        this.state = state;
        this.seat = seat;
        this.agent = agent;
    }

    @Override public GameState state() {
        return state;
    }

    @Override public int seat() {
        return seat;
    }

    @Override public int roundsLeft() {
        int cap = 8;
        try {
            cap = kelium.dataio.Ctx.cfg(state).content.get("market").entries.size();
        } catch (RuntimeException e) {
            // сцена без данных рынка (собранный вручную тест) — печатные восемь
        }
        return Math.max(0, cap - state.round);
    }

    @Override public List<Token> myTokensOnField() {
        List<Token> out = new ArrayList<>();
        out.addAll(me().unitsOnField());
        out.addAll(me().buildingsOnField());
        return out;
    }

    @Override public List<Token> enemyTokensOnField() {
        List<Token> out = new ArrayList<>();
        for (PlayerState p : state.players) {
            if (p.seat == seat) {
                continue;
            }
            out.addAll(p.unitsOnField());
            out.addAll(p.buildingsOnField());
        }
        return out;
    }

    @Override public List<String> attackReach(UnitToken unit) {
        if (unit.hexId == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String nb : state.field.neighbors(unit.hexId)) {
            if (Passability.canShootAcross(state, unit, nb)) {
                out.add(nb);
            }
        }
        return out;
    }

    @Override public void gain(Resource r, int amount) {
        if (amount == 0) {
            return;
        }
        switch (r) {
            case KELIUM -> Storage.addKeliumCapped(state, me(), amount);
            case AMMO -> Storage.addAmmoCapped(state, me(), amount);
            case TROPHY -> Storage.addTrophyCapped(state, me(), amount);
            case COIN -> me().resources.add(Resource.COIN, amount);
        }
    }

    @Override public int pay(Resource r, int amount) {
        int have = me().resources.get(r);
        int paid = Math.min(have, amount);
        me().resources.pay(r, paid);
        return paid;
    }

    @Override public void heal(Token token, int n) {
        setDamage(token, Math.max(0, damageOf(token) - n));
    }

    @Override public void damage(Token token, int n) {
        setDamage(token, damageOf(token) + n);
    }

    private static int damageOf(Token t) {
        if (t instanceof kelium.core.UnitToken u) {
            return u.damage;
        }
        if (t instanceof kelium.core.BuildingToken b) {
            return b.damage;
        }
        return 0;
    }

    private static void setDamage(Token t, int v) {
        if (t instanceof kelium.core.UnitToken u) {
            u.damage = v;
        } else if (t instanceof kelium.core.BuildingToken b) {
            b.damage = v;
        }
    }

    @Override public boolean move(UnitToken unit, String hexId) {
        if (!state.field.neighbors(unit.hexId == null ? hexId : unit.hexId).contains(hexId)
                && unit.hexId != null) {
            return false;
        }
        unit.setHexId(hexId);
        return true;
    }

    @Override public void freeAction(String action) {
        // Разыгрывается той же машиной, что и обычные бесплатные действия карт
        // (Effects.freeAction) — здесь только точка входа для карт-объектов,
        // которые захотят выдавать свободное действие напрямую, а не через YAML.
        kelium.engine.Effects.apply("free_action", state, seat, Map.of("action", action));
    }

    // ВОПРОСЫ ПРО ПОЛЕ. Один обход на всех: прежде каждый такой перебор жил
    // отдельной строкой в реестре предикатов, и карта до него не доставала.

    @Override public java.util.List<kelium.core.Token> enemyTokensOn(String hexId) {
        java.util.List<kelium.core.Token> out = new java.util.ArrayList<>();
        for (kelium.core.PlayerState pl : state.players) {
            if (pl.seat == seat) {
                continue;
            }
            for (kelium.core.UnitToken u : pl.unitsOnField()) {
                if (hexId.equals(u.hexId)) {
                    out.add(u);
                }
            }
            for (kelium.core.BuildingToken b : pl.buildingsOnField()) {
                if (hexId.equals(b.hexId)) {
                    out.add(b);
                }
            }
        }
        return out;
    }

    @Override public java.util.List<kelium.core.Token> enemyBuildingsOn(String hexId) {
        java.util.List<kelium.core.Token> out = new java.util.ArrayList<>();
        for (kelium.core.PlayerState pl : state.players) {
            if (pl.seat == seat) {
                continue;
            }
            for (kelium.core.BuildingToken b : pl.buildingsOnField()) {
                if (hexId.equals(b.hexId)) {
                    out.add(b);
                }
            }
        }
        return out;
    }

    @Override public boolean adjacentToEnemy(String hexId) {
        for (String nb : state.field.neighbors(hexId)) {
            if (!enemyTokensOn(nb).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override public java.util.Set<String> myBuildingHexes() {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (kelium.core.BuildingToken b : state.player(seat).buildingsOnField()) {
            out.add(b.hexId);
        }
        return out;
    }

    @Override public java.util.Set<String> cuHexesOf(int who) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (kelium.core.BuildingToken b : state.player(who).buildingsOnField()) {
            if (b.type == kelium.core.BuildingType.COMMAND_CENTER) {
                out.add(b.hexId);
            }
        }
        return out;
    }

    @Override public java.util.Collection<String> neighbors(String hexId) {
        return state.field.neighbors(hexId);
    }

    @Override public java.util.Collection<String> allHexes() {
        return state.field.hexes.keySet();
    }

    @Override public boolean passable(String hexId) {
        return kelium.engine.Movement.passable(state, hexId);
    }

    @Override public Integer distance(String from, java.util.Collection<String> targets) {
        return kelium.engine.Movement.distance(state, from, targets);
    }

    @Override public int largestWallChain(boolean militaryOnly) {
        java.util.Set<kelium.core.BuildingType> mil = java.util.Set.of(
            kelium.core.BuildingType.BARRACKS, kelium.core.BuildingType.FACTORY,
            kelium.core.BuildingType.AIRBASE);
        java.util.List<kelium.core.BuildingToken> pool = new java.util.ArrayList<>();
        for (kelium.core.BuildingToken b : state.player(seat).buildingsOnField()) {
            if (!militaryOnly || mil.contains(b.type)) {
                pool.add(b);
            }
        }
        return kelium.engine.Chains.largestWallChain(state, pool);
    }

    @Override public boolean chooseNonAdjacent(java.util.List<String> pool, int need) {
        return kelium.engine.Chains.chooseNonAdjacent(state, pool, 0,
            new java.util.ArrayList<>(), need);
    }

    @Override public boolean hasSpawnTile(String hexId) {
        return state.field.get(hexId).hasSpawnTile();
    }

    @Override public boolean spawnTileIsStart(String hexId) {
        var h = state.field.get(hexId);
        return h.spawnTile != null && h.spawnTile.isStart;
    }

    @Override public void freeAction(String action, Map<String, Object> limits) {
        Map<String, Object> p = new java.util.HashMap<>(limits == null ? Map.of() : limits);
        p.put("action", action);
        kelium.engine.Effects.apply("free_action", state, seat, p);
    }

    // ОДНОРАЗОВЫЕ ЭФФЕКТЫ ВЕРХА. Каждый — правило игры, уже реализованное в
    // Effects; здесь только типизированный вход для карт, живущих в коде, чтобы
    // им не приходилось описывать свой верх строкой в каталоге. Второй
    // реализации нет ни у одного: правило остаётся в одном месте.

    @Override public boolean shield(java.util.List<String> kinds) {
        return !kelium.engine.Effects.apply("shield", state, seat,
            Map.of("types", kinds == null ? java.util.List.of() : kinds)).isEmpty();
    }

    @Override public boolean speedBoost() {
        return !kelium.engine.Effects.apply("speed_boost", state, seat, Map.of()).isEmpty();
    }

    @Override public boolean landing(int count) {
        return !kelium.engine.Effects.apply("landing", state, seat,
            Map.of("count", count)).isEmpty();
    }

    @Override public boolean energyOrModules() {
        return !kelium.engine.Effects.apply("energy_or_modules", state, seat, Map.of()).isEmpty();
    }

    @Override public boolean convert(Resource from, Resource to, int amount) {
        return !kelium.engine.Effects.apply("convert", state, seat,
            Map.of("from", from.code, "to", to.code, "amount", amount)).isEmpty();
    }

    @Override public boolean healHex() {
        return !kelium.engine.Effects.apply("heal_hex", state, seat, Map.of()).isEmpty();
    }

    @Override public void grantVp(int amount, String source) {
        if (amount == 0) {
            return;
        }
        state.player(seat).objectiveCardVp += amount;
    }

    @Override @SuppressWarnings("unchecked")
    public <T> T ask(String kind, List<T> options) {
        if (options.isEmpty()) {
            return null;
        }
        if (agent == null) {
            return options.get(0);
        }
        List<Choice> choices = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            choices.add(new Choice(kind, i, String.valueOf(options.get(i))));
        }
        Choice picked = agent.choose(state, choices, Map.of("kind", kind));
        if (picked == null || picked.payload() == null) {
            return null;
        }
        return options.get((Integer) picked.payload());
    }

    @Override public void log(String event, Map<String, Object> details) {
        // НАМЕРЕННО ПУСТО в этой реализации. У GameEngine есть свой канал
        // событий (emit), и когда карты подключат к реальным действиям игрока
        // (не только к признакам для бота), туда и нужно будет передавать —
        // отдельным конструктором с Consumer<Map<String,Object>>. Пока контекст
        // используется для ЧТЕНИЯ картой своего состояния (progress/needed),
        // а не для розыгрыша, эмиттер не нужен.
    }

    @Override public PlayerState me() {
        return state.player(seat);
    }
}
