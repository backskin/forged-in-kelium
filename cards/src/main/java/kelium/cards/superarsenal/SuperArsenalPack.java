package kelium.cards.superarsenal;

import java.util.ArrayList;
import java.util.List;

import kelium.engine.cards.Card;
import kelium.engine.cards.CardRegistry;

/** Реестр всех 9 карт супер-арсенала. */
public final class SuperArsenalPack implements CardRegistry.CardPack {

    @Override
    public List<Card> cards() {
        List<Card> out = new ArrayList<>();
        out.add(new СуперАрсенал.ГвардияКель());
        out.add(new СуперАрсенал.ТяжёлыйТанкРаздор());
        out.add(new СуперАрсенал.ШтурмовикГроза());
        out.add(new СуперАрсенал.Цитадель());
        out.add(new СуперАрсенал.ШтабнаяДиректива());
        out.add(new СуперАрсенал.КелемиевыйРудник());
        out.add(new СуперАрсенал.ВоеннаяМашина());
        out.add(new СуперАрсенал.МандатСовета());
        out.add(new СуперАрсенал.ПараллельныеШтабы());
        return out;
    }
}
