package kelium.engine.cards;

/**
 * СУПЕР ЗАДАНИЕ — вскрытие сразу всех четырёх ячеек, жетон супероружия,
 * счётчик запуска. Движок ({@code kelium.engine.SuperWeapon}) читает ячейки и
 * прочие поля из {@link #data()} напрямую — здесь только описание карты.
 */
public interface SuperObjectiveCard extends Card {

    @Override
    default Family family() {
        return Family.СУПЕР_ЗАДАНИЕ;
    }
}
