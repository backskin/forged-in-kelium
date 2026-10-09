package kelium;

/**
 * Латинская точка входа обучения стратега — для кнопки «Обучение ботов.cmd»:
 * кириллица в командной строке Windows ненадёжна. Всё делает {@link ЦиклСтратега}.
 */
public final class TrainStrateg {

    private TrainStrateg() {
    }

    public static void main(String[] args) throws Exception {
        ЦиклСтратега.main(args);
    }
}
