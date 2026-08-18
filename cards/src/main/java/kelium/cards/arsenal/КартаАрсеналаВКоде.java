package kelium.cards.arsenal;

import java.util.Map;

/**
 * КАРТА АРСЕНАЛА, ЖИВУЩАЯ ЦЕЛИКОМ В КОДЕ.
 *
 * <p>Наследует {@link ArsenalCardBase} и получает даром всё, что уже правильно
 * работает от данных: оценку установки/утиля по самоописанию способности
 * ({@code installValue}/{@code burnValue}), сжигание верха через реестр
 * эффектов ({@code CardTop.burn}). Единственное, что здесь меняется, — ОТКУДА
 * берётся запись: не из YAML, а из {@link #лицо()} самого класса.
 *
 * <p>ПОЧЕМУ ЭТОГО ДОСТАТОЧНО, А НЕ НУЖНО ПЕРЕПИСЫВАТЬ burn()/usefulness()
 * ПО-СВОЕМУ У КАЖДОЙ КАРТЫ. У заданий предикат был МЁРТВЫМ строковым
 * диспетчером: незарегистрированный ключ молча означал «не выполнено», и три
 * карты из-за этого не работали годами. У арсенала другая история: реестр
 * эффектов и реестр способностей — уже код, уже проверены (ни одного мёртвого
 * id), и разбор награды/утиля по данным — это законная общая услуга движка,
 * тот же принцип, каким объектные карты заданий пользуются
 * {@code CardRewards.grantFromData} или {@code ctx.freeAction(...)}. Разница
 * между картами арсенала — это данные (какой эффект, какая способность), а не
 * поведение, и плодить 42 одинаковых by-hand реализации burn() значило бы
 * имитировать объектное устройство, а не строить его.
 */
public abstract class КартаАрсеналаВКоде extends ArsenalCardBase {

    protected КартаАрсеналаВКоде(String id) {
        super(id);
    }

    /** ВСЁ, ЧТО НА КАРТЕ НАПЕЧАТАНО. Объявляется каждой картой в её классе. */
    public abstract ЛицоАрсенала лицо();

    @Override
    public final boolean describesItself() {
        return true;
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
    public final void bind(Map<String, Object> entry) {
        // намеренно пусто: источник данных — класс, а не запись в файле
    }

    @Override
    public final Map<String, Object> data() {
        return лицо().выгрузить(id());
    }

    @Override
    public String passiveId() {
        return лицо().пассивка();
    }
}
