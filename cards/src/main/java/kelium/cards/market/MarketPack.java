package kelium.cards.market;

import java.util.ArrayList;
import java.util.List;

import kelium.engine.cards.Card;
import kelium.engine.cards.CardRegistry;

/** Реестр всех 8 карт сделок на рынке. */
public final class MarketPack implements CardRegistry.CardPack {

    @Override
    public List<Card> cards() {
        List<Card> out = new ArrayList<>();
        out.add(new СделкиНаРынке.ВоенныйПодряд());
        out.add(new СделкиНаРынке.ТеневойОбоз());
        out.add(new СделкиНаРынке.ИнженернаяКонтора());
        out.add(new СделкиНаРынке.НаучнаяМиссия());
        out.add(new СделкиНаРынке.ОружейнаяЯрмарка());
        out.add(new СделкиНаРынке.ВоеннаяТревога());
        out.add(new СделкиНаРынке.ШтабКорпуса());
        out.add(new СделкиНаРынке.ГражданскийПодряд());
        return out;
    }
}
