package kelium.cards.язык;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.core.UnitType;

/**
 * КТО — род жетона в требовании карты, со всеми печатными формами слова.
 *
 * <p>Формы: винительный единственного («имей свой добытчик»), родительный
 * единственного («2 своих добытчика»), родительный множественного («5 своих
 * добытчиков»), именительный единственного («где есть добытчик врага»),
 * творительный («уничтожь техникой») и род слова — от него зависят «свой /
 * свою / своё» и прилагательные при единице.
 */
public enum Кто {
    ЖЕТОН("жетон", "жетона", "жетонов", "жетон", "жетоном", Род.М),
    ВОЙСКО("войско", "войска", "войск", "войско", "войском", Род.С),
    НАЗЕМНОЕ("наземное войско", "наземных войска", "наземных войск", "наземное войско",
        "наземным войском", Род.С),
    ПЕХОТА("жетон пехоты", "жетона пехоты", "жетонов пехоты", "пехота", "пехотой", Род.М),
    ТЕХНИКА("жетон техники", "жетона техники", "жетонов техники", "техника", "техникой", Род.М),
    АВИАЦИЯ("жетон авиации", "жетона авиации", "жетонов авиации", "авиация", "авиацией", Род.М),
    ВЫШКА("жетон вышки", "жетона вышки", "жетонов вышки", "вышка", "вышкой", Род.М),
    ЗДАНИЕ("здание", "здания", "зданий", "здание", "зданием", Род.С),
    ВОЕННОЕ("военное здание", "военных здания", "военных зданий", "военное здание",
        "военным зданием", Род.С),
    ДОБЫТЧИК("добытчик", "добытчика", "добытчиков", "добытчик", "добытчиком", Род.М),
    ЭНЕРГОСТАНЦИЯ("энергостанцию", "энергостанции", "энергостанций", "энергостанция",
        "энергостанцией", Род.Ж),
    КАЗАРМА("казарму", "казармы", "казарм", "казарма", "казармой", Род.Ж),
    ЗАВОД("завод", "завода", "заводов", "завод", "заводом", Род.М),
    АВИАБАЗА("авиабазу", "авиабазы", "авиабаз", "авиабаза", "авиабазой", Род.Ж),
    ЦУ("ЦУ", "ЦУ", "ЦУ", "ЦУ", "ЦУ", Род.М);

    /** Род слова. */
    public enum Род { М, Ж, С }

    public final String винЕд;
    public final String родЕд;
    public final String родМн;
    public final String имЕд;
    public final String твор;
    public final Род род;

    Кто(String винЕд, String родЕд, String родМн, String имЕд, String твор, Род род) {
        this.винЕд = винЕд;
        this.родЕд = родЕд;
        this.родМн = родМн;
        this.имЕд = имЕд;
        this.твор = твор;
        this.род = род;
    }

    /** Войско ли это (для «кем уничтожено» годятся только войска). */
    public boolean войско() {
        return this == ПЕХОТА || this == ТЕХНИКА || this == АВИАЦИЯ || this == ВЫШКА;
    }

    /** Подходит ли жетон под этот род. */
    public boolean подходит(Token t) {
        if (t instanceof UnitToken u) {
            return switch (this) {
                case ЖЕТОН, ВОЙСКО -> true;
                case НАЗЕМНОЕ -> u.type != UnitType.AIRCRAFT;
                case ПЕХОТА -> u.type == UnitType.INFANTRY;
                case ТЕХНИКА -> u.type == UnitType.VEHICLE;
                case АВИАЦИЯ -> u.type == UnitType.AIRCRAFT;
                case ВЫШКА -> u.type == UnitType.TOWER;
                default -> false;
            };
        }
        if (t instanceof BuildingToken b) {
            return switch (this) {
                case ЖЕТОН, ЗДАНИЕ -> true;
                case ВОЕННОЕ -> b.type == BuildingType.BARRACKS || b.type == BuildingType.FACTORY
                    || b.type == BuildingType.AIRBASE;
                case ДОБЫТЧИК -> b.type == BuildingType.MINER;
                case ЭНЕРГОСТАНЦИЯ -> b.type == BuildingType.POWER_PLANT;
                case КАЗАРМА -> b.type == BuildingType.BARRACKS;
                case ЗАВОД -> b.type == BuildingType.FACTORY;
                case АВИАБАЗА -> b.type == BuildingType.AIRBASE;
                case ЦУ -> b.type == BuildingType.COMMAND_CENTER;
                default -> false;
            };
        }
        return false;
    }

    /** Подходит ли код рода из записи боя ({@code infantry}, {@code minerL2}…). */
    public boolean подходитКод(String код, boolean здание) {
        if (код == null) {
            return false;
        }
        return switch (this) {
            case ЖЕТОН -> true;
            case ВОЙСКО -> !здание;
            case НАЗЕМНОЕ -> !здание && !код.startsWith("aircraft");
            case ПЕХОТА -> код.startsWith("infantry");
            case ТЕХНИКА -> код.startsWith("vehicle");
            case АВИАЦИЯ -> код.startsWith("aircraft");
            case ВЫШКА -> код.startsWith("tower");
            case ЗДАНИЕ -> здание;
            case ВОЕННОЕ -> код.startsWith("barracks") || код.startsWith("factory")
                || код.startsWith("airbase");
            case ДОБЫТЧИК -> код.startsWith("miner");
            case ЭНЕРГОСТАНЦИЯ -> код.startsWith("power_plant") || код.startsWith("plant");
            case КАЗАРМА -> код.startsWith("barracks");
            case ЗАВОД -> код.startsWith("factory");
            case АВИАБАЗА -> код.startsWith("airbase");
            case ЦУ -> код.startsWith("command_center") || код.startsWith("cu");
        };
    }

    /** Код войска в записи боя ({@code infantry}…); {@code null} у зданий. */
    public String кодВойска() {
        return switch (this) {
            case ПЕХОТА -> "infantry";
            case ТЕХНИКА -> "vehicle";
            case АВИАЦИЯ -> "aircraft";
            case ВЫШКА -> "tower";
            default -> null;
        };
    }
}
