import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.util.Arrays;
import java.util.Random;
import javax.swing.*;

class OscilloscopeWaveform implements MouseListener, ActionListener {
	
	CircuitElm elm;
	int elm_no;
	private Oscilloscope scope;
	
	Image wf_img;
	private MemoryImageSource img_src;
	private int[] pixels;
	private Color[] wave_color;
	private int last_column;
	private int columns_visible;
	private Point last_draw_point;
	private boolean redraw_needed;
	private Oscilloscope.ScopeType type;
	
	private double value[];
	private double[][] min_values;
	private double[][] max_values;
	
	JLabel label;
	private JPopupMenu menu;
	private JPopupMenu[] type_menus;
	private JCheckBoxMenuItem show;
	private JCheckBoxMenuItem show_v, show_i, show_p;
	private Color elm_color;
	
	private Dimension size;
	private int counter;
	
	OscilloscopeWaveform( CircuitElm e, Oscilloscope o ) {
		elm = e;
		scope = o;
		elm_no = o.sim.locateElm(elm);
		last_draw_point = new Point(-1, -1);
		reset(scope.canvas_size);
		
		// Allocate memory for storing current values
		value = new double[Oscilloscope.Value.values().length];
		
		// Randomize wave colors
		wave_color = new Color[Oscilloscope.Value.values().length];
		for ( Oscilloscope.Value v : Oscilloscope.Value.values() )
			wave_color[v.ordinal()] = randomColor();
		
		type_menus = new JPopupMenu[Oscilloscope.ScopeType.values().length];
		elm_color = randomColor();
		
		type_menus[Oscilloscope.ScopeType.VIP_VS_T.ordinal()] = buildMenu_VIP_VS_T();
		type_menus[Oscilloscope.ScopeType.I_VS_V.ordinal()] = buildMenu_V_VS_I();
		
		// Setup label
		label = new JLabel();
		setType(scope.getType());
		
		// Element popup menu
		label.addMouseListener(this);
		label.setPreferredSize(new Dimension(110, 30));
		
		// Add label to window
		scope.add(label);
		scope.validate();
		scope.repaint();
	}
	
	private void setLabel() {
		String info[] = new String[10];
		elm.getInfo(info);
		
		switch (type) {
		case VIP_VS_T:
			label.setText("<html>" +  
					info[0].substring(0, 1).toUpperCase().concat(info[0].substring(1)) +  // capitalize type of element
					"<br>" +
					"<font color=#" + colorToHex(wave_color[Oscilloscope.Value.VOLTAGE.ordinal()]) + ">\u25FC V</font>\t" +
					"<font color=#" + colorToHex(wave_color[Oscilloscope.Value.CURRENT.ordinal()]) + ">\u25FC I</font>\t" +
					"<font color=#" + colorToHex(wave_color[Oscilloscope.Value.POWER.ordinal()]) + ">\u25FC P</font>"
				);
			break;
		case I_VS_V:
			label.setText("<html><font color=#" + colorToHex(elm_color) + ">" +
					info[0].substring(0, 1).toUpperCase().concat(info[0].substring(1)) +  // capitalize type of element
					"</font>"
				);
			break;
		case X_VS_Y:
			break;
		}
		
		if ( this == scope.getSelectedWaveform() )
			label.setFont(Oscilloscope.selected_label_font);
		else
			label.setFont(Oscilloscope.label_font);
	}
	
	public void setType(Oscilloscope.ScopeType new_type) {
		type = new_type;
		
		setLabel();
		
		menu = type_menus[new_type.ordinal()];
		
		scope.validate();
		scope.repaint();
	}
	
	/* ******************************************************************************************
	 * *                                                                                        *
	 * ******************************************************************************************/
	private void setLastColumn() {
		min_values[Oscilloscope.Value.VOLTAGE.ordinal()][last_column] = elm.getVoltageDiff();
		max_values[Oscilloscope.Value.VOLTAGE.ordinal()][last_column] = elm.getVoltageDiff();
		min_values[Oscilloscope.Value.CURRENT.ordinal()][last_column] = elm.getCurrent();
		max_values[Oscilloscope.Value.CURRENT.ordinal()][last_column] = elm.getCurrent();
		min_values[Oscilloscope.Value.POWER.ordinal()][last_column] = elm.getPower();
		max_values[Oscilloscope.Value.POWER.ordinal()][last_column] = elm.getPower();
	}
	
