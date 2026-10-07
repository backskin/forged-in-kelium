package kelium.cards.язык;

import java.util.Map;

import kelium.core.BuildingToken;
import kelium.core.Token;
import kelium.core.UnitToken;

/**
 * ГРУППА ЖЕТОНОВ в требовании: кто, чьи и в каком состоянии — «2 своих
 * запитанных добытчика», «здание врага».
 *
 * @param кто   род жетона
 * @param свои  свои жетоны или жетоны врага (любого соперника)
 * @param сост  состояние жетона (запитан, цел…) или {@link Состояние#ЛЮБОЕ}
 */
public record Группа(Кто кто, boolean свои, Состояние сост) {

    /** Состояние жетона, видное на столе. */
    public enum Состояние {
        ЛЮБОЕ("", "", "", ""),
        ЗАПИТАН("запитанный", "запитанную", "запитанное", "запитанных"),
        НЕ_ЗАПИТАН("незапитанный", "незапитанную", "незапитанное", "незапитанных"),
        ЦЕЛЫЙ("целый", "целую", "целое", "целых"),
        С_УРОНОМ("повреждённый", "повреждённую", "повреждённое", "повреждённых"),
        // УРОВЕНЬ ЗДАНИЯ (02.10.2026): печатается после имени — «2 своих
        // добытчика 3-го уровня или выше»
        КРУПНЫЙ("", "", "", "");

        final String м;
        final String ж;
        final String с;
        final String мн;

        Состояние(String м, String ж, String с, String мн) {
            this.м = м;
            this.ж = ж;
            this.с = с;
            this.мн = мн;
        }

        String ед(Кто.Род род) {
            return switch (род) {
                case М -> м;
                case Ж -> ж;
                case С -> с;
            };
        }

        boolean подходит(Token t) {
            return switch (this) {
                case ЛЮБОЕ -> true;
                case ЗАПИТАН -> t instanceof BuildingToken b && b.powered();
                case НЕ_ЗАПИТАН -> t instanceof BuildingToken b && !b.powered();
                case ЦЕЛЫЙ -> урон(t) == 0;
                case С_УРОНОМ -> урон(t) > 0;
                case КРУПНЫЙ -> t instanceof BuildingToken b && b.level != null && b.level >= 3;
            };
        }

        private static int урон(Token t) {
            return t instanceof UnitToken u ? u.damage
                : t instanceof BuildingToken b ? b.damage : 0;
        }
    }

    public Группа(Кто кто, boolean свои) {
        this(кто, свои, Состояние.ЛЮБОЕ);
    }

    /** Подходит ли жетон (владелец проверяется снаружи). */
    public boolean подходит(Token t) {
        return кто.подходит(t) && сост.подходит(t);
    }

    /**
     * ФРАЗА ДЛЯ ЧИСЛА: 1 — «свой запитанный добытчик», 2–4 — «2 своих
     * запитанных добытчика», 5 и больше — «5 своих … добытчиков». Жетоны врага
     * — без «свой», с «врага» в конце.
     */
    public String фраза(int n) {
        StringBuilder sb = new StringBuilder();
        if (n == 1) {
            if (свои) {
                sb.append(switch (кто.род) {
                    case М -> "свой ";
                    case Ж -> "свою ";
                    case С -> "своё ";
                });
            }
            String прил = сост.ед(кто.род);
            if (!прил.isEmpty()) {
                sb.append(прил).append(' ');
            }
            sb.append(кто.винЕд);
        } else {
            sb.append(n).append(' ');
            if (свои) {
                sb.append("своих ");
            }
            if (!сост.мн.isEmpty()) {
                sb.append(сост.мн).append(' ');
            }
            int последняя = n % 10;
            boolean мало = последняя >= 2 && последняя <= 4 && (n % 100 < 12 || n % 100 > 14);
            sb.append(мало ? кто.родЕд : кто.родМн);
        }
        if (сост == Состояние.КРУПНЫЙ) {
            sb.append(" 3-го уровня или выше");
        }
        if (!свои) {
            sb.append(" врага");
        }
        return sb.toString();
    }

    /** «где есть добытчик врага» — именительный, для отношений гексов. */
    public String гдеЕсть() {
        String прил = сост.ед(кто.род);
        if (кто.род == Кто.Род.Ж && !прил.isEmpty()) {
            прил = прил.substring(0, прил.length() - 2) + "ая";
        }
        String твой = !свои ? "" : switch (кто.род) {
            case М -> "твой ";
            case Ж -> "твоя ";
            case С -> "твоё ";
        };
        return твой + (прил.isEmpty() ? "" : прил + " ") + кто.имЕд + (свои ? "" : " врага");
    }

    /** Запись группы в данные карты. */
    public Map<String, Object> запись() {
        return Map.of("кто", кто.name(), "чьё", свои ? "своё" : "врага", "сост", сост.name());
    }

    /** Группа из записи данных. */
    public static Группа из(Object o) {
        if (!(o instanceof Map<?, ?> m)) {
            throw new IllegalArgumentException("группа — не запись: " + o);
        }
        Кто кто = Кто.valueOf(String.valueOf(m.get("кто")));
        boolean свои = !"врага".equals(String.valueOf(m.get("чьё")));
        Состояние сост = m.get("сост") == null ? Состояние.ЛЮБОЕ
            : Состояние.valueOf(String.valueOf(m.get("сост")));
        return new Группа(кто, свои, сост);
    }
}
