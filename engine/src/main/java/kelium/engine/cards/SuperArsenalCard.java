package kelium.engine.cards;

/**
 * СУПЕР-АРСЕНАЛ — супер-войско (kind: troop) или супер-способность
 * (kind: power) на вершине трека технологий. Движок читает поля из
 * {@link #data()} напрямую — здесь только описание карты.
 */
public interface SuperArsenalCard extends Card {

    @Override
    default Family family() {
        return Family.СУПЕР_АРСЕНАЛ;
    }
}
