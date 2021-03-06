package org.uecide;

import javax.swing.UIManager;

class NimbusLAF extends LookAndFeel {
    public static String getName() { return "Nimbus"; }

    public static void applyLAF() {
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception e) {
            Base.error(e);
        }
    }

    public static PropertyFile getPreferencesTree() {
        return null;
    }

    public static boolean isCompatible() { return true; }
}
