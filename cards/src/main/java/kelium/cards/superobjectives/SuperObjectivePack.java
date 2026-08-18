package kelium.cards.superobjectives;

import java.util.ArrayList;
import java.util.List;

import kelium.engine.cards.Card;
import kelium.engine.cards.CardRegistry;

/** Реестр всех 8 карт супер-заданий. */
public final class SuperObjectivePack implements CardRegistry.CardPack {

    @Override
    public List<Card> cards() {
        List<Card> out = new ArrayList<>();
        out.add(new СуперЗадания.Призма());
        out.add(new СуперЗадания.Литейня());
        out.add(new СуперЗадания.Колосс());
        out.add(new СуперЗадания.Завеса());
        out.add(new СуперЗадания.Крот());
        out.add(new СуперЗадания.Ядро());
        out.add(new СуперЗадания.Рой());
        out.add(new СуперЗадания.Зенит());
        return out;
    }
}
