package kelium.cards.superobjectives;

import java.util.Map;

import kelium.engine.cards.SuperObjectiveCard;

/** КАРТА СУПЕР-ЗАДАНИЯ, ЖИВУЩАЯ ЦЕЛИКОМ В КОДЕ (заказ дизайнера 18.08.2026). */
public abstract class СупероружиеВКоде implements SuperObjectiveCard {

    private final String id;

    protected СупероружиеВКоде(String id) {
        this.id = id;
    }

    public abstract ЛицоСупероружия лицо();

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
