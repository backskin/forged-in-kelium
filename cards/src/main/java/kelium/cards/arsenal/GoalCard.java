package kelium.cards.arsenal;

import java.util.Map;

import kelium.engine.cards.CardContext;

/**
 * КАРТА-ЦЕЛЬ АРСЕНАЛА — не меняет правил, а платит очками в конце партии.
 *
 * <p>Новая семья карт (2.1.0). Отличается от остальных тем, что низ у неё не
 * способность, а УСЛОВИЕ НА КОНЕЦ ПАРТИИ: «1 очко за каждые два здания», «5
 * очков, если есть жетон уничтожения ЦУ и три обломка». Считает такие карты
 * движок при подведении счёта; здесь живёт то, чего движок не умеет, — ОЦЕНКА,
 * стоит ли карта того ПРЯМО СЕЙЧАС.
 *
 * <p>Оценка у карты-цели устроена иначе всех: обычная карта тем ценнее, чем
 * острее нехватка, а карта-цель — тем ценнее, чем БЛИЖЕ игрок к её условию и чем
 * больше раундов осталось, чтобы дотянуть. Ставить цель в последнем раунде,
 * будучи от неё далеко, — выброшенная карта.
 */
public final class GoalCard extends ArsenalCardBase {

    public GoalCard(String id) {
        super(id);
    }

    /** У карты-цели нет способности: низ считает очки, а не меняет правила. */
    @Override
    public String passiveId() {
        return null;
    }

    @Override
    protected double installValue(CardContext ctx) {
        Map<String, Object> sc = scoring();
        if (sc == null) {
            return 0.2;
        }
        double left = Math.min(4, Math.max(0, ctx.roundsLeft())) / 4.0;
        double closeness;
        if (sc.get("combo") instanceof java.util.List<?> combo) {
            // Комбинация: насколько выполнена САМАЯ ОТСТАЮЩАЯ часть — именно она
            // и решает, будет ли карта оплачена вообще.
            double worst = 1.0;
            for (Object part : combo) {
                if (!(part instanceof Map<?, ?> cond)) {
                    continue;
                }
                int need = cond.get("at_least") instanceof Number n ? n.intValue() : 1;
                int have = count(ctx, String.valueOf(cond.get("of")));
                worst = Math.min(worst, Math.min(1.0, have / (double) Math.max(1, need)));
            }
            closeness = worst;
        } else {
            // Линейная плата: чем больше уже есть, тем очевиднее выгода.
            int per = sc.get("per") instanceof Number n ? n.intValue() : 2;
            int have = count(ctx, String.valueOf(sc.get("of")));
            closeness = Math.min(1.0, have / (double) Math.max(1, per * 3));
        }
        // Даже далёкая цель чего-то стоит, если впереди вся партия.
        return clamp(0.1 + 0.9 * (0.4 * left + 0.6 * closeness * left));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> scoring() {
        if (data().get("bottom") instanceof Map<?, ?> b
                && b.get("scoring") instanceof Map<?, ?> sc) {
            return (Map<String, Object>) sc;
        }
        return null;
    }

    private static int units(CardContext ctx, kelium.core.UnitType type) {
        int n = 0;
        for (kelium.core.UnitToken u : ctx.me().unitsOnField()) {
            if (u.type == type) {
                n++;
            }
        }
        return n;
    }

    private static int buildings(CardContext ctx, kelium.core.BuildingType type) {
        int n = 0;
        for (kelium.core.BuildingToken b : ctx.me().buildingsOnField()) {
            if (b.type == type) {
                n++;
            }
        }
        return n;
    }

    /** То же, что считает движок при подведении счёта. */
    private static int count(CardContext ctx, String what) {
        return switch (what) {
            case "buildings_on_field" -> ctx.me().buildingsOnField().size();
            case "aircraft_on_field" -> units(ctx, kelium.core.UnitType.AIRCRAFT);
            case "vehicles_on_field" -> units(ctx, kelium.core.UnitType.VEHICLE);
            case "airbase" -> buildings(ctx, kelium.core.BuildingType.AIRBASE);
            case "military_buildings" -> buildings(ctx, kelium.core.BuildingType.BARRACKS)
                + buildings(ctx, kelium.core.BuildingType.FACTORY)
                + buildings(ctx, kelium.core.BuildingType.AIRBASE);
            case "level2_economy" -> {
                int n = 0;
                for (kelium.core.BuildingToken b : ctx.me().buildingsOnField()) {
                    boolean economy = b.type == kelium.core.BuildingType.MINER
                        || b.type == kelium.core.BuildingType.POWER_PLANT;
                    if (economy && b.level != null && b.level == 2) {
                        n++;
                    }
                }
                yield n;
            }
            case "units_off_home" -> {
                java.util.Set<String> home = new java.util.HashSet<>();
                for (kelium.core.BuildingToken b : ctx.me().buildingsOnField()) {
                    if (b.hexId != null) {
                        home.add(b.hexId);
                    }
                }
                int n = 0;
                for (kelium.core.UnitToken u : ctx.me().unitsOnField()) {
                    if (u.hexId != null && !home.contains(u.hexId)) {
                        n++;
                    }
                }
                yield n;
            }
            case "units_on_field" -> ctx.me().unitsOnField().size();
            case "debris" -> ctx.have(kelium.core.Resource.DEBRIS);
            case "cu_tokens" -> ctx.me().cuDestructionTokens;
            case "unit_kinds" -> {
                java.util.Set<kelium.core.UnitType> kinds =
                    java.util.EnumSet.noneOf(kelium.core.UnitType.class);
                for (kelium.core.UnitToken u : ctx.me().unitsOnField()) {
                    kinds.add(u.type);
                }
                yield kinds.size();
            }
            case "tech_steps" -> {
                int n = 0;
                for (String track : ctx.state().tech.tracks) {
                    n += ctx.me().techSteps.getOrDefault(track, 0);
                }
                yield n;
            }
            default -> 0;
        };
    }
}
