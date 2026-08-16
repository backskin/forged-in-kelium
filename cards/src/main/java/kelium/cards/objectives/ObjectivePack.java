package kelium.cards.objectives;

import java.util.ArrayList;
import java.util.List;

import kelium.cards.CardTop;
import kelium.core.Resource;
import kelium.engine.cards.Card;
import kelium.engine.cards.CardContext;
import kelium.engine.cards.CardRegistry;

/**
 * ВСЕ КАРТЫ ЗАДАНИЙ действующего каталога (1.8.0): 40 обычных и 12 начальных.
 *
 * <p>ПОЧЕМУ ОДИН ФАЙЛ, А НЕ 54. Заказ был «карты — полноценные объекты с кодом»,
 * и он выполнен: каждая карта здесь — объект со своим поведением. Но 54 файла по
 * шесть строк, отличающихся одной строкой, — это не объектное устройство, а его
 * имитация: читать такое нельзя, а расходиться с данными оно начнёт на первой же
 * правке каталога. Здесь карта занимает одну строку, и весь каталог виден
 * целиком — как список карт на столе.
 *
 * <p>Карта, поведение которой не сводится к общим случаям, получает свой класс
 * (см. {@link CountingObjectives}) и подключается сюда одной строкой.
 *
 * <p>ГЛАВНОЕ, ЧТО ЗДЕСЬ ПОЯВИЛОСЬ, — прогресс. У каждой карты, где выполнение
 * счётное, есть {@code progress}: доля пути до награды. Раньше карта отвечала
 * боту только «да/нет», и задание, до которого один шаг, выглядело так же, как
 * невыполнимое. Замер 15.08.2026: из шести полученных заданий выполнялось одно,
 * пять сжигались.
 */
public final class ObjectivePack implements CardRegistry.CardPack {

    @Override
    public List<Card> cards() {
        List<Card> out = new ArrayList<>();

        // ================= ВОЙНА И ДАВЛЕНИЕ (20 карт) =================
        // Половина колоды платит за конфликт (заказ дизайнера 15.08.2026).
        // До каталога 1.7.0 таких карт было шесть из 54 — каталог платил
        // игроку за то, чтобы НЕ воевать.
        out.add(new GroupDevelopment.Strongpoint());          // o03 Опорный пункт
        out.add(new GroupDevelopment.Ambush());               // o07 Засада
        out.add(new GroupInfrastructure.ForwardNode());       // o11 Передовой узел
        out.add(new GroupInfrastructure.FieldConstruction()); // o12 Стройка в поле
        out.add(new GroupInfrastructure.Encirclement());      // o14 Круговая порука
        out.add(new GroupInfrastructure.NewGround());         // o17 Ход на новостройку
        out.add(new GroupOperation.FirstBlood());             // o21 Первая кровь
        out.add(new GroupOperation.Mopping());                // o22 Зачистка
        out.add(new GroupOperation.Spread());                 // o23 Растяжка
        out.add(new GroupOperation.Siege());                  // o25 Осада
        out.add(new GroupOperation.Raid());                   // o26 Наскок
        out.add(new GroupOperation.OnEnemyGround());          // o27 На чужой земле
        out.add(new GroupOperation.Pincers());                // o28 Клещи
        out.add(new GroupOperation.DeepRaid());               // o29 Дальний рейд
        out.add(new GroupOperation.AirSupremacy());           // o31 Воздушное превосходство
        out.add(new GroupNewWar.Riposte());                   // o41 Ответный удар
        out.add(new GroupNewWar.Devastation());               // o42 Разорение
        out.add(new GroupNewWar.StrongerHunt());              // o43 Охота на сильного
        out.add(new PredicateObjective("o45",
            "в этот ход нанести урон двум разным зданиям противника"));
        out.add(new PredicateObjective("o46",
            "набрать в трофеи жетоны трёх разных видов"));

        // ============ ЭКОНОМИКА, НАУКА, РИСУНКИ (20 карт) ============
        out.add(new GroupDevelopment.FullSalvo());            // o01 Полный залп
        out.add(new GroupDevelopment.Conveyor("o02", true));  // o02 Конвейер
        out.add(new GroupDevelopment.Vein());                 // o04 Жила
        out.add(new GroupDevelopment.LastDrop());             // o05 Последыш
        out.add(new GroupDevelopment.LuckyRun());             // o08 Счастливый рейс
        out.add(new GroupInfrastructure.Contractor());        // o15 Подрядчик
        out.add(new GroupInfrastructure.Redivision());        // o16 Передел
        out.add(new GroupInfrastructure.FullPower());         // o18 Полное питание
        out.add(new GroupInfrastructure.Garrison());          // o19 Гарнизон
        out.add(new GroupInfrastructure.Rewiring());          // o20 Перекоммутация
        out.add(new GroupAcquisitions.Deal());                // o33 Сделка
        out.add(new GroupAcquisitions.FarFrontier());         // o36 Дальний рубеж
        out.add(new GroupAcquisitions.ZeroBalance());         // o40 По-нулям
        out.add(new PredicateObjective("o34",
            "в этот ход взять три разных предложения планшета технологий"));
        out.add(new PredicateObjective("o39",
            "в этот ход сдать в Науку два трофейных жетона"));
        // ЖЕРТВЫ. Плата вносится в момент розыгрыша, действий не требует —
        // дизайнер отдельно отметил, что таких карт в каталоге не было ни одной.
        out.add(new PredicateObjective("o06", "сдать два своих неоткрытых контейнера"));
        out.add(new PredicateObjective("o10",
            "вернуть в запас два своих войска с разных гексов вне гексов своих зданий"));
        // ЗАДАНИЯ-РИСУНКИ 10.0: считается не число жетонов, а СВЯЗЬ — какие
        // гексы соединяет непрерывное соседство твоих жетонов.
        out.add(new PredicateObjective("o50",
            "связать войсками два гекса со своими добытчиками"));
        out.add(new PredicateObjective("o53",
            "связать зданиями три гекса, лежащих по прямой"));
        out.add(new PredicateObjective("o54",
            "связать жетонами два противоположных гекса вокруг тайла зарождения"));

        // ==================== НАЧАЛЬНЫЕ (8 карт) ====================
        out.add(new GroupAcquisitions.FirstBuildings());      // n1 Основа
        out.add(new GroupAcquisitions.FirstUnits());          // n2 Первый набор
        out.add(new GroupAcquisitions.Stock("n3", Resource.AMMO));    // n3 Запасы
        out.add(new GroupAcquisitions.FirstMarket());         // n4 Первый рынок
        out.add(new GroupAcquisitions.FirstPower());          // n5 Подключение
        out.add(new GroupAcquisitions.FirstStep());           // n6 Выход
        out.add(new GroupAcquisitions.Stock("n7", Resource.KELIUM));  // n7 Жила
        out.add(new GroupAcquisitions.FirstFind());           // n8 Находка
        out.add(new PredicateObjective("n9", "шагнуть на любом треке технологий"));
        out.add(new PredicateObjective("n10", "уничтожить любой жетон противника"));
        out.add(new PredicateObjective("n11",
            "сыграть карту приказа, нижний приказ которой вскрыт другим игроком"));
        out.add(new PredicateObjective("n12",
            "сыграть верхом тот же приказ, что и другой игрок"));

        return out;
    }

}
