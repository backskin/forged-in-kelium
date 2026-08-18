package kelium.engine.cards;

/**
 * КАРТА СДЕЛКИ НА РЫНКЕ — два предложения (left/right), одна карта активна
 * за раунд. Движок читает предложения из {@link #data()} напрямую, тем же
 * способом, что и у контейнера, — применение уже общая услуга движка.
 */
public interface MarketCard extends Card {

    @Override
    default Family family() {
        return Family.РЫНОК;
    }
}
