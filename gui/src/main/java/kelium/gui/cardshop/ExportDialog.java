package kelium.gui.cardshop;

import java.awt.BorderLayout;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;

import net.miginfocom.swing.MigLayout;

/**
 * ВЫПУСК ГРУППЫ (дизайнер 06.10.2026): только текущий тип карт, в окне —
 * поштучно (NN — имя.png) или полотном: сетка карт без отступов, столбцов ×
 * строк; карт больше, чем мест, — несколько полотен. Рисуется в фоне, ход —
 * на полосе; окно мастерской не замирает.
 */
final class ExportDialog extends JDialog {
    private static final long serialVersionUID = 1L;

    ExportDialog(Window owner, CardAssets assets, CardSpec.Type type, List<CardSpec> cards, File defDir) {
        super(owner, "Выпуск: " + type.ru + " (" + cards.size() + ")", ModalityType.MODELESS);
        JPanel p = new JPanel(new MigLayout("insets 16, fillx, wrap 2, gapy 8", "[150!]10[grow,fill]", ""));
        p.setBackground(Style.BG);
        JRadioButton single = new JRadioButton("поштучно — каждая карта своим файлом", true);
        JRadioButton sheet = new JRadioButton("полотно — карты сеткой в одном файле");
        ButtonGroup g = new ButtonGroup();
        g.add(single);
        g.add(sheet);
        p.add(new JLabel("Как выпустить"));
        p.add(single);
        p.add(new JLabel(""));
        p.add(sheet);
        int cols0 = type.layout == CardSpec.Layout.ARSENAL || type.layout == CardSpec.Layout.MARKET ? 7 : 10;
        JSpinner cols = new JSpinner(new SpinnerNumberModel(cols0, 1, 30, 1));
        JSpinner rows = new JSpinner(new SpinnerNumberModel(Math.max(1, (int) Math.ceil(cards.size() / (double) cols0)), 1, 30, 1));
        JLabel fit = new JLabel();
        Runnable upd = () -> {
            int n = (Integer) cols.getValue() * (Integer) rows.getValue();
            int files = (int) Math.ceil(cards.size() / (double) n);
            fit.setText("мест на полотне: " + n + " · полотен: " + files);
            cols.setEnabled(sheet.isSelected());
            rows.setEnabled(sheet.isSelected());
        };
        cols.addChangeListener(e -> upd.run());
        rows.addChangeListener(e -> upd.run());
        single.addActionListener(e -> upd.run());
        sheet.addActionListener(e -> upd.run());
        JPanel cr = new JPanel(new MigLayout("insets 0", "[]6[70!]16[]6[70!]", ""));
        cr.setOpaque(false);
        cr.add(new JLabel("столбцов"));
        cr.add(cols);
        cr.add(new JLabel("строк"));
        cr.add(rows);
        p.add(new JLabel("Полотно"));
        p.add(cr);
        p.add(new JLabel(""));
        p.add(fit);
        JSpinner scale = new JSpinner(new SpinnerNumberModel(100, 10, 100, 5));
        p.add(new JLabel("Размер карт, %"));
        p.add(scale, "growx 0, w 80!");
        JCheckBox back = new JCheckBox("и рубашку отдельным файлом");
        p.add(new JLabel(""));
        p.add(back);
        JTextField dir = new JTextField(defDir.getPath());
        JButton pick = new JButton("…");
        pick.addActionListener(e -> {
            JFileChooser ch = new JFileChooser(dir.getText());
            ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (ch.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                dir.setText(ch.getSelectedFile().getPath());
            }
        });
        p.add(new JLabel("Папка"));
        p.add(dir, "split 2, growx");
        p.add(pick, "growx 0");
        JProgressBar bar = new JProgressBar(0, Math.max(1, cards.size()));
        bar.setStringPainted(true);
        bar.setString("");
        JLabel result = new JLabel(" ");
        result.setForeground(Style.INK2);
        JButton go = new JButton("Выпустить");
        go.setBackground(Style.ACCENT);
        go.addActionListener(e -> {
            go.setEnabled(false);
            File out = new File(dir.getText());
            out.mkdirs();
            run(assets, type, cards, out, sheet.isSelected(), (Integer) cols.getValue(),
                (Integer) rows.getValue(), (Integer) scale.getValue() / 100.0, back.isSelected(), bar, result, go);
        });
        p.add(bar, "span 2, growx");
        p.add(result, "span 2, growx");
        p.add(go, "span 2, right");
        upd.run();
        setContentPane(p);
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(owner);
        getRootPane().setBorder(BorderFactory.createLineBorder(Style.LINE));
    }

