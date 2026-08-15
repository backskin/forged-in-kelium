// Шаблон самораспаковывающегося запускателя (компилируется csc.exe из состава
// Windows, см. make-exe.ps1). Внутрь exe зашивается payload.zip со всем
// приложением вместе с Java-средой.
//
// Логика: распаковать во временную папку с версией в имени (если ещё не
// распаковано) и запустить настоящий exe. Повторные запуски используют уже
// распакованное — поэтому долгим бывает только первый старт.
//
// Плейсхолдеры @VERSION@ и @TARGET@ подставляет сборочный скрипт.
using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Text;
using System.Threading;
using System.Windows.Forms;

static class Sfx
{
    const string Version = "@VERSION@";   // отпечаток содержимого: меняется — меняется папка
    const string Target = "@TARGET@";     // какой exe запускать после распаковки
    const string Title = "Кристаллы Раздора";

    [STAThread]
    static int Main(string[] argv)
    {
        try
        {
            string baseDir = Path.Combine(Path.GetTempPath(), "Kelium-" + Version);
            string ready = Path.Combine(baseDir, ".ready");

            if (!File.Exists(ready))
            {
                Extract(baseDir, ready);
            }

            string exe = Path.Combine(baseDir, Target);
            if (!File.Exists(exe))
            {
                throw new FileNotFoundException("после распаковки не найден " + Target);
            }

            ProcessStartInfo psi = new ProcessStartInfo(exe);
            psi.UseShellExecute = false;
            // Рабочая папка — та, где лежит САМ этот exe: отчёты и логи должны
            // появляться рядом с ним, а не во временной папке.
            psi.WorkingDirectory = AppDomain.CurrentDomain.BaseDirectory;
            if (argv.Length > 0)
            {
                psi.Arguments = Join(argv);
            }
            Process p = Process.Start(psi);
            p.WaitForExit();
            return p.ExitCode;
        }
        catch (Exception e)
        {
            MessageBox.Show("Не удалось запустить приложение.\n\n" + e.Message
                + "\n\nПопробуй освободить место на диске или запустить от своего пользователя.",
                Title, MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }
    }

    // Распаковка «через сторону»: сначала во временную папку со случайным
    // именем, затем переименование. Так одновременный запуск двух приложений
    // не порождает недораспакованного каталога.
    static void Extract(string baseDir, string ready)
    {
        string staging = baseDir + "." + Guid.NewGuid().ToString("N").Substring(0, 8) + ".tmp";
        Assembly self = Assembly.GetExecutingAssembly();
        using (Stream s = self.GetManifestResourceStream("payload.zip"))
        {
            if (s == null)
            {
                throw new InvalidOperationException("внутри exe нет вложенного архива");
            }
            using (ZipArchive z = new ZipArchive(s, ZipArchiveMode.Read))
            {
                z.ExtractToDirectory(staging);
            }
        }
        File.WriteAllText(Path.Combine(staging, ".ready"), Version);
        try
        {
            Directory.Move(staging, baseDir);
        }
        catch (Exception)
        {
            // Кто-то распаковал ту же версию первым — просто выбрасываем свою копию.
            try { Directory.Delete(staging, true); }
            catch (Exception) { }
        }
        // Дождаться, пока победитель гонки допишет маркер готовности.
        for (int i = 0; i < 300 && !File.Exists(ready); i++)
        {
            Thread.Sleep(100);
        }
        if (!File.Exists(ready))
        {
            throw new IOException("распаковка не завершилась: " + baseDir);
        }
    }

    static string Join(string[] argv)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < argv.Length; i++)
        {
            if (i > 0)
            {
                sb.Append(' ');
            }
            if (argv[i].IndexOf(' ') >= 0)
            {
                sb.Append('"').Append(argv[i]).Append('"');
            }
            else
            {
                sb.Append(argv[i]);
            }
        }
        return sb.ToString();
    }
}
