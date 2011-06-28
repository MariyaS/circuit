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
		
		// Position component labels
		int nLabels = 0;
		for ( int i = 0; i < target.getComponentCount(); i++ ) {
			Component c = target.getComponent(i);
			if ( c instanceof JLabel ) {
				int x = insets.left+5 + nLabels * c.getPreferredSize().width;
				int y = insets.top;
				c.setLocation(x, y);
				c.setSize(c.getPreferredSize());
				nLabels++;
			}
		}
	}
	
}