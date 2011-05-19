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
		
		Component canvas = target.getComponent(0);
		canvas.setLocation(insets.left, insets.top+40);
		canvas.setSize(targetw, targeth-40);
		
		for ( int i = 1; i < target.getComponentCount(); i++ ) {
			Component c = target.getComponent(i);
			if ( c instanceof JLabel ) {
				if ( (i-1) % 2 == 0 ) {
					Point p = new Point(insets.left+5, insets.top);
					c.setLocation(p);
				}
				else
					c.setLocation(5, insets.top+15);
				c.setSize(c.getPreferredSize());
			}
		}
	}
	
}