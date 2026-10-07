// ЗАПУСКАТЕЛЬ ПРИЛОЖЕНИЯ — маленький exe без данных внутри (сборка 26.09.2026).
//
// Прежде каждый exe был самораспаковывающимся архивом на 390 МБ: Java, код,
// данные и картинки лежали в каждом из пяти exe, и любая правка перепаковывала
// всё. Теперь exe — только запускатель, а содержимое лежит рядом, в папке
// packs, отдельными паками:
//
//   runtime.pak   Java-среда           code.pak      код игры
//   libs.pak      сторонние библиотеки rules.pak     правила, карты, книга
//   textures.pak  картинки             packs.txt     список: имя, отпечаток, файл
//
// Каждый пак распаковывается ОДИН РАЗ в %LOCALAPPDATA%\Kelium\<имя>-<отпечаток>.
// Пересобрали пак — у него новый отпечаток, распакуется только он; старые
// версии подчищаются. Плейсхолдеры @MAIN@, @APP@, @DEVDATA@ подставляет
// make-exe.ps1.
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Text;
using System.Threading;
using System.Windows.Forms;

static class Launcher
{
    const string MainClass = "@MAIN@";     // точка входа Java
    const string AppName = "@APP@";        // как называется приложение в сообщениях
    const string DevData = @"@DEVDATA@";   // папка data проекта на машине сборки
    const string Title = "Кристаллы Раздора";

