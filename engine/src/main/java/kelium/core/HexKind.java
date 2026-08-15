package kelium.core;

/** Тип гекса на поле: обычный, грядка (зарождение), запрещённый или стартовый. */
public enum HexKind {
    NORMAL("normal"),        // обычный (тайл зарождения — ОТДЕЛЬНЫЙ жетон Hex.spawnTile)
    FORBIDDEN("forbidden"),  // блокирует движение/стройку
    START("start");          // стартовый гекс игрока (сюда ставится ЦУ)

    public final String code;

    HexKind(String code) {
        this.code = code;
    }
}
