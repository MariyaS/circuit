import javax.swing.JLabel;
import java.awt.*;
import java.util.Random;
import java.awt.event.*;

class OscilloscopeElmLabel extends JLabel implements MouseListener {
	private static final long serialVersionUID = 7847884518095331490L;
	
	PopupMenu menu;
	CheckboxMenuItem showVoltage, showCurrent, showPower;
	
	Oscilloscope scope;
	
	Color vColor; // color for voltage waveform
	Color iColor; // color for current waveform
	Color pColor; // color for power waveform
	
	OscilloscopeElmLabel( CircuitElm elm, Oscilloscope o ) {
		
		scope = o;
		
		vColor = randomColor();
		iColor = randomColor();
		pColor = randomColor();
		
		String info[] = new String[10];
		elm.getInfo(info);
		
		this.setText("<html>" + 
				info[3].substring(4) + " " +  // R, L, C, etc. value 
				info[0].substring(0, 1).toUpperCase().concat(info[0].substring(1)) +  // capitalize type of element
				"<br>" +
				"<font color=#" + this.getVColorHex() + ">\u25FC V</font>\t" +
				"<font color=#" + this.getIColorHex() + ">\u25FC I</font>\t" +
				"<font color=#" + this.getPColorHex() + ">\u25FC P</font>"
			);
		
		// Popup menu for removing element
		menu = buildMenu();
		this.add(menu);
		
		this.addMouseListener(this);
		
		this.setPreferredSize(new Dimension(110, 30));
		
	}
	
	/* ********************************************************* */
	/* Waveforms to show                                         */
	/* ********************************************************* */
	boolean showingVoltage() {
		return showVoltage.getState();
	}
	
	boolean showingCurrent() {
		return showCurrent.getState();
	}
	
	boolean showingPower() {
		return showPower.getState();
	}
	
	/* ********************************************************* */
	/* Colors for waveforms                                      */
	/* ********************************************************* */
	static Color randomColor() {
		Color c;
		Random rand = new Random();
		do {
			c = new Color( Math.abs(rand.nextInt()) % 256, Math.abs(rand.nextInt()) % 256, Math.abs(rand.nextInt()) % 256 );
		} while ( c.getRed() + c.getGreen() + c.getBlue() > 600 );
		return c;
	}
	
	// Get colors for each waveform
	public Color getVColor() {
		return vColor;
	}
	
	public Color getIColor() {
		return iColor;
	}
	
	public Color getPColor() {
		return pColor;
	}
	
	// Get colors in hex for HTML labels
	public String getVColorHex() {
		return Integer.toHexString(vColor.getRGB()).substring(2);
	}
	
	public String getIColorHex() {
		return Integer.toHexString(iColor.getRGB()).substring(2);
	}
	
	public String getPColorHex() {
		return Integer.toHexString(pColor.getRGB()).substring(2);
	}

	/* ********************************************************* */
	/* Mouse listener implementation                             */
	/* ********************************************************* */
	@Override
	public void mousePressed(MouseEvent e) {
		// Open popup menu
		if ( e.isPopupTrigger() ) {
			menu.show(e.getComponent(), e.getX(), e.getY());
		}
	}
	
	@Override
	public void mouseClicked(MouseEvent e) {}

	@Override
	public void mouseEntered(MouseEvent e) {}

	@Override
	public void mouseExited(MouseEvent e) {}

	@Override
	public void mouseReleased(MouseEvent e) {}
	
	/* ********************************************************* */
	/* Create menu                                               */
	/* ********************************************************* */
	private PopupMenu buildMenu() {
		PopupMenu m = new PopupMenu();
		
		// Which values to show
		showVoltage = new CheckboxMenuItem("Show Voltage");
		m.add(showVoltage);
		showVoltage.setState(true);
		showCurrent = new CheckboxMenuItem("Show Current");
		m.add(showCurrent);
		showPower = new CheckboxMenuItem("Show Power");
		m.add(showPower);
		m.addSeparator();
		
		// Remove element from scope
		MenuItem removeItem = new MenuItem("Remove from Scope");
		removeItem.addActionListener(scope);
		removeItem.setActionCommand("REMOVE_ELM");
		m.add(removeItem);
		
		return m;
	}
}