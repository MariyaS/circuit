// Circuit.java (c) 2005,2008 by Paul Falstad, www.falstad.com

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class Circuit extends JApplet implements ComponentListener {
	private static final long serialVersionUID = 5089856351882399672L;
	
	static CirSim ogf;
    void destroyFrame() {
		if (ogf != null)
		    ogf.dispose();
		ogf = null;
		repaint();
    }
    boolean started = false;
    public void init() {
    	addComponentListener(this);
    }

    public static void main(String args[]) {
		ogf = new CirSim(null);
		ogf.init();
    }
    
    void showFrame() {
		if (ogf == null) {
		    started = true;
		    ogf = new CirSim(this);
		    ogf.init();
		    repaint();
		}
    }

    public void toggleSwitch(int x) { ogf.toggleSwitch(x); }
    
    public void paint(Graphics g) {
		String s = "Applet is open in a separate window.";
		if (!started)
		    s = "Applet is starting.";
		else if (ogf == null)
		    s = "Applet is finished.";
		else if (ogf.useFrame)
		    ogf.triggerShow();
		g.drawString(s, 10, 30);
    }
    
    public void componentHidden(ComponentEvent e){}
    public void componentMoved(ComponentEvent e){}
    public void componentShown(ComponentEvent e) {
    	showFrame();
    }
    
    public void componentResized(ComponentEvent e) {
	if (ogf != null)
	    ogf.componentResized(e);
    }
    
    public void destroy() {
		if (ogf != null)
		    ogf.dispose();
		ogf = null;
		repaint();
    }
};

