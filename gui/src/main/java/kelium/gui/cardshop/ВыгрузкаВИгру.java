package kelium.gui.cardshop;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * ВЫГРУЗКА КАРТ МАСТЕРСКОЙ В КОЛОДЫ ИГРЫ: все {@code .kcard} папки (с
 * подпапками) → {@code мастерская — задания.yaml} и {@code мастерская —
 * арсенал.yaml} в формате колод {@code data/cards}. Задание — язык карт
 * (требование-узор), арсенал — верх и свойство с условием-фигурой.
 *
 * <pre>java -cp kelium-runner.jar kelium.gui.cardshop.ВыгрузкаВИгру &lt;папка&gt; [куда]</pre>
 */
public final class ВыгрузкаВИгру {

    private ВыгрузкаВИгру() {
    }

    /** Итог выгрузки: сколько карт и что не так. */
    public record Итог(int заданий, int арсенала, List<String> замечания, File задания, File арсенал) {
    }

    public static Итог выгрузить(File папка, File куда) throws Exception {
        List<File> файлы = new ArrayList<>();
        собрать(папка, файлы);
        List<Object> obj = new ArrayList<>();
        List<Object> ars = new ArrayList<>();
        List<String> замечания = new ArrayList<>();
        for (File f : файлы) {
            CardSpec c = CardSpec.load(f);
            List<String> warn = new ArrayList<>();
            String номер = c.text("номер").isBlank() ? String.valueOf(obj.size() + ars.size() + 1)
                : c.text("номер");
            switch (c.type().layout) {
                case OBJECTIVE -> {
                    Map<String, Object> язык = GameEntry.objective(c, warn);
                    if (язык != null) {
                        Map<String, Object> e = new LinkedHashMap<>();
                        e.put("id", "m_" + номер);
                        e.put("язык", язык);
                        obj.add(e);
                    }
                }
                case ARSENAL -> ars.add(GameEntry.arsenal(c, "ma_" + номер, warn));
                default -> warn.add("тип «" + c.type().ru + "» в колоды пока не выгружается");
            }
            for (String w : warn) {
                замечания.add(f.getName() + ": " + w);
            }
        }
        куда.mkdirs();
        File fo = new File(куда, "мастерская — задания.yaml");
        File fa = new File(куда, "мастерская — арсенал.yaml");
        записать(fo, "objectives", obj);
        записать(fa, "arsenal", ars);
        return new Итог(obj.size(), ars.size(), замечания, fo, fa);
    }

    private static void собрать(File d, List<File> out) {
        File[] fs = d.listFiles();
        if (fs == null) {
            return;
        }
        for (File f : fs) {
            if (f.isDirectory()) {
                собрать(f, out);
            } else if (f.getName().endsWith(".kcard")) {
                out.add(f);
            }
        }
    }

    private static void записать(File f, String тип, List<Object> карты) throws Exception {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("meta", Map.of("id", "мастерская", "type", тип));
        doc.put(тип, карты);
        DumperOptions o = new DumperOptions();
        o.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        o.setAllowUnicode(true);
        o.setWidth(160);
        Files.writeString(f.toPath(), "# Карты мастерской, выгружены " + java.time.LocalDate.now()
            + "\n" + new Yaml(o).dump(doc), StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        File in = new File(args[0]);
        File out = args.length > 1 ? new File(args[1]) : in;
        Итог и = выгрузить(in, out);
        System.out.println("заданий: " + и.заданий() + ", арсенала: " + и.арсенала() + " → " + out);
        for (String з : и.замечания()) {
            System.out.println("  " + з);
        }
    }
}