    private static String fileName(int i, CardSpec c) {
        String nm = Library.title(c).replaceAll("[\\\\/:*?\"<>|]", " ").trim();
        if (nm.length() > 60) {
            nm = nm.substring(0, 60);
        }
        return String.format("%02d", i + 1) + (nm.isEmpty() ? "" : " — " + nm) + ".png";
    }

    private void run(CardAssets assets, CardSpec.Type type, List<CardSpec> cards, File out, boolean asSheet,
                     int cols, int rows, double scale, boolean back, JProgressBar bar, JLabel result, JButton go) {
        new SwingWorker<String, Integer>() {
            @Override protected String doInBackground() throws Exception {
                List<String> bad = new ArrayList<>();
                int per = cols * rows;
                BufferedImage canvas = null;
                int cw = 0;
                int ch = 0;
                int sheetNo = 0;
                for (int i = 0; i < cards.size(); i++) {
                    BufferedImage im;
                    try {
                        im = CardRender.render(assets, cards.get(i));
                        if (scale < 1) {
                            im = CardAssets.scale(im, (int) Math.round(im.getWidth() * scale),
                                (int) Math.round(im.getHeight() * scale));
                        }
                    } catch (Exception e) {
                        bad.add(String.format("%02d", i + 1) + ": " + e.getMessage());
                        publish(i + 1);
                        continue;
                    }
                    if (!asSheet) {
                        ImageIO.write(im, "png", new File(out, fileName(i, cards.get(i))));
                    } else {
                        int slot = i % per;
                        if (slot == 0) {
                            cw = im.getWidth();
                            ch = im.getHeight();
                            int left = Math.min(per, cards.size() - i);
                            int usedRows = (int) Math.ceil(left / (double) cols);
                            int usedCols = Math.min(cols, left);
                            canvas = new BufferedImage(cw * usedCols, ch * usedRows, BufferedImage.TYPE_INT_ARGB);
                        }
                        Graphics2D g = canvas.createGraphics();
                        g.drawImage(im, (slot % cols) * cw, (slot / cols) * ch, cw, ch, null);
                        g.dispose();
                        if (slot == per - 1 || i == cards.size() - 1) {
                            sheetNo++;
                            ImageIO.write(canvas, "png", new File(out, type.ru + " — полотно " + sheetNo + ".png"));
                        }
                    }
                    publish(i + 1);
                }
                if (back) {
                    BufferedImage b = assets.back(type);
                    if (b != null) {
                        ImageIO.write(b, "png", new File(out, type.ru + " — рубашка.png"));
                    }
                }
                int ok = cards.size() - bad.size();
                return (asSheet ? "Полотен: " + sheetNo + ", карт " + ok : "Карт: " + ok) + " из " + cards.size()
                    + " → " + out + (bad.isEmpty() ? "" : " · не нарисовались: " + String.join("; ", bad));
            }

            @Override protected void process(List<Integer> done) {
                int n = done.get(done.size() - 1);
                bar.setValue(n);
                bar.setString(n + " из " + cards.size());
            }

            @Override protected void done() {
                go.setEnabled(true);
                try {
                    result.setText(get());
                } catch (Exception e) {
                    result.setText("Выпуск прервался: " + e.getMessage());
                }
            }
        }.execute();
    }
}
