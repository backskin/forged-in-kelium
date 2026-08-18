package kelium.cards.market;

import java.util.Map;

import kelium.engine.cards.MarketCard;

/** КАРТА СДЕЛКИ НА РЫНКЕ, ЖИВУЩАЯ ЦЕЛИКОМ В КОДЕ (заказ дизайнера 18.08.2026). */
public abstract class СделкаВКоде implements MarketCard {

    private final String id;

    protected СделкаВКоде(String id) {
        this.id = id;
    }

    public abstract ЛицоСделки лицо();

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
