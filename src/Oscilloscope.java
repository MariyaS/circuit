import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.*;
import java.text.*;
import java.util.Iterator;
import java.util.Vector;
import javax.swing.*;

class Oscilloscope extends JFrame implements
  ActionListener, ComponentListener, WindowListener {
	private static final long serialVersionUID = 4788980391955265718L;
	
	private CirSim sim;
	
	private static final int MAX_ELEMENTS = 10;
	
	/**
	 * Waveforms displayed in scope.
	 */
	private Vector<OscilloscopeWaveform> waveforms;
	private OscilloscopeWaveform selected_wf;
	
	private static final Color bg_color = Color.WHITE;
	private static final Color grid_color = new Color(0x80,0x80,0x80);
	
	static final Font label_font = new Font("SansSerif", 0, 12);
	static final Font selected_label_font = new Font(label_font.getName(), Font.BOLD, label_font.getSize());
	private static final Font grid_label_font = label_font.deriveFont(9.0f);
	private static final Font info_font = label_font.deriveFont(10.0f);
	private static final NumberFormat display_format = DecimalFormat.getInstance();
	static { display_format.setMinimumFractionDigits(2); }
	
	private OscilloscopeCanvas canvas;
	Dimension canvas_size;
	
	private BufferedImage main_img;
	private Graphics2D main_img_gfx;
	
	private BufferedImage info_img;
	private Graphics2D info_img_gfx;
	
	private Graphics2D info_window_gfx;

	/**
	 * Number of time steps per scope pixel.
	 * This variable was named 'speed' in the original Scope class.
	 */
	private int time_scale;
	private double[] range;
	
	static enum ScopeType { VIP_VS_T, I_VS_V, X_VS_Y }
	private ScopeType scope_type;
	
	private static final int DEFAULT_TIME_SCALE = 64;
	static enum Value { VOLTAGE, CURRENT, POWER }
	private static final double[] default_range = { 5, 0.1, 0.5 };
	static final String[] value_units = { "V", "A", "W" };
	
	private JMenuItem reset;
	private JMenuItem t_scale_up, t_scale_down;
	private JMenuItem v_range_up, v_range_down;
	private JMenuItem i_range_up, i_range_down;
	private JMenuItem p_range_up, p_range_down;
	private JMenuItem all_ranges_up, all_ranges_down;
	private JMenuItem fit_ranges;
	private ButtonGroup show_options;
	private JRadioButtonMenuItem show_vip_vs_t, show_v_vs_i, show_x_vs_y;
	private JCheckBoxMenuItem show_peak, show_n_peak, show_freq;
	private JCheckBoxMenuItem show_grid, show_labels;
	private JCheckBoxMenuItem stack_scopes;
	
	Oscilloscope(CirSim s) {
		
		// Initialize variables
		sim = s;
		waveforms = new Vector<OscilloscopeWaveform>();
		selected_wf = null;
		
		// Setup window
		setTitle("Oscilloscope");
		setJMenuBar(buildMenu());
		setLayout(new OscilloscopeLayout());
		getContentPane().setBackground(bg_color);
		setSize(getPreferredSize());
		Point p = sim.getLocation();
		setLocation( p.x+sim.getWidth(), p.y+50 );
		addWindowListener(this);
		addComponentListener(this);
		canvas = new OscilloscopeCanvas(this);
		add(canvas);
		
		// Initialize scales
		time_scale = DEFAULT_TIME_SCALE;
		range = new double[Value.values().length];
		for ( Value v : Value.values() )
			range[v.ordinal()] = default_range[v.ordinal()];
		
		scope_type = ScopeType.VIP_VS_T;
		
		// Show window
		setVisible(true);
		handleResize(); // Necessary to allocate main_img and info_img before drawScope is called
	}
	
	/**
	 * Clear all waveforms associated with this scope.
	 * Also resizes the waveforms if needed after a stack/unstack.
	 */
	private void resetGraph() {
		if ( scope_type == ScopeType.VIP_VS_T && stack_scopes.getState() == true && !waveforms.isEmpty() ) {
			Dimension wf_size = new Dimension(canvas_size.width, canvas_size.height / waveforms.size());
			for ( Iterator<OscilloscopeWaveform> wfi = waveforms.iterator(); wfi.hasNext(); )
				wfi.next().reset(wf_size);
		} else {
			for ( Iterator<OscilloscopeWaveform> wfi = waveforms.iterator(); wfi.hasNext(); )
				wfi.next().reset(canvas_size);
		}
		main_img_gfx.clearRect(0, 0, canvas_size.width, canvas_size.height);
	}
	
	/**
	 * Draw scope axes.
	 * In v/i/p mode, draws X axes
	 * In v/i/p stacked mode, also draws separators between waveforms
	 * In v vs i mode, draws X and Y axes
	 * @param gfx The Graphics object to draw on
	 */
	private void drawAxes(Graphics gfx) {
		switch (scope_type) {
		case VIP_VS_T:
			if ( stack_scopes.getState() == false)
				gfx.drawRect(0, canvas_size.height/2, canvas_size.width, 1);
			else {
				for ( int i = 0; i < waveforms.size(); i++ ) {
					int y = i * canvas_size.height / waveforms.size();
					gfx.drawLine(0, y, canvas_size.width, y);
					y = i * canvas_size.height / waveforms.size() + canvas_size.height / waveforms.size() / 2;
					gfx.drawRect(0, y, canvas_size.width, 1);
				}
				gfx.drawLine(0, canvas_size.height-1, canvas_size.width, canvas_size.height-1);
			}
			break;
		case I_VS_V:
			gfx.drawRect(0, canvas_size.height/2, canvas_size.width, 1);
			gfx.drawRect(canvas_size.width/2, 0, 1, canvas_size.height);
			break;
		}
	}
	
	/**
	 * Draw gridlines for the time axis.
	 * Labels every other line.
	 * @param gfx The graphics object to draw on.
	 */
	private void drawTimeGridlines(Graphics gfx) {
		
		double ts = sim.timeStep * time_scale;  		// Timestep adjusted for current scale
		double tstart = sim.t - ts * canvas_size.width;	// Time at the far left of the scope window

		// Calculate step between gridlines (in time)
		double grid_step = 1e-15;
		while ( grid_step/ts < 25 ) 
			grid_step *= 10;
		if ( grid_step/ts > 80 )		// Make sure gridlines aren't too far apart
			grid_step /= 2;
		else if ( grid_step/ts < 35 )	// Make sure gridlines aren't too close together
			grid_step *= 2;
		
		double tx = sim.t - (sim.t % grid_step);
		
		// Draw grid lines
		double t;
		int lx = canvas_size.width;
		int ln;
		for ( int i = 0; ; i++ ) {
			t = tx - i * grid_step;	// Time at gridline
			if ( t < 0 || t < tstart )
				break;
			
			lx = (int) Math.round((t - tstart) / ts);	// Pixel position of gridline
			gfx.drawLine(lx, 0, lx, canvas_size.height);
			
			// Mark time every other gridline
			ln = (int) Math.round(t / grid_step ); // Gridline number (since beginning of time)
			if ( ln % 2 == 0 )
				gfx.drawString(getUnitText(t, "s"), lx+2, canvas_size.height-2);
		}
	}
	
	/**
	 * Draw gridlines.
	 * In v/i/p vs t unstacked mode, draws 8 gridlines parallel to X axis
	 * In v/i/p vs t stacked mode, does nothing
	 * In v vs i mode, draws 8 gridlines in each direction
	 * @param gfx The graphics object to draw on.
	 */
	private void drawGridlines(Graphics gfx) {
		switch ( scope_type ) {
		case VIP_VS_T:
			if ( stack_scopes.getState() == false ) {
				for ( int i = 1; i <= 7; i++ )
					gfx.drawLine(0, i * canvas_size.height/8, canvas_size.width, i * canvas_size.height/8);
			}
			break;
		case I_VS_V:
			for ( int i = 1; i <= 7; i++ ) {
				gfx.drawLine(0, i * canvas_size.height/8, canvas_size.width, i * canvas_size.height/8);
				gfx.drawLine(i * canvas_size.width/8, 0, i * canvas_size.width/8, canvas_size.height);
			}
			break;
		}
	}
	
	/**
	 * Draw gridline labels
	 * In v/i/p unstacked mode, draws labels at each gridline with the value at that line
	 * In v/i/p stacked mode, draws min/max value of each waveform
	 * In v vs i mode, draws min/max voltage and current
	 * @param gfx The graphics object to draw on
	 */
	private void drawGridLabels(Graphics gfx) {
		switch (scope_type) {
		case VIP_VS_T:
			if ( stack_scopes.getState() == false ) {
				if ( showingValue(Value.VOLTAGE) || showingValue(Value.CURRENT) || showingValue(Value.POWER) ) {
					String str;
					Rectangle2D r;
					for ( int i = 3; i >= -3; i-- ) {
						str = "";
						if ( i == 0 ) {
							str = "0.00";
						} else {
							for ( Value v : Value.values() ) {
								if ( showingValue(v) )
									str += getUnitText(range[v.ordinal()]/8 * i, value_units[v.ordinal()]) + " | ";
							}
						}
						str = str.substring(0, str.length()-3);
						r = gfx.getFontMetrics().getStringBounds(str, gfx);
						int offset = (i > 0) ? 5 : 2;
						
						// Clear area behind label
						gfx.clearRect(3, canvas_size.height/2-canvas_size.height/8*i-offset-(int) Math.ceil(r.getHeight()), (int) Math.ceil(r.getWidth()), (int) Math.ceil(r.getHeight())+2);
						
						gfx.drawString(str, 3, Math.round(canvas_size.height/2-canvas_size.height/8*i-offset));
					}
				}
			} else {
				for ( int i = 0; i < waveforms.size(); i++ ) {
					String str = "";
					for ( Value v : Value.values() ) {
						if ( showingValue(v) )
							str += getUnitText(range[v.ordinal()]/2, value_units[v.ordinal()]) + " | ";
					}
					str = str.substring(0,str.length()-3);
					
					Rectangle2D r = gfx.getFontMetrics().getStringBounds(str, gfx);
					gfx.clearRect(3, i * canvas_size.height / waveforms.size()+2, (int) Math.ceil(r.getWidth()), (int) Math.ceil(r.getHeight()));
					gfx.drawString(str, 3, i * canvas_size.height / waveforms.size() + (int) Math.ceil(r.getHeight()));
				}
				
				for ( int i = 1; i <= waveforms.size(); i++ ) {
					String str = "";
					for ( Value v : Value.values() ) {
						if ( showingValue(v) )
							str += getUnitText(-range[v.ordinal()]/2, value_units[v.ordinal()]) + " | ";
					}
					str = str.substring(0,str.length()-3);
					
					Rectangle2D r = gfx.getFontMetrics().getStringBounds(str, gfx);
					gfx.clearRect(3, i * canvas_size.height / waveforms.size() - (int) Math.ceil(r.getHeight()) - 1, (int) Math.ceil(r.getWidth()) + 2, (int) Math.ceil(r.getHeight()));
					gfx.drawString(str, 3, i * canvas_size.height / waveforms.size() - 3);
				}
			}
			break;
		case I_VS_V:
			FontMetrics fm = gfx.getFontMetrics();
			
			String str = getUnitText(-range[Value.VOLTAGE.ordinal()]/2, value_units[Value.VOLTAGE.ordinal()]);
			Rectangle2D r = gfx.getFontMetrics().getStringBounds(str, gfx);
			gfx.clearRect(3, canvas_size.height/2-fm.getDescent()-fm.getAscent(), (int) Math.ceil(r.getWidth())+2, (int) Math.ceil(r.getHeight()));
			gfx.drawString(str,3, canvas_size.height/2-fm.getDescent());
			
			
			str = getUnitText(range[Value.VOLTAGE.ordinal()]/2, value_units[Value.VOLTAGE.ordinal()]);
			r = gfx.getFontMetrics().getStringBounds(str, gfx);
			gfx.clearRect(canvas_size.width-3-(int) Math.ceil(r.getWidth()), canvas_size.height/2-fm.getDescent()-fm.getAscent(), (int) Math.ceil(r.getWidth())+2, (int) Math.ceil(r.getHeight()));
			gfx.drawString(str,canvas_size.width-3-(int) Math.ceil(r.getWidth()), canvas_size.height/2-fm.getDescent());
			
			str = getUnitText(-range[Value.CURRENT.ordinal()]/2, value_units[Value.CURRENT.ordinal()]);
			r = gfx.getFontMetrics().getStringBounds(str, gfx);
			gfx.clearRect(canvas_size.width/2+3, -fm.getDescent(), (int) Math.ceil(r.getWidth())+2, (int) Math.ceil(r.getHeight()));
			gfx.drawString(str, canvas_size.width/2 + 3, fm.getAscent());
			
			str = getUnitText(range[Value.CURRENT.ordinal()]/2, value_units[Value.CURRENT.ordinal()]);
			r = gfx.getFontMetrics().getStringBounds(str, gfx);
			gfx.clearRect(canvas_size.width/2+3, canvas_size.height-fm.getAscent()-fm.getDescent(), (int) Math.ceil(r.getWidth())+2, (int) Math.ceil(r.getHeight()));
			gfx.drawString(str, canvas_size.width/2 + 3, canvas_size.height-fm.getDescent());
			break;
		}
	}
	
	/**
	 * Displays element info for the currently selected element.
	 * Also displays +/- peak values and frequency.
	 * @param gfx The graphics object to draw on.
	 */
	private void drawElementInfo(Graphics gfx) {
		String info[] = new String[10];
		selected_wf.elm.getInfo(info);
		
		FontMetrics fm = gfx.getFontMetrics();
		int font_height = fm.getHeight();
		
		// Draw all strings in info
		// Put 10px spacing between strings, wrap to next line after 5 strings
		int x = 3;
		for ( int i = 0; i < 10 && info[i] != null; i++ ) {
			gfx.drawString(info[i], x, font_height*(1+(int)(i/5)));
			if ( i == 5 )
				x = 3;
			else
				x += Math.round(fm.getStringBounds(info[i], gfx).getWidth()) + 10;
		}
		
		// +/- peak values, frequency
		if ( selected_wf.showingValue(Value.VOLTAGE) || selected_wf.showingValue(Value.CURRENT) || selected_wf.showingValue(Value.POWER) ) {
			String peak_str = "";
			if ( show_peak.getState() ) {
				peak_str += "Peak: ";
				for ( Value v : Value.values() ) {
					if ( selected_wf.showingValue(v) )
						peak_str += getUnitText(selected_wf.getPeakValue(v), value_units[v.ordinal()]) + " | ";
				}
				peak_str = peak_str.substring(0, peak_str.length()-3);
			}
			
			String npeak_str = "";
			if ( show_n_peak.getState() ) {
				npeak_str += "Neg Peak: ";
				for ( Value v : Value.values() ) {
					if ( selected_wf.showingValue(v) )
						npeak_str += getUnitText(selected_wf.getNegativePeakValue(v), value_units[v.ordinal()]) + " | ";
				}
				npeak_str = npeak_str.substring(0, npeak_str.length()-3);
			}
			
			String freq_str = "";
			if ( show_freq.getState() ) {
				freq_str += "Freq: ";
				for ( Value v : Value.values() ) {
					if ( selected_wf.showingValue(v) ) {
						double f = selected_wf.getFrequency(v);
						if ( f != 0 )
							freq_str += getUnitText(selected_wf.getFrequency(v), "Hz") + " | ";
						else
							freq_str += "? | ";
					}
						
				}
				freq_str = freq_str.substring(0, freq_str.length()-3);
			}
			
			x = 3;
			if ( !peak_str.isEmpty() ) {
				gfx.drawString(peak_str, x, font_height*3);
				x += Math.round(fm.getStringBounds(peak_str, gfx).getWidth()) + 15;
			}
			if ( !npeak_str.isEmpty() ) {
				gfx.drawString(npeak_str, x, font_height*3);
				x += Math.round(fm.getStringBounds(npeak_str, gfx).getWidth()) + 15;
			}
			if ( !freq_str.isEmpty() ) {
				gfx.drawString(freq_str, x, font_height*3);
				x += Math.round(fm.getStringBounds(freq_str, gfx).getWidth()) + 15;
			}
		}
		
		
	}
	
	/**
	 * Displays current time of scope.  Equal to sim.t.
	 * @param gfx The graphics object to draw on.
	 */
	private void drawCurrentTime(Graphics gfx) {
		String time = getUnitText(sim.t, "s");
		FontMetrics fm = gfx.getFontMetrics();
		gfx.drawString(time, canvas_size.width-(int)fm.getStringBounds(time, gfx).getWidth()-3, fm.getHeight());
	}
	
	/**
	 * Update min/max values for the rightmost pixel of each waveform in the scope.
	 */
	public void timeStep() {
		for ( Iterator<OscilloscopeWaveform> wfi = waveforms.iterator(); wfi.hasNext(); )
			wfi.next().timeStep();
	}
	
	/**
	 * Repaint the main drawing area of the scope.
	 * @param canvas_gfx The graphics object for the OscilloscopeCanvas.
	 */
	public void drawScope(Graphics canvas_gfx) {		
		
		// Clear main image
		main_img_gfx.clearRect(0, 0, canvas_size.width, canvas_size.height);
		main_img_gfx.setColor(grid_color);
		main_img_gfx.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 1.0f));
		
		drawAxes(main_img_gfx);
		if ( show_grid.getState() )
			drawGridlines(main_img_gfx);
		
		switch (scope_type) {
		case VIP_VS_T:
			drawTimeGridlines(main_img_gfx);
			
			if ( stack_scopes.getState() == false ) {
				for ( int i = 0; i < waveforms.size(); i++ ) {
					OscilloscopeWaveform wf = waveforms.get(i);
					wf.redraw();
					main_img_gfx.drawImage(wf.wf_img, 0, 0, null);
				}
			} else {
				int y = 0;
				for ( int i = 0; i < waveforms.size(); i++ ) {
					OscilloscopeWaveform wf = waveforms.get(i);
					wf.redraw();
					main_img_gfx.drawImage(wf.wf_img, 0, y, null);
					y += canvas_size.height / waveforms.size();
				}
				
			}
			break;
		case I_VS_V:
			Graphics g = main_img_gfx.create();
			for ( int i = 0; i < waveforms.size(); i++ ) {
				OscilloscopeWaveform wf = waveforms.get(i);
				if ( wf.isShowing() ) {
					wf.redraw();
					main_img_gfx.drawImage(wf.wf_img, 0, 0, null);
					Point p = wf.drawPosition();
					g.setColor(wf.getColor());
					g.fillOval(p.x-3, p.y-3, 7, 7);
				}
			}
		}
		
		if ( show_labels.getState() )
			drawGridLabels(main_img_gfx);
		
		// Clear info area
		info_img_gfx.clearRect(0, 0, info_img.getWidth(), info_img.getHeight());
		
		// Draw element info and current time
		if ( selected_wf != null )
			drawElementInfo(info_img_gfx);
		drawCurrentTime(info_img_gfx);
		
		// Update window
		canvas_gfx.drawImage(main_img, 0, 0, null);
		info_window_gfx.drawImage(info_img, 0, 0, null);

		canvas.repaint();
	}
	
	/**
	 * Add an element to the scope.
	 * Prevents duplicate elements from being added.
	 * @param elm The element to be added
	 */
	public void addElement(CircuitElm elm) {
		// This was to keep labels from overflowing onto the canvas when the scope window is small.
		if ( waveforms.size() >= MAX_ELEMENTS ) {
			System.out.println("Scope accepts maximum of " + MAX_ELEMENTS + " elements");
			return;
		}
		
		// Do not allow duplicate elements
		for ( Iterator<OscilloscopeWaveform> wfi = waveforms.iterator(); wfi.hasNext(); ) {
			if ( wfi.next().elm == elm )
				return;
		}
		
		waveforms.add(new OscilloscopeWaveform(elm, this));
		
		if ( stack_scopes.getState() )
			resetGraph();
	}
	
	/**
	 * Add an element to the scope and set which values to show for that element.
	 * Only called from CirSim.readSetup.
	 * @param elm The element to be added.
	 * @param flags The values to be shown. (1 = current, 2 = voltage, 4 = power)
	 */
	public void addElement(CircuitElm elm, int flags) {
		addElement(elm);
		waveforms.lastElement().setShow(flags);
	}
	
	/**
	 * Remove an element from the scope.
	 * @param wf The waveform to be removed.
	 */
	public void removeElement(OscilloscopeWaveform wf) {
		if ( selected_wf == wf )
			selected_wf = null;
		remove(wf.label);
		waveforms.remove(wf);
		validate();
		repaint();
		
		if ( stack_scopes.getState() )
			resetGraph();
	}
	
	/**
	 * Select a waveform to show info about.
	 * @param wf The waveform to select.
	 */
	public void setSelectedWaveform(OscilloscopeWaveform wf) {
		selected_wf = wf;
		for ( Iterator<OscilloscopeWaveform> wfi = waveforms.iterator(); wfi.hasNext(); )
			wfi.next().label.setFont(label_font);
		wf.label.setFont(Oscilloscope.selected_label_font);
	}
	
	/**
	 * Return the currently selected waveform.
	 * @return Selected waveform
	 */
	public OscilloscopeWaveform getSelectedWaveform() { return selected_wf; }
	
	/**
	 * Stack/unstack waveforms in scope.
	 * @param stack True to stack, false to unstack.
	 */
	public void setStack(boolean stack) {
		stack_scopes.setState(stack);
		if (scope_type == ScopeType.VIP_VS_T)
			resetGraph();
	}
	
	/**
	 * Allocate new images and resize graphics objects when the scope window is resized.
	 */
	private void handleResize() {
		canvas_size = canvas.getSize();
		
		main_img = new BufferedImage(canvas_size.width, canvas_size.height, BufferedImage.TYPE_INT_ARGB);
		main_img_gfx = (Graphics2D) main_img.getGraphics();
		main_img_gfx.setColor(grid_color);
		main_img_gfx.setFont(grid_label_font);
		main_img_gfx.setBackground(bg_color);
		main_img_gfx.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 1.0f));
		
		info_img = new BufferedImage(canvas_size.width, OscilloscopeLayout.INFO_AREA_HEIGHT, BufferedImage.TYPE_INT_ARGB);
		info_img_gfx = (Graphics2D) info_img.getGraphics();
		info_img_gfx.setColor(Color.BLACK);
		info_img_gfx.setFont(info_font);
		info_img_gfx.setBackground(bg_color);
		
		info_window_gfx = (Graphics2D) this.getGraphics().create(this.getInsets().left, this.getHeight()-this.getInsets().bottom-OscilloscopeLayout.INFO_AREA_HEIGHT, canvas_size.width, OscilloscopeLayout.INFO_AREA_HEIGHT);
	}
	
	/**
	 * Scale Y axis ranges to fit data.
	 * Starts from default ranges and adjusts by factor of 2 to find minimum range into which all data currently displayed can fit.
	 */
	public void fitRanges() {
		
		for ( Value v : Value.values() ) {
			double max_abs_value = Double.MIN_VALUE;
			for ( Iterator<OscilloscopeWaveform> wfi = waveforms.iterator(); wfi.hasNext(); ) {
				OscilloscopeWaveform wf = wfi.next();
				if ( wf.showingValue(v) )
					max_abs_value = Math.max(max_abs_value, Math.max(Math.abs(wf.getPeakValue(v)), Math.abs(wf.getNegativePeakValue(v))));
			}
			double r = default_range[v.ordinal()];
			if ( r < max_abs_value && max_abs_value != Double.MIN_VALUE ) {
				while ( r < max_abs_value )
					r *= 2;
			} else {
				while ( r >= max_abs_value && max_abs_value != Double.MIN_VALUE )
					r /= 2;
				r *= 2;
			}
			range[v.ordinal()] = r * 2;
		}
		resetGraph();
	}
	
	/**
	 * Set type of scope
	 * @param new_type VIP_VS_T (voltage/current/power vs time), V_VS_I (voltage vs current), or X_VS_Y (arbitrary vs arbitrary)
	 */
	public void setType(ScopeType new_type) {;
		switch (new_type) {
			case VIP_VS_T:	show_vip_vs_t.setSelected(true);	break;
			case I_VS_V:	show_v_vs_i.setSelected(true);		break;
			case X_VS_Y:	show_x_vs_y.setSelected(true);		break;
		}
		scope_type = new_type;
		for ( Iterator<OscilloscopeWaveform> wfi = waveforms.iterator(); wfi.hasNext(); )
			wfi.next().setType(new_type);
		resetGraph();
	}
	
	/**
	 * Returns type of scope
	 * @return VIP_VS_T, V_VS_I, or X_VS_Y
	 */
	public ScopeType getType() { return scope_type; }
	
	/**
	 * Set time scale of scope
	 * @param new_scale New time scale
	 */
	public void setTimeScale(int new_scale) {
		time_scale = new_scale;
		resetGraph();
	}
	
	/**
	 * Returns time scale of scope.
	 * @return Time scale
	 */
	public int getTimeScale() { return time_scale; }
	
	/**
	 * Set Y axis range of scope
	 * @param value The value to set the range for
	 * @param new_range The new range
	 */
	public void setRange(Value value, double new_range) {
		range[value.ordinal()] = new_range;
		resetGraph();
	}
	
	/**
	 * Returns the Y axis range for the specified value.
	 * @param value Value to get range for
	 * @return Range for the specified value.
	 */
	public double getRange(Value value) { return range[value.ordinal()]; }
	
	/**
	 * Returns current time of scope.
	 * Equal to sim.t.
	 * @return Current time of scope.
	 */
	public double getTime() { return sim.t; }
	
	/**
	 * Returns scope's time step.
	 * @return Scope's time step.
	 */
	public double getTimeStep() { return sim.timeStep; }
	
	/**
	 * Whether or not the specified value is shown for any waveform in the scope
	 * @param value The value to test
	 * @return true if the value is shown for any waveform, false if not
	 */
	public boolean showingValue(Value value) {
		for ( Iterator<OscilloscopeWaveform> wfi = waveforms.iterator(); wfi.hasNext(); ) {
			if ( wfi.next().showingValue(value) )
				return true;
		}
		return false;
	}
	
	/**
	 * Format a value into a string with a unit attached. 
	 * @param value
	 * @param unit
	 * @return
	 */
	static private String getUnitText(double value, String unit) {
		double va = Math.abs(value);
		if (va < 1e-14)
		    return "0 " + unit;
		if (va < 1e-9)
		    return display_format.format(value*1e12) + " p" + unit;
		if (va < 1e-6)
		    return display_format.format(value*1e9) + " n" + unit;
		if (va < 1e-3)
		    return display_format.format(value*1e6) + " " + CirSim.muString + unit;
		if (va < 1)
		    return display_format.format(value*1e3) + " m" + unit;
		if (va < 1e3)
		    return display_format.format(value) + " " + unit;
		if (va < 1e6)
		    return display_format.format(value*1e-3) + " k" + unit;
		if (va < 1e9)
		    return display_format.format(value*1e-6) + " M" + unit;
		return display_format.format(value*1e-9) + " G" + unit;
	}
	
	/* ********************************************************* */
	/* Action Listener Implementation                            */
	/* ********************************************************* */
	@Override public void actionPerformed(ActionEvent e) {
		// Reset
		if ( e.getSource() == reset ) {
			time_scale = DEFAULT_TIME_SCALE;
			resetGraph();
		}
		
		// Change time scale
		else if ( e.getSource() == t_scale_up )
			setTimeScale( time_scale * 2 );
		else if ( e.getSource() == t_scale_down )
			setTimeScale( time_scale / 2 );
		
		// Change y-axis scales
		else if ( e.getSource() == v_range_up )
			setRange(Value.VOLTAGE, getRange(Value.VOLTAGE) * 2);
		else if ( e.getSource() == v_range_down )
			setRange(Value.VOLTAGE, getRange(Value.VOLTAGE) / 2);
		else if ( e.getSource() == i_range_up )
			setRange(Value.CURRENT, getRange(Value.CURRENT) * 2);
		else if ( e.getSource() == i_range_down )
			setRange(Value.CURRENT, getRange(Value.CURRENT) / 2);
		else if ( e.getSource() == p_range_up )
			setRange(Value.POWER, getRange(Value.POWER) * 2);
		else if ( e.getSource() == p_range_down )
			setRange(Value.POWER, getRange(Value.POWER) / 2);
		else if ( e.getSource() == all_ranges_up ) {
			setRange(Value.VOLTAGE, getRange(Value.VOLTAGE) * 2);
			setRange(Value.CURRENT, getRange(Value.CURRENT) * 2);
			setRange(Value.POWER, getRange(Value.POWER) * 2);
		} else if ( e.getSource() == all_ranges_down ) {
			setRange(Value.VOLTAGE, getRange(Value.VOLTAGE) / 2);
			setRange(Value.CURRENT, getRange(Value.CURRENT) / 2);
			setRange(Value.POWER, getRange(Value.POWER) / 2);
		} else if ( e.getSource() == fit_ranges )
			fitRanges();
		
		// Change value shown on scope
		else if ( e.getSource() == show_vip_vs_t )
			setType(ScopeType.VIP_VS_T);
		else if ( e.getSource() == show_v_vs_i )
			setType(ScopeType.I_VS_V);
		else if ( e.getSource() == show_x_vs_y )
			setType(ScopeType.X_VS_Y);
		
		// Stack scopes
		else if ( e.getSource() == stack_scopes && scope_type == ScopeType.VIP_VS_T )
			resetGraph();
	}
	
	/* ********************************************************* */
	/* Component Listener Implementation                         */
	/* ********************************************************* */
	@Override public void componentShown(ComponentEvent e) { canvas.repaint(); }
	@Override public void componentHidden(ComponentEvent e) {
		if ( this == sim.selected_scope )
			sim.selected_scope = null;
		sim.scopes.remove(this);
		dispose();
	}
	@Override public void componentMoved(ComponentEvent e) {}
	@Override public void componentResized(ComponentEvent e) {
		handleResize();
		resetGraph();
		canvas.repaint();
	}
	
	/* ********************************************************* */
	/* Window Listener Implementation                            */
	/* ********************************************************* */
	
	@Override public void windowActivated(WindowEvent e) { sim.selected_scope = this; }
	@Override public void windowClosed(WindowEvent e) {}
	@Override public void windowClosing(WindowEvent e) {}
	@Override public void windowDeactivated(WindowEvent e) {}
	@Override public void windowDeiconified(WindowEvent e) {}
	@Override public void windowIconified(WindowEvent e) {}
	@Override public void windowOpened(WindowEvent e) {}

	
	/**
	 * Create scope's menu bar.
	 * @return
	 */
	private JMenuBar buildMenu() {
		
		JMenuBar mb = new JMenuBar();
		
		JMenu m = new JMenu("Reset");
		mb.add(m);
		m.add(reset = new JMenuItem("Reset"));
		reset.addActionListener(this);
		
		m = new JMenu("Time Scale");
		mb.add(m);
		m.add(t_scale_up = new JMenuItem("Time Scale 2x"));
		m.add(t_scale_down = new JMenuItem("Time Scale 1/2x"));
		for ( int i = 0; i < m.getItemCount(); i++ )
			((JMenuItem) m.getMenuComponent(i)).addActionListener(this);
		
		m = new JMenu("Amplitude Range");
		mb.add(m);
		m.add(v_range_up = new JMenuItem("Voltage Range 2x"));
		m.add(v_range_down = new JMenuItem("Voltage Range 1/2x"));
		m.addSeparator();
		m.add(i_range_up = new JMenuItem("Current Range 2x"));
		m.add(i_range_down = new JMenuItem("Current Range 1/2x"));
		m.addSeparator();
		m.add(p_range_up = new JMenuItem("Power Range 2x"));
		m.add(p_range_down = new JMenuItem("Power Range 1/2x"));
		m.addSeparator();
		m.add(all_ranges_up = new JMenuItem("All Ranges 2x"));
		m.add(all_ranges_down = new JMenuItem("All Ranges 1/2x"));
		m.addSeparator();
		m.add(fit_ranges = new JMenuItem("Fit Ranges"));
		for ( int i = 0; i < m.getItemCount(); i++ ) {
			if ( m.getMenuComponent(i) instanceof JMenuItem )
				((JMenuItem) m.getMenuComponent(i)).addActionListener(this);
		}
		
		m = new JMenu("Show");
		mb.add(m);
		show_options = new ButtonGroup();
		m.add(show_vip_vs_t = new JRadioButtonMenuItem("Voltage/Current/Power"));
		show_vip_vs_t.setActionCommand("SHOW_VCP");
		m.add(show_v_vs_i = new JRadioButtonMenuItem("Plot I vs V"));
		show_v_vs_i.setActionCommand("SHOW_V_VS_I");
		m.add(show_x_vs_y = new JRadioButtonMenuItem("Plot X vs Y"));
		show_x_vs_y.setActionCommand("SHOW_X_VS_Y");
		show_options.add(show_vip_vs_t);
		show_options.add(show_v_vs_i);
		show_options.add(show_x_vs_y);
		show_vip_vs_t.setSelected(true); // default to show voltage/current/power
		for ( int i = 0; i < 3; i++ )
			((JRadioButtonMenuItem) m.getMenuComponent(i)).addActionListener(this);
		m.addSeparator();
		m.add(show_peak = new JCheckBoxMenuItem("Peak Value"));
		m.add(show_n_peak = new JCheckBoxMenuItem("Negative Peak Value"));
		m.add(show_freq = new JCheckBoxMenuItem("Frequency"));
		m.addSeparator();
		m.add(show_grid = new JCheckBoxMenuItem("Gridlines"));
		show_grid.setState(true);
		m.add(show_labels = new JCheckBoxMenuItem("Grid Labels"));
		show_labels.setState(true);
		
		m = new JMenu("Stack");
		mb.add(m);
		m.add(stack_scopes = new JCheckBoxMenuItem("Stack All"));
		stack_scopes.addActionListener(this);
		
		return mb;
	}
}