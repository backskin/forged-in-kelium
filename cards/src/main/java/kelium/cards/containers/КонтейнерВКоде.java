package kelium.cards.containers;

import java.util.Map;

import kelium.engine.cards.ContainerCard;

/**
 * КАРТА КОНТЕЙНЕРА, ЖИВУЩАЯ ЦЕЛИКОМ В КОДЕ.
 *
 * <p>У контейнеров и раньше не было отдельной поломки (движок читает эффект
 * стороны прямо из данных, реестр эффектов проверен — ни одной мёртвой
 * ссылки). Перенос сюда — тот же принцип «код, а не YAML источник правды»,
 * применённый по заказу дизайнера 18.08.2026, а не починка бага.
 */
public abstract class КонтейнерВКоде implements ContainerCard {

    private final String id;

    protected КонтейнерВКоде(String id) {
        this.id = id;
    }

    public abstract ЛицоКонтейнера лицо();

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final String name() {
        return лицо().имя();
    }

    @Override
    public final String describe() {
        return лицо().описание();
    }

    @Override
    public final boolean describesItself() {
        return true;
    }

    @Override
    public final void bind(Map<String, Object> entry) {
        // намеренно пусто: источник данных — класс, а не запись в файле
    }

    @Override
    public final Map<String, Object> data() {
        return лицо().выгрузить(id);
    }
}
