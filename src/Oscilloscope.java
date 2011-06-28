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
	
	CirSim sim;
	
	static final int MAX_ELEMENTS = 10;
	
	// Waveforms to display
	Vector<OscilloscopeWaveform> waveforms;
	Iterator<OscilloscopeWaveform> wfi;
	CircuitElm selected_elm;
	
	static Color bg_color = Color.WHITE;
	static Color grid_color = new Color(0x80,0x80,0x80);
	Font grid_label_font;
	Font info_font;
	static Font label_font;
	static Font selected_label_font;
	
	static NumberFormat display_format;
	static {
		label_font = UIManager.getFont("Label.font");
		selected_label_font = new Font(label_font.getName(), Font.BOLD, label_font.getSize());
		display_format = DecimalFormat.getInstance();
		display_format.setMinimumFractionDigits(2);
	}
	
	OscilloscopeCanvas cv;
	Dimension cv_size;
	
	BufferedImage grid_image; // Gridlines drawn on this
	Graphics2D grid_gfx;
	
	boolean zero_visible; // Used in drawing gridlines
	int zero_x;

	double time_scale; // this is the 'speed' variable in the original Scope class
	
	double voltage_range;
	double current_range;
	double power_range;
	
	// Menu items
	JMenuItem reset;
	JMenuItem t_scale_up, t_scale_down;
	JMenuItem v_scale_up, v_scale_down;
	JMenuItem i_scale_up, i_scale_down;
	JMenuItem p_scale_up, p_scale_down;
	JMenuItem all_scales_up, all_scales_down;
	JMenuItem max_scale;
	ButtonGroup show_options;
	JRadioButtonMenuItem show_v_i_p, show_v_vs_i;
	JCheckBoxMenuItem show_peak, show_n_peak, show_freq, show_grid;
	
	Oscilloscope(CirSim s) {
		
		sim = s;
		waveforms = new Vector<OscilloscopeWaveform>();
		selected_elm = null;
		
		// Setup window
		this.setTitle("Oscilloscope");
		this.setJMenuBar(buildMenu());
		this.setBackground(bg_color);
		this.setSize(600,450);
		this.setMinimumSize(new Dimension(500, 300));
		Point p = sim.getLocation();
		this.setLocation( p.x+sim.getWidth(), p.y+50 );
		this.setLayout(new OscilloscopeLayout());
		addWindowListener(this);
		addComponentListener(this);
		cv = new OscilloscopeCanvas(this);
		this.add(cv);
		
		// Initialize scales
		resetScales();
		
		// Show window
		this.setVisible(true);
		createImage(); // Necessary to allocate gridImage before drawScope is called
		
		Font f = cv.getGraphics().getFont();
		grid_label_font = f.deriveFont(9.0f);
		info_font = f.deriveFont(10.0f);
	}
	
	/* ******************************************************************************************
	 * *                                                                                        *
	 * ******************************************************************************************/
	// Reset to default time and amplitude scales
	private void resetScales() {
		time_scale = 64;
		voltage_range = 5.0;
		current_range = 0.1;
		power_range = 1.0;
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
	private void drawTimeGridlines(Graphics realg) {
		
		// Clear scope window
		/*grid_gfx.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR, 0.0f));
		grid_gfx.fillRect(0, 0, cv_size.width, cv_size.height);
		grid_gfx.setComposite(AlphaComposite.getInstance(AlphaComposite.DST, 1.0f));*/
		grid_gfx.clearRect(0, 0, cv_size.width, cv_size.height);
		
		double ts = sim.timeStep * time_scale;  // Timestep adjusted for current scale
		double tstart = sim.t - ts * cv_size.width;  // Time at the far left of the scope window

		// Calculate step between gridlines (in time)
		double gridStep = 1e-15;	// Time between gridlines
		while ( gridStep/ts < 25 )  // Using ts * constant here makes gridline
			gridStep *= 10;					  // spacing independent of window size
		if ( gridStep/ts > 80 )	// Make sure gridlines aren't too far apart
			gridStep /= 2;
		else if ( gridStep/ts < 35 )
			gridStep *= 2;
		
		double tx = sim.t - (sim.t % gridStep);
		
		// Draw X axis
		grid_gfx.drawRect(0, cv_size.height/2, cv_size.width, 1);
		
		// Draw grid lines parallel to Y axis
		double t;
		int lx = cv_size.width;
		int ln;
		for ( int i = 0; ; i++ ) {
			t = tx - i * gridStep; // time at gridline
			if ( t < 0 ) {				// If the gridline at t = 0 is visible, store
				zero_visible = true;	// its position so we can go back and clear
										// everything to the left of it.
				zero_x = lx;		// Since t here is less than 0, we want the position
									// of the previous gridline, which should have been at
									// t=0.  So use lx from the last time through the loop
				break;
			}
			if ( t < tstart )
				break;
			
			lx = (int) Math.round((t - tstart) / ts); // pixel position of gridline
			grid_gfx.drawLine(lx, 0, lx, cv_size.height);
			
			// Mark time every other gridline
			ln = (int) Math.round(t / gridStep ); // gridline number (since beginning of time)
			if ( ln % 2 == 0 )
				grid_gfx.drawString(getUnitText(t, "s"), lx+2, cv_size.height-2);
		}
		realg.drawImage(grid_image, 0, 0, null);
	}
	
	/* ******************************************************************************************
	 * *                                                                                        *
	 * ******************************************************************************************/
	private void drawHorizontalGridlines(Graphics g) {
		for ( int i = 1; i <= 7; i++ ) {
			g.drawLine(0, i * cv_size.height/8, cv_size.width, i * cv_size.height/8);
		}
	}
	
	/* ******************************************************************************************
	 * *                                                                                        *
	 * ******************************************************************************************/
	private void drawLabels(Graphics realg) {
		realg.setColor(grid_color);
		String str = new String();
		Rectangle2D r;
		for ( int i = 3; i >= -3; i-- ) {
			str = "";
			if ( i == 0 && (this.showingVoltage() || this.showingCurrent() || this.showingPower() ) ) {
				str = "0.00";
			} else {
				if ( this.showingVoltage() )
					str = str.concat(getUnitText(voltage_range/8 * i,"V"));
				if ( this.showingCurrent() ) {
					if ( ! str.equals("")  )
						str = str.concat(" | ");
					str = str.concat(getUnitText(current_range/8 * i,"A"));
				}
				if ( this.showingPower() ) {
					if ( ! str.equals("")  )
						str = str.concat(" | ");
					str = str.concat(getUnitText(power_range/8 * i,"W"));
				}
			}
			r = realg.getFontMetrics().getStringBounds(str, realg);
			int offset = (i > 0) ? 5 : 2;
			realg.clearRect(3, cv_size.height/2-cv_size.height/8*i-offset-(int) Math.ceil(r.getHeight()), (int) Math.ceil(r.getWidth()), (int) Math.ceil(r.getHeight())+2);
			realg.drawString(str, 3, Math.round(cv_size.height/2-cv_size.height/8*i-offset));
		}
	}
	
	/* ******************************************************************************************
	 * *                                                                                        *
	 * ******************************************************************************************/
	private void drawElementInfo(Graphics g) {
		String info[] = new String[10];
		selected_elm.getInfo(info);
		
		FontMetrics fm = g.getFontMetrics();
		int fontHeight = fm.getHeight();
		
		int x = 3;
		for ( int i = 0; i < 10 && info[i] != null; i++ ) {
			g.drawString(info[i], x, this.getHeight()-40+fontHeight*(1+(int)(i/5)));
			if ( i == 5 ) {
				x = 3;
			} else {
				x += Math.round(fm.getStringBounds(info[i], g).getWidth()) + 10;
			}
		}
	}
	
	private void drawCurrentTime(Graphics g) {
		String time = getUnitText(sim.t, "s");
		FontMetrics fm = g.getFontMetrics();
		g.drawString(time, this.getWidth()-(int)fm.getStringBounds(time, g).getWidth()-3, this.getHeight()-40+fm.getHeight());
	}
	
	/* ******************************************************************************************
	 * *                                                                                        *
	 * ******************************************************************************************/
	public void timeStep() {
		for ( wfi = waveforms.iterator(); wfi.hasNext(); )
			wfi.next().timeStep();
	}
	
	// Where the magic happens
	public void drawScope(Graphics realg) {		
		
		realg.setColor(grid_color);
		realg.setFont(grid_label_font);
		
		drawTimeGridlines(realg);
		if (show_grid.getState()) {
			drawHorizontalGridlines(realg);
		}
		
		for ( wfi = waveforms.iterator(); wfi.hasNext(); ) {
			OscilloscopeWaveform wf = wfi.next();
			wf.redraw();
			realg.drawImage(wf.wf_img, 0, 0, null);
		}
		
		// Clear everything to the left of t = 0
		if ( zero_visible ) {
			realg.setColor( bg_color );
			realg.fillRect(0, 0, zero_x, cv_size.height);
		}
		
		drawLabels(realg);
		
		// Clear bottom 40 pixels to draw current time and element info
		Graphics g = this.getGraphics();
		g.clearRect(0, this.getHeight()-40, this.getWidth(), 40);
		drawCurrentTime(g);
		if ( selected_elm != null ) {
			drawElementInfo(g);
		}
		
		cv.repaint();
	}
	
	/* ******************************************************************************************
	 * *                                                                                        *
	 * ******************************************************************************************/
	public void addElement(CircuitElm elm) {
		
		// This was to keep labels from overflowing onto the canvas
		// when the scope window is small.
		if ( waveforms.size() >= MAX_ELEMENTS ) {
			System.out.println("Scope accepts maximum of " + MAX_ELEMENTS + " elements");
			return;
		}
			
		waveforms.add(new OscilloscopeWaveform(elm, this));
		
	}
	
	/* ******************************************************************************************
	 * *                                                                                        *
	 * ******************************************************************************************/	
	public void removeElement(OscilloscopeWaveform wf) {
		this.remove(wf.label);
		waveforms.remove(wf);
		this.validate();
		this.repaint();
		
	}
	
	/* ******************************************************************************************
	 * *                                                                                        *
	 * ******************************************************************************************/
	// Called whenever the canvas is resized and also during Oscilloscope's
	// constructor
	private void createImage() {
		cv_size = cv.getSize();
		grid_image = new BufferedImage(cv_size.width, cv_size.height, BufferedImage.TYPE_INT_ARGB);
		grid_gfx = (Graphics2D) grid_image.getGraphics();
		grid_gfx.setColor(grid_color);
		grid_gfx.setFont(grid_label_font);
		grid_gfx.setBackground(bg_color);
	}
	
	/* ******************************************************************************************
	 * *                                                                                        *
	 * ******************************************************************************************/
	public boolean showingVoltage() {
		for ( wfi = waveforms.iterator(); wfi.hasNext(); ) {
			if ( wfi.next().show_v.getState() )
				return true;
		}
		return false;
	}
	
	public boolean showingCurrent() {
		for ( wfi = waveforms.iterator(); wfi.hasNext(); ) {
			if ( wfi.next().show_i.getState() )
				return true;
		}
		return false;
	}
	
	public boolean showingPower() {
		for ( wfi = waveforms.iterator(); wfi.hasNext(); ) {
			if ( wfi.next().show_p.getState() )
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
	@Override
	public void actionPerformed(ActionEvent e) {
		// Reset
		if ( e.getSource() == reset ) {
			resetScales();
			resetGraph();
		}
		// Change time scale
		else if ( e.getSource() == t_scale_up ) {
			time_scale *= 2;
			resetGraph();
		} else if ( e.getSource() == t_scale_down ) {
			time_scale /= 2;
			resetGraph();
		}
		
		// Change y-axis scales
		else if ( e.getSource() == v_scale_up ) {
			voltage_range *= 2;
			resetGraph();
		} else if ( e.getSource() == v_scale_down ) {
			voltage_range /= 2;
			resetGraph();
		}
		else if ( e.getSource() == i_scale_up ) {
			current_range *= 2;
			resetGraph();
		} else if ( e.getSource() == i_scale_down ) {
			current_range /= 2;
			resetGraph();
		}
		else if ( e.getSource() == p_scale_up ) {
			power_range *= 2;
			resetGraph();
		} else if ( e.getSource() == p_scale_down ) {
			power_range /= 2;
			resetGraph();
		}
		else if ( e.getSource() == all_scales_up ) {
			voltage_range *= 2;
			current_range *= 2;
			power_range *= 2;
			resetGraph();
		} else if ( e.getSource() == all_scales_down ) {
			voltage_range /= 2;
			current_range /= 2;
			power_range /= 2;
			resetGraph();
		}
		// Change value shown on scope
		else if ( e.getSource() instanceof JRadioButtonMenuItem ) {
			System.out.println("Radio: " + e.getActionCommand());
			//String cmd = e.getActionCommand();
			resetGraph();
		}
	}
	
	/* ********************************************************* */
	/* Component Listener Implementation                         */
	/* ********************************************************* */
	@Override
	public void componentShown(ComponentEvent e) { cv.repaint(); }
	
	@Override
	public void componentHidden(ComponentEvent e) {
		if ( this == sim.selected_scope ) {
			sim.selected_scope = null;
		}
		sim.scopes.remove(this);
		this.dispose();
	}
	
	@Override
	public void componentMoved(ComponentEvent e) {}
	
	@Override
	public void componentResized(ComponentEvent e) {
		createImage();
		resetGraph();
		for ( wfi = waveforms.iterator(); wfi.hasNext(); )
			wfi.next().reset(cv_size);
		cv.repaint();
	}
	
	/* ********************************************************* */
	/* Window Listener Implementation                            */
	/* ********************************************************* */
	
	@Override
	public void windowActivated(WindowEvent e) {
		sim.selected_scope = this;
	}

	@Override
	public void windowClosed(WindowEvent e) {}

	@Override
	public void windowClosing(WindowEvent e) {}

	@Override
	public void windowDeactivated(WindowEvent e) {}

	@Override
	public void windowDeiconified(WindowEvent e) {}

	@Override
	public void windowIconified(WindowEvent e) {}

	@Override
	public void windowOpened(WindowEvent e) {}

	
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
		m.add(v_scale_up = new JMenuItem("Voltage Range 2x"));
		m.add(v_scale_down = new JMenuItem("Voltage Range 1/2x"));
		m.addSeparator();
		m.add(i_scale_up = new JMenuItem("Current Range 2x"));
		m.add(i_scale_down = new JMenuItem("Current Range 1/2x"));
		m.addSeparator();
		m.add(p_scale_up = new JMenuItem("Power Range 2x"));
		m.add(p_scale_down = new JMenuItem("Power Range 1/2x"));
		m.addSeparator();
		m.add(all_scales_up = new JMenuItem("All Ranges 2x"));
		m.add(all_scales_down = new JMenuItem("All Ranges 1/2x"));
		//m.add(maxScale = new JMenuItem("Max Scale"));
		for ( int i = 0; i < m.getItemCount(); i++ ) {
			if ( m.getMenuComponent(i) instanceof JMenuItem )
				((JMenuItem) m.getMenuComponent(i)).addActionListener(this);
		}
		
		m = new JMenu("Show");
		mb.add(m);
		show_options = new ButtonGroup();
		m.add(show_v_i_p = new JRadioButtonMenuItem("Voltage/Current/Power"));
		show_v_i_p.setActionCommand("SHOW_VCP");
		m.add(show_v_vs_i = new JRadioButtonMenuItem("Plot V vs I"));
		show_v_vs_i.setActionCommand("SHOW_V_VS_I");
		show_options.add(show_v_i_p);
		show_options.add(show_v_vs_i);
		show_v_i_p.setSelected(true);
		for ( int i = 0; i < 2; i++ )
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