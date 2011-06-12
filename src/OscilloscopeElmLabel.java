import javax.swing.JLabel;
import java.awt.*;
import java.util.Random;
import java.awt.event.*;

class OscilloscopeElmLabel extends JLabel implements MouseListener {
	private static final long serialVersionUID = 7847884518095331490L;
	
	PopupMenu elmMenu;
	MenuItem removeItem;
	
	Color vColor;
	Color iColor;
	
	OscilloscopeElmLabel( CircuitElm elm, Oscilloscope scope ) {
		
		vColor = randomColor();
		iColor = randomColor();
		
		String info[] = new String[10];
		elm.getInfo(info);
		
		this.setText("<html>" + 
				info[3].substring(4) + " " + info[0].substring(0, 1).toUpperCase().concat(info[0].substring(1)) +
				"<br>" +
				"<font color=#" + this.getVColorHex() + ">\u25FC V</font>\t" +
				"<font color=#" + this.getIColorHex() + ">\u25FC I</font>");
		
		// Popup menu for removing element
		elmMenu = new PopupMenu();
		removeItem = new MenuItem("Remove");
		removeItem.addActionListener(scope);
		removeItem.setActionCommand("REMOVE_ELM");
		elmMenu.add(removeItem);
		this.add(elmMenu);
		
		this.addMouseListener(this);
		
		this.setPreferredSize(new Dimension(110, 30));
		
	}
	
	static Color randomColor() {
		Color c;
		Random rand = new Random();
		do {
			c = new Color( Math.abs(rand.nextInt()) % 256, Math.abs(rand.nextInt()) % 256, Math.abs(rand.nextInt()) % 256 );
		} while ( c.getRed() + c.getGreen() + c.getBlue() > 600 );
		return c;
	}
	
	public Color getVColor() {
		return vColor;
	}
	
	public Color getIColor() {
		return iColor;
	}
	
	public String getVColorHex() {
		return Integer.toHexString(vColor.getRGB()).substring(2);
	}
	
	public String getIColorHex() {
		return Integer.toHexString(iColor.getRGB()).substring(2);
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		// TODO Auto-generated method stub
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseExited(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mousePressed(MouseEvent e) {
		// Open popup menu
		if ( e.isPopupTrigger() ) {
			elmMenu.show(e.getComponent(), e.getX(), e.getY());
		}
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}
}