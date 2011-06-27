import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.util.Arrays;
import java.util.Random;
import javax.swing.*;


class OscilloscopeWaveform implements MouseListener, ActionListener {
	
	CircuitElm elm;
	Oscilloscope scope;
	
	Color v_color, i_color, p_color;
	
	Image wf_img;
	MemoryImageSource img_src;
	int[] pixels;
	int last_column;
	int columns_visible;
	boolean redraw;
	
	double[] min_v, max_v, min_i, max_i, min_p, max_p;
	
	JLabel label;
	PopupMenu menu;
	CheckboxMenuItem show_v, show_i, show_p;
	
	Dimension size;
	private int counter;
	
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
		Arrays.fill(pixels, 0);
		img_src.newPixels();
		
		last_column = 0;
		columns_visible = 1;
		
		// Reset min/max voltage, current, power to the current values
		min_v[last_column] = max_v[last_column] = elm.getVoltageDiff();
		min_i[last_column] = max_i[last_column] = elm.getCurrent();
		min_p[last_column] = max_p[last_column] = elm.getPower();
		
		counter = 0;
		
		redraw = true;
	}
	
	public void reset( Dimension s ) {
		size = s;
		
		// Create new image
		pixels = new int[size.width * size.height];
		img_src = new MemoryImageSource(size.width, size.height, pixels, 0, size.width);
		img_src.setAnimated(true);
		img_src.setFullBufferUpdates(true);
		wf_img = scope.cv.createImage(img_src);
		
		max_v = new double[size.width];
		min_v = new double[size.width];
		max_i = new double[size.width];
		min_i = new double[size.width];
		max_p = new double[size.width];
		min_p = new double[size.width];
		
		last_column = 0;
		columns_visible = 0;
		
		// Reset min/max voltage, current, power to the current values
		min_v[last_column] = max_v[last_column] = elm.getVoltageDiff();
		min_i[last_column] = max_i[last_column] = elm.getCurrent();
		min_p[last_column] = max_p[last_column] = elm.getPower();
		
		counter = 0;
		
		redraw = true;
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
		if ( v > max_v[last_column] )
			max_v[last_column] = v;
		else if ( v < min_v[last_column] )
			min_v[last_column] = v;
		double i = elm.getCurrent();
		if ( i > max_i[last_column] )
			max_i[last_column] = i;
		else if ( i < min_i[last_column] )
			min_i[last_column] = i;
		double p = elm.getPower();
		if ( p > max_p[last_column] )
			max_p[last_column] = p;
		else if ( p < min_p[last_column] )
			min_p[last_column] = p;
		
		if ( counter == scope.time_scale ) {
			
			last_column = (last_column + 1) % size.width;
			columns_visible = Math.min(columns_visible+1, size.width);
			
			// Reset min/max values for next column
			min_v[last_column] = max_v[last_column] = elm.getVoltageDiff();
			min_i[last_column] = max_i[last_column] = elm.getCurrent();
			min_p[last_column] = max_p[last_column] = elm.getPower();
			
			counter = 0;
			
			redraw = true;
		}
	}
	
	public void redraw() {
		
		if ( ! redraw ) {
			return;
		}
		
		Arrays.fill(pixels, 0);
		
		int max_v_y, min_v_y;
		int max_i_y, min_i_y;
		int max_p_y, min_p_y;
		for ( int col = size.width-1; col > (size.width - columns_visible); col-- ) {
			
			// Draw voltage
			if ( show_v.getState() ) {
				max_v_y = Math.min((int) Math.round(size.height/2 - (min_v[(last_column+1+col) % size.width] / scope.voltage_range * size.height)), size.height-1);
				min_v_y = Math.max((int) Math.round(size.height/2 - (max_v[(last_column+1+col) % size.width] / scope.voltage_range * size.height)), 0);
				for ( int row = min_v_y; row <= max_v_y; row++ ) {
					pixels[row * size.width + col] = v_color.getRGB();
				}
			}
			
			// Draw current
			if ( show_i.getState() ) {
				max_i_y = Math.min((int) Math.round(size.height/2 - (min_i[(last_column+1+col) % size.width] / scope.voltage_range * size.height)), size.height-1);
				min_i_y = Math.max((int) Math.round(size.height/2 - (max_i[(last_column+1+col) % size.width] / scope.voltage_range * size.height)), 0);
				for ( int row = min_i_y; row <= max_i_y; row++ ) {
					pixels[row * size.width + col] = i_color.getRGB();
				}
			}
			
			// Draw power
			if ( show_p.getState() ) {
				max_p_y = Math.min((int) Math.round(size.height/2 - (min_p[(last_column+1+col) % size.width] / scope.voltage_range * size.height)), size.height-1);
				min_p_y = Math.max((int) Math.round(size.height/2 - (max_p[(last_column+1+col) % size.width] / scope.voltage_range * size.height)), 0);
				for ( int row = min_p_y; row <= max_p_y; row++ ) {
					pixels[row * size.width + col] = p_color.getRGB();
				}
			}
			
		}
		
		img_src.newPixels();
		
		redraw = false;
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