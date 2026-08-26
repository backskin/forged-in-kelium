package kelium.report;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Textures — КАРТИНКИ ЖЕТОНОВ вместо векторных силуэтов, когда они нарисованы.
 *
 * <p>Правило простое: нашлась картинка — рисуем её; не нашлась — рисуем базовую
 * форму, как раньше. Поэтому можно принести один файл и посмотреть на него, ничего
 * больше не меняя.
 *
 * <p><b>ОТПЕЧАТКИ ЗАГОТОВОК.</b> Заготовки ({@link TextureStubs}) лежат в той же
 * папке и по тем же именам, что настоящие текстуры — иначе непонятно, по чему
 * рисовать. Но заготовка это ещё не текстура: если её оставили как есть, показывать
 * её вместо силуэта нельзя, получится хуже, чем было. Поэтому при раскладке
 * заготовок пишется список их отпечатков, и здесь картинка сверяется с ним:
 * совпал отпечаток — файл считается НЕТРОНУТОЙ заготовкой и не грузится.
 *
 * <p>Отпечаток берётся с ПИКСЕЛЕЙ, а не с байтов файла: тогда простое
 * перезакрытие картинки в редакторе (другая упаковка, другие метаданные) не
 * притворится работой художника, а любое изменение рисунка — притворится не может.
 *
 * <p>Поиск идёт от самого точного имени к общему:
 * {@code <код>_l<уровень>_p<место>} → {@code <код>_p<место>} →
 * {@code <код>_l<уровень>} → {@code <код>}.
 */
public final class Textures {

    private Textures() {
    }

    /** Имя файла со отпечатками заготовок. */
    public static final String MANIFEST = "_заготовки.txt";

    private static Path root;
    private static final Map<String, BufferedImage> CACHE = new HashMap<>();
    private static Map<String, String> stubPrints = new LinkedHashMap<>();
    private static boolean ready;
    /** Сколько картинок отброшено как нетронутые заготовки — для отчёта в логе. */
    private static int skipped;

    /** Папка с текстурами; по умолчанию {@code <данные игры>/textures}. */
    public static synchronized void useFolder(Path folder) {
        root = folder;
        CACHE.clear();
        stubPrints = new LinkedHashMap<>();
        ready = false;
        skipped = 0;
        Zones.forget();
    }

