import java.awt.*;
import javax.swing.*;

class OscilloscopeLayout implements LayoutManager {
	
	public static final int LABEL_AREA_HEIGHT = 40;
	public static final int INFO_AREA_HEIGHT = 50;
	
	public OscilloscopeLayout() {}
	
	public Dimension preferredLayoutSize(Container target) { return new Dimension(650,450); }
	public Dimension minimumLayoutSize(Container target) { return new Dimension(500,300); }
	
	public void addLayoutComponent(String name, Component c) {}
	public void removeLayoutComponent(Component c) {}
	
	public void layoutContainer(Container window) {
		Insets insets = window.getInsets();
		int window_width = window.getWidth() - (insets.left + insets.right);
		int window_height = window.getHeight() - (insets.top + insets.bottom);
		 		
		// Layout canvas
		Component canvas = window.getComponent(0);
		canvas.setLocation(insets.left, insets.top+LABEL_AREA_HEIGHT);
		canvas.setSize(window_width, window_height-(LABEL_AREA_HEIGHT+INFO_AREA_HEIGHT));
		
		// Layout component labels
		int x = insets.left + 5;
		int label_no = 0;
		for ( int i = 1; i < window.getComponentCount(); i++ ) {
			Component c = window.getComponent(i);
			if ( c instanceof JLabel ) {
				if ( label_no < 9 ) {
					c.setLocation(insets.left+3, insets.top + window_height - INFO_AREA_HEIGHT + 16 * (label_no / 3));
					c.setSize(window_width-6, 15);
				} else {
					c.setLocation(x, insets.top);
					x += c.getPreferredSize().width + 20;
					c.setSize(c.getPreferredSize());
				}
				label_no++;
			}
		}
	}
	
}