    [STAThread]
    static int Main(string[] argv)
    {
        try
        {
            string exeDir = AppDomain.CurrentDomain.BaseDirectory;
            string packsDir = Path.Combine(exeDir, "packs");
            string list = Path.Combine(packsDir, "packs.txt");
            if (!File.Exists(list))
            {
                throw new FileNotFoundException("рядом с программой нет папки packs со списком паков ("
                    + list + "). Скопируй папку packs вместе с exe.");
            }
            // АВТООБНОВЛЕНИЕ — до распаковки: изменившиеся паки докачиваются, и
            // дальше всё идёт как обычно. На машине сборки не обновляемся: там
            // свежая сборка, а на Диске — прошлая выкладка.
            if (!Directory.Exists(DevData) || Environment.GetEnvironmentVariable("KELIUM_UPDATE") == "1")
            {
                Updater.Run(packsDir, list, Title);
            }
            string cacheRoot = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Kelium");
            Directory.CreateDirectory(cacheRoot);

            // что распаковать: имя → папка в кэше
            Dictionary<string, string> dirs = new Dictionary<string, string>();
            List<string[]> todo = new List<string[]>();     // {имя, файл, папка}
            foreach (string line in File.ReadAllLines(list, Encoding.UTF8))
            {
                string[] f = line.Split('\t');
                if (f.Length < 3 || line.StartsWith("#"))
                {
                    continue;
                }
                string dir = Path.Combine(cacheRoot, f[0] + "-" + f[1]);
                dirs[f[0]] = dir;
                if (!File.Exists(Path.Combine(dir, ".ready")))
                {
                    todo.Add(new string[] { f[0], Path.Combine(packsDir, f[2]), dir });
                }
            }
            foreach (string need in new string[] { "runtime", "code", "libs" })
            {
                if (!dirs.ContainsKey(need))
                {
                    throw new InvalidDataException("в списке паков нет «" + need + "»");
                }
            }
            if (todo.Count > 0)
            {
                ExtractWithProgress(todo);
            }
            CleanOld(cacheRoot, dirs);

            // НА МАШИНЕ СБОРКИ — ЖИВЫЕ ДАННЫЕ ПРОЕКТА: правка правил или карт видна
            // сразу, без пересборки. KELIUM_PACKS=1 заставляет брать паки и здесь.
            bool dev = Directory.Exists(DevData)
                && Environment.GetEnvironmentVariable("KELIUM_PACKS") != "1";
            StringBuilder args = new StringBuilder();
            // ПАМЯТЬ — ДО ПОЛОВИНЫ ОПЕРАТИВКИ (29.09.2026): живых данных партии
            // ~0,9 ГБ (печатные картинки в полном разрешении), а по умолчанию Java
            // берёт четверть — на машине с 4 ГБ это 1 ГБ, и игре тесно.
            args.Append("-XX:MaxRAMPercentage=50 ");
            if (dev)
            {
                args.Append(Q("-Dkelium.data=" + DevData)).Append(' ');
            }
            else
            {
                if (dirs.ContainsKey("rules"))
                {
                    args.Append(Q("-Dkelium.data=" + Path.Combine(dirs["rules"], "data"))).Append(' ');
                }
                if (dirs.ContainsKey("textures"))
                {
                    args.Append(Q("-Dkelium.textures=" + dirs["textures"])).Append(' ');
                }
            }
            args.Append("-cp ").Append(Q(Path.Combine(dirs["code"], "*") + ";"
                + Path.Combine(dirs["libs"], "*"))).Append(' ');
            args.Append(MainClass);
            foreach (string a in argv)
            {
                args.Append(' ').Append(Q(a));
            }

            string javaw = Path.Combine(dirs["runtime"], "bin", "javaw.exe");
            ProcessStartInfo psi = new ProcessStartInfo(javaw, args.ToString());
            psi.UseShellExecute = false;
            // Рабочая папка — та, где лежит САМ exe: отчёты и логи появляются рядом с ним.
            psi.WorkingDirectory = exeDir;
            psi.RedirectStandardError = true;
            Process p = Process.Start(psi);
            StringBuilder err = new StringBuilder();
            p.ErrorDataReceived += (s, e) => { if (e.Data != null) lock (err) { err.AppendLine(e.Data); } };
            p.BeginErrorReadLine();
            // Упала в первые секунды — показываем почему, а не молчим.
            if (p.WaitForExit(8000) && p.ExitCode != 0)
            {
                string text;
                lock (err) { text = err.ToString(); }
                if (text.Length > 1500)
                {
                    text = "…" + text.Substring(text.Length - 1500);
                }
                MessageBox.Show(AppName + " закрылось с ошибкой (код " + p.ExitCode + ").\n\n" + text,
                    Title, MessageBoxButtons.OK, MessageBoxIcon.Error);
                return p.ExitCode;
            }
            return 0;
        }
        catch (Exception e)
        {
            MessageBox.Show("Не удалось запустить " + AppName + ".\n\n" + e.Message,
                Title, MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }
    }

    static string Q(string s)
    {
        return s.IndexOf(' ') >= 0 || s.IndexOf(';') >= 0 ? "\"" + s + "\"" : s;
    }

    // ---------------- распаковка с окошком ----------------

    static void ExtractWithProgress(List<string[]> todo)
    {
        long total = 0;
        foreach (string[] t in todo)
        {
            using (ZipArchive z = ZipFile.OpenRead(t[1]))
            {
                foreach (ZipArchiveEntry e in z.Entries) { total += e.Length; }
            }
        }
        Form form = new Form();
        form.Text = Title;
        form.FormBorderStyle = FormBorderStyle.FixedDialog;
        form.StartPosition = FormStartPosition.CenterScreen;
        form.ClientSize = new Size(460, 96);
        form.MaximizeBox = false;
        form.MinimizeBox = false;
        form.ControlBox = false;
        Label label = new Label();
        label.SetBounds(16, 14, 428, 22);
        label.Text = "Первый запуск: распаковываю…";
        ProgressBar bar = new ProgressBar();
        bar.SetBounds(16, 44, 428, 22);
        bar.Maximum = 1000;
        form.Controls.Add(label);
        form.Controls.Add(bar);
        Exception failure = null;
        long done = 0;
        Thread worker = new Thread(() =>
        {
            try
            {
                foreach (string[] t in todo)
                {
                    string name = t[0];
                    form.BeginInvoke((Action)(() => label.Text = "Первый запуск: распаковываю «" + Human(name) + "»…"));
                    Extract(t[1], t[2], n =>
                    {
                        done += n;
                        int v = total <= 0 ? 0 : (int)(done * 1000 / total);
                        form.BeginInvoke((Action)(() => bar.Value = Math.Min(1000, v)));
                    });
                }
            }
            catch (Exception e) { failure = e; }
            form.BeginInvoke((Action)(() => form.Close()));
        });
        worker.IsBackground = true;
        form.Shown += (s, e) => worker.Start();
        Application.Run(form);
        worker.Join();
        if (failure != null)
        {
            throw failure;
        }
    }

    static string Human(string pack)
    {
        switch (pack)
        {
            case "runtime": return "Java";
            case "code": return "код игры";
            case "libs": return "библиотеки";
            case "rules": return "правила и карты";
            case "textures": return "картинки";
            default: return pack;
        }
    }

    // Распаковка «через сторону»: во временную папку, затем переименование —
    // одновременный запуск двух приложений не оставит недораспакованного.
    static void Extract(string pak, string dir, Action<long> progress)
    {
        if (!File.Exists(pak))
        {
            throw new FileNotFoundException("нет файла пака: " + pak);
        }
        string staging = dir + "." + Guid.NewGuid().ToString("N").Substring(0, 8) + ".tmp";
        Directory.CreateDirectory(staging);
        using (ZipArchive z = ZipFile.OpenRead(pak))
        {
            foreach (ZipArchiveEntry e in z.Entries)
            {
                string target = Path.GetFullPath(Path.Combine(staging, e.FullName));
                if (e.FullName.EndsWith("/"))
                {
                    Directory.CreateDirectory(target);
                    continue;
                }
                Directory.CreateDirectory(Path.GetDirectoryName(target));
                e.ExtractToFile(target, true);
                progress(e.Length);
            }
        }
        File.WriteAllText(Path.Combine(staging, ".ready"), "ok");
        try
        {
            Directory.Move(staging, dir);
        }
        catch (Exception)
        {
            try { Directory.Delete(staging, true); } catch (Exception) { }
        }
        for (int i = 0; i < 300 && !File.Exists(Path.Combine(dir, ".ready")); i++)
        {
            Thread.Sleep(100);
        }
        if (!File.Exists(Path.Combine(dir, ".ready")))
        {
            throw new IOException("распаковка не завершилась: " + dir);
        }
    }

    // Старые версии паков и распаковки прежних «толстых» exe (%TEMP%\Kelium-*,
    // по 2–3 ГБ каждая) — удаляем; занятые (идёт старая версия) просто пропускаем.
    static void CleanOld(string cacheRoot, Dictionary<string, string> current)
    {
        HashSet<string> keep = new HashSet<string>(current.Values, StringComparer.OrdinalIgnoreCase);
        try
        {
            foreach (string d in Directory.GetDirectories(cacheRoot))
            {
                string n = Path.GetFileName(d);
                foreach (string pack in current.Keys)
                {
                    if (n.StartsWith(pack + "-", StringComparison.OrdinalIgnoreCase) && !keep.Contains(d))
                    {
                        try { Directory.Delete(d, true); } catch (Exception) { }
                    }
                }
            }
            foreach (string d in Directory.GetDirectories(Path.GetTempPath(), "Kelium-*"))
            {
                try { Directory.Delete(d, true); } catch (Exception) { }
            }
        }
        catch (Exception) { }
    }
}

// АВТООБНОВЛЕНИЕ (29.09.2026). Рядом с паками лежит packs\update.txt — ссылка на
// раздачу: публичная папка Яндекс.Диска (https://disk.yandex.ru/d/…) или
// обычный адрес, где лежат packs/packs.txt и паки. При старте запускатель берёт
// оттуда список паков, сравнивает отпечатки со своим и докачивает только
// изменившиеся (обычно код и правила — единицы МБ). Скачанный пак проверяется
// по отпечатку (первые 12 знаков SHA-256). Нет сети, нет ссылки, что-то не так —
// молча играем тем, что есть.
static class Updater
{
    public static void Run(string packsDir, string list, string title)
    {
        string src = Path.Combine(packsDir, "update.txt");
        if (!File.Exists(src))
        {
            return;
        }
        string link = File.ReadAllText(src, Encoding.UTF8).Trim();
        if (link.Length == 0)
        {
            return;
        }
        try
        {
            System.Net.ServicePointManager.SecurityProtocol = (System.Net.SecurityProtocolType)3072; // TLS 1.2
            string remote = Encoding.UTF8.GetString(Fetch(link, "packs/packs.txt", 6000));
            Dictionary<string, string> local = Read(File.ReadAllLines(list, Encoding.UTF8));
            List<string[]> need = new List<string[]>();          // {файл, отпечаток}
            foreach (string line in remote.Split('\n'))
            {
                string[] f = line.TrimEnd('\r').Split('\t');
                if (f.Length < 3 || f[0].StartsWith("#"))
                {
                    continue;
                }
                string mine;
                bool have = local.TryGetValue(f[0], out mine) && mine == f[1]
                    && File.Exists(Path.Combine(packsDir, f[2]));
                if (!have)
                {
                    need.Add(new string[] { f[2], f[1] });
                }
            }
            if (need.Count == 0)
            {
                return;
            }
            Exception failure = null;
            Form form = new Form();
            form.Text = title;
            form.FormBorderStyle = FormBorderStyle.FixedDialog;
            form.StartPosition = FormStartPosition.CenterScreen;
            form.ClientSize = new Size(460, 96);
            form.MaximizeBox = false;
            form.MinimizeBox = false;
            form.ControlBox = false;
            Label label = new Label();
            label.SetBounds(16, 14, 428, 22);
            label.Text = "Обновление: скачиваю…";
            ProgressBar bar = new ProgressBar();
            bar.SetBounds(16, 44, 428, 22);
            bar.Maximum = 1000;
            form.Controls.Add(label);
            form.Controls.Add(bar);
            Thread worker = new Thread(() =>
            {
                try
                {
                    for (int i = 0; i < need.Count; i++)
                    {
                        string file = need[i][0];
                        string fp = need[i][1];
                        int n = i;
                        form.BeginInvoke((Action)(() => label.Text = "Обновление: " + file
                            + " (" + (n + 1) + " из " + need.Count + ")…"));
                        string part = Path.Combine(packsDir, file + ".part");
                        Download(link, "packs/" + file, part, v =>
                            form.BeginInvoke((Action)(() => bar.Value = Math.Max(0, Math.Min(1000, v)))));
                        if (Sha12(part) != fp)
                        {
                            File.Delete(part);
                            throw new IOException("скачанный " + file + " не сошёлся с отпечатком");
                        }
                        string dest = Path.Combine(packsDir, file);
                        if (File.Exists(dest))
                        {
                            File.Delete(dest);
                        }
                        File.Move(part, dest);
                        File.WriteAllText(dest + ".sha", fp, Encoding.ASCII);
                    }
                    // список — последним: пока все паки не на месте, остаётся старый
                    File.WriteAllText(list, remote, new UTF8Encoding(false));
                }
                catch (Exception e) { failure = e; }
                form.BeginInvoke((Action)(() => form.Close()));
            });
            worker.IsBackground = true;
            form.Shown += (s, e) => worker.Start();
            Application.Run(form);
            worker.Join();
            if (failure != null)
            {
                MessageBox.Show("Обновление не скачалось — запускаю прежнюю версию.\n\n" + failure.Message,
                    title, MessageBoxButtons.OK, MessageBoxIcon.Warning);
            }
        }
        catch (Exception)
        {
            // нет сети или ссылки — играем тем, что есть
        }
    }

