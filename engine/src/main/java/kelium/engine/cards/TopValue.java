package kelium.engine.cards;

import java.util.Map;

import kelium.core.BuildingType;
import kelium.core.Resource;
import kelium.engine.ability.Hint;

/**
 * СКОЛЬКО СТОИТ ВЕРХ КАРТЫ ЗДЕСЬ И СЕЙЧАС — общая оценка для всех колод.
 *
 * <p>ЗАЧЕМ ОДНА НА ВСЕХ. Верхняя половина устроена одинаково у карт арсенала и
 * у карт заданий: идентификатор эффекта из общего реестра плюс параметры. И
 * ошибка у обеих была одна и та же — сторона утиля оценивалась КОНСТАНТОЙ
 * (0.70 у арсенала, 1.5 у заданий), то есть карта свой верх не оценивала
 * вовсе. Установка и выполнение при этом считались по-настоящему, и сравнение
 * шло «живое число против плоской цифры»: всякий раз, когда низ слабее, карта
 * уходила в костёр независимо от того, что её верх делает.
 *
 * <p>Замер по арсеналу после этой правки: сожжений 1.70 -> 1.39, установок
 * 0.50 -> 0.68 на игрока за партию. Держать вторую копию этой таблицы для
 * заданий значило бы гарантированно её рассинхронизировать.
 *
 * <p>ЧТО ЗДЕСЬ НЕ РЕШАЕТСЯ. Оценка отвечает на вопрос «пригодится ли верх
 * сейчас», а не «выгоднее ли он низа»: сравнение половин — дело того, кто
 * спрашивает, потому что у арсенала низ вечный, а у задания разовый.
 */
public final class TopValue {

    private TopValue() {
    }

    /**
     * Цена верха от 0.0 (сейчас бесполезен) до 1.0 (закрывает нужду целиком).
     *
     * @param top запись верхней половины карты: {@code effect}, {@code params}
     */
    public static double of(CardContext ctx, Map<?, ?> top) {
        if (top == null) {
            return 0.0;
        }
        String effect = String.valueOf(top.get("effect"));
        Map<?, ?> params = top.get("params") instanceof Map<?, ?> p ? p : Map.of();
        return switch (effect) {
            case "gain" -> выдача(ctx, params);
            case "free_action" -> действие(ctx, String.valueOf(params.get("action")))
                + выдача(ctx, params) * 0.5;
            // КРАЖИ И СНОС: цена ровно в том, есть ли у кого брать.
            case "steal_arsenal_card" -> чужаяРука(ctx) > 0 ? 0.65 : 0.05;
            case "discard_enemy_arsenal" -> чужиеУстановленные(ctx) > 0 ? 0.7 : 0.05;
            case "steal_objective_cards" -> чужиеЗадания(ctx) > 0 ? 0.6 : 0.05;
            case "steal_resource" -> 0.45;
            // Позолота без разложенных жетонов модуля невозможна.
            case "gild_module" -> ctx.me().redPlacements.size()
                + ctx.me().bluePlacements.size() > ctx.me().goldModules ? 0.75 : 0.05;
            // Десант ставит войска из ЗАПАСА: запас пуст — ставить нечего.
            case "landing", "deploy_units" -> запасВойск(ctx) > 0
                ? 0.35 + 0.5 * давление(ctx, Hint.Bottleneck.UNITS) : 0.05;
            case "grab_first_player" -> ctx.state().firstPlayer == ctx.seat() ? 0.1 : 0.6;
            case "market_card_from_discard" -> ctx.have(Resource.KELIUM) > 0 ? 0.5 : 0.05;
            case "unlimited_spec" -> 0.3 + 0.5 * давление(ctx, Hint.Bottleneck.ACTIONS);
            case "swap_order_card" -> 0.45;
            case "heal_one", "heal_all_own", "heal_hex" -> ранены(ctx) ? 0.6 : 0.05;
            case "move_unit" -> ctx.me().unitsOnField().isEmpty() ? 0.05 : 0.45;
            // ЩИТ прикрывает род войск в бою: некого прикрывать — нечего и жечь.
            case "shield" -> ctx.me().unitsOnField().isEmpty() ? 0.05
                : 0.25 + 0.45 * давление(ctx, Hint.Bottleneck.DEFENCE);
            case "convert", "exchange_table" -> 0.4;
            // Смена энергии или модулей: цена в том, сколько зданий стоит голодными.
            case "energy_or_modules" -> голодные(ctx) > 0 ? 0.55 : 0.15;
            // Скорость нужна только тем, кому есть чем ехать.
            case "speed_boost" -> ctx.me().unitsOnField().isEmpty() ? 0.05 : 0.4;
            case "empty" -> 0.0;
            default -> 0.5;              // незнакомый эффект — середина, как раньше
        };
    }

    /** Цена разовой выдачи ресурсов: сколько дают и насколько это нужно. */
    private static double выдача(CardContext ctx, Map<?, ?> params) {
        double value = 0;
        value += нужда(ctx, Resource.COIN) * число(params, "coin") * 0.10;
        value += нужда(ctx, Resource.AMMO) * число(params, "ammo") * 0.15;
        value += нужда(ctx, Resource.KELIUM) * число(params, "kelium") * 0.10;
        value += нужда(ctx, Resource.DEBRIS) * число(params, "debris") * 0.20;
        value += число(params, "objective_cards") * 0.20;
        value += число(params, "containers") * 0.15;
        return clamp(value);
    }

