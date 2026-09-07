package kelium.cards.market;

import java.util.ArrayList;
import java.util.List;

import kelium.engine.cards.Card;
import kelium.engine.cards.CardRegistry;

/** Реестр карт сделок на рынке: набор 1.2.0 и набор 2.0.0. */
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
        // НАБОР 2.0.0 (заказ 02.09.2026) — свои номера m2_*, чтобы правка не
        // переписывала задним числом набор 1.2.0, на котором сыграны замеры.
        out.add(new СделкиНаРынке2.ВоенныйПодряд());
        out.add(new СделкиНаРынке2.ТеневойОбоз());
        out.add(new СделкиНаРынке2.ЭнергетическаяКонтора());
        out.add(new СделкиНаРынке2.НаучнаяМиссия());
        out.add(new СделкиНаРынке2.ОружейнаяЯрмарка());
        out.add(new СделкиНаРынке2.ВоеннаяТревога());
        out.add(new СделкиНаРынке2.ШтабКорпуса());
        out.add(new СделкиНаРынке2.ГражданскийПодряд());
        return out;
    }
}