    static Dictionary<string, string> Read(string[] lines)
    {
        Dictionary<string, string> m = new Dictionary<string, string>();
        foreach (string line in lines)
        {
            string[] f = line.Split('\t');
            if (f.Length >= 3 && !f[0].StartsWith("#"))
            {
                m[f[0]] = f[1];
            }
        }
        return m;
    }

    /** Прямая ссылка на файл раздачи: у Яндекс.Диска — через его открытый API. */
    static string Url(string link, string path)
    {
        if (link.IndexOf("disk.yandex", StringComparison.OrdinalIgnoreCase) >= 0
            || link.IndexOf("yadi.sk", StringComparison.OrdinalIgnoreCase) >= 0)
        {
            string api = "https://cloud-api.yandex.net/v1/disk/public/resources/download?public_key="
                + Uri.EscapeDataString(link) + "&path=" + Uri.EscapeDataString("/" + path);
            string json = Encoding.UTF8.GetString(Get(api, 6000));
            System.Text.RegularExpressions.Match m =
                System.Text.RegularExpressions.Regex.Match(json, "\"href\"\\s*:\\s*\"([^\"]+)\"");
            if (!m.Success)
            {
                throw new IOException("Диск не дал ссылку на " + path);
            }
            return m.Groups[1].Value.Replace("\\/", "/");
        }
        return link.TrimEnd('/') + "/" + path;
    }

