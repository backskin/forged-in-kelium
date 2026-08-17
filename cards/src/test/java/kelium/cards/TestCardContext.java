package kelium.cards;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kelium.core.BuildingToken;
import kelium.core.GameState;
import kelium.core.Resource;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.engine.Storage;
import kelium.engine.cards.CardContext;

/**
 * ПОДСТАВНОЙ КОНТЕКСТ КАРТЫ для тестов.
 *
 * <p>Реализует тот же договор, что и движок, но на собранной руками сцене. Всё,
 * что карта делает с партией, проходит через него, поэтому тест может проверить
 * не только «выполнилось ли условие», но и ЧТО ИМЕННО карта выдала: сколько
 * ресурсов, сколько очков, что записала в журнал.
 *
 * <p>ВАЖНО: выдача ресурсов идёт ЧЕРЕЗ {@link Storage}, а не прямым сложением.
 * Иначе тест проверял бы не игру, а самого себя: главный вопрос к наградам — что
 * будет, когда на складе НЕТ МЕСТА, и обойти вместимость в тесте значит закрыть
 * глаза ровно на то, что мы проверяем.
 */
public final class TestCardContext implements CardContext {

    private final GameState state;
    private final int seat;

    /** Что карта выдала: ресурс → сколько РЕАЛЬНО легло на склад. */
    public final Map<Resource, Integer> granted = new HashMap<>();
    /** Сколько победных очков выдано и из какого источника. */
    public int vp;
    public String vpSource;
    /** Что карта записала в журнал партии. */
    public final List<Map<String, Object>> log = new ArrayList<>();
    /** Что карта спрашивала у игрока. */
    public final List<String> asked = new ArrayList<>();

    public TestCardContext(GameState state, int seat) {
        this.state = state;
        this.seat = seat;
    }

    @Override public GameState state() {
        return state;
    }

    @Override public int seat() {
        return seat;
    }

    @Override public int roundsLeft() {
        return Math.max(0, 8 - state.round);
    }

    @Override public List<Token> myTokensOnField() {
        List<Token> out = new ArrayList<>();
        out.addAll(me().unitsOnField());
        out.addAll(me().buildingsOnField());
        return out;
    }

    @Override public List<Token> enemyTokensOnField() {
        List<Token> out = new ArrayList<>();
        for (var p : state.players) {
            if (p.seat == seat) {
                continue;
            }
            out.addAll(p.unitsOnField());
            out.addAll(p.buildingsOnField());
        }
        return out;
    }

    @Override public List<String> attackReach(UnitToken unit) {
        return unit.hexId == null ? List.of() : state.field.neighbors(unit.hexId);
    }

    @Override public void gain(Resource r, int amount) {
        int added = switch (r) {
            case KELIUM -> Storage.addKeliumCapped(state, me(), amount);
            case AMMO -> Storage.addAmmoCapped(state, me(), amount);
            case DEBRIS -> Storage.addDebrisCapped(state, me(), amount);
            // Монеты склад не занимают: они не кубики, а деньги.
            case COIN -> {
                me().resources.add(Resource.COIN, amount);
                yield amount;
            }
        };
        granted.merge(r, added, Integer::sum);
    }

    @Override public int pay(Resource r, int amount) {
        int have = me().resources.get(r);
        int paid = Math.min(have, amount);
        me().resources.pay(r, paid);
        granted.merge(r, -paid, Integer::sum);
        return paid;
    }

    @Override public void heal(Token token, int n) {
        if (token instanceof UnitToken u) {
            u.damage = Math.max(0, u.damage - n);
        } else if (token instanceof BuildingToken b) {
            b.damage = Math.max(0, b.damage - n);
        }
    }

    @Override public void damage(Token token, int n) {
        if (token instanceof UnitToken u) {
            u.damage += n;
        } else if (token instanceof BuildingToken b) {
            b.damage += n;
        }
    }

    @Override public boolean move(UnitToken unit, String hexId) {
        unit.hexId = hexId;
        return true;
    }

    @Override public void freeAction(String action) {
        log.add(Map.of("free_action", action));
    }

    // ОДНОРАЗОВЫЕ ЭФФЕКТЫ ВЕРХА И ВОПРОСЫ ПРО ПОЛЕ. Двойник их только ЗАПИСЫВАЕТ:
    // правила живут в движке, а здесь проверяется, что карта дёрнула нужный
    // эффект с нужными пределами.

    @Override public void freeAction(String action, Map<String, Object> limits) {
        Map<String, Object> row = new HashMap<>(limits == null ? Map.of() : limits);
        row.put("free_action", action);
        log.add(row);
    }

    @Override public boolean shield(List<String> kinds) {
        log.add(Map.of("shield", kinds));
        return true;
    }

    @Override public boolean speedBoost() {
        log.add(Map.of("speed_boost", true));
        return true;
    }

    @Override public boolean landing(int count) {
        log.add(Map.of("landing", count));
        return true;
    }

    @Override public boolean energyOrModules() {
        log.add(Map.of("energy_or_modules", true));
        return true;
    }

    @Override public boolean convert(Resource from, Resource to, int amount) {
        log.add(Map.of("convert", from.code + "->" + to.code, "amount", amount));
        return true;
    }

    @Override public boolean healHex() {
        log.add(Map.of("heal_hex", true));
        return true;
    }

    @Override public List<Token> enemyTokensOn(String hexId) {
        List<Token> out = new java.util.ArrayList<>();
        for (Token t : enemyTokensOnField()) {
            if (hexId != null && hexId.equals(хексОм(t))) {
                out.add(t);
            }
        }
        return out;
    }

    @Override public List<Token> enemyBuildingsOn(String hexId) {
        List<Token> out = new java.util.ArrayList<>();
        for (Token t : enemyTokensOn(hexId)) {
            if (t instanceof BuildingToken) {
                out.add(t);
            }
        }
        return out;
    }

    @Override public boolean adjacentToEnemy(String hexId) {
        for (String nb : neighbors(hexId)) {
            if (!enemyTokensOn(nb).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override public java.util.Set<String> myBuildingHexes() {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (BuildingToken b : me().buildingsOnField()) {
            out.add(b.hexId);
        }
        return out;
    }

    @Override public java.util.Set<String> cuHexesOf(int who) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (BuildingToken b : state().player(who).buildingsOnField()) {
            if (b.type == kelium.core.BuildingType.COMMAND_CENTER) {
                out.add(b.hexId);
            }
        }
        return out;
    }

    @Override public java.util.Collection<String> neighbors(String hexId) {
        return state().field.neighbors(hexId);
    }

    @Override public java.util.Collection<String> allHexes() {
        return state().field.hexes.keySet();
    }

    @Override public boolean passable(String hexId) {
        return kelium.engine.Movement.passable(state(), hexId);
    }

    @Override public Integer distance(String from, java.util.Collection<String> targets) {
        return kelium.engine.Movement.distance(state(), from, targets);
    }

    private static String хексОм(Token t) {
        if (t instanceof UnitToken u) {
            return u.hexId;
        }
        return t instanceof BuildingToken b ? b.hexId : null;
    }

    @Override public void grantVp(int amount, String source) {
        vp += amount;
        vpSource = source;
    }

    @Override public <T> T ask(String kind, List<T> options) {
        asked.add(kind);
        return options.isEmpty() ? null : options.get(0);
    }

    @Override public void log(String event, Map<String, Object> details) {
        Map<String, Object> row = new HashMap<>(details);
        row.put("event", event);
        log.add(row);
    }
}
