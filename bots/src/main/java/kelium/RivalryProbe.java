package kelium;

/**
 * Латинское имя для запуска {@link ЗамерСоперничества} из командной строки
 * Windows: кириллица в имени класса и в аргументах теряется по дороге к java.
 * Режимы латиницей: {@code new} (новые), {@code old} (прежние), {@code duel}
 * (турнир новых против прежних).
 */
public final class RivalryProbe {

    private RivalryProbe() {
    }

    public static void main(String[] args) throws Exception {
        String[] a = args.clone();
        if (a.length > 0) {
            a[0] = switch (a[0]) {
                case "new" -> "новые";
                case "old" -> "прежние";
                case "duel" -> "турнир";
                default -> a[0];
            };
        }
        ЗамерСоперничества.main(a);
    }
}
