package com.github.manolo8.darkbot.gui.titlebar;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.core.itf.ExtraMenuProvider;
import com.github.manolo8.darkbot.core.utils.Lazy;
import com.github.manolo8.darkbot.extensions.features.Feature;
import com.github.manolo8.darkbot.gui.utils.PopupMenuListenerAdapter;
import com.github.manolo8.darkbot.gui.utils.TitledSeparator;
import com.github.manolo8.darkbot.gui.utils.UIUtils;
import com.github.manolo8.darkbot.utils.I18n;
import com.github.manolo8.darkbot.utils.SystemUtils;
import com.github.manolo8.darkbot.utils.debug.SWFUtils;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.PopupMenuEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExtraButton extends TitleBarToggleButton<JFrame> {

    private final Main main;
    private final JPopupMenu extraOptions = new JPopupMenu("Extra Options");

    private static final Set<ExtraMenuProvider> EXTRA_DECORATIONS = new LinkedHashSet<>();
    private static final Lazy<Void> clean = new Lazy.NoCache<>();


    public static void register(ExtraMenuProvider provider) {
        EXTRA_DECORATIONS.add(provider);
        clean.send(null);
    }

    public static void unregister(ExtraMenuProvider provider) {
        EXTRA_DECORATIONS.remove(provider);
        clean.send(null);
    }

    protected boolean empty = true;

    ExtraButton(Main main, JFrame frame) {
        super(UIUtils.getIcon("hamburger"), frame);

        this.main = main;

        extraOptions.setBorder(UIUtils.getBorder());
        extraOptions.addPopupMenuListener(new PopupMenuListenerAdapter() {
            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                setSelected(false);
            }
        });

        clean.add(v -> {
            extraOptions.removeAll();
            empty = true;
        });

    }

    private void rebuild(Main main) {
        if (!empty) return;
        for (ExtraMenuProvider extraDecoration : EXTRA_DECORATIONS) {
            for (JComponent component : extraDecoration.getExtraMenuItems(main)) {
                extraOptions.add(component);
            }
        }
        empty = false;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        rebuild(main);
        if (isSelected()) extraOptions.show(this, 0, getHeight() - 1);
    }

    @Feature(name = "Extra menu default provider", description = "Provides default extra buttons")
    public static class DefaultExtraMenuProvider implements ExtraMenuProvider {

        @Override
        public Collection<JComponent> getExtraMenuItems(Main main) {
            List<JComponent> list = new ArrayList<>();

            list.add(create("home", e -> {
                String sid = main.statsManager.sid, instance = main.statsManager.instance;
                if (sid == null || sid.isEmpty() || instance == null || instance.isEmpty()) return;
                String url = instance + "?dosid=" + sid;
                if ((e.getModifiers() & InputEvent.BUTTON3_MASK) != 0) SystemUtils.toClipboard(url);
                else SystemUtils.openUrl(url);
            }));
            list.add(create("reload", e -> {
                System.out.println("Triggering refresh: user requested");
                Main.API.handleRefresh();
            }));
            list.add(create("discord", () -> SystemUtils.openUrl("https://discord.gg/KFd8vZT")));
            list.add(create("copy_sid", () -> SystemUtils.toClipboard(main.statsManager.sid)));


            if (main.config.BOT_SETTINGS.DEV_STUFF) {
                list.add(new TitledSeparator("Dev stuff"));
                list.add(create("Save SWF", SWFUtils::dumpMainSWF));
            }

            return list;
        }


        private JMenuItem create(String key, Runnable listener) {
            return create(key, e -> listener.run());
        }

        private JMenuItem create(String key, ActionListener listener) {
            JMenuItem item = new JMenuItem(I18n.getOrDefault("gui.hamburger_button." + key, key));
            if (listener != null) item.addActionListener(listener);
            return item;
        }

    }

}