	public void reset(Dimension new_size) {
		
		if ( size != new_size ) {
			size = new_size;
			
			// Allocate new image
			pixels = new int[size.width * size.height];
			img_src = new MemoryImageSource(size.width, size.height, pixels, 0, size.width);
			img_src.setAnimated(true);
			img_src.setFullBufferUpdates(true);
			wf_img = scope.createImage(img_src);
			
			// Allocate arrays for scope values
			min_values = new double[Oscilloscope.Value.values().length][size.width];
			max_values = new double[Oscilloscope.Value.values().length][size.width];
		}
		
		// Clear image
		Arrays.fill(pixels, 0);
		img_src.newPixels();
		
		last_draw_point.x = -1;
		last_draw_point.y = -1;
		
		last_column = 0;
		columns_visible = 0;
		setLastColumn();
		counter = 0;
		redraw_needed = true;
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
		value[Oscilloscope.Value.VOLTAGE.ordinal()] = elm.getVoltageDiff();
		value[Oscilloscope.Value.CURRENT.ordinal()] = elm.getCurrent();
		value[Oscilloscope.Value.POWER.ordinal()] = elm.getPower();
		for ( Oscilloscope.Value v : Oscilloscope.Value.values() ) {
			int n = v.ordinal();
			if ( value[n] > max_values[n][last_column] )
				max_values[n][last_column] = value[n];
			if ( value[n] < min_values[n][last_column] )
				min_values[n][last_column] = value[n];
		}
		
		

		if ( counter == scope.getTimeScale() ) {
			last_column = mod(last_column + 1, size.width);
			columns_visible = Math.min(columns_visible+1, size.width);
			setLastColumn();
			counter = 0;
			redraw_needed = true;
			
			if ( scope.getType() == Oscilloscope.ScopeType.I_VS_V ) {
				double avg_i = (max_values[Oscilloscope.Value.CURRENT.ordinal()][mod(last_column-1,size.width)] + min_values[Oscilloscope.Value.CURRENT.ordinal()][mod(last_column,size.width)]) / 2;
				int current_y = (int) Math.round(size.height/2 - avg_i / scope.getRange(Oscilloscope.Value.CURRENT) * size.height);
				double avg_v = (max_values[Oscilloscope.Value.VOLTAGE.ordinal()][mod(last_column-1,size.width)] + min_values[Oscilloscope.Value.VOLTAGE.ordinal()][mod(last_column,size.width)]) / 2;
				int current_x = (int) Math.round(size.width/2 + avg_v / scope.getRange(Oscilloscope.Value.VOLTAGE) * size.width);
				
				if ( last_draw_point.x != -1 && last_draw_point.y != -1 ) {
					if (last_draw_point.x == current_x && last_draw_point.y == current_y) {
					    int index = current_x + size.width * current_y;
					    if ( index >= 0 && index < pixels.length && current_x >= 0 && current_x < size.width && current_y >= 0 && current_y < size.height )
				    		pixels[index] = elm_color.getRGB();
					} else if (Math.abs(current_y-last_draw_point.y) > Math.abs(current_x-last_draw_point.x)) {
					    // y difference is greater, so we step along y's
					    // from min to max y and calculate x for each step
					    double sgn = Math.signum(current_y-last_draw_point.y);
					    int x, y;
					    for (y = last_draw_point.y; y != current_y+sgn; y += sgn) {
					    	x = last_draw_point.x+(current_x-last_draw_point.x)*(y-last_draw_point.y)/(current_y-last_draw_point.y);
					    	int index = x + size.width * y;
					    	if ( index >= 0 && index < pixels.length && x >= 0 && x < size.width && y >= 0 && y < size.height )
					    		pixels[index] = elm_color.getRGB();
					    }
					} else {
					    // x difference is greater, so we step along x's
					    // from min to max x and calculate y for each step
					    double sgn = Math.signum(current_x-last_draw_point.x);
					    int x, y;
					    for (x = last_draw_point.x; x != current_x+sgn; x += sgn) {
					    	y = last_draw_point.y+(current_y-last_draw_point.y)*(x-last_draw_point.x)/(current_x-last_draw_point.x);
					    	int index = x + size.width * y;
					    	if ( index >= 0 && index < pixels.length && x >= 0 && x < size.width && y >= 0 && y < size.height )
					    		pixels[index] = elm_color.getRGB();
					    }
					}	
				}
				last_draw_point.x = current_x;
				last_draw_point.y = current_y;
			}
		}
	}
	
	public void redraw() {
		if ( ! redraw_needed )
			return;
		
		switch (type) {
		case VIP_VS_T:
			Arrays.fill(pixels, 0);
			
			int max_y, min_y;
			for ( int col = size.width-1; col > (size.width - columns_visible); col-- ) {
				for ( Oscilloscope.Value value : Oscilloscope.Value.values() ) {
					if ( isShowing(value) ) {
						max_y = Math.min((int) Math.round(size.height/2 - (min_values[value.ordinal()][mod(last_column+1+col, size.width)] / scope.getRange(value) * size.height)), size.height-1);
						min_y = Math.max((int) Math.round(size.height/2 - (max_values[value.ordinal()][mod(last_column+1+col, size.width)] / scope.getRange(value) * size.height)), 0);
						for ( int row = min_y; row <= max_y; row++ )
							pixels[row * size.width + col] = getColor(value).getRGB();
					}
				}			
			}
			
			img_src.newPixels();
			break;
		case I_VS_V:
			img_src.newPixels();
			break;
		}
		
		redraw_needed = false;
	}
	
