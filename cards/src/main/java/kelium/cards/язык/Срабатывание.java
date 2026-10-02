package kelium.cards.язык;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ПЕЧАТНЫЙ ТЕКСТ СРАБАТЫВАНИЯ установленной карты (Карты 2.0, 30.09.2026):
 * «Каждый раз, когда играешь ветку «Добыть», получи 1 монету. Не больше 1 раза
 * за ход». Текст собирается из тех же данных низа, которые исполняет движок
 * ({@code kelium.engine.Срабатывания}), — разойтись им нечему.
 */
public final class Срабатывание {

    private Срабатывание() {
    }

    /** Текст низа карты по записи {@code когда / эффект / предел}. */
    public static String текст(Map<?, ?> низ) {
        Map<?, ?> когда = низ.get("когда") instanceof Map<?, ?> m ? m : Map.of();
        Map<?, ?> эффект = низ.get("эффект") instanceof Map<?, ?> m ? m : Map.of();
        int предел = низ.get("предел") instanceof Number n ? n.intValue() : 1;
        // плата: «можешь заплатить 1 келемий: получи …» — платить или нет, решает игрок
        String плата = низ.get("плата") instanceof Map<?, ?> пл && !пл.isEmpty()
            ? "можешь заплатить " + добро(пл) + ": " : "";
        return "Каждый раз, когда " + событие(когда) + ", " + плата + эффект(эффект) + ". Не больше "
            + (предел == 1 ? "1 раза" : предел + " раз") + " за ход.";
    }

    /** Событие словами: «играешь ветку «Добыть»». */
    public static String событие(Map<?, ?> когда) {
        String что = String.valueOf(когда.get("событие"));
        return switch (что) {
            case "ветка" -> "играешь ветку " + Требование.ветка(String.valueOf(когда.get("ветка")));
            case "развилка" -> "играешь любую ветку " + Требование.развилкиРод(
                String.valueOf(когда.get("развилка")));
            case "задание" -> "выполняешь задание";
            case "сжёг" -> "сжигаешь карту";
            case "установил" -> "устанавливаешь карту арсенала";
            case "контейнер" -> "вскрываешь контейнер";
            case "уничтожил" -> {
                String кого = когда.get("жертва") == null ? "жетон врага" : switch (String.valueOf(
                        когда.get("жертва"))) {
                    case "building" -> "здание врага";
                    case "unit" -> "войско врага";
                    default -> "жетон врага";
                };
                String кем = когда.get("кем") == null ? "" : " " + switch (String.valueOf(когда.get("кем"))) {
                    case "infantry" -> "пехотой";
                    case "vehicle" -> "техникой";
                    case "aircraft" -> "авиацией";
                    case "tower" -> "вышкой";
                    default -> "";
                };
                yield "уничтожаешь " + кого + кем;
            }
            case "потерял" -> "твой жетон уничтожают";
            case "совпадение" -> "твой приказ совпадает с чужим";
            case "низ" -> "у тебя открыт нижний приказ";
            case "ход" -> "начинаешь свой ход";
            case "построил_крупно" -> "строишь добытчик или энергостанцию 3-го уровня или выше";
            default -> что;
        };
    }

