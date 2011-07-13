import java.awt.*;
import javax.swing.*;

class CircuitLayout implements LayoutManager {
    public CircuitLayout() {}
    public void addLayoutComponent(String name, Component c) {}
    public void removeLayoutComponent(Component c) {}
    public Dimension preferredLayoutSize(Container target) {
    	return new Dimension(500, 500);
    }
    public Dimension minimumLayoutSize(Container target) {
    	return new Dimension(100,100);
    }
    
    
    // This defines where all the GUI elements go
    public void layoutContainer(Container target) {
		
    	// Subtract borders from container size to get content area size
    	Insets insets = target.getInsets();
		int targetw = target.getSize().width - insets.left - insets.right;
		int targeth = target.getSize().height - (insets.top+insets.bottom);
		
		Point topleft = new Point(insets.left,insets.top);
		
		// Scrollbar width
		int sbarwidth = targetw/6;
		
		// getComponent(0) returns the main circuit canvas 
		target.getComponent(0).setLocation(insets.left, insets.top+45);
		target.getComponent(0).setSize(targetw, targeth-75);
		
		// target.getComponent
		// i    component
		// 0	circuit canvas
		// 1	stopped checkbox
		// 2	reset button
		// 3	simulation speed label
		// 4	simulation speed scrollbar
		// 5	current speed label
		// 6	current speed scrollbar
		// 7	power brightness label
		// 8	power brightness scrollbar
		// 9	www.falstad.com label
		// 10	current circuit label
		Point[] componentLocations = new Point[14];
		componentLocations[0] = new Point(0,0); // circuit canvas
		componentLocations[1] = new Point(topleft.x+3, topleft.y+3); // stopped checkbox
		componentLocations[2] = new Point(topleft.x+43,topleft.y+3); // reset button
		componentLocations[3] = new Point(topleft.x+83,topleft.y+3); // new scope button
		componentLocations[4] = new Point(topleft.x+targetw/4,topleft.y); // simulation speed label
		componentLocations[5] = new Point(topleft.x+targetw/4,topleft.y+20); // simulation speed scrollbar
		componentLocations[6] = new Point(topleft.x+targetw/4+sbarwidth,topleft.y); // current speed label
		componentLocations[7] = new Point(topleft.x+targetw/4+sbarwidth,topleft.y+20); // current speed scrollbar
		componentLocations[8] = new Point(topleft.x+targetw/4+2*sbarwidth,topleft.y); // power brightness label
		componentLocations[9] = new Point(topleft.x+targetw/4+2*sbarwidth,topleft.y+20); // power brightness scrollbar
		componentLocations[10] = new Point(topleft.x+targetw,topleft.y+targeth); // www.falstad.com label
		componentLocations[11] = new Point(topleft.x+targetw-(sbarwidth+5),topleft.y); // "Current Circuit" label
		componentLocations[12] = new Point(topleft.x+targetw-(sbarwidth+5),topleft.y+15); // circuit title label
		componentLocations[13] = new Point(topleft.x+3,topleft.y+36);
		
		// Set positions and sizes of all GUI elements belonging to the container
		Point p = new Point(topleft.x, topleft.y+targeth-30);
		for (int i = 1; i < target.getComponentCount(); i++) {
		    Component m = target.getComponent(i);
		    if (m.isVisible()) {
		    	Dimension d = m.getPreferredSize();
		    	if (m instanceof JButton || m instanceof JCheckBox)
		    		d = new Dimension(30,30);
				if (m instanceof JSlider)
				    d.width = sbarwidth;
				if (m instanceof JLabel) {
					d.width = sbarwidth;
				}
				
				if ( i < componentLocations.length ) {
					if ( i == 10 ) {
						d = m.getPreferredSize();
						componentLocations[i].translate(-d.width, -d.height);
					}	
					m.setLocation(componentLocations[i]);
					m.setSize(d);
				} else {
					if (m instanceof JLabel) {
						m.setLocation(p);
						m.setForeground(Color.BLACK);
					}
					else if (m instanceof JSlider) {
						m.setLocation(p.x, p.y+15);
						p.translate(sbarwidth + 10, 0);
					}
					m.setSize(sbarwidth, 15);
					
				}
		    }
		}
    }
};
