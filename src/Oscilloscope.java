import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.*;
import java.text.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;
import javax.swing.*;

class Oscilloscope extends JFrame implements
  ActionListener, ComponentListener, WindowListener {
	private static final long serialVersionUID = 4788980391955265718L;
	
	CirSim sim;
	
	private static final int MAX_ELEMENTS = 10;
	
	/**
	 * Waveforms displayed in scope.
	 */
	private Vector<OscilloscopeWaveform> waveforms;
	private OscilloscopeWaveform selected_wf;
	private int x_elm_no, y_elm_no;
	private Value x_value, y_value;
	private TransistorValue x_tvalue, y_tvalue;
	private double x_range, y_range;
	
	private static final Color bg_color = Color.WHITE;
	private static final Color grid_color = new Color(0x80,0x80,0x80);
	
	static final Font label_font = new Font("SansSerif", 0, 12);
	static final Font selected_label_font = new Font(label_font.getName(), Font.BOLD, label_font.getSize());
	private static final Font grid_label_font = label_font.deriveFont(9.0f);
	private static final Font info_font = label_font.deriveFont(10.0f);
	private static final NumberFormat display_format = DecimalFormat.getInstance();
	static { display_format.setMinimumFractionDigits(2); display_format.setMaximumFractionDigits(2); }
	
	private OscilloscopeCanvas canvas;
	Dimension canvas_size;
	
	private BufferedImage main_img;
	private Graphics2D main_img_gfx;
	
	private Image xy_img;
	private int[] xy_pixels;
	private MemoryImageSource xy_img_src;
	private Point xy_last_pt;
	
	private JLabel[] info_line;

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
	static final String[] value_units = { "V", "A", "W" };
	static enum TransistorValue { V_BE, V_BC, V_CE, I_B, I_C, I_E, POWER };
	static final String[] transistor_value_units = { "V", "V", "V", "A", "A", "A", "W" };
	private static final double[] default_range = { 5, 0.1, 0.5 };
	
	
	private JMenuItem reset;
	private JMenuItem t_scale_up, t_scale_down;
	private JMenuItem v_range_up, v_range_down;
	private JMenuItem i_range_up, i_range_down;
	private JMenuItem p_range_up, p_range_down;
	private JMenuItem x_range_up, x_range_down;
	private JMenuItem y_range_up, y_range_down;
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
		
		x_elm_no = y_elm_no = -1;
		x_value = y_value = null;
		x_tvalue = y_tvalue = null;
		
		scope_type = ScopeType.VIP_VS_T;
		
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
		
		info_line = new JLabel[9];
		for ( int i = 0; i < info_line.length; i++ ) {
			switch ( i % 3 ) {
			case 0:	add(info_line[i] = new JLabel(""));	break;
			case 1: add(info_line[i] = new JLabel("", JLabel.CENTER));	break;
			case 2: add(info_line[i] = new JLabel("", JLabel.RIGHT));	break;
			}
			info_line[i].setFont(info_font);
		}
		
		// Initialize scales
		time_scale = DEFAULT_TIME_SCALE;
		range = new double[Value.values().length];
		for ( Value v : Value.values() )
			range[v.ordinal()] = default_range[v.ordinal()];
				
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
		Arrays.fill(xy_pixels, 0);
		xy_last_pt = new Point(-1,-1);
		
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
		case X_VS_Y:
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
		case X_VS_Y:
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
		gfx.setColor(grid_color);
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
		case X_VS_Y:
			if ( x_elm_no != -1 && y_elm_no != -1 ) {
				fm = gfx.getFontMetrics();
				
				String x_unit, y_unit;
				if ( sim.getElm(x_elm_no) instanceof TransistorElm )
					x_unit = transistor_value_units[x_tvalue.ordinal()];
				else
					x_unit = value_units[x_value.ordinal()];
				if ( sim.getElm(y_elm_no) instanceof TransistorElm )
					y_unit = transistor_value_units[y_tvalue.ordinal()];
				else
					y_unit = value_units[y_value.ordinal()];
				
				str = getUnitText(-x_range/2, x_unit);
				r = gfx.getFontMetrics().getStringBounds(str, gfx);
				gfx.clearRect(3, canvas_size.height/2-fm.getDescent()-fm.getAscent(), (int) Math.ceil(r.getWidth())+2, (int) Math.ceil(r.getHeight()));
				gfx.drawString(str,3, canvas_size.height/2-fm.getDescent());
				
				
				str = getUnitText(x_range/2, x_unit);
				r = gfx.getFontMetrics().getStringBounds(str, gfx);
				gfx.clearRect(canvas_size.width-3-(int) Math.ceil(r.getWidth()), canvas_size.height/2-fm.getDescent()-fm.getAscent(), (int) Math.ceil(r.getWidth())+2, (int) Math.ceil(r.getHeight()));
				gfx.drawString(str,canvas_size.width-3-(int) Math.ceil(r.getWidth()), canvas_size.height/2-fm.getDescent());
				
				str = getUnitText(-y_range/2, y_unit);
				r = gfx.getFontMetrics().getStringBounds(str, gfx);
				gfx.clearRect(canvas_size.width/2+3, -fm.getDescent(), (int) Math.ceil(r.getWidth())+2, (int) Math.ceil(r.getHeight()));
				gfx.drawString(str, canvas_size.width/2 + 3, fm.getAscent());
				
				str = getUnitText(y_range/2, y_unit);
				r = gfx.getFontMetrics().getStringBounds(str, gfx);
				gfx.clearRect(canvas_size.width/2+3, canvas_size.height-fm.getAscent()-fm.getDescent(), (int) Math.ceil(r.getWidth())+2, (int) Math.ceil(r.getHeight()));
				gfx.drawString(str, canvas_size.width/2 + 3, canvas_size.height-fm.getDescent());
			}
		}
	}
	
	/**
	 * Displays element info for the currently selected element.
	 * Also displays +/- peak values and frequency.
	 * @param gfx The graphics object to draw on.
	 */
	private void drawElementInfo() {
		if ( scope_type == ScopeType.VIP_VS_T || scope_type == ScopeType.I_VS_V ) {
			String info[] = new String[10];
			selected_wf.elm.getInfo(info);
			if ( info[0] != null )
				info[0] = info[0].substring(0,1).toUpperCase() + info[0].substring(1);
			
			String info_str = "";
			for ( int i = 0; i < 10 && info[i] != null; i++ )
				info_str += info[i] + "     ";
			info_line[0].setText(info_str);
			
			// +/- peak values, frequency
			String peak_str = "", npeak_str = "", freq_str = "";
			if ( (selected_wf.isShowing(Value.VOLTAGE) || selected_wf.isShowing(Value.CURRENT) || selected_wf.isShowing(Value.POWER)) && ! (selected_wf.elm instanceof TransistorElm) ) {
				if ( show_peak.getState() ) {
					peak_str += "Peak: ";
					for ( Value v : Value.values() ) {
						if ( selected_wf.isShowing(v) )
							peak_str += getUnitText(selected_wf.getPeakValue(v), value_units[v.ordinal()]) + " | ";
					}
					peak_str = peak_str.substring(0, peak_str.length()-3);
				}
				
				if ( show_n_peak.getState() ) {
					npeak_str += "Neg Peak: ";
					for ( Value v : Value.values() ) {
						if ( selected_wf.isShowing(v) )
							npeak_str += getUnitText(selected_wf.getNegativePeakValue(v), value_units[v.ordinal()]) + " | ";
					}
					npeak_str = npeak_str.substring(0, npeak_str.length()-3);
				}
				
				if ( show_freq.getState() ) {
					freq_str += "Freq: ";
					for ( Value v : Value.values() ) {
						if ( selected_wf.isShowing(v) ) {
							double f = selected_wf.getFrequency(v);
							if ( f != 0 )
								freq_str += getUnitText(selected_wf.getFrequency(v), "Hz") + " | ";
							else
								freq_str += "? | ";
						}
							
					}
					freq_str = freq_str.substring(0, freq_str.length()-3);
				}
			}
			info_line[6].setText(peak_str);
			info_line[7].setText(npeak_str);
			info_line[8].setText(freq_str);
		}
		else { // type == X_VS_Y
			String plot_elements = "";
			String plot_values = "";
			if ( x_elm_no != -1 ) {
				CircuitElm elm = sim.getElm(x_elm_no);
				String info[] = new String[10];
				elm.getInfo(info);
				if ( info[0] != null )
					info[0] = info[0].substring(0,1).toUpperCase() + info[0].substring(1);
				plot_elements += "X: " + info[0] + " ";
				String value;
				if ( elm instanceof TransistorElm ) {
					value = x_tvalue.name();
					value = value.substring(0,1) + "<sub>" + value.substring(2) + "</sub>";
					plot_values += getUnitText( getElementValue(elm,x_tvalue), transistor_value_units[x_tvalue.ordinal()] );
				} else {
					value = x_value.name();
					value = value.substring(0,1) + value.substring(1).toLowerCase();
					plot_values += getUnitText( getElementValue(elm,x_value), value_units[x_value.ordinal()] );
				}
				plot_elements += value;
				
				if ( y_elm_no != -1 ) {
					plot_elements += "  |  ";
					plot_values += "  |  ";
				}
			}
			if ( y_elm_no != -1 ) {
				CircuitElm elm = sim.getElm(y_elm_no);
				String info[] = new String[10];
				elm.getInfo(info);
				if ( info[0] != null )
					info[0] = info[0].substring(0,1).toUpperCase() + info[0].substring(1);
				plot_elements += "Y: " + info[0] + " ";
				String value;
				if ( elm instanceof TransistorElm ) {
					value = y_tvalue.name();
					value = value.substring(0,1) + "<sub>" + value.substring(2) + "</sub>";
					plot_values += getUnitText( getElementValue(elm,y_tvalue), transistor_value_units[y_tvalue.ordinal()] );
				} else {
					value = y_value.name();
					value = value.substring(0,1) + value.substring(1).toLowerCase();
					plot_values += getUnitText( getElementValue(elm,y_value), value_units[y_value.ordinal()] );
				}
				plot_elements += value;
			}
			info_line[0].setText("<html>" + plot_elements);
			info_line[3].setText(plot_values);
		}
	}
	
	/**
	 * Displays current time of scope.  Equal to sim.t.
	 * @param gfx The graphics object to draw on.
	 */
	private void drawCurrentTime() {
		info_line[2].setText("t = " + getUnitText(sim.t, "s"));
	}
	
	private double getElementValue(CircuitElm elm, Value value) {
		switch (value) {
		case VOLTAGE:	return elm.getVoltageDiff();
		case CURRENT:	return elm.getCurrent();
		case POWER:		return elm.getPower();
		}
		return 0;
	}
	
	private double getElementValue(CircuitElm elm, TransistorValue value) {
		return ((TransistorElm) elm).getScopeValue(value);
	}
	
	/**
	 * Update min/max values for the rightmost pixel of each waveform in the scope.
	 */
	public void timeStep() {
		for ( Iterator<OscilloscopeWaveform> wfi = waveforms.iterator(); wfi.hasNext(); )
			wfi.next().timeStep();
					
		if( scope_type == ScopeType.X_VS_Y && x_elm_no != -1 && y_elm_no != -1 ) {
			int current_x, current_y;
			
			if ( sim.getElm(x_elm_no) instanceof TransistorElm )
				current_x = (int) Math.round(canvas_size.width/2 + getElementValue(sim.getElm(x_elm_no),x_tvalue) / x_range * canvas_size.width);
			else
				current_x = (int) Math.round(canvas_size.width/2 + getElementValue(sim.getElm(x_elm_no),x_value) / x_range * canvas_size.width);
			
			if ( sim.getElm(y_elm_no) instanceof TransistorElm )
				current_y = (int) Math.round(canvas_size.height/2 - getElementValue(sim.getElm(y_elm_no),y_tvalue) / y_range * canvas_size.height);
			else
				current_y = (int) Math.round(canvas_size.height/2 - getElementValue(sim.getElm(y_elm_no),y_value) / y_range * canvas_size.height);
			
			Rectangle r = new Rectangle(0, 0, canvas_size.width, canvas_size.height);
			
			if ( xy_last_pt.x != -1 && xy_last_pt.y != -1 && (r.contains(xy_last_pt) || r.contains(current_x, current_y)) ) {
				if (xy_last_pt.x == current_x && xy_last_pt.y == current_y) {
				    int index = current_x + canvas_size.width * current_y;
				    if ( index >= 0 && index < xy_pixels.length && current_x >= 0 && current_x < canvas_size.width && current_y >= 0 && current_y < canvas_size.height )
			    		xy_pixels[index] = 0xFF00FF00;
				} else if (Math.abs(current_y-xy_last_pt.y) > Math.abs(current_x-xy_last_pt.x)) {
				    // y difference is greater, so we step along y's
				    // from min to max y and calculate x for each step
				    double sgn = Math.signum(current_y-xy_last_pt.y);
				    int x, y;
				    for (y = xy_last_pt.y; y != current_y+sgn; y += sgn) {
				    	x = xy_last_pt.x+(current_x-xy_last_pt.x)*(y-xy_last_pt.y)/(current_y-xy_last_pt.y);
				    	int index = x + canvas_size.width * y;
				    	if ( index >= 0 && index < xy_pixels.length && x >= 0 && x < canvas_size.width && y >= 0 && y < canvas_size.height )
				    		xy_pixels[index] = 0xFF00FF00;
				    }
				} else {
				    // x difference is greater, so we step along x's
				    // from min to max x and calculate y for each step
				    double sgn = Math.signum(current_x-xy_last_pt.x);
				    int x, y;
				    for (x = xy_last_pt.x; x != current_x+sgn; x += sgn) {
				    	y = xy_last_pt.y+(current_y-xy_last_pt.y)*(x-xy_last_pt.x)/(current_x-xy_last_pt.x);
				    	int index = x + canvas_size.width * y;
				    	if ( index >= 0 && index < xy_pixels.length && x >= 0 && x < canvas_size.width && y >= 0 && y < canvas_size.height )
				    		xy_pixels[index] = 0xFF00FF00;
				    }
				}
			}
			xy_last_pt.x = current_x;
			xy_last_pt.y = current_y;
		}
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
			break;
		case X_VS_Y:
			xy_img_src.newPixels();
			main_img_gfx.drawImage(xy_img, 0, 0, null);
			main_img_gfx.setColor(Color.GREEN);
			if ( xy_last_pt.x != -1 && xy_last_pt.y != -1 )
				main_img_gfx.fillOval(xy_last_pt.x-3, xy_last_pt.y-3, 7, 7);
			break;
		}
		
		if ( show_labels.getState() )
			drawGridLabels(main_img_gfx);
		
		// Draw element info and current time
		if ( selected_wf != null || scope_type == ScopeType.X_VS_Y )
			drawElementInfo();
		drawCurrentTime();
		
		// Update window
		canvas_gfx.drawImage(main_img, 0, 0, null);

		canvas.repaint();
	}
	
	/**
	 * Add an element to the scope.
	 * Prevents duplicate elements from being added.
	 * @param elm The element to be added
	 */
	public boolean addElement(CircuitElm elm) {
		// This was to keep labels from overflowing onto the canvas when the scope window is small.
		if ( waveforms.size() >= MAX_ELEMENTS ) {
			System.out.println("Scope accepts maximum of " + MAX_ELEMENTS + " elements");
			return false;
		}
		
		// Do not allow duplicate elements
		for ( int i = 0; i < waveforms.size(); i++ ) {
			if ( waveforms.get(i).elm_no == sim.locateElm(elm) ) {
				waveforms.get(i).elm = elm;
				return false;
			}
		}
		
		waveforms.add(new OscilloscopeWaveform(elm, this));
		
		if ( stack_scopes.getState() )
			resetGraph();
		
		return true;
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
		if ( x_elm_no == wf.elm_no ) {
			x_elm_no = -1;
			x_value = null;
			x_tvalue = null;
		}
		if ( y_elm_no == wf.elm_no ) {
			y_elm_no = -1;
			y_value = null;
			y_tvalue = null;
		}
		remove(wf.label);
		waveforms.remove(wf);
		validate();
		repaint();
		
		if ( stack_scopes.getState() )
			resetGraph();
	}
	
	public void removeElement(CircuitElm e) {
		for ( int i = 0; i < waveforms.size(); i++ ) {
			OscilloscopeWaveform wf = waveforms.get(i);
			if ( wf.elm == e )
				removeElement(wf);
		}
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
		
		xy_pixels = new int[canvas_size.width * canvas_size.height];
		xy_img_src = new MemoryImageSource(canvas_size.width, canvas_size.height, xy_pixels, 0, canvas_size.width);
		xy_img_src.setAnimated(true);
		xy_img_src.setFullBufferUpdates(true);
		xy_img = createImage(xy_img_src);
		xy_last_pt = new Point(-1,-1);
	}
	
	/**
	 * Scale Y axis ranges to fit data.
	 * Starts from default ranges and adjusts by factor of 2 to find minimum range into which all data currently displayed can fit.
	 */
	public void fitRanges() {
		
		switch(scope_type) {
		case VIP_VS_T:
			for ( Value v : Value.values() ) {
				double max_abs_value = Double.MIN_VALUE;
				for ( Iterator<OscilloscopeWaveform> wfi = waveforms.iterator(); wfi.hasNext(); ) {
					OscilloscopeWaveform wf = wfi.next();
					if ( wf.isShowing(v) )
						max_abs_value = Math.max(max_abs_value, Math.max(Math.abs(wf.getPeakValue(v)), Math.abs(wf.getNegativePeakValue(v))));
				}
				double r = default_range[v.ordinal()];
				if ( r < max_abs_value && max_abs_value != Double.MIN_VALUE ) {
					while ( r <= max_abs_value )
						r *= 2;
				} else {
					while ( r > max_abs_value && max_abs_value != Double.MIN_VALUE )
						r /= 2;
					r *= 2;
				}
				range[v.ordinal()] = r * 2;
			}
			break;
		case I_VS_V:
			Value[] values = { Value.VOLTAGE, Value.CURRENT };
			for ( Value v : values ) {
				double max_abs_value = Double.MIN_VALUE;
				for ( Iterator<OscilloscopeWaveform> wfi = waveforms.iterator(); wfi.hasNext(); ) {
					OscilloscopeWaveform wf = wfi.next();
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
			break;
		case X_VS_Y:
			if ( x_elm_no != -1 && y_elm_no != -1 ) {
				double x_max = 0, y_max = 0;
				for ( int i = 0; i < waveforms.size(); i++ ) {
					if ( waveforms.get(i).elm_no == x_elm_no ) {
						if ( sim.getElm(x_elm_no) instanceof TransistorElm )
							x_max = waveforms.get(i).getPeakValue(x_tvalue);
						else
							x_max = waveforms.get(i).getPeakValue(x_value);
					}
					if ( waveforms.get(i).elm_no == y_elm_no ) {
						if ( sim.getElm(y_elm_no) instanceof TransistorElm )
							y_max = waveforms.get(i).getPeakValue(y_tvalue);
						else
							y_max = waveforms.get(i).getPeakValue(y_value);
					}
				}
				
				if ( sim.getElm(x_elm_no) instanceof TransistorElm )
					x_range = default_range[x_tvalue.ordinal()/3];
				else
					x_range = default_range[x_value.ordinal()];
				if ( x_range <= x_max && x_max != Double.MIN_VALUE ) {
					while ( x_range <= x_max )
						x_range *= 2;
				} else {
					while ( x_range >= x_max && x_max != Double.MIN_VALUE )
						x_range /= 2;
					x_range *= 2;
				}
				
				if ( sim.getElm(y_elm_no) instanceof TransistorElm )
					y_range = default_range[y_tvalue.ordinal()/3];
				else
					y_range = default_range[y_value.ordinal()];
				if ( y_range < y_max && y_max != Double.MIN_VALUE ) {
					while ( y_range < y_max )
						y_range *= 2;
				} else {
					while ( y_range >= y_max && y_max != Double.MIN_VALUE )
						y_range /= 2;
					y_range *= 2;
				}
				x_range *= 2;
				y_range *= 2;
			}
			break;
		}
		resetGraph();
	}
	
	/**
	 * Set type of scope
	 * @param new_type VIP_VS_T (voltage/current/power vs time), V_VS_I (voltage vs current), or X_VS_Y (arbitrary vs arbitrary)
	 */
	public void setType(ScopeType new_type) {
		scope_type = new_type;	
		setJMenuBar(buildMenu());
		switch (new_type) {
			case VIP_VS_T:	show_vip_vs_t.setSelected(true);	break;
			case I_VS_V:	show_v_vs_i.setSelected(true);		break;
			case X_VS_Y:	show_x_vs_y.setSelected(true);		break;
		}
		for ( Iterator<OscilloscopeWaveform> wfi = waveforms.iterator(); wfi.hasNext(); )
			wfi.next().setType(new_type);
		for ( JLabel l : info_line )
			l.setText("");
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
	
	public void setRange(String xy, double new_range) {
		if ( xy.equalsIgnoreCase("x") ) { x_range = new_range; }
		else if ( xy.equalsIgnoreCase("y") ) { y_range = new_range; }
		resetGraph();
	}
	
	/**
	 * Returns the Y axis range for the specified value.
	 * @param value Value to get range for
	 * @return Range for the specified value.
	 */
	public double getRange(Value value) { return range[value.ordinal()]; }
	public double getRange(TransistorValue value) { return range[value.ordinal()/3]; }
	
	public void setXElm(int elm_no) { x_elm_no = elm_no; }
	public void setYElm(int elm_no) { y_elm_no = elm_no; }
	
	public void setXValue(Value value) { x_value = value; }
	public void setXValue(TransistorValue value) { x_tvalue = value; }
	
	public void setYValue(Value value) { y_value = value; }
	public void setYValue(TransistorValue value) { y_tvalue = value; }
	
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
			if ( wfi.next().isShowing(value) )
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
		else if ( e.getSource() == x_range_up )
			setRange("x", x_range * 2);
		else if ( e.getSource() == x_range_down )
			setRange("x", x_range / 2);
		else if ( e.getSource() == y_range_up )
			setRange("y", y_range * 2);
		else if ( e.getSource() == y_range_down )
			setRange("y", y_range / 2);
		else if ( e.getSource() == all_ranges_up ) {
			if ( scope_type == ScopeType.VIP_VS_T || scope_type == ScopeType.I_VS_V ) {
				setRange(Value.VOLTAGE, getRange(Value.VOLTAGE) * 2);
				setRange(Value.CURRENT, getRange(Value.CURRENT) * 2);
			}
			if ( scope_type == ScopeType.VIP_VS_T )
				setRange(Value.POWER, getRange(Value.POWER) * 2);
			if ( scope_type == ScopeType.X_VS_Y ) {
				setRange("x", x_range * 2);
				setRange("y", y_range * 2);
			}
		} else if ( e.getSource() == all_ranges_down ) {
			if ( scope_type == ScopeType.VIP_VS_T || scope_type == ScopeType.I_VS_V ) {
				setRange(Value.VOLTAGE, getRange(Value.VOLTAGE) / 2);
				setRange(Value.CURRENT, getRange(Value.CURRENT) / 2);
			}
			if ( scope_type == ScopeType.VIP_VS_T )
				setRange(Value.POWER, getRange(Value.POWER) / 2);
			if ( scope_type == ScopeType.X_VS_Y ) {
				setRange("x", x_range * 2);
				setRange("y", y_range * 2);
			}
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
		
		else if ( e.getActionCommand().substring(0, 5).equals("SETXY") ) {
			String[] str = e.getActionCommand().split(":");
			
			if ( str[1].equals("X") ) {
				x_elm_no = new Integer(str[2]).intValue();
				if ( sim.getElm(x_elm_no) instanceof TransistorElm )
					x_tvalue = TransistorValue.valueOf(str[3]);
				else
					x_value = Value.valueOf(str[3]);
			} else {
				y_elm_no = new Integer(str[2]).intValue();
				if ( sim.getElm(y_elm_no) instanceof TransistorElm )
					y_tvalue = TransistorValue.valueOf(str[3]);
				else
					y_value = Value.valueOf(str[3]);
			}
			fitRanges();
			
			Arrays.fill(xy_pixels, 0);
		}
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
		
		// Set default values for check items on the first run
		// Otherwise, use their previous value when rebuilding the menu
		boolean peak = (show_peak != null) ? show_peak.getState() : true;
		boolean npeak = (show_n_peak != null) ? show_n_peak.getState() : false;
		boolean freq = (show_freq != null) ? show_freq.getState() : false;
		boolean grid = (show_grid != null) ? show_grid.getState() : true;
		boolean labels = (show_labels != null) ? show_labels.getState() : true;
		boolean stack = (stack_scopes != null) ? stack_scopes.getState() : false;
		
		JMenuBar mb = new JMenuBar();
		
		JMenu m = new JMenu("Reset");
		mb.add(m);
		m.add(reset = new JMenuItem("Reset"));
		reset.addActionListener(this);
		
		m = new JMenu("Time Scale");
		if ( scope_type == ScopeType.VIP_VS_T )
			mb.add(m);
		m.add(t_scale_up = new JMenuItem("Time Scale 2x"));
		m.add(t_scale_down = new JMenuItem("Time Scale 1/2x"));
		for ( int i = 0; i < m.getItemCount(); i++ )
			((JMenuItem) m.getMenuComponent(i)).addActionListener(this);
		
		switch (scope_type) {
		case VIP_VS_T:
			m = new JMenu("Amplitude Ranges");	break;
		case I_VS_V:
			m = new JMenu("I/V Ranges");		break;
		case X_VS_Y:
			m = new JMenu("X/Y Ranges");		break;
		}
		mb.add(m);
		if ( scope_type == ScopeType.VIP_VS_T || scope_type == ScopeType.I_VS_V ) {
			m.add(v_range_up = new JMenuItem("Voltage Range 2x"));
			m.add(v_range_down = new JMenuItem("Voltage Range 1/2x"));
			m.addSeparator();
			m.add(i_range_up = new JMenuItem("Current Range 2x"));
			m.add(i_range_down = new JMenuItem("Current Range 1/2x"));
			m.addSeparator();
		}
		if ( scope_type == ScopeType.VIP_VS_T ) {
			m.add(p_range_up = new JMenuItem("Power Range 2x"));
			m.add(p_range_down = new JMenuItem("Power Range 1/2x"));
			m.addSeparator();
		}
		if ( scope_type == ScopeType.X_VS_Y ) {
			m.add(x_range_up = new JMenuItem("X Range 2x"));
			m.add(x_range_down = new JMenuItem("X Range 1/2x"));
			m.addSeparator();
			m.add(y_range_up = new JMenuItem("Y Range 2x"));
			m.add(y_range_down = new JMenuItem("Y Range 1/2x"));
			m.addSeparator();
		}
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
		show_peak = new JCheckBoxMenuItem("Peak Value");
		show_peak.setState(peak);
		show_n_peak = new JCheckBoxMenuItem("Negative Peak Value");
		show_n_peak.setState(npeak);
		show_freq = new JCheckBoxMenuItem("Frequency");
		show_freq.setState(freq);
		if ( scope_type == ScopeType.VIP_VS_T || scope_type == ScopeType.I_VS_V ) {
			m.add(show_peak);
			m.add(show_n_peak);
			m.add(show_freq);
			m.addSeparator();
		}
		m.add(show_grid = new JCheckBoxMenuItem("Gridlines"));
		show_grid.setState(grid);
		m.add(show_labels = new JCheckBoxMenuItem("Grid Labels"));
		show_labels.setState(labels);
		
		m = new JMenu("Stack");
		if ( scope_type == ScopeType.VIP_VS_T )
			mb.add(m);
		m.add(stack_scopes = new JCheckBoxMenuItem("Stack All"));
		stack_scopes.setState(stack);
		stack_scopes.addActionListener(this);
		
		return mb;
	}
	
	String dump() {
		
		String dump = "o2 ";
		
		// Window location and size
		Point p = this.getLocation();
		Dimension d = this.getSize();
		dump += p.x + " " + p.y + " " + d.width + " " + d.height + " ";
		
		// Ranges
		dump += getTimeScale() + " ";
		for ( Value v : Value.values() )
			dump += getRange(v) + " ";
		
		int flags = 0;
		flags |= (show_peak.getState() ? 8 : 0);
		flags |= (show_n_peak.getState() ? 4 : 0);
		flags |= (show_freq.getState() ? 2 : 0);
		flags |= (stack_scopes.getState() ? 1 : 0);
		dump += flags + " ";
		
		dump += x_elm_no + " " + ((x_value != null) ? x_value.ordinal() : -1) + " " + ((x_tvalue != null) ? x_tvalue.ordinal() : -1) + " ";
		dump += y_elm_no + " " + ((y_value != null) ? y_value.ordinal() : -1) + " " + ((y_tvalue != null) ? y_tvalue.ordinal() : -1) + " ";
		dump += x_range + " " + y_range + " ";
		
		// Scope type
		dump += scope_type.name() + " ";
		
		for ( int i = 0; i < waveforms.size(); i++ ) {
			OscilloscopeWaveform wf = waveforms.get(i);
			
			// Element number
			String wf_dump = sim.locateElm(wf.elm) + " ";
			
			// Which values are showing
			int elm_flags = wf.isShowing() ? 1 : 0;
			if ( wf.elm instanceof TransistorElm ) {
				for ( TransistorValue v : TransistorValue.values() )
					elm_flags |= (wf.isShowing(v) ? (1 << (v.ordinal()+1)) : 0);
			} else {
				for ( Value v : Value.values() )
					elm_flags |= (wf.isShowing(v) ? (1 << (v.ordinal()+1)) : 0);
			}
			wf_dump += elm_flags + " ";
			
			// Colors of each value
			if ( wf.elm instanceof TransistorElm ) {
				for ( TransistorValue v : TransistorValue.values() )
					wf_dump += wf.getColor(v).getRGB() + " ";
			} else {
				for ( Value v : Value.values() )
					wf_dump += wf.getColor(v).getRGB() + " ";
			}
			wf_dump += wf.getColor().getRGB() + " ";
			
			dump += wf_dump + " ";
			
		}
		
		return dump;
	}
	
	String legacyDump() {
		String dump = "";
		
		if ( getType() == ScopeType.X_VS_Y ) {
			
			dump += "o " + x_elm_no + " ";
			dump += time_scale + " 0 ";
			int flags = 0;
			flags |= 1; // show current
			flags |= 2; // show voltage
			flags |= (show_peak.getState() ? 0 : 4);
		    flags |= (show_freq.getState() ? 8 : 0);
		    flags |= 32;
		    flags |= 64; // plot2d
		    flags |= 128; // plotxy
		    flags |= (show_n_peak.getState() ? 256 : 0);
		    dump += flags + " ";
		    dump += Math.max(x_range, y_range)/2 + " 0 ";
		    dump += " -1 ";
		    dump += y_elm_no;
		    dump += "\n";
			
		} else {
		
			for ( int i = 0; i < waveforms.size(); i++ ) {
				OscilloscopeWaveform wf = waveforms.get(i);
				String wf_dump = "o ";
				
				wf_dump += sim.locateElm(wf.elm) + " ";
				
				wf_dump += getTimeScale() + " ";
				wf_dump += "0 "; // value
				int flags = 0;
				flags |= (wf.isShowing(Value.CURRENT) ? 1 : 0);
				flags |= (wf.isShowing(Value.VOLTAGE) ? 2 : 0);
				flags |= (show_peak.getState() ? 0 : 4);
			    flags |= (show_freq.getState() ? 8 : 0);
			    //flags |= (lockScale ? 16 : 0);
			    flags |= 32;
			    flags |= ((scope_type == ScopeType.I_VS_V) ? 64 : 0);
			    flags |= ((scope_type == ScopeType.X_VS_Y) ? 128 : 0);
			    flags |= (show_n_peak.getState() ? 256 : 0);
			    wf_dump += flags + " ";
			    wf_dump += getRange(Value.VOLTAGE)/2 + " ";
			    wf_dump += getRange(Value.CURRENT)/2 + " ";
			    
			    if ( stack_scopes.getState() )
			    	wf_dump += sim.scopes.indexOf(this) + " ";
			    
			    dump += wf_dump + "\n";
			
			}
		}
		
		return dump;
	}
	
	public void undump(StringTokenizer st) {
		
		// Window location and size
		Point p = new Point(new Integer(st.nextToken()).intValue(), new Integer(st.nextToken()).intValue() );
		Dimension d = new Dimension(new Integer(st.nextToken()).intValue(), new Integer(st.nextToken()).intValue() );
		this.setLocation(p);
		this.setSize(d);
		
		// Ranges
		int new_time_scale = new Integer(st.nextToken()).intValue();
		if ( new_time_scale != getTimeScale() )
			setTimeScale(new_time_scale);
		for ( Value v : Value.values() ) {
			double new_range = new Double(st.nextToken()).doubleValue();
			if ( new_range != getRange(v) )
				setRange(v, new_range);
		}
		
		int flags = new Integer(st.nextToken()).intValue();
		show_peak.setState( (flags & 8) != 0 );
		show_n_peak.setState( (flags & 4) != 0 );
		show_freq.setState( (flags & 2) != 0 );
		boolean stack = ((flags & 1) != 0);
		if ( stack != stack_scopes.getState() )
			stack_scopes.setState(stack);
		
		x_elm_no = new Integer(st.nextToken()).intValue();
		int x_val = new Integer(st.nextToken()).intValue();
		x_value = (x_val != -1) ? Value.values()[x_val] : null;
		int x_tval = new Integer(st.nextToken()).intValue();
		x_tvalue = (x_tval != -1) ? TransistorValue.values()[x_tval] : null;
		
		y_elm_no = new Integer(st.nextToken()).intValue();
		int y_val = new Integer(st.nextToken()).intValue();
		y_value = (y_val != -1) ? Value.values()[y_val] : null;
		int y_tval = new Integer(st.nextToken()).intValue();
		y_tvalue = (y_tval != -1) ? TransistorValue.values()[y_tval] : null;
		
		x_range = new Double(st.nextToken()).doubleValue();
		y_range = new Double(st.nextToken()).doubleValue();
		
		// Scope type
		String new_type = st.nextToken();
		if ( scope_type != ScopeType.valueOf(new_type) )
			setType(ScopeType.valueOf(new_type));
		
		int pos = 0;
		while (st.hasMoreTokens()) {
			int element_num = new Integer(st.nextToken()).intValue();
			boolean added = addElement(sim.getElm(element_num));
			
			if ( added ) {
				OscilloscopeWaveform wf = waveforms.lastElement();
				
				int elm_flags = new Integer(st.nextToken()).intValue();
				wf.show((elm_flags & 1) != 0);
				if ( sim.getElm(element_num) instanceof TransistorElm ) {
					for ( TransistorValue v : TransistorValue.values() ) {
						wf.show(v, (elm_flags & (1 << (v.ordinal()+1))) != 0 );
					}
					for ( TransistorValue v : TransistorValue.values() ) {
						int color = new Integer(st.nextToken()).intValue();
						wf.setColor(v, new Color(color));
					}
				} else {
					for ( Value v : Value.values() ) {
						wf.show(v, (elm_flags & (1 << (v.ordinal()+1))) != 0 );
					}
					for ( Value v : Value.values() ) {
						int color = new Integer(st.nextToken()).intValue();
						wf.setColor(v, new Color(color));
					}
				}
				wf.setColor(new Color(new Integer(st.nextToken()).intValue()));
			}
			
			else {
				st.nextToken(); // flags
				for ( int i = 0; i < Value.values().length; i++ )
					st.nextToken(); // colors
				st.nextToken(); // color
			}
			
			pos++;
		}
		
	}
}