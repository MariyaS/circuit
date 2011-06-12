import java.awt.*;
import javax.swing.*;

class OscilloscopeLayout implements LayoutManager {
	public OscilloscopeLayout() {}
	
	public Dimension preferredLayoutSize(Container target) {
		return new Dimension(350,450);
	}
	public Dimension minimumLayoutSize(Container target) {
		return new Dimension(200,250);
	}
	public void addLayoutComponent(String name, Component c) {}
	public void removeLayoutComponent(Component c) {}
	
	public void layoutContainer(Container target) {
		Insets insets = target.getInsets();
		int targetw = target.getWidth() - (insets.left + insets.right);
		int targeth = target.getHeight() - (insets.top + insets.bottom);
		
		// Position canvas
		Component canvas = target.getComponent(0);
		canvas.setLocation(insets.left, insets.top+40);
		canvas.setSize(targetw, targeth-40);
		
		// Position component labels
		int nLabels = 0;
		for ( int i = 1; i < target.getComponentCount(); i++ ) {
			Component c = target.getComponent(i);
			if ( c instanceof JLabel ) {
				
				/* Two per column
				int x = insets.left+5 + nLabels/2 * 80;
				int y = insets.top;
				if ( nLabels % 2 == 1 ) {
					y = insets.top+15;
				} */
				int x = insets.left+5 + nLabels * c.getPreferredSize().width;
				int y = insets.top;
				c.setLocation(x, y);
				c.setSize(c.getPreferredSize());
				nLabels++;
			}
		}
	}
	
}