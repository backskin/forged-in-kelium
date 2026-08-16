package kelium.cards.arsenal;

import kelium.core.BuildingToken;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.engine.cards.CardContext;

/**
 * Карты арсенала, у которых оценка «стоит ли она сейчас» СВОЯ.
 *
 * <p>Общая оценка (см. {@link ArsenalCardBase}) считает пользу по самоописанию
 * способности: какое узкое место она снимает и насколько сильно. Для большинства
 * карт этого достаточно. Но у трёх карт польза зависит не от МОИХ запасов, а от
 * того, что делают СОПЕРНИКИ, — и общая формула такую пользу видеть не может.
 *
 * <p>Это и есть та часть, ради которой карта должна быть объектом: правило
 * «сколько эта карта стоит» иногда невыразимо числом в таблице.
 */
final class SpecialArsenalCards {

    private SpecialArsenalCards() {
    }
}

/**
 * b13 «ТРОФЕЙНЫЙ СКЛАД» — задержать один чужой жетон у себя ещё на раунд.
 *
 * <p>Ценность карты не в очках, а в ОТКАЗЕ: личный запас каждого игрока — ровно
 * четыре жетона рода, и пока чужой жетон лежит на карте, владелец не может
 * выставить его заново. Значит карта тем дороже, чем БОЛЬШЕ Я ВОЮЮ: без трофеев
 * она пустая, с потоком трофеев — постоянный налог на чужую армию.
 *
 * <p>Общая формула этого не видит: она смотрит на мои запасы, а надо смотреть на
 * поток уничтожений.
 */
final class TrophyStorageCard extends ArsenalCardBase {

    TrophyStorageCard() {
        super("b13");
    }

    @Override
    protected double installValue(CardContext ctx) {
        // Трофеи на столе прямо сейчас — прямой признак, что карте будет что
        // держать. Нет трофеев и нет войск для их добычи — карта мертва.
        int trophies = ctx.me().trophySpace.size();
        int myUnits = ctx.me().unitsOnField().size();
        if (trophies == 0 && myUnits == 0) {
            return 0.05;
        }
        double flow = clamp(0.25 * trophies + 0.12 * myUnits);
        // Ценность держится весь остаток партии: каждый раунд — новый отказ.
        double left = Math.min(4, Math.max(0, ctx.roundsLeft())) / 4.0;
        return clamp(0.15 + 0.85 * flow * left);
    }
}

/**
 * bs5 «ПРЕМИЯ ЗА ГОЛОВУ» — монета за каждое уничтожение.
 *
 * <p>Карта окупается ровно настолько, насколько игрок собирается драться, и
 * бесполезна мирному. Общая формула оценила бы её по нехватке монет — то есть
 * ровно наоборот: у мирного игрока монет обычно меньше, и он бы её и поставил.
 */
final class KillBountyCard extends ArsenalCardBase {

    KillBountyCard() {
        super("bs5");
    }

    @Override
    protected double installValue(CardContext ctx) {
        // Сколько чужих жетонов я вообще достаю: соседи моих войск.
        int reachable = 0;
        for (UnitToken u : ctx.me().unitsOnField()) {
            if (u.hexId == null) {
                continue;
            }
            for (String nb : ctx.state().field.neighbors(u.hexId)) {
                for (PlayerState other : ctx.state().players) {
                    if (other.seat == ctx.seat()) {
                        continue;
                    }
                    for (UnitToken e : other.unitsOnField()) {
                        if (nb.equals(e.hexId)) {
                            reachable++;
                        }
                    }
                    for (BuildingToken b : other.buildingsOnField()) {
                        if (nb.equals(b.hexId)) {
                            reachable++;
                        }
                    }
                }
            }
        }
        double left = Math.min(4, Math.max(0, ctx.roundsLeft())) / 4.0;
        return clamp(0.1 + 0.9 * clamp(reachable / 4.0) * left);
    }
}

/**
 * bs8 «ОСАДНЫЕ ИНЖЕНЕРЫ» — строить на соседнем гексе через стену.
 *
 * <p>Карта нужна тому, кого стены ЗАПИРАЮТ. Если вокруг просторно, она не даёт
 * ничего; если база зажата чужими и нейтральными постройками — открывает
 * застройку заново. Оценка считает, сколько сторон вокруг моих зданий закрыто.
 */
final class SiegeEngineerCard extends ArsenalCardBase {

    SiegeEngineerCard() {
        super("bs8");
    }

    @Override
    protected double installValue(CardContext ctx) {
        int blocked = 0;
        int looked = 0;
        for (BuildingToken b : ctx.me().buildingsOnField()) {
            if (b.hexId == null) {
                continue;
            }
            for (String nb : ctx.state().field.neighbors(b.hexId)) {
                looked++;
                var hex = ctx.state().field.get(nb);
                if (hex != null && hex.hasNeutral()) {
                    blocked++;
                }
            }
        }
        if (looked == 0) {
            return 0.1;
        }
        double tightness = blocked / (double) looked;
        double left = Math.min(4, Math.max(0, ctx.roundsLeft())) / 4.0;
        return clamp(0.1 + 0.9 * tightness * left);
    }
}