	public Point drawPosition() {
		return last_draw_point;
	}
	
	private int mod(int x, int y) {
	    int result = x % y;
	    if (result < 0)
	        result += y;
	    return result;
	}
	
	public double getPeakValue(Oscilloscope.Value value) {
		/* Peak of most recent crest
		int index = value.ordinal();
		int zero1 = -1;
		int zero2 = -1;
		int i;
		for ( i = 0; i < columns_visible-2; i++ ) {
			if ( Math.signum(max_values[index][mod(last_column-2-i,size.width)]) > Math.signum(max_values[index][mod(last_column-1-i,size.width)]) ) {
				zero1 = mod(last_column-1-i, size.width);
				break;
			}
		}
		for ( i++; i < columns_visible-2; i++ ) {
			if ( Math.signum(max_values[index][mod(last_column-2-i,size.width)]) < Math.signum(max_values[index][mod(last_column-1-i,size.width)]) ) {
				zero2 = mod(last_column-1-i, size.width);
				break;
			}
		}
		
		if ( zero1 > 0 && zero2 > 0 ) {
			double peak = Double.MIN_VALUE;
			for ( i = zero2; i < zero1; i++ ) {
				if ( max_values[index][i] > peak )
					peak = max_values[index][i];
			}
			return peak;
		}
		else {
			for ( int i = (size.width-columns_visible+1); i < size.width; i++ )
				peak = Math.max(peak, max_values[value.ordinal()][mod(last_column+i, size.width)]);
		}
		*/
		double peak = Double.MIN_VALUE;
		for ( int i = 0; i < columns_visible; i++ )
			peak = Math.max(peak, max_values[value.ordinal()][mod(last_column-i, size.width)]);
		return peak;
	}
	
	public double getNegativePeakValue(Oscilloscope.Value value) {
		double npeak = Double.MAX_VALUE;
		for ( int i = 0; i < columns_visible; i++ )
			npeak = Math.min(npeak, max_values[value.ordinal()][mod(last_column-i, size.width)]);
		return npeak;
	}
	
	public double getFrequency(Oscilloscope.Value value) {
		int index = value.ordinal();
		
		double avg_period = 0;
		double avg_period2 = 0;
		int period_count = 0;
		
		int zero1 = -1;
		int zero2 = -1;
	
		// Calculate average time between zero crossings.
		for ( int i = (size.width-columns_visible+1); i < size.width; i++ ) {
			if ( Math.signum(min_values[index][mod(last_column+i,size.width)]) != Math.signum(max_values[index][mod(last_column+i,size.width)]) ) {
				zero2 = zero1;
				zero1 = mod(last_column+i,size.width);
				if ( zero2 != -1 && zero1 > zero2 ) {
					avg_period += zero1 - zero2;
					avg_period2 += (zero1 - zero2) * (zero1 - zero2);
					period_count++;
				}
			}
		}
		avg_period /= period_count;
		avg_period2 /= period_count;
		
		// Don't show period if standard deviation is too high.
		double std_dev = Math.sqrt(avg_period2 - avg_period * avg_period);
		if ( period_count < 1 || std_dev > 2 )
			return 0;
		else
			return  (1 / (avg_period * scope.getTimeScale() * scope.getTimeStep() )) / 2;
	}
	
	/* ******************************************************************************************
	 * *                                                                                        *
	 * ******************************************************************************************/
	
	// Generate random colors for drawing voltage, current, power
	static private Color randomColor() {
		Color c;
		Random rand = new Random();
		do {
			c = new Color( Math.abs(rand.nextInt()) % 256, Math.abs(rand.nextInt()) % 256, Math.abs(rand.nextInt()) % 256 );
		} while ( c.getRed() + c.getGreen() + c.getBlue() > 600 );
		return c;
	}
	
	// Convert color to hex string for HTML label
	static private String colorToHex(Color c) {
		return Integer.toHexString(c.getRGB()).substring(2);
	}
	
	/* ******************************************************************************************
	 * *                                                                                        *
	 * ******************************************************************************************/
	public void setShow(int flags) {
		show_p.setState((flags & 4) != 0);
		show_v.setState((flags & 2) != 0);
		show_i.setState((flags & 1) != 0);
	}
	
