package kelium.cards.containers;

import java.util.ArrayList;
import java.util.List;

import kelium.engine.cards.Card;
import kelium.engine.cards.CardRegistry;

/**
 * ВСЕ 32 КАРТЫ КОНТЕЙНЕРОВ действующего набора (5.0.0).
 *
 * <p>Лиц четырнадцать, карт тридцать две: сколько каких, напечатано на
 * самой карте числом в правом верхнем углу.
 */
public final class ContainerPack implements CardRegistry.CardPack {

    @Override
    public List<Card> cards() {
        List<Card> out = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            out.add(new КонтейнерыПоЭкспорту.СпрятаннаяНаличность(i));   // A — спрятанная наличность
        }
        for (int i = 1; i <= 3; i++) {
            out.add(new КонтейнерыПоЭкспорту.КассетаЗарядов(i));   // B — кассета зарядов
        }
        for (int i = 1; i <= 3; i++) {
            out.add(new КонтейнерыПоЭкспорту.СлужебныеДонесения(i));   // D — служебные донесения
        }
        for (int i = 1; i <= 3; i++) {
            out.add(new КонтейнерыПоЭкспорту.ПробаКристаллов(i));   // C — проба кристаллов
        }
        for (int i = 1; i <= 3; i++) {
            out.add(new КонтейнерыПоЭкспорту.ОплавленнаяОбшивка(i));   // E — оплавленная обшивка
        }
        for (int i = 1; i <= 3; i++) {
            out.add(new КонтейнерыПоЭкспорту.ОружейныйПодвес(i));   // F — оружейный подвес
        }
        for (int i = 1; i <= 3; i++) {
            out.add(new КонтейнерыПоЭкспорту.ШтурмовойПротокол(i));   // H — штурмовой протокол
        }
        for (int i = 1; i <= 2; i++) {
            out.add(new КонтейнерыПоЭкспорту.ЗондРазведчик(i));   // G — зонд-разведчик
        }
        for (int i = 1; i <= 2; i++) {
            out.add(new КонтейнерыПоЭкспорту.ТерминалСвязи(i));   // I — терминал связи
        }
        for (int i = 1; i <= 2; i++) {
            out.add(new КонтейнерыПоЭкспорту.КредитныйЧип(i));   // J — кредитный чип
        }
        for (int i = 1; i <= 2; i++) {
            out.add(new КонтейнерыПоЭкспорту.БатареяЗарядов(i));   // K — батарея зарядов
        }
        for (int i = 1; i <= 1; i++) {
            out.add(new КонтейнерыПоЭкспорту.ОпытныйОбразец(i));   // L — опытный образец
        }
        for (int i = 1; i <= 1; i++) {
            out.add(new КонтейнерыПоЭкспорту.ПрототипБтр(i));   // M — прототип БТР
        }
        for (int i = 1; i <= 1; i++) {
            out.add(new КонтейнерыПоЭкспорту.ВоеннаяКриокапсула(i));   // N — военная криокапсула
        }
        return out;
    }
}
