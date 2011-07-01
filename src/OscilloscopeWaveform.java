import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.util.Arrays;
import java.util.Random;
import javax.swing.*;

class OscilloscopeWaveform implements MouseListener, ActionListener {
	
	CircuitElm elm;
	private Oscilloscope scope;
	
	private Color v_color, i_color, p_color;
	
	Image wf_img;
	private MemoryImageSource img_src;
	private int[] pixels;
	private int last_column;
	private int columns_visible;
	private boolean redraw_needed;
	
	private double value[];
	private double[][] min_values;
	private double[][] max_values;
	
	JLabel label;
	private PopupMenu menu;
	private CheckboxMenuItem show_v, show_i, show_p;
	
	private Dimension size;
	private int counter;
	
	OscilloscopeWaveform( CircuitElm e, Oscilloscope o ) {
		elm = e;
		scope = o;
		reset(scope.canvas_size);
		
		v_color = randomColor();
		i_color = randomColor();
		p_color = randomColor();
		
		value = new double[Oscilloscope.Value.values().length];
		
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
	private void setLastColumn() {
		min_values[Oscilloscope.Value.VOLTAGE.ordinal()][last_column] = elm.getVoltageDiff();
		max_values[Oscilloscope.Value.VOLTAGE.ordinal()][last_column] = elm.getVoltageDiff();
		min_values[Oscilloscope.Value.CURRENT.ordinal()][last_column] = elm.getCurrent();
		max_values[Oscilloscope.Value.CURRENT.ordinal()][last_column] = elm.getCurrent();
		min_values[Oscilloscope.Value.POWER.ordinal()][last_column] = elm.getPower();
		max_values[Oscilloscope.Value.POWER.ordinal()][last_column] = elm.getPower();
	}
	
	public void reset() {

		// Clear image
		Arrays.fill(pixels, 0);
		img_src.newPixels();
		
		last_column = 0;
		columns_visible = 0;
		setLastColumn();
		counter = 0;
		redraw_needed = true;
	}
	
	public void reset( Dimension s ) {
		size = s;
		
		// Allocate new image
		pixels = new int[size.width * size.height];
		img_src = new MemoryImageSource(size.width, size.height, pixels, 0, size.width);
		img_src.setAnimated(true);
		img_src.setFullBufferUpdates(true);
		wf_img = scope.createImage(img_src);
		
		// Allocate arrays for scope values
		min_values = new double[Oscilloscope.Value.values().length][size.width];
		max_values = new double[Oscilloscope.Value.values().length][size.width];
		
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
		}
	}
	
	public void redraw() {
		
		if ( ! redraw_needed )
			return;
		
		Arrays.fill(pixels, 0);
		
		int max_y, min_y;
		for ( int col = size.width-1; col > (size.width - columns_visible); col-- ) {
			for ( Oscilloscope.Value value : Oscilloscope.Value.values() ) {
				if ( showingValue(value) ) {
					max_y = Math.min((int) Math.round(size.height/2 - (min_values[value.ordinal()][mod(last_column+1+col, size.width)] / scope.getRange(value) * size.height)), size.height-1);
					min_y = Math.max((int) Math.round(size.height/2 - (max_values[value.ordinal()][mod(last_column+1+col, size.width)] / scope.getRange(value) * size.height)), 0);
					for ( int row = min_y; row <= max_y; row++ )
						pixels[row * size.width + col] = getColor(value).getRGB();
				}
			}			
		}
		
		img_src.newPixels();
		
		redraw_needed = false;
	}
	
	private int mod(int x, int y) {
	    int result = x % y;
	    if (result < 0)
	        result += y;
	    return result;
	}
	
	public double getPeakValue(Oscilloscope.Value value) {
		double peak = Double.MIN_VALUE;
		
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
			for ( double v : max_values[value.ordinal()] )
				peak = Math.max(v, peak);
		}
		*/
		
		for ( double v : max_values[value.ordinal()] )
			peak = Math.max(v, peak);
		return peak;
	}
	
	public double getNegativePeakValue(Oscilloscope.Value value) {
		double npeak = Double.MAX_VALUE;
		for ( double v : min_values[value.ordinal()] )
			npeak = Math.min(v, npeak);
		return npeak;
	}
	
	public double getFrequency(Oscilloscope.Value value) {
		int index = value.ordinal();
		
		double avg_period = 0;
		double avg_period2 = 0;
		int period_count = 0;
		
		int zero1 = -1;
		int zero2 = -1;
		
		// Calculate average period in scope window.
		for ( int i = 2; i < size.width; i++ ) {
			if ( Math.signum(max_values[index][mod(last_column+i-1,size.width)]) > Math.signum(max_values[index][mod(last_column+i,size.width)]) ) {
				zero2 = zero1;
				zero1 = mod(last_column+i+1,size.width);
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
			return 1 / (avg_period * scope.getTimeScale() * scope.getTimeStep() );
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
		show_v.setState((flags & 2) != 0);
		show_i.setState((flags & 1) != 0);
	}
	
	public boolean showingValue(Oscilloscope.Value value) {
		switch(value) {
			case VOLTAGE:	return show_v.getState();
			case CURRENT:	return show_i.getState();
			case POWER:		return show_p.getState();
		}
		return false;
	}
	
	private Color getColor(Oscilloscope.Value value) {
		switch (value) {
			case VOLTAGE:	return v_color;
			case CURRENT:	return i_color;
			case POWER:		return p_color;
			default:		throw new Error("Attempting to get invalid color");
		}
	}
	
	/* ********************************************************* */
	/* Create menu (shown by right clicking on label)            */
	/* ********************************************************* */
	private PopupMenu buildMenu() {
		PopupMenu m = new PopupMenu();
		
		// Checkboxes to tell which values to show
		m.add(show_v = new CheckboxMenuItem("Show Voltage"));
		m.add(show_i = new CheckboxMenuItem("Show Current"));
		m.add(show_p = new CheckboxMenuItem("Show Power"));
		show_v.setState(true); // Show voltage by default
		
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
	}
}