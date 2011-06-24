import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.util.Random;

import javax.swing.*;


class OscilloscopeWaveform implements MouseListener, ActionListener {
	
	CircuitElm elm;
	Oscilloscope scope;
	
	BufferedImage wf_img;
	private Graphics2D wf_gfx;
	Color v_color, i_color, p_color;
	
	JLabel label;
	PopupMenu menu;
	CheckboxMenuItem show_v, show_i, show_p;
	
	Dimension size;
	private int counter;
	
	double min_v, max_v, min_i, max_i, min_p, max_p;
	
	OscilloscopeWaveform( CircuitElm e, Oscilloscope o ) {
		elm = e;
		scope = o;
		reset(scope.cv_size);
		
		v_color = randomColor();
		i_color = randomColor();
		p_color = randomColor();
		
		String info[] = new String[10];
		elm.getInfo(info);
		
		label = new JLabel("<html>" +  
				info[0].substring(0, 1).toUpperCase().concat(info[0].substring(1)) +  // capitalize type of element
				"<br>" +
				"<font color=#" + colorToHex(v_color) + ">\u25FC V</font>\t" +
				"<font color=#" + colorToHex(i_color) + ">\u25FC I</font>\t" +
				"<font color=#" + colorToHex(p_color) + ">\u25FC P</font>"
			);
		
		// Popup menu for removing element
		menu = buildMenu();
		label.add(menu);
		label.addMouseListener(this);
		label.setPreferredSize(new Dimension(110, 30));
		
		scope.add(label);
		scope.validate();
		scope.repaint();
	}
	