    /** Эффект словами: «получи 1 монету и 1 боеприпас». */
    public static String эффект(Map<?, ?> эффект) {
        String имя = String.valueOf(эффект.get("effect"));
        Map<?, ?> п = эффект.get("params") instanceof Map<?, ?> m ? m : Map.of();
        return switch (имя) {
            case "спец" -> {
                int n = п.get("n") instanceof Number k ? k.intValue() : 1;
                yield n == 1 ? "получи ещё одно спец-действие" : "получи ещё " + n + " спец-действия";
            }
            case "free_action" -> "сыграй ветку " + Требование.ветка(String.valueOf(п.get("action")));
            case "heal_one" -> "сними 1 урон со своего жетона";
            case "gain" -> "получи " + добро(п);
            // ЭФФЕКТЫ, МЕНЯЮЩИЕ ПРАВИЛО (01.10.2026): не ресурс, а поступок вне
            // очереди — движок их уже знает (Effects.moveUnit, placeDamage,
            // stealResource)
            case "move_unit" -> "передвинь своё войско на соседний гекс";
            case "place_damage" -> "нанеси 1 урон жетону врага на гексе со своим войском или соседнем с ним";
            case "steal_resource" -> "забери у одного врага " + switch (String.valueOf(п.get("resource"))) {
                case "coin" -> "1 монету";
                case "ammo" -> "1 боеприпас";
                default -> "1 келемий";
            };
            // ЭНЕРГИЯ И УРОВНИ (02.10.2026)
            case "gain_per" -> "получи " + поОдному(п) + " " + заКаждый(String.valueOf(п.get("per")))
                + (п.get("max") instanceof Number m ? " (не больше " + m.intValue() + ")" : "");
            case "upgrade_building" -> {
                int цена = п.get("cost") instanceof Number c ? c.intValue() : 1;
                String что = switch (String.valueOf(п.get("type"))) {
                    case "miner" -> "свой добытчик";
                    case "plant" -> "свою энергостанцию";
                    default -> "свой добытчик или энергостанцию";
                };
                yield "замени " + что + " на поле жетоном на уровень выше из запаса"
                    + (цена == 0 ? "" : " за " + Требование.числом(цена, "монету", "монеты", "монет"));
            }
            case "permanent_energy" -> "положи кубик энергии из общего запаса в свободную ячейку своего здания";
            default -> имя;
        };
    }

    /** «по монете», «по боеприпасу» — добро эффекта gain_per (по одному за счёт). */
    private static String поОдному(Map<?, ?> п) {
        List<String> части = new ArrayList<>();
        if (п.get("coin") instanceof Number) {
            части.add("по монете");
        }
        if (п.get("ammo") instanceof Number) {
            части.add("по боеприпасу");
        }
        if (п.get("kelium") instanceof Number) {
            части.add("по келемию");
        }
        if (п.get("trophy") instanceof Number) {
            части.add("по трофею");
        }
        return String.join(" и ", части);
    }

    /** «за каждый свой запитанный добытчик» — счёт эффекта gain_per. */
    private static String заКаждый(String per) {
        return switch (per) {
            case "own_powered_miner" -> "за каждый свой запитанный добытчик";
            case "own_powered_plant" -> "за каждую свою запитанную энергостанцию";
            case "own_powered_building" -> "за каждое своё запитанное здание";
            case "miner_levels" -> "за каждый уровень своих добытчиков на поле";
            case "plant_levels" -> "за каждый уровень своих энергостанций на поле";
            case "top_miner_level" -> "за каждый уровень своего старшего добытчика";
            case "top_plant_level" -> "за каждый уровень своей старшей энергостанции";
            case "own_military_building" -> "за каждое своё военное здание";
            case "own_economy_building" -> "за каждый свой добытчик и энергостанцию";
            default -> "за каждое своё войско на поле";
        };
    }

    private static String добро(Map<?, ?> п) {
        List<String> части = new ArrayList<>();
        добавить(части, п, "coin", "монету", "монеты", "монет");
        добавить(части, п, "ammo", "боеприпас", "боеприпаса", "боеприпасов");
        добавить(части, п, "kelium", "келемий", "келемия", "келемия");
        добавить(части, п, "trophy", "трофей", "трофея", "трофеев");
        добавить(части, п, "objective_cards", "карту задания", "карты задания", "карт задания");
        return части.isEmpty() ? "ничего" : String.join(" и ", части);
    }

    private static void добавить(List<String> части, Map<?, ?> п, String ключ, String одна,
                                 String две, String пять) {
        if (п.get(ключ) instanceof Number n && n.intValue() > 0) {
            части.add(Требование.числом(n.intValue(), одна, две, пять));
        }
    }
}
