package kelium.gui.cardshop;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/** FlowLayout, который переносит ряды и честно сообщает свою высоту (для прокрутки). */
final class WrapLayout extends FlowLayout {
    private static final long serialVersionUID = 1L;

    WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return size(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension d = size(target, false);
        d.width -= getHgap() + 1;
        return d;
    }

    private Dimension size(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getSize().width;
            Container c = target;
            while (c.getSize().width == 0 && c.getParent() != null) {
                c = c.getParent();
            }
            targetWidth = c.getSize().width;
            if (targetWidth == 0) {
                targetWidth = Integer.MAX_VALUE;
            }
            Insets in = target.getInsets();
            int maxWidth = targetWidth - (in.left + in.right + getHgap() * 2);
            Dimension dim = new Dimension(0, 0);
            int rowW = 0;
            int rowH = 0;
            for (Component m : target.getComponents()) {
                if (!m.isVisible()) {
                    continue;
                }
                Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                if (rowW + d.width > maxWidth) {
                    dim.width = Math.max(dim.width, rowW);
                    dim.height += rowH + getVgap();
                    rowW = 0;
                    rowH = 0;
                }
                if (rowW != 0) {
                    rowW += getHgap();
                }
                rowW += d.width;
                rowH = Math.max(rowH, d.height);
            }
            dim.width = Math.max(dim.width, rowW);
            dim.height += rowH;
            dim.width += in.left + in.right + getHgap() * 2;
            dim.height += in.top + in.bottom + getVgap() * 2;
            Container sp = SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
            if (sp != null && target.isValid()) {
                dim.width -= getHgap() + 1;
            }
            return dim;
        }
    }
}