	/* ******************************************************************************************
	 * *                                                                                        *
	 * ******************************************************************************************/
	public void reset() {

		// Clear image
		wf_gfx.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR, 0.0f));
		wf_gfx.fillRect(0, 0, size.width, size.height);
		
		// Reset min/max voltage, current, power to the current values
		min_v = max_v = elm.getVoltageDiff();
		min_i = max_i = elm.getCurrent();
		min_p = max_p = elm.getPower();
		
		counter = 0;
	}
	
	public void reset( Dimension s ) {
		size = s;
		
		// Create new image
		wf_img = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
		wf_gfx = (Graphics2D) wf_img.getGraphics();
		
		// Reset min/max voltage, current, power to the current values
		min_v = max_v = elm.getVoltageDiff();
		min_i = max_i = elm.getCurrent();
		min_p = max_p = elm.getPower();
		
		counter = 0;
	}
	
	/* ******************************************************************************************
	 * *                                                                                        *
	 * ******************************************************************************************/
	// Called from Oscilloscope.timeStep, which is called from CirSim.runCircuit.  There is more than
	// one time step per horizontal pixel in the scope.  The number of time steps per pixel is defined by
	// the Oscilloscope.timeScale variable.  Thus, here we keep track of the minimum and maximum scope
	// values for each horizontal pixel.  When enough time steps have gone by to equal a pixel, a line
	// is drawn in the rightmost pixel column of the scope from the minimum to the maximum value.
	public void timeStep() {
		counter++;
		
		// Update min/max voltage, current, power
		double v = elm.getVoltageDiff();
		if ( v > max_v )
			max_v = v;
		else if ( v < min_v )
			min_v = v;
		double i = elm.getCurrent();
		if ( i > max_i )
			max_i = i;
		else if ( i < min_i )
			min_i = i;
		double p = elm.getPower();
		if ( p > max_p )
			max_p = p;
		else if ( p < min_p )
			min_p = p;
		
		if ( counter == scope.time_scale ) {
				
			// Shift image one pixel to the left
			wf_img.getRaster().setRect(-1, 0, wf_img.getRaster());
			
			// Set last pixel column to transparent
			wf_gfx.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR, 0.0f));
			wf_gfx.fill(new Rectangle(size.width-1, 0, 1, size.height));
			
			// Draw last column
			wf_gfx.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC));
			int min_y, max_y;
			
			// Draw voltage 
			if ( show_v.getState() ) {
				min_y = (int) Math.round(size.height/2 - (min_v / scope.voltage_range * size.height));
				max_y = (int) Math.round(size.height/2 - (max_v / scope.voltage_range * size.height));
				wf_gfx.setColor(v_color);
				wf_gfx.drawLine(size.width-1, min_y, size.width-1, max_y);
			}
			
			// Draw current
			if ( show_i.getState() ) {
				min_y = (int) Math.round(size.height/2 - (min_i / scope.current_range * size.height));
				max_y = (int) Math.round(size.height/2 - (max_i / scope.current_range * size.height));
				wf_gfx.setColor(i_color);
				wf_gfx.drawLine(size.width-1, min_y, size.width-1, max_y);
			}
			
			// Draw power
			if ( show_p.getState() ) {
				min_y = (int) Math.round(size.height/2 - (min_p / scope.power_range * size.height));
				max_y = (int) Math.round(size.height/2 - (max_p / scope.power_range * size.height));
				wf_gfx.setColor(p_color);
				wf_gfx.drawLine(size.width-1, min_y, size.width-1, max_y);
			}
			
			// Reset min/max voltage, current, power to the current values
			min_v = max_v = elm.getVoltageDiff();
			min_i = max_i = elm.getCurrent();
			min_p = max_p = elm.getPower();
			
			counter = 0;
		}
	}
	
	/* ******************************************************************************************
	 * *                                                                                        *
	 * ******************************************************************************************/
	
	// Generate random colors for drawing voltage, current, power
	static Color randomColor() {
		Color c;
		Random rand = new Random();
		do {
			c = new Color( Math.abs(rand.nextInt()) % 256, Math.abs(rand.nextInt()) % 256, Math.abs(rand.nextInt()) % 256 );
		} while ( c.getRed() + c.getGreen() + c.getBlue() > 600 );
		return c;
	}
	
	// Convert color to hex string for HTML label
	static String colorToHex(Color c) {
		return Integer.toHexString(c.getRGB()).substring(2);
	}
	
	/* ********************************************************* */
	/* Create menu (shown by right clicking on label)            */
	/* ********************************************************* */
	private PopupMenu buildMenu() {
		PopupMenu m = new PopupMenu();
		
		// Checkboxes to tell which values to show
		show_v = new CheckboxMenuItem("Show Voltage");
		m.add(show_v);
		show_v.setState(true); // Show voltage by default
		show_i = new CheckboxMenuItem("Show Current");
		m.add(show_i);
		show_p = new CheckboxMenuItem("Show Power");
		m.add(show_p);
		
		m.addSeparator();
		
		// Remove element from scope
		MenuItem removeItem = new MenuItem("Remove from Scope");
		removeItem.addActionListener(this);
		removeItem.setActionCommand("REMOVE");
		m.add(removeItem);
		
		return m;
	}
	
	/* ******************************************************************************************
	 * * MouseListener implementation                                                           *
	 * ******************************************************************************************/

	@Override
	public void mouseClicked(MouseEvent e) {}

	@Override
	public void mouseEntered(MouseEvent e) {}

	@Override
	public void mouseExited(MouseEvent e) {}

	@Override
	public void mousePressed(MouseEvent e) {
		
		// Right clicking shows popup menu
		if ( e.isPopupTrigger() ) {
			menu.show(e.getComponent(), e.getX(), e.getY());
		}
		
		// Left clicking displays instantaneous info about this element
		else if ( e.getButton() == MouseEvent.BUTTON1 ) {
			scope.selected_elm = this.elm;
			for ( int i = 0; i < scope.waveforms.size(); i++ ) {
				scope.waveforms.get(i).label.setFont(Oscilloscope.label_font);
			}
			label.setFont(Oscilloscope.selected_label_font);
		}
	}

	@Override
	public void mouseReleased(MouseEvent e) {}

	/* ******************************************************************************************
	 * * ActionListener implementation                                                          *
	 * ******************************************************************************************/
	
	@Override
	public void actionPerformed(ActionEvent e) {
		if ( e.getActionCommand().equals("REMOVE") ) {
			scope.removeElement(this);
		}
	}
}