    /**
     * ЦЕНА БЕСПЛАТНОГО ДЕЙСТВИЯ. Ход — самый дорогой ресурс игры (их около
     * двадцати четырёх на партию), поэтому даровое действие дорого САМО ПО СЕБЕ.
     * Но только то, которое можно сыграть с толком: Бой без цели, Рынок без
     * келемия и Сборка без запитанного военного здания не стоят ничего.
     */
    private static double действие(CardContext ctx, String action) {
        return switch (action == null ? "" : action) {
            case "combat" -> ctx.enemyTokensOnField().isEmpty() ? 0.05
                : 0.35 + 0.45 * давление(ctx, Hint.Bottleneck.AMMO);
            case "mining" -> 0.3 + 0.5 * давление(ctx, Hint.Bottleneck.KELIUM);
            case "assembly" -> запитанныеВоенные(ctx) > 0
                ? 0.3 + 0.45 * давление(ctx, Hint.Bottleneck.AMMO) : 0.1;
            case "build" -> ctx.have(Resource.COIN) > 0 ? 0.6 : 0.15;
            case "movement" -> ctx.me().unitsOnField().isEmpty() ? 0.05 : 0.55;
            case "market" -> ctx.have(Resource.KELIUM) > 0 ? 0.6 : 0.05;
            case "science" -> ctx.have(Resource.DEBRIS) > 0
                || ctx.have(Resource.KELIUM) > 0 ? 0.6 : 0.1;
            case "energy_swap" -> голодные(ctx) > 0 ? 0.55 : 0.15;
            default -> 0.5;
        };
    }

    // ==================================================================
    //  ЧЕМ ЖМЁТ ПОЛОЖЕНИЕ
    // ==================================================================

    /** Насколько жмёт узкое место: 0.2 (не жмёт) … 1.0 (нечем играть). */
    public static double давление(CardContext ctx, Hint.Bottleneck what) {
        return switch (what) {
            case AMMO -> нехватка(ctx.have(Resource.AMMO), 4);
            case COINS -> нехватка(ctx.have(Resource.COIN), 6);
            case KELIUM -> нехватка(ctx.have(Resource.KELIUM), 4);
            case UNITS -> нехватка(ctx.me().unitsOnField().size(), 4);
            case ENERGY -> {
                int idle = 0;
                for (var b : ctx.me().buildingsOnField()) {
                    idle += b.energyIdle;
                }
                yield нехватка(idle, 3);
            }
            case REACH, DEFENCE -> ctx.me().unitsOnField().isEmpty() ? 0.2 : 0.8;
            case TROPHY -> нехватка(ctx.have(Resource.DEBRIS), 4);
            case ACTIONS, VP -> 0.9;     // действий и очков не хватает всегда
        };
    }

    /** Чем меньше есть от нормы, тем сильнее жмёт. */
    private static double нехватка(int have, int norm) {
        if (norm <= 0) {
            return 0.5;
        }
        return clamp(1.0 - Math.min(1.0, have / (double) norm) * 0.8);
    }

    private static double нужда(CardContext ctx, Resource r) {
        return нехватка(ctx.have(r), r == Resource.COIN ? 6 : 4);
    }

    private static double число(Map<?, ?> params, String key) {
        return params.get(key) instanceof Number n ? n.doubleValue() : 0.0;
    }

    public static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    // ==================================================================
    //  ЧТО ЕСТЬ НА СТОЛЕ
    // ==================================================================

    private static int чужаяРука(CardContext ctx) {
        int n = 0;
        for (var p : ctx.state().players) {
            if (p.seat != ctx.seat()) {
                n += p.arsenalHand.size();
            }
        }
        return n;
    }

    private static int чужиеУстановленные(CardContext ctx) {
        int n = 0;
        for (var p : ctx.state().players) {
            if (p.seat != ctx.seat()) {
                n += p.arsenalInstalled.size();
            }
        }
        return n;
    }

    private static int чужиеЗадания(CardContext ctx) {
        int n = 0;
        for (var p : ctx.state().players) {
            if (p.seat != ctx.seat()) {
                n += p.objectiveHand.size();
            }
        }
        return n;
    }

    private static int запасВойск(CardContext ctx) {
        int n = 0;
        for (var u : ctx.me().units) {
            if (u.hexId == null && u.alive()) {
                n++;
            }
        }
        return n;
    }

    private static int запитанныеВоенные(CardContext ctx) {
        int n = 0;
        for (var b : ctx.me().buildingsOnField()) {
            boolean военное = b.type == BuildingType.BARRACKS
                || b.type == BuildingType.FACTORY
                || b.type == BuildingType.AIRBASE
                || b.type == BuildingType.COMMAND_CENTER;
            if (военное && b.powered()) {
                n++;
            }
        }
        return n;
    }

    private static int голодные(CardContext ctx) {
        int n = 0;
        for (var b : ctx.me().buildingsOnField()) {
            if (b.energySlots > b.energyPlaced) {
                n++;
            }
        }
        return n;
    }

    private static boolean ранены(CardContext ctx) {
        for (var t : ctx.myTokensOnField()) {
            if (t instanceof kelium.core.UnitToken u && u.damage > 0) {
                return true;
            }
            if (t instanceof kelium.core.BuildingToken b && b.damage > 0) {
                return true;
            }
        }
        return false;
    }
}