	public boolean isShowing() {
		return show.getState();
	}
	
	public void show(boolean show) {
		this.show.setState(show);
	}
	
	public boolean isShowing(Oscilloscope.Value value) {
		switch(value) {
			case VOLTAGE:	return show_v.getState();
			case CURRENT:	return show_i.getState();
			case POWER:		return show_p.getState();
		}
		return false;
	}
	
	public void show(Oscilloscope.Value value, boolean show) {
		switch(value) {
			case VOLTAGE:	show_v.setState(show);
			case CURRENT:	show_i.setState(show);
			case POWER:		show_p.setState(show);
		}
	}
	
	public Color getColor() { return elm_color; }
	
	public void setColor(Color color) {
		elm_color = color;
		setLabel();
	}
	
	public Color getColor(Oscilloscope.Value value) {
		return wave_color[value.ordinal()];
	}
	
	public void setColor(Oscilloscope.Value value, Color color) {
		wave_color[value.ordinal()] = color;
		setLabel();
	}
	
	/* ********************************************************* */
	/* Create menu (shown by right clicking on label)            */
	/* ********************************************************* */
	private JPopupMenu buildMenu_VIP_VS_T() {
		JPopupMenu menu = new JPopupMenu();
		
		// Checkboxes to tell which values to show
		menu.add(show_v = new JCheckBoxMenuItem("Show Voltage"));
		menu.add(show_i = new JCheckBoxMenuItem("Show Current"));
		menu.add(show_p = new JCheckBoxMenuItem("Show Power"));
		show_v.setState(true); // Show voltage by default
		menu.addSeparator();
		
		JMenu color_menu = new JMenu("Change Colors");
		for ( Oscilloscope.Value v : Oscilloscope.Value.values() ) {
			JMenuItem mi = new JMenuItem(v.name().substring(0,1) + v.name().substring(1).toLowerCase() + " Color");
			mi.setActionCommand("SET_COLOR_" + v.name());
			mi.addActionListener(this);
			color_menu.add(mi);
		}
		menu.add(color_menu);
		menu.addSeparator();
		
		// Remove element from scope
		JMenuItem removeItem = new JMenuItem("Remove from Scope");
		removeItem.addActionListener(this);
		removeItem.setActionCommand("REMOVE");
		menu.add(removeItem);
		
		return menu;
	}
	
	private JPopupMenu buildMenu_V_VS_I() {
		JPopupMenu menu = new JPopupMenu();
		
		menu.add(show = new JCheckBoxMenuItem("Show"));
		show.setState(true); // show this element by default
		menu.addSeparator();
		
		JMenuItem mi = new JMenuItem("Change Color");
		mi.setActionCommand("SET_COLOR");
		mi.addActionListener(this);
		menu.add(mi);
		menu.addSeparator();
		
		// Remove element from scope
		JMenuItem removeItem = new JMenuItem("Remove from Scope");
		removeItem.addActionListener(this);
		removeItem.setActionCommand("REMOVE");
		menu.add(removeItem);
		
		return menu;
	}
	
	/* ******************************************************************************************
	 * * MouseListener implementation                                                           *
	 * ******************************************************************************************/

	@Override public void mouseClicked(MouseEvent e) {}
	@Override public void mouseEntered(MouseEvent e) {}
	@Override public void mouseExited(MouseEvent e) {}
	@Override public void mousePressed(MouseEvent e) {
		// Right clicking shows popup menu
		if ( e.isPopupTrigger() )
			menu.show(e.getComponent(), e.getX(), e.getY());
		
		// Left clicking displays instantaneous info about this element
		else if ( e.getButton() == MouseEvent.BUTTON1 )
			scope.setSelectedWaveform(this);
	}
	@Override public void mouseReleased(MouseEvent e) {}

	/* ******************************************************************************************
	 * * ActionListener implementation                                                          *
	 * ******************************************************************************************/
	
	@Override public void actionPerformed(ActionEvent e) {
		if ( e.getActionCommand().equals("REMOVE") )
			scope.removeElement(this);
		
		else if ( e.getActionCommand().substring(0, 9).equals("SET_COLOR") ) {
			switch (type) {
			case VIP_VS_T:
				int v = Oscilloscope.Value.valueOf(e.getActionCommand().substring(10)).ordinal();
				wave_color[v] = JColorChooser.showDialog(scope, "Choose New Color", wave_color[v]);
				break;
			case I_VS_V:
				Color old_color = elm_color;
				elm_color = JColorChooser.showDialog(scope, "Choose New Color", elm_color);
				for ( int i = 0; i < pixels.length; i++ ) {
					if ( pixels[i] == old_color.getRGB() )
						pixels[i] = elm_color.getRGB();
				}
				break;
			}
			setLabel();
		}
	}
}