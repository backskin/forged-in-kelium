package kelium.gui.replay2;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kelium.dataio.GameConfig;
import kelium.engine.ModuleSets;
import kelium.report.ReplayRecord;
import kelium.rules.Ruleset;

/**
 * МЕШОК МОДУЛЕЙ — что в нём лежит и что каждый жетон даёт.
 *
 * <p>Заказ дизайнера 08.09.2026: «хочу каталог мешочков с жетонами, красными и
 * синими… нажимаешь — открывается окошко, там жетончики с картинками и рядом
 * описания, что этот жетончик даёт».
 *
 * <p>СОДЕРЖИМОЕ БЕРЁТСЯ ИЗ ПРАВИЛ ТОЙ ПАРТИИ, что открыта: свод называет версию
 * наборов и id мешка ({@code modules.red_bag}), набор — сами жетоны. Партии нет
 * — показываем мешки свода по умолчанию: каталог полезен и до игры, когда
 * смотрят, что вообще бывает.
 *
 * <p>ОДИНАКОВЫЕ ЖЕТОНЫ СВЁРНУТЫ. В мешке лежат по два-три близнеца («шесть пар
 * целей по два»), и показывать их подряд шестью одинаковыми картинками — значит
 * прятать разнообразие за повторами. Каждый вид показан один раз, с числом
 * копий.
 */
final class МешокМодулей {

    /** Один вид жетона: картинка, что даёт, сколько таких в мешке. */
    record Вид(String id, boolean красный, ModuleSets.ModuleToken token, int копий) {

        /** Картинка стороны: обычной или золотой; {@code null} — печати нет. */
        java.awt.image.BufferedImage арт(boolean gold) {
            return красный
                ? kelium.report.ModuleArt.red(token.targets(), gold)
                : kelium.report.ModuleArt.blue(token.ammo(), token.units(),
                    token.gild(), gold);
        }
    }

    /** Мешок целиком. */
    record Мешок(String id, boolean красный, List<Вид> виды) {

        int всего() {
            int n = 0;
            for (Вид в : виды) {
                n += в.копий();
            }
            return n;
        }
    }

    private МешокМодулей() {
    }

    /** Красный и синий мешки той партии, что открыта (или мешки по умолчанию). */
    static List<Мешок> мешки(ReplayRecord record) {
        Path root = GameConfig.resolveDataRoot(null);
        Ruleset rules = свод(record, root);
        if (rules == null) {
            return List.of();
        }
        Object v = rules.get("content_versions.modules", null);
        ModuleSets.Library lib = ModuleSets.load(root, v == null ? null : v.toString());
        if (lib.isEmpty()) {
            return List.of();
        }
        List<Мешок> out = new ArrayList<>();
        Мешок r = собрать(lib, true,
            String.valueOf(rules.get("modules.red_bag", "bag_R30")));
        Мешок b = собрать(lib, false,
            String.valueOf(rules.get("modules.blue_bag", "bag_C30")));
        if (r != null) {
            out.add(r);
        }
        if (b != null) {
            out.add(b);
        }
        return out;
    }

    private static Ruleset свод(ReplayRecord record, Path root) {
        String id = record == null || record.ruleset == null || record.ruleset.isBlank()
            ? GameConfig.DEFAULT_RULESET : record.ruleset;
        try {
            return Ruleset.loadById(id, root.resolve("rulesets"));
        } catch (RuntimeException e) {
            try {
                return Ruleset.loadById(GameConfig.DEFAULT_RULESET, root.resolve("rulesets"));
            } catch (RuntimeException e2) {
                return null;
            }
        }
    }

    private static Мешок собрать(ModuleSets.Library lib, boolean красный, String bagId) {
        Map<String, ModuleSets.ModuleSet> sets = красный ? lib.redSets() : lib.blueSets();
        List<String> setIds = (красный ? lib.redBags() : lib.blueBags()).get(bagId);
        if (setIds == null) {
            return null;
        }
        // Ключ свёртки — не id жетона, а ЕГО СОДЕРЖАНИЕ: R30-1 и R30-2 это один
        // и тот же жетон в двух экземплярах, и в каталоге он один.
        Map<String, Вид> виды = new LinkedHashMap<>();
        for (String sid : setIds) {
            ModuleSets.ModuleSet set = sets.get(sid);
            if (set == null || set.proposal()) {
                continue;
            }
            for (ModuleSets.ModuleToken t : set.tokens()) {
                String ключ = содержание(t, красный);
                Вид был = виды.get(ключ);
                виды.put(ключ, был == null
                    ? new Вид(t.id(), красный, t, 1)
                    : new Вид(был.id(), красный, был.token(), был.копий() + 1));
            }
        }
        return виды.isEmpty() ? null : new Мешок(bagId, красный, List.copyOf(виды.values()));
    }