    static byte[] Fetch(string link, string path, int timeoutMs)
    {
        return Get(Url(link, path), timeoutMs);
    }

    static byte[] Get(string url, int timeoutMs)
    {
        System.Net.HttpWebRequest rq = (System.Net.HttpWebRequest)System.Net.WebRequest.Create(url);
        rq.Timeout = timeoutMs;
        rq.ReadWriteTimeout = timeoutMs;
        rq.UserAgent = "Kelium-Launcher";
        using (System.Net.WebResponse rs = rq.GetResponse())
        using (Stream s = rs.GetResponseStream())
        using (MemoryStream ms = new MemoryStream())
        {
            s.CopyTo(ms);
            return ms.ToArray();
        }
    }

    static void Download(string link, string path, string to, Action<int> progress)
    {
        System.Net.HttpWebRequest rq = (System.Net.HttpWebRequest)System.Net.WebRequest.Create(
            Url(link, path));
        rq.Timeout = 15000;
        rq.ReadWriteTimeout = 30000;
        rq.UserAgent = "Kelium-Launcher";
        using (System.Net.WebResponse rs = rq.GetResponse())
        using (Stream s = rs.GetResponseStream())
        using (FileStream f = File.Create(to))
        {
            long total = rs.ContentLength;
            long done = 0;
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = s.Read(buf, 0, buf.Length)) > 0)
            {
                f.Write(buf, 0, n);
                done += n;
                if (total > 0)
                {
                    progress((int)(done * 1000 / total));
                }
            }
        }
    }

    static string Sha12(string file)
    {
        using (System.Security.Cryptography.SHA256 sha = System.Security.Cryptography.SHA256.Create())
        using (FileStream f = File.OpenRead(file))
        {
            return BitConverter.ToString(sha.ComputeHash(f)).Replace("-", "").Substring(0, 12).ToLower();
        }
    }
}
