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
	
	// Waveforms to display
	private Vector<OscilloscopeWaveform> waveforms;
	private Iterator<OscilloscopeWaveform> wfi;
	private CircuitElm selected_elm;
	
	private static final Color bg_color = Color.WHITE;
	private static final Color grid_color = new Color(0x80,0x80,0x80);
	
	private static final Font label_font = UIManager.getFont("Label.font");
	private static final Font selected_label_font = new Font(label_font.getName(), Font.BOLD, label_font.getSize());
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

	// Number of time steps per scope pixel
	// Variable named 'speed' in the original Scope class
	private int time_scale;
	private double[] range;
	
	private static final int DEFAULT_TIME_SCALE = 64;
	private static final double DEFAULT_VOLTAGE_RANGE = 5;
	private static final double DEFAULT_CURRENT_RANGE = 0.1;
	private static final double DEFAULT_POWER_RANGE = 0.5;
	
	static enum ScopeType { VIP_VS_T, V_VS_I, X_VS_Y }
	private ScopeType scope_type;
	
	static enum Value { VOLTAGE, CURRENT, POWER }
	
	// Menu items
	private JMenuItem reset;
	private JMenuItem t_scale_up, t_scale_down;
	private JMenuItem v_range_up, v_range_down;
	private JMenuItem i_range_up, i_range_down;
	private JMenuItem p_range_up, p_range_down;
	private JMenuItem all_ranges_up, all_ranges_down;
	private JMenuItem fit_ranges;
	private ButtonGroup show_options;
	private JRadioButtonMenuItem show_vip_vs_t, show_v_vs_i, show_x_vs_y;
	private JCheckBoxMenuItem show_peak, show_n_peak, show_freq, show_grid;
	
	Oscilloscope(CirSim s) {
		
		// Initialize variables
		sim = s;
		waveforms = new Vector<OscilloscopeWaveform>();
		selected_elm = null;
		
		scope_type = ScopeType.VIP_VS_T;
		
		// Setup window
		setTitle("Oscilloscope");
		setJMenuBar(buildMenu());
		setLayout(new OscilloscopeLayout());
		setBackground(bg_color);
		setSize(600,450);
		setMinimumSize(new Dimension(500, 300));
		Point p = sim.getLocation();
		setLocation( p.x+sim.getWidth(), p.y+50 );
		addWindowListener(this);
		addComponentListener(this);
		canvas = new OscilloscopeCanvas(this);
		add(canvas);
		
		// Initialize scales
		range = new double[Value.values().length];
		resetScales();
		
		// Show window
		setVisible(true);
		handleResize(); // Necessary to allocate gridImage before drawScope is called
	}
	
	/* ******************************************************************************************
	 * *                                                                                        *
	 * ******************************************************************************************/
	// Reset to default time and amplitude scales
	private void resetScales() {
		time_scale = DEFAULT_TIME_SCALE;
		range[Value.VOLTAGE.ordinal()] = DEFAULT_VOLTAGE_RANGE;
		range[Value.CURRENT.ordinal()] = DEFAULT_CURRENT_RANGE;
		range[Value.POWER.ordinal()] = DEFAULT_POWER_RANGE;
	}
	
	/* ******************************************************************************************
	 * *                                                                                        *
	 * ******************************************************************************************/
	// Clear the waveform image when changing time or amplitude scales
	private void resetGraph() {
		for ( wfi = waveforms.iterator(); wfi.hasNext(); )
			wfi.next().reset();
	}
	
	/* ******************************************************************************************
	 * *                                                                                        *
	 * ******************************************************************************************/
	private void drawTimeGridlines(Graphics gfx) {
		
		double ts = sim.timeStep * time_scale;  // Timestep adjusted for current scale
		double tstart = sim.t - ts * canvas_size.width;  // Time at the far left of the scope window

		// Calculate step between gridlines (in time)
		double grid_step = 1e-15;	// Time between gridlines
		while ( grid_step/ts < 25 )  // Using ts * constant here makes gridline
			grid_step *= 10;					  // spacing independent of window size
		if ( grid_step/ts > 80 )	// Make sure gridlines aren't too far apart
			grid_step /= 2;
		else if ( grid_step/ts < 35 )  // Make sure gridlines aren't too close together
			grid_step *= 2;
		
		double tx = sim.t - (sim.t % grid_step);
		
		// Draw X axis
		gfx.drawRect(0, canvas_size.height/2, canvas_size.width, 1);
		
		// Draw grid lines parallel to Y axis
		double t;
		int lx = canvas_size.width;
		int ln;
		for ( int i = 0; ; i++ ) {
			t = tx - i * grid_step; // time at gridline
			if ( t < 0 || t < tstart )
				break;
			
			lx = (int) Math.round((t - tstart) / ts); // pixel position of gridline
			gfx.drawLine(lx, 0, lx, canvas_size.height);
			
			// Mark time every other gridline
			ln = (int) Math.round(t / grid_step ); // gridline number (since beginning of time)
			if ( ln % 2 == 0 )
				gfx.drawString(getUnitText(t, "s"), lx+2, canvas_size.height-2);
		}
	}
	
	/* ******************************************************************************************
	 * *                                                                                        *
	 * ******************************************************************************************/
	private void drawHorizontalGridlines(Graphics gfx) {
		for ( int i = 1; i <= 7; i++ )
			gfx.drawLine(0, i * canvas_size.height/8, canvas_size.width, i * canvas_size.height/8);
	}
	
	/* ******************************************************************************************
	 * *                                                                                        *
	 * ******************************************************************************************/
	private void drawLabels(Graphics gfx) {
		String str;
		Rectangle2D r;
		for ( int i = 3; i >= -3; i-- ) {
			str = "";
			if ( i == 0 ) {
				str = "0.00";
			} else {
				if ( showingValue(Value.VOLTAGE) )
					str = str.concat(getUnitText(range[Value.VOLTAGE.ordinal()]/8 * i,"V"));
				if ( showingValue(Value.CURRENT) ) {
					if ( !str.isEmpty() ) { str = str.concat(" | "); }
					str = str.concat(getUnitText(range[Value.CURRENT.ordinal()]/8 * i,"A"));
				}
				if ( showingValue(Value.POWER) ) {
					if ( !str.isEmpty() ) { str = str.concat(" | "); }
					str = str.concat(getUnitText(range[Value.POWER.ordinal()]/8 * i,"W"));
				}
			}
			r = gfx.getFontMetrics().getStringBounds(str, gfx);
			int offset = (i > 0) ? 5 : 2;
			gfx.clearRect(3, canvas_size.height/2-canvas_size.height/8*i-offset-(int) Math.ceil(r.getHeight()), (int) Math.ceil(r.getWidth()), (int) Math.ceil(r.getHeight())+2);
			gfx.drawString(str, 3, Math.round(canvas_size.height/2-canvas_size.height/8*i-offset));
		}
	}
	
	/* ******************************************************************************************
	 * *                                                                                        *
	 * ******************************************************************************************/
	private void drawElementInfo(Graphics gfx) {
		String info[] = new String[10];
		selected_elm.getInfo(info);
		
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
	}
	
	private void drawCurrentTime(Graphics gfx) {
		String time = getUnitText(sim.t, "s");
		FontMetrics fm = gfx.getFontMetrics();
		gfx.drawString(time, this.getWidth()-(int)fm.getStringBounds(time, gfx).getWidth()-3, fm.getHeight());
	}
	
	/* ******************************************************************************************
	 * *                                                                                        *
	 * ******************************************************************************************/
	public void timeStep() {
		for ( wfi = waveforms.iterator(); wfi.hasNext(); )
			wfi.next().timeStep();
	}
	
	// Where the magic happens
	public void drawScope(Graphics canvas_gfx) {		
		
		// Clear main image
		main_img_gfx.clearRect(0, 0, canvas_size.width, canvas_size.height);
		
		// Draw gridlines
		drawTimeGridlines(main_img_gfx);
		if ( show_grid.getState() )
			drawHorizontalGridlines(main_img_gfx);
		
		// Draw waveforms
		for ( wfi = waveforms.iterator(); wfi.hasNext(); ) {
			OscilloscopeWaveform wf = wfi.next();
			wf.redraw();
			main_img_gfx.drawImage(wf.wf_img, 0, 0, null);
		}
		
		// Draw labels
		if ( showingValue(Value.VOLTAGE) || showingValue(Value.CURRENT) || showingValue(Value.POWER) )
			drawLabels(main_img_gfx);
		
		// Draw element info and current time
		info_img_gfx.clearRect(0, 0, info_img.getWidth(), info_img.getHeight());
		if ( selected_elm != null )
			drawElementInfo(info_img_gfx);
		drawCurrentTime(info_img_gfx);
		
		// Update window
		canvas_gfx.drawImage(main_img, 0, 0, null);
		info_window_gfx.drawImage(info_img, 0, 0, null);
		canvas.repaint();
	}
	
	/* ******************************************************************************************
	 * *                                                                                        *
	 * ******************************************************************************************/
	public void addElement(CircuitElm elm) {
		// This was to keep labels from overflowing onto the canvas when the scope window is small.
		if ( waveforms.size() >= MAX_ELEMENTS ) {
			System.out.println("Scope accepts maximum of " + MAX_ELEMENTS + " elements");
			return;
		}
		waveforms.add(new OscilloscopeWaveform(elm, this));
	}
	
	public void addElement(CircuitElm elm, int flags) {
		addElement(elm);
		waveforms.lastElement().setShow(flags);
	}
		
	public void removeElement(OscilloscopeWaveform wf) {
		remove(wf.label);
		waveforms.remove(wf);
		validate();
		repaint();	
	}
	
	public void setSelectedElement(OscilloscopeWaveform wf) {
		selected_elm = wf.elm;
		for ( wfi = waveforms.iterator(); wfi.hasNext(); )
			wfi.next().label.setFont(label_font);
		wf.label.setFont(Oscilloscope.selected_label_font);
	}
	
	/* ******************************************************************************************
	 * *                                                                                        *
	 * ******************************************************************************************/
	// Called whenever the canvas is resized and also during Oscilloscope's
	// constructor
	private void handleResize() {
		canvas_size = canvas.getSize();
		
		main_img = new BufferedImage(canvas_size.width, canvas_size.height, BufferedImage.TYPE_INT_ARGB);
		main_img_gfx = (Graphics2D) main_img.getGraphics();
		main_img_gfx.setColor(grid_color);
		main_img_gfx.setFont(grid_label_font);
		main_img_gfx.setBackground(bg_color);
		
		info_img = new BufferedImage(canvas_size.width, 40, BufferedImage.TYPE_INT_ARGB);
		info_img_gfx = (Graphics2D) info_img.getGraphics();
		info_img_gfx.setColor(Color.BLACK);
		info_img_gfx.setFont(info_font);
		info_img_gfx.setBackground(bg_color);
		
		info_window_gfx = (Graphics2D) this.getGraphics().create(0, this.getHeight()-40, this.getWidth(), 40);
	}
	
	public void setType(ScopeType new_type) {;
		switch (new_type) {
			case VIP_VS_T:	show_vip_vs_t.setSelected(true);	break;
			case V_VS_I:	show_v_vs_i.setSelected(true);		break;
			case X_VS_Y:	show_x_vs_y.setSelected(true);		break;
		}
		scope_type = new_type;
	}
	
	public void setTimeScale(int new_scale) {
		time_scale = new_scale;
		resetGraph();
	}
	
	public int getTimeScale() { return time_scale; }
	
	public void setRange(Value value, double new_range) {
		range[value.ordinal()] = new_range;
		resetGraph();
	}
	
	public double getRange(Value value) { return range[value.ordinal()]; }
	
	/* ******************************************************************************************
	 * *                                                                                        *
	 * ******************************************************************************************/
	public boolean showingValue(Value value) {
		for ( wfi = waveforms.iterator(); wfi.hasNext(); ) {
			if ( wfi.next().showingValue(value) )
				return true;
		}
		return false;
	}
	
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
			resetScales();
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
		}
		
		// Change value shown on scope
		else if ( e.getSource() == show_vip_vs_t )
			setType(ScopeType.VIP_VS_T);
		else if ( e.getSource() == show_v_vs_i )
			setType(ScopeType.V_VS_I);
		else if ( e.getSource() == show_x_vs_y )
			setType(ScopeType.X_VS_Y);
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
		for ( wfi = waveforms.iterator(); wfi.hasNext(); )
			wfi.next().reset(canvas_size);
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

	
	/* ********************************************************* */
	/* Create menu bar                                           */
	/* ********************************************************* */
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
		m.add(show_v_vs_i = new JRadioButtonMenuItem("Plot V vs I"));
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
		
		return mb;
	}
}