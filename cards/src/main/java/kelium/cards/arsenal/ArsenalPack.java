package kelium.cards.arsenal;

import java.util.ArrayList;
import java.util.List;

import kelium.engine.cards.Card;
import kelium.engine.cards.CardRegistry;

/**
 * ВСЕ 24 КАРТЫ АРСЕНАЛА последней номерной версии (2.0.0).
 *
 * <p>Порядок как в каталоге дизайнера, чтобы сверять с бумагой подряд.
 *
 * <p>ПОЧЕМУ ПОЧТИ ВСЕ КАРТЫ — ОДИН КЛАСС. Карта арсенала устроена единообразно:
 * верх сжигают ради разового эффекта, низ включает способность. И то и другое уже
 * описано — эффект в реестре эффектов, способность в реестре способностей вместе
 * со своим самоописанием (какое узкое место снимает и насколько сильно). Значит
 * различие между картами — это ДАННЫЕ, а не поведение, и плодить 24 одинаковых
 * класса значило бы имитировать объектное устройство, а не строить его.
 *
 * <p>Своё поведение получают только те карты, у которых оно действительно своё:
 * они наследуются от {@link ArsenalCardBase} и переопределяют оценку. Сейчас
 * таких три — см. ниже.
 *
 * <p>ЧТО ЭТОТ МОДУЛЬ ДОБАВИЛ К ПРЕЖНЕЙ СХЕМЕ: карта теперь умеет отвечать, стоит
 * ли она ПРЯМО СЕЙЧАС, — сверяя обещание способности с тем, чего игроку не
 * хватает. Замер 15.08.2026: устанавливается 0.73 карты за партию против 1.09
 * сожжённых, то есть карту чаще выбрасывают, чем играют.
 */
public final class ArsenalPack implements CardRegistry.CardPack {

    @Override
    public List<Card> cards() {
        List<Card> out = new ArrayList<>();

        // ---- скорость и живучесть ----
        out.add(new ArsenalCardBase("b01"));   // Ускоренный марш — пехота и техника +1 скорость
        out.add(new ArsenalCardBase("b02"));   // Форсаж — авиация и вышки +1 скорость
        out.add(new ArsenalCardBase("b03"));   // Облегчённая броня — техника быстрее и хрупче

        // ---- склад и СПЕЦ-действия ----
        out.add(new ArsenalCardBase("b04"));   // Патронный ящик
        out.add(new ArsenalCardBase("b05"));   // Келемиевый бак
        out.add(new ArsenalCardBase("b06"));   // Ремонтная бригада
        out.add(new ArsenalCardBase("b07"));   // Кабельная бригада
        out.add(new ArsenalCardBase("b08"));   // Проходчики

        // ---- приказы и экономика ----
        out.add(new ArsenalCardBase("b09"));   // Штабная выучка — низ приказа даёт два действия
        out.add(new ArsenalCardBase("b10"));   // Оперативный резерв — то же, другой утиль
        out.add(new ArsenalCardBase("b11"));   // Энергосбыт — энергия приносит монеты
        out.add(new ArsenalCardBase("b12"));   // Научный обмен — келемий вместо трофея
        out.add(new TrophyStorageCard());      // b13 Трофейный склад — своя оценка

        // ---- авиация ----
        out.add(new ArsenalCardBase("b14"));   // Целеуказание — с гекса с авиацией бьёшь на 2
        out.add(new ArsenalCardBase("b15"));   // Воздушный зонт — авиация защищает свой гекс
        out.add(new ArsenalCardBase("b16"));   // Лёгкая база — авиабаза дешевле по энергии

        // ---- стартовые ----
        out.add(new ArsenalCardBase("bs1"));   // Полевой генератор
        out.add(new ArsenalCardBase("bs2"));   // Активная броня — боеприпас за ответный удар
        out.add(new ArsenalCardBase("bs3"));   // Мародёры
        out.add(new ArsenalCardBase("bs4"));   // Штабная связь
        out.add(new KillBountyCard());         // bs5 Премия за голову — своя оценка
        out.add(new ArsenalCardBase("bs6"));   // Укреплённые базы
        out.add(new ArsenalCardBase("bs7"));   // Старатели
        out.add(new SiegeEngineerCard());      // bs8 Осадные инженеры — своя оценка

        // ---- НОВЫЕ КАРТЫ 2.3.0 (ревью дизайнера 17.08.2026) ----
        // Четырнадцать карт: пять переделанных «имб» из старой колоды получили
        // условие, остальные пришли из заметок, до колоды не дошедших.
        out.add(new ArsenalCardBase("b17"));   // Тяжёлое крыло — авиация медленнее и живучее
        out.add(new ArsenalCardBase("b18"));   // Укреплённые перекрытия — здания в 1 HP держат два удара
        out.add(new ArsenalCardBase("b19"));   // Трофейный сейф — +2 ячейки только под обломки
        out.add(new ArsenalCardBase("b20"));   // Аварийное питание — единственный источник доплаты за энергию
        out.add(new ArsenalCardBase("b21"));   // Маркшейдер — разворот добытчика по ячейкам
        out.add(new ArsenalCardBase("b22"));   // Параллельные штабы — два СПЕЦ без Безопасности
        out.add(new ArsenalCardBase("b23"));   // Штабная коллегия — вторая копия
        out.add(new ArsenalCardBase("b24"));   // Оперативный отдел — третья копия
        out.add(new ArsenalCardBase("b25"));   // Второй контур — второй гекс Смены энергии даром
        out.add(new ArsenalCardBase("b26"));   // Разгонная полоса — вторая копия «Лёгкого взлёта»
        out.add(new ArsenalCardBase("b27"));   // Абордаж — чужой жетон в трофеи ценой пехоты
        out.add(new ArsenalCardBase("b28"));   // Обмен пленными — выкуп своих разными ресурсами
        out.add(new ArsenalCardBase("b29"));   // Десантные тропы — пехота через зарождения
        out.add(new ArsenalCardBase("b30"));   // Келемиевый дождь — новый тайл ценой двух очков
        out.add(new ArsenalCardBase("b31"));   // Ядерный удар — гекс становится запретным навсегда

        // ---- карты-цели: очки за положение жетона в конце партии (2.3.0) ----
        for (String id : new String[]{"v01", "v02", "v03", "v04", "v05", "v06"}) {
            out.add(new GoalCard(id));
        }

        return out;
    }
}
