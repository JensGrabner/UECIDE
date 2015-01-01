package org.uecide.plugin;

import org.uecide.*;
import org.uecide.debug.*;
import org.uecide.editors.*;
import java.io.*;
import java.util.*;
import java.net.*;
import java.util.zip.*;
import java.util.regex.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;

import jssc.*;

import say.swing.*;


public class VirtualLCD extends Plugin implements SerialPortEventListener,MessageConsumer
{
    public static HashMap<String, String> pluginInfo = null;
    public static void setInfo(HashMap<String, String>info) { pluginInfo = info; }
    public static String getInfo(String item) { return pluginInfo.get(item); }

    JFrame win = null;
    LCD lcd;
    SerialPort port;

    String serialPort;

    int baudRate;

    public VirtualLCD(Editor e) { editor = e; }
    public VirtualLCD(EditorBase e) { editorTab = e; }

    Dimension size = new Dimension(64, 64);

    int col_r = 0;
    int col_g = 0;
    int col_b = 0;


    public void run()
    {
        if (win != null) {
            close();
        }

        win = new JFrame(Translate.t("Virtual LCD"));
        win.getContentPane().setLayout(new BorderLayout());
        win.setResizable(true);
        lcd = new LCD(size.width,size.height);
        win.add(lcd, BorderLayout.CENTER);
        win.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        win.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                close();
            }
        });
        Base.setIcon(win);
        serialPort = editor.getSerialPort();
        win.setTitle(Translate.t("Virtual LCD") + " :: " + serialPort);
        win.setLocationRelativeTo(editor);
        win.pack();
        win.setVisible(true);


        
        try {
            Debug.message(this + ": Open port " + serialPort);
            port = Serial.requestPort(serialPort, 115200);
            if (port == null) {
                editor.error("Unable to open serial port");
                return;
            }
                
        } catch(Exception e) {
            editor.error("Unable to open serial port:");
            editor.error(e);
            return;
        }
        try {
            port.addEventListener(this);
        } catch (Exception e) {
            Base.error(e);
        }
    }

    public void close()
    {
        if (port != null) {
            if (port.isOpened()) {
                try {
                    Serial.closePort(port);
                } catch (Exception e) {
                    editor.error(e);
                }
            }
            port = null;
        }
        win.dispose();
        Debug.message(this + ": Closing serial terminal on port " + serialPort);
    }

    public void warning(String m) { editor.warning(m); }
    public void error(String m) { editor.error(m); }

    public void message(String m) {
        if (port == null) {
            win.setVisible(false);
            return;
        }
        if (Base.preferences.getBoolean("serial.autocr_out")) {
            m = m.replace("\n", "\r\n");
        }

        try {
            port.writeString(m);
        } catch (Exception e) {
            Base.error(e);
        }
    }
    
    public void addToolbarButtons(JToolBar toolbar, int flags) {
    }

    public static void populatePreferences(JPanel p) {
    }

    public static void savePreferences() {
    }

    public void releasePort(String portName) {
        if (port == null) {
            return;
        }
        if (portName == null) {
            return;
        }
        if (portName.equals(serialPort)) {
            close();
        }
    }

    boolean secondNibble = false;

    byte[] data = new byte[size.width/8 * size.height];
    byte currentByte = 0;
    int bytepos = 0;

    Stack<Integer> commandStack = new Stack<Integer>();

    public final int NEXT_NONE = 0;
    public final int NEXT_PUSH_L = 1;
    public final int NEXT_PUSH_H = 2;
    public final byte CMD_PUSH = (byte)129;
    public final byte CMD_SET_DIM = (byte)130;
    public final byte CMD_SET_BG = (byte)131;
    public final byte CMD_SET_FG = (byte)132;
    public final byte CMD_SET_PIX = (byte)133;
    public final byte CMD_CLR_PIX = (byte)134;

    int nextByte = NEXT_NONE;

    int px = 0;
    int py = 0;

    int pushVal = 0;

    public void serialEvent(SerialPortEvent e) {
        int red;
        int green;
        int blue;
        int px;
        int py;
        int pz;
        if (e.isRXCHAR()) {
            try {
                if (port == null) {
                    return;
                }
                byte[] bytes = port.readBytes();
                if (bytes == null) {
                    return;
                }
                for (byte b : bytes) {
                    if (((int)b & 0xFF) > 128) {
                        switch (b) {
                            case CMD_PUSH: nextByte = NEXT_PUSH_L; break;
                            case CMD_SET_DIM: // Set Dimensions
                                if (commandStack.size() >= 2) {
                                    size.height = commandStack.pop();
                                    size.width = commandStack.pop();
                                    size.height &= 0xFFF8;
                                    size.width &= 0xFFF8;
                                    data = new byte[size.width/8 * size.height];
                                    lcd.setDimensions(size);
                                    win.pack();
                                }
                                commandStack.clear();
                                break;
                            case CMD_SET_BG: // Set background
                                if (commandStack.size() >= 3) {
                                    blue = commandStack.pop();
                                    green = commandStack.pop();
                                    red = commandStack.pop();
                                    lcd.setBackground(red, green, blue);
                                }
                                commandStack.clear();
                                break;
                            case CMD_SET_FG: // Set foreground
                                if (commandStack.size() >= 3) {
                                    blue = commandStack.pop();
                                    green = commandStack.pop();
                                    red = commandStack.pop();
                                    lcd.setForeground(red, green, blue);
                                }
                                commandStack.clear();
                                break;
                            case CMD_SET_PIX: // Set pixel
                                if (commandStack.size() >= 2) {
                                    py = commandStack.pop();
                                    px = commandStack.pop();
                                    lcd.setPixel(px, py, true);
                                }
                                commandStack.clear();
                                break;
                            case CMD_CLR_PIX: // Clear pixel
                                if (commandStack.size() >= 2) {
                                    py = commandStack.pop();
                                    px = commandStack.pop();
                                    lcd.setPixel(px, py, false);
                                }
                                commandStack.clear();
                                break;

                            default: nextByte = NEXT_NONE; break;
                        }
                        continue;
                    }

                    switch (nextByte) {
                        default:
                            if (b <= 80) { // First nibble of first byte) {
                                bytepos = 0;
                                currentByte = (byte)((b - 65) << 4);
                                secondNibble = true;
                            } else {
                                if (!secondNibble) {
                                    currentByte = (byte)((b - 97) << 4);
                                    secondNibble = true;
                                } else {
                                    currentByte |= (b - 97);
                                    if (bytepos < (size.width/8) * size.height) {
                                        data[bytepos++] = currentByte;
                                        if (bytepos == (size.width/8) * size.height) {
                                            lcd.setData(data);
                                        }
                                    }
                                    secondNibble = false;
                                }
                            }
                            break;
                        case NEXT_PUSH_L:
                            pushVal = (((int)(b - 97)) & 0xF);
                            nextByte = NEXT_PUSH_H;
                            break;
                        case NEXT_PUSH_H:
                            pushVal |= ((((int)(b - 97)) & 0xF) << 4);
                            commandStack.push(pushVal);
                            nextByte = NEXT_NONE;
                            break;
                    } 
                }
                //term.message(port.readString());
            } catch (Exception ex) {
                editor.error(ex);
            }
        }
    }

    public void populateMenu(JMenu menu, int flags) {
        if (flags == (Plugin.MENU_TOOLS | Plugin.MENU_MID)) {
            JMenuItem item = new JMenuItem("Virtual LCD");
            item.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    run();
                }
            });
            menu.add(item);
        }
    }

    public static String getPreferencesTitle() {
        return "Virtual LCD";
    }

    public void populateContextMenu(JPopupMenu menu, int flags, DefaultMutableTreeNode node) {
    }

    public ImageIcon getFileIconOverlay(File f) { return null; }

}

