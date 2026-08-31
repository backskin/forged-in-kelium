package kelium.core;

/**
 * ПАРТИЮ ЗАКРЫЛИ, НЕ ДОИГРАВ — игрок вышел из окна партии.
 *
 * <p>Это не ошибка: движок синхронный, и единственный честный способ снять его
 * с недоигранной партии — размотать поток из точки, где он ждёт ответа игрока
 * ({@link InteractiveAgent#choose}). Отдельный тип нужен, чтобы окно отличило
 * «игрок закрыл партию» от настоящей поломки и не показывало «партия прервана
 * ошибкой» там, где ошибки не было.
 */
public final class GameAborted extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GameAborted(String message) {
        super(message);
    }
}
