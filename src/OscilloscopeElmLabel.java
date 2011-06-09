import javax.swing.JLabel;
import java.awt.*;
import java.util.Random;
import java.awt.event.*;

class OscilloscopeElmLabel extends JLabel implements MouseListener {
	private static final long serialVersionUID = 7847884518095331490L;
	
	PopupMenu elmMenu;
	MenuItem removeItem;
	
	OscilloscopeElmLabel( String labelString, Oscilloscope scope ) {
		super( labelString );
		
		// Random color for label
		Color c;
		Random rand = new Random();
		do {
			c = new Color( Math.abs(rand.nextInt()) % 256, Math.abs(rand.nextInt()) % 256, Math.abs(rand.nextInt()) % 256 );
		} while ( c.getRed() + c.getGreen() + c.getBlue() > 600 );
		this.setForeground(c);
		
		// Popup menu for removing element
		elmMenu = new PopupMenu();
		removeItem = new MenuItem("Remove");
		removeItem.addActionListener(scope);
		removeItem.setActionCommand("REMOVE_ELM");
		elmMenu.add(removeItem);
		this.add(elmMenu);
		
		this.addMouseListener(this);
		
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