    private static synchronized void init() {
        if (ready) {
            return;
        }
        ready = true;
        if (root == null) {
            try {
                root = kelium.dataio.GameConfig.resolveDataRoot(null).resolve("textures");
            } catch (RuntimeException e) {
                root = null;
                return;
            }
        }
        Path list = root.resolve(MANIFEST);
        if (!Files.isReadable(list)) {
            return;                    // списка нет — грузим всё, что лежит
        }
        try {
            for (String line : Files.readAllLines(list, StandardCharsets.UTF_8)) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#")) {
                    continue;
                }
                int sp = s.lastIndexOf(' ');
                if (sp > 0) {
                    stubPrints.put(s.substring(0, sp).trim(), s.substring(sp + 1).trim());
                }
            }
        } catch (IOException e) {
            // список не прочитался — не беда, просто грузим всё
        }
    }

    /**
     * Текстура здания: пробуем имена от точного к общему.
     *
     * @param type  код типа здания, например {@code command_center}
     * @param level уровень (добытчик, энергостанция) или null
     * @param seat  номер места 0..3
     */
    public static BufferedImage building(String type, Integer level, int seat) {
        return find(names(type, level, seat));
    }

    /** Текстура войска: те же правила, уровня у войск нет. */
    public static BufferedImage unit(String type, int seat) {
        return find(names(type, null, seat));
    }

    /**
     * ТЕКСТУРА ЭЛЕМЕНТА ПОЛЯ — картона, а не жетона: сам гекс, запретный гекс, тайл
     * зарождения, нейтральная постройка, печатная ячейка контейнера. Лежат в папке
     * {@code field/} и не зависят от места игрока.
     *
     * <p>Ключи: {@code hex}, {@code hex_forbidden}, {@code spawn}, {@code spawn_start},
     * {@code spawn_flipped}, {@code spawn_start_flipped}, {@code neutral_big},
     * {@code neutral_small}, {@code container}.
     */
    public static BufferedImage field(String... keys) {
        List<String> list = new ArrayList<>(keys.length);
        for (String k : keys) {
            list.add("field/" + k);
        }
        return find(list);
    }

    /**
     * ТЕКСТУРА КАРТЫ — рубашка колоды. Лежат в папке {@code card/}.
     *
     * <p>Отдельная папка, а не {@code field/}: рубашка не картон поля и не жетон,
     * а третий вид вещи на столе, и у неё своя форма — прямоугольник со скруглением,
     * а не гекс.
     *
     * <p>Ключи: {@code deck_objectives}, {@code deck_arsenal},
     * {@code deck_containers}, {@code deck_orders}, {@code deck_market},
     * {@code deck_super_objectives}, плюс общая {@code deck} на случай, когда своей
     * рубашки для набора нет. Ищется от точного к общему — как у жетонов.
     */
    public static BufferedImage card(String... keys) {
        List<String> list = new ArrayList<>(keys.length);
        for (String k : keys) {
            list.add("card/" + k);
        }
        return find(list);
    }

    /**
     * ПЕЧАТНЫЙ ПЛАНШЕТ ИГРОКА — картинка настоящего компонента со стола. Лежат в
     * папке {@code board/}: {@code troop-A} (планшет войск), {@code storage-A}
     * (планшет хранилища). Ключ — вид планшета и сторона.
     *
     * <p>Куда на этой картинке игра кладёт живое (кубики склада, жетоны модулей),
     * записано рядом в {@code board/anchors.yaml} — он сгенерирован по самой
     * картинке и сверен с {@code data/boards}. Нет картинки своей стороны —
     * планшет рисуется прежним рисованным видом, партия от этого не зависит.
     */
    public static BufferedImage board(String... keys) {
        List<String> list = new ArrayList<>(keys.length);
        for (String k : keys) {
            list.add("board/" + k);
        }
        return find(list);
    }

    /** Имена-кандидаты в порядке убывания точности. */
    public static List<String> names(String type, Integer level, int seat) {
        List<String> out = new ArrayList<>(4);
        int p = Math.floorMod(seat, 4) + 1;
        if (level != null) {
            out.add(type + "_l" + level + "_p" + p);
            out.add(type + "_p" + p);
            out.add(type + "_l" + level);
        } else {
            out.add(type + "_p" + p);
        }
        out.add(type);
        return out;
    }

    /** Найденная картинка вместе с ключом: по ключу ищется разметка зон. */
    public record Found(BufferedImage image, String key) {
    }

    /** То же, что {@link #find}, но с ключом — он нужен для поиска маски зон. */
    public static synchronized Found found(String type, Integer level, int seat) {
        for (String key : names(type, level, seat)) {
            BufferedImage img = find(List.of(key));
            if (img != null) {
                return new Found(img, key);
            }
        }
        return null;
    }

    /** Папка с текстурами — по ней ищется и разметка зон. */
    public static synchronized Path folder() {
        init();
        return root;
    }

    /**
     * Это ещё нетронутая заготовка? Тем же правилом проверяются и МАСКИ ЗОН: пока
     * художник не правил разметку, приложение работает по-старому.
     *
     * @param fileKey имя вместе с расширением, как оно записано в списке отпечатков
     */
    public static synchronized boolean isUntouchedStub(String fileKey, BufferedImage img) {
        init();
        String print = stubPrints.get(fileKey);
        return print != null && print.equals(fingerprint(img));
    }

    /** Первая существующая и НЕ ЗАГОТОВОЧНАЯ картинка из списка имён. */
    public static synchronized BufferedImage find(List<String> keys) {
        init();
        if (root == null) {
            return null;
        }
        for (String key : keys) {
            if (CACHE.containsKey(key)) {
                BufferedImage got = CACHE.get(key);
                if (got != null) {
                    return got;
                }
                continue;
            }
            BufferedImage img = load(key);
            CACHE.put(key, img);
            if (img != null) {
                return img;
            }
        }
        return null;
    }

    private static BufferedImage load(String key) {
        // Ключ может нести подпапку: «field/hex» ищется в field/, остальное в token/.
        Path file = key.contains("/")
            ? root.resolve(key.replace('/', java.io.File.separatorChar) + ".png")
            : root.resolve("token").resolve(key + ".png");
        if (!Files.isReadable(file)) {
            // Сохранение в BMP дизайнеру тоже разрешено — читаем и его
            Path bmp = Path.of(file.toString().replaceAll("\\.png$", ".bmp"));
            if (!Files.isReadable(bmp)) {
                return null;
            }
            file = bmp;
        }
        try {
            BufferedImage img = ImageIO.read(file.toFile());
            if (img == null) {
                return null;
            }
            String stub = stubPrints.get(key + ".png");
            if (stub != null && stub.equals(fingerprint(img))) {
                skipped++;             // это нетронутая заготовка, а не текстура
                return null;
            }
            return img;
        } catch (IOException e) {
            return null;               // битый файл не должен ломать показ партии
        }
    }

    /**
     * ОТПЕЧАТОК КАРТИНКИ по пикселям: размеры плюс все точки в ARGB. Байты файла не
     * годятся — редактор перепакует PNG, и нетронутая заготовка сойдёт за работу.
     */
    public static String fingerprint(BufferedImage img) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            int w = img.getWidth();
            int h = img.getHeight();
            md.update(new byte[]{(byte) (w >> 8), (byte) w, (byte) (h >> 8), (byte) h});
            int[] row = new int[w];
            byte[] buf = new byte[w * 4];
            for (int y = 0; y < h; y++) {
                img.getRGB(0, y, w, 1, row, 0, w);
                for (int x = 0; x < w; x++) {
                    int v = row[x];
                    buf[x * 4] = (byte) (v >>> 24);
                    buf[x * 4 + 1] = (byte) (v >>> 16);
                    buf[x * 4 + 2] = (byte) (v >>> 8);
                    buf[x * 4 + 3] = (byte) v;
                }
                md.update(buf);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            return "";
        }
    }

    /** Сколько файлов отброшено как нетронутые заготовки (для строки состояния). */
    public static synchronized int skippedStubs() {
        return skipped;
    }

    /** Сколько текстур реально взято в работу. */
    public static synchronized int loaded() {
        int n = 0;
        for (BufferedImage v : CACHE.values()) {
            if (v != null) {
                n++;
            }
        }
        return n;
    }
}
