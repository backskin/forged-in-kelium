package kelium.core;

import java.util.EnumMap;
import java.util.Map;

/**
 * Кошелёк из четырёх расходуемых ресурсов (энергия сюда не входит по замыслу —
 * см. {@link Resource}). Изменяемый: движок добавляет и списывает ресурсы по ходу
 * партии.
 */
public final class Resources {

    private final Map<Resource, Integer> bag = new EnumMap<>(Resource.class);

    /** Создать пустой кошелёк (все ресурсы = 0). */
    public Resources() {
        for (Resource r : Resource.values()) {
            bag.put(r, 0);
        }
    }

    /** Создать кошелёк с заданными начальными значениями. */
    public Resources(int coin, int kelium, int ammo, int trophy) {
        this();
        bag.put(Resource.COIN, coin);
        bag.put(Resource.KELIUM, kelium);
        bag.put(Resource.AMMO, ammo);
        bag.put(Resource.TROPHY, trophy);
    }

    /** Текущее количество ресурса {@code r}. */
    public int get(Resource r) {
        return bag.get(r);
    }

    /** Добавить {@code n} единиц ресурса {@code r} (n может быть отрицательным). */
    public void add(Resource r, int n) {
        bag.put(r, bag.get(r) + n);
    }

    /** Хватает ли {@code n} единиц ресурса {@code r} для оплаты. */
    public boolean canPay(Resource r, int n) {
        return bag.get(r) >= n;
    }

    /**
     * Списать {@code n} единиц ресурса {@code r}.
     * @throws IllegalStateException если ресурса не хватает
     */
    public void pay(Resource r, int n) {
        int cur = bag.get(r);
        if (cur < n) {
            throw new IllegalStateException(
                "нельзя оплатить " + n + " " + r + ", есть " + cur);
        }
        bag.put(r, cur - n);
    }

    // Удобные геттеры, зеркалящие Python-версию (res.coin и т.п.).
    public int coin()   { return bag.get(Resource.COIN); }
    public int kelium() { return bag.get(Resource.KELIUM); }
    public int ammo()   { return bag.get(Resource.AMMO); }
    public int trophy() { return bag.get(Resource.TROPHY); }

    public void setKelium(int v) { bag.put(Resource.KELIUM, v); }
    public void setAmmo(int v)   { bag.put(Resource.AMMO, v); }

    /** Копия кошелька (для снимков состояния). */
    public Resources copy() {
        return new Resources(coin(), kelium(), ammo(), trophy());
    }

    @Override
    public String toString() {
        return "Res(coin=" + coin() + ", kel=" + kelium()
             + ", ammo=" + ammo() + ", trophy=" + trophy() + ")";
    }
}
