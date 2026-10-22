package kelium;

/**
 * Ярлык запуска цикла AlphaZero латинским именем: командная строка Windows
 * портит кириллицу в имени класса. Всё делает {@link ЦиклAZ}.
 */
public final class TrainAZ {

    private TrainAZ() {
    }

    public static void main(String[] args) throws Exception {
        ЦиклAZ.main(args);
    }
}