    private static String содержание(ModuleSets.ModuleToken t, boolean красный) {
        if (красный) {
            return String.join("+", t.targets()) + "|" + t.stat() + t.plus()
                + "|" + t.ammo() + "|" + t.effect();
        }
        return "a" + t.ammo() + "u" + t.units() + "|" + t.gild();
    }

    // ==================== человеческие описания ====================

    /** Заголовок жетона: коротко, чем он является. */
    static String имя(Вид в) {
        ModuleSets.ModuleToken t = в.token();
        if (в.красный()) {
            if (t.targets().size() == 2) {
                return цель(t.targets().get(0)) + " и " + цель(t.targets().get(1));
            }
            if (t.stat() != null) {
                return "+" + t.plus() + " " + характеристика(t.stat());
            }
            return t.id();
        }
        return t.ammo() + " БПР или " + t.units() + " войск";
    }

    /**
     * ТОЛЬКО СУТЬ ЖЕТОНА, без общей для всего мешка шапки «красный — прокачка
     * атаки». Шапка одинакова у каждой карточки и, повторённая шесть раз,
     * заслоняет то единственное, чем жетоны различаются.
     */
    static String чтоДаёт(Вид в, boolean gold) {
        String s = описание(в, gold);
        int i = s.indexOf("\n\n");
        return i < 0 ? s : s.substring(i + 2);
    }

    /** Полное описание: что даёт обычная сторона и что — золотая. */
    static String описание(Вид в, boolean gold) {
        ModuleSets.ModuleToken t = в.token();
        StringBuilder sb = new StringBuilder();
        if (в.красный()) {
            sb.append("КРАСНЫЙ — прокачка атаки. Ложится на вторичный ряд атаки рода войск.\n\n");
            if (t.targets().size() == 2) {
                sb.append("Открывает вторичную атаку по целям: ")
                  .append(цель(t.targets().get(0))).append(" и ")
                  .append(цель(t.targets().get(1))).append(".\n");
                sb.append(gold
                    ? "ЗОЛОТАЯ СТОРОНА: за бой стреляют ОБЕ цели, каждая атака оплачивается отдельно.\n"
                    : "Обычная сторона: за бой выбирается ОДНА из двух целей.\n");
            } else if (t.stat() != null) {
                sb.append("Поднимает ").append(характеристика(t.stat()))
                  .append(" рода войск на +").append(t.plus()).append(".\n");
                sb.append(gold ? "ЗОЛОТАЯ СТОРОНА: растут обе характеристики.\n" : "");
            } else if (t.effect() != null) {
                sb.append("Особый эффект: ").append(t.effect()).append(".\n");
            }
            sb.append("Цена атаки: ").append(t.ammo()).append(" БПР.");
        } else {
            sb.append("СИНИЙ — прокачка найма. Накрывает зону Сборки военного здания.\n\n");
            sb.append("Сборка на этом здании даёт ").append(t.ammo()).append(" БПР ИЛИ ")
              .append(t.units()).append(" войск — выбор один за действие.\n");
            String что = "ammo".equals(t.gild()) ? "боеприпасы" : "войска";
            sb.append(gold
                ? "ЗОЛОТАЯ СТОРОНА: помеченный стрелкой выход (" + что + ") уже увеличен на +1."
                : "Стрелка золота смотрит на " + что + ": позолотив жетон, растёт именно этот выход.");
        }
        return sb.toString();
    }

    static String цель(String code) {
        return switch (code) {
            case "infantry" -> "пехота";
            case "vehicle" -> "техника";
            case "aircraft" -> "авиация";
            case "buildings_towers" -> "здания и вышки";
            case "any_unit" -> "любое войско";
            default -> code;
        };
    }

    private static String характеристика(String stat) {
        return switch (stat) {
            case "hp" -> "прочность";
            case "speed" -> "скорость";
            default -> stat;
        };
    }
}
