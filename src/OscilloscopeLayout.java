import java.awt.*;

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
		canvas.setLocation(insets.left, insets.top);
		canvas.setSize(targetw, targeth);
	}
	
}