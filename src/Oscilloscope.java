import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.text.DecimalFormat;
import java.util.Vector;

class Oscilloscope extends JFrame implements
  ActionListener, ComponentListener, ItemListener {
	private static final long serialVersionUID = 4788980391955265718L;
	
	CirSim sim;
	Vector<CircuitElm> elements;
	Vector<Color> elementColors;
	Vector<OscilloscopeElmLabel> elementLabels;
	
	OscilloscopeCanvas cv;
	Dimension cvSize;
	Color bgColor, gridLineColor;
	
	BufferedImage gridImage; // Gridlines drawn on this
	BufferedImage wfImage;   // Waveform drawn on this
	// wfImage is transparent except for the waveform itself, so drawing it on
	// top of gridImage allows the gridlines to show through.
	
	// Variables from the last call of drawScope
	int last_py;  // Keeps track of the last point drawn to draw a line between
				  // it and the current point.
	double last_t;
	double last_ps;
	
	boolean graph_reset;

	double timeScale; // this is the 'speed' variable in the original Scope class
	double voltageRange;
	double currentRange;
	
	// The type of measurement that's currently displayed
	static final int VOLTAGE = 1;
	static final int CURRENT = 2;
	static final int POWER = 3;
	static final int V_VS_I = 4;
	int showingValue;
	
	String[] info;
	
	/* Menu items */
	JMenuItem reset;
	JMenuItem timeScaleUp, timeScaleDown;
	JMenuItem scaleUp, scaleDown, maxScale;
	ButtonGroup showOptions;
	JRadioButtonMenuItem showVoltage, showCurrent, showPower, showVvsI;
	JCheckBoxMenuItem showPeak, showNPeak, showFreq;
	
	Oscilloscope(CirSim s) {
		
		sim = s;
		elements = new Vector<CircuitElm>(); // no elements to start with
		elementLabels = new Vector<OscilloscopeElmLabel>();
		elementColors = new Vector<Color>();
		
		bgColor = Color.WHITE;
		gridLineColor = new Color(0x80,0x80,0x80);
		this.setBackground(bgColor);

		Point p = sim.getLocation();
		this.setLocation( p.x+sim.getWidth(), p.y+50 );
		this.setSize(350,450);
		this.setMinimumSize(new Dimension(200, 250));
		
		this.setTitle("Oscilloscope");
		this.setJMenuBar(buildMenu());
		showingValue = VOLTAGE;
		
		graph_reset = false;
		
		this.setLayout(new OscilloscopeLayout());
		
		cv = new OscilloscopeCanvas(this);
		this.add(cv);
		cv.addComponentListener(this);
		
		resetScales();
		
		this.setVisible(true);
		
		createImage(); // Necessary to allocate dbimage before the first
					   // call to drawScope
		
		last_t = 0;
		info = new String[10];
	}
	
	// Reset to default time and amplitude scales
	private void resetScales() {
		timeScale = 64;
		voltageRange = 5.0;
		currentRange = 0.1;
	}
	
	// Clear the waveform image when changing time or amplitude scales
	private void resetGraph() {
		graph_reset = true;
		last_ps = 0;
	}
	
	// Convert time to string with appropriate unit
	private String formatTime( double t ) {
		DecimalFormat df = new DecimalFormat("#0.0##");
		if ( t == 0 )
			return "0.00s";
		else if ( t < 10e-12 )
			return df.format(t/10e-15).concat("fs");
		else if ( t < 10e-9 )
			return df.format(t/10e-12).concat("ps");
		else if ( t < 10e-6 )
			return df.format(t/10e-9).concat("ns");
		else if ( t < 10e-3 )
			return df.format(t/10e-6).concat("\u03bcs");
		else if ( t < 1 )
			return df.format(t/10e-3).concat("ms");
		else
			return df.format(t).concat("s");
	}
	
	// Where the magic happens
	public void drawScope(Graphics realg) {		
		
		// This will have to change to an array or vector of doubles.
		// One value per element attached to the scope.
		// Use array.  In addElement/removeElement, reallocate array to
		// correct size.
		double val = 0;
		if ( elements.size() > 0 ) {
			if ( showingValue == VOLTAGE )
				val = elements.get(0).getVoltageDiff();
			else if ( showingValue == CURRENT )
				val = elements.get(0).getCurrent();
		}
		
		// Clear scope window
		Graphics g = gridImage.getGraphics();
		g.setColor(bgColor);
		g.fillRect(0, 0, cvSize.width, cvSize.height);
		
		double ts = sim.timeStep * timeScale;  // Timestep adjusted for current scale
		double tstart = sim.t - ts * cvSize.width;  // Time at the far left of the scope window

		double gridStep = 1e-15;	// Time between gridlines
		while ( gridStep/ts < 25 )  // Using ts * constant here makes gridline
			gridStep *= 10;					  // spacing independent of window size
		if ( gridStep/ts > 80 )	// Make sure gridlines aren't too far apart
			gridStep /= 2;
		else if ( gridStep/ts < 35 )
			gridStep *= 2;
		
		double tx = sim.t - (sim.t % gridStep);
		
		// Calculate number of pixels to shift waveform image
		double total_ps = (sim.t - last_t) / ts + last_ps;
		last_t = sim.t;
		int ps = (int) Math.floor( total_ps );
		last_ps = total_ps - ps; // Store the remainder to add to the next shift

		// Draw X axis
		g.setColor(gridLineColor);
		g.drawRect(0, cvSize.height/2, cvSize.width, 1);
		
		// Draw grid lines parallel to Y axis
		double t;
		int lx = cvSize.width;
		int ln;
		boolean zero_visible = false;
		int zero_lx = 0;
		for ( int i = 0; ; i++ ) {
			
			t = tx - i * gridStep; // time at gridline
			
			if ( t < 0 ) {				// If the gridline at t = 0 is visible, store
				zero_visible = true;	// its position so we can go back and clear
										// everything to the left of it.
				zero_lx = lx;		// Since t here is less than 0, we want the position
									// of the previous gridline, which should have been at
									// t=0.  So use lx from the last time through the loop
				break;
			}
			if ( t < tstart )
				break;
			
			lx = (int) Math.round((t - tstart) / ts); // pixel position of gridline
			
			g.drawLine(lx, 0, lx, cvSize.height);
			
			// Mark time every other gridline
			ln = (int) Math.round(t / gridStep ); // gridline number (since beginning of time)
			if ( ln % 2 == 0 )
				g.drawString(formatTime(t), lx+2, cvSize.height - 15);
		}
		
		realg.drawImage(gridImage, 0, 0, null);
		
		// Shift waveform image to the left according to the value of ps
		// Set rightmost columns which were vacated by shift to transparent
		// Thank you, StackOverflow
		// http://stackoverflow.com/questions/2825837/java-how-to-do-fast-copy-of-a-bufferedimages-pixels-unit-test-included
		wfImage.getRaster().setRect(-ps, 0, wfImage.getRaster());
		Graphics2D g2d = (Graphics2D) wfImage.getGraphics();
		Composite c = g2d.getComposite();
		g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR, 0.0f));
		g2d.fill(new Rectangle(cvSize.width-ps, 0, ps, cvSize.height));
		g2d.setComposite(c);
		
		// Calculate pixel Y coordinate of current value
		int py = 0;
		if ( showingValue == VOLTAGE )
			py = (int) Math.round(((voltageRange/2 - val) / (voltageRange)) * cvSize.height);
		else if ( showingValue == CURRENT )
			py = (int) Math.round(((currentRange/2 - val) / (currentRange)) * cvSize.height);
		
		// Draw a line from the previous point (which has now been moved ps pixels from the right of the image)
		// to the current point (which is at the right of the image)
		g = wfImage.getGraphics();
		g.setColor(Color.GREEN);
		CircuitElm.drawThickLine(g, cvSize.width-ps-2, last_py, cvSize.width-2, py);
		last_py = py;
		
		// Clear the waveform image after a reset.  Must be done here after drawing the line to the current
		// point.  Changing the time scale will result in the point (cvSize.width-ps, last_py) being in the
		// wrong place since that point will be positioned according to the old time scale.  Thus, when the
		// graph is reset, we must remove the line between (cvSize.width-ps, last_py) and (cvSize.width, py)
		if ( graph_reset == true ) {
			c = g2d.getComposite();
			g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR, 0.0f));
			g2d.fill(new Rectangle(0, 0, cvSize.width, cvSize.height));
			g2d.setComposite(c);
			graph_reset = false;
		}
		
		realg.drawImage(wfImage, 0, 0, null);
		
		// Do not draw anything to the left of t = 0
		if ( zero_visible ) {
			realg.setColor( bgColor );
			realg.fillRect(0, 0, zero_lx, cvSize.height);
		}
		
		cv.repaint(); // This makes it actually show up
	}
	
	public void addElement(CircuitElm elm) {
		
		// Add name of component to oscilloscope window
		elm.getInfo(info);
		OscilloscopeElmLabel lbl = new OscilloscopeElmLabel(info[0]); 
		elementLabels.add(lbl);
		this.add(lbl);
		this.validate();
		
		// Add element to list to show values of
		elements.add(elm);
	}
	
	// Called whenever the canvas is resized and also during Oscilloscope's
	// constructor
	private void createImage() {
		cvSize = cv.getSize();
		gridImage = new BufferedImage(cvSize.width, cvSize.height, BufferedImage.TYPE_INT_ARGB);
		wfImage = new BufferedImage(cvSize.width, cvSize.height, BufferedImage.TYPE_INT_ARGB);
		last_py = cvSize.height/2;
		last_ps = 0;
	}
	
	/* ********************************************************* */
	/* Action Listener Implementation                            */
	/* ********************************************************* */
	@Override
	public void actionPerformed(ActionEvent e) {
		if ( e.getSource() == reset ) {
			resetScales();
			resetGraph();
		} else if ( e.getSource() == timeScaleUp ) {
			timeScale *= 2;
			resetGraph();
		} else if ( e.getSource() == timeScaleDown ) {
			timeScale /= 2;
			resetGraph();
		} else if ( e.getSource() == scaleUp ) {
			if ( showingValue == VOLTAGE ) {
				voltageRange *= 2;
			} else if ( showingValue == CURRENT ) {
				currentRange *= 2;
			}
			resetGraph();
		} else if ( e.getSource() == scaleDown ) {
			if ( showingValue == VOLTAGE ) {
				voltageRange /= 2;
			} else if ( showingValue == CURRENT ) {
				currentRange /= 2;
			}
			resetGraph();
		} else if ( e.getSource() instanceof JRadioButtonMenuItem ) {
			System.out.println("Radio: " + e.getActionCommand());
			String cmd = e.getActionCommand();
			if ( cmd.equals("SHOW_VOLTAGE") ) {
				showingValue = VOLTAGE;
			} else if ( cmd.equals("SHOW_CURRENT") ) {
				showingValue = CURRENT;
			} else if ( cmd.equals("SHOW_POWER") ) {
				showingValue = POWER;
			} else if ( cmd.equals("SHOW_V_VS_I") ) {
				showingValue = V_VS_I;
			}
			System.out.println(showingValue);
			resetGraph();
		}
	}
	
	/* ********************************************************* */
	/* Item Listener Implementation                              */
	/* ********************************************************* */
	@Override
	public void itemStateChanged(ItemEvent e) {
		System.out.println(showOptions.getSelection().toString());
	}
	
	/* ********************************************************* */
	/* Component Listener Implementation                         */
	/* ********************************************************* */
	@Override
	public void componentShown(ComponentEvent e) { cv.repaint(); }
	public void componentHidden(ComponentEvent e) {}
	public void componentMoved(ComponentEvent e) {}
	public void componentResized(ComponentEvent e) {
		createImage();
		resetGraph();
		cv.repaint();
	}
	
	/* ********************************************************* */
	/* Create menu bar                                           */
	/* ********************************************************* */
	private JMenuBar buildMenu() {
		
		JMenuBar mb = new JMenuBar();
		
		/* */
		JMenu m = new JMenu("Reset");
		mb.add(m);
		m.add(reset = new JMenuItem("Reset"));
		reset.addActionListener(this);
		
		/* */
		m = new JMenu("Time Scale");
		mb.add(m);
		m.add(timeScaleUp = new JMenuItem("Time Scale 2x"));
		m.add(timeScaleDown = new JMenuItem("Time Scale 1/2x"));
		for ( int i = 0; i < m.getItemCount(); i++ )
			((JMenuItem) m.getMenuComponent(i)).addActionListener(this);
		
		/* */
		m = new JMenu("Amplitude Scale");
		mb.add(m);
		m.add(scaleUp = new JMenuItem("Scale 2x"));
		m.add(scaleDown = new JMenuItem("Scale 1/2x"));
		m.add(maxScale = new JMenuItem("Max Scale"));
		for ( int i = 0; i < m.getItemCount(); i++ )
			((JMenuItem) m.getMenuComponent(i)).addActionListener(this);
		
		/* */
		m = new JMenu("Show");
		mb.add(m);
		showOptions = new ButtonGroup();
		m.add(showVoltage = new JRadioButtonMenuItem("Voltage"));
		showVoltage.setActionCommand("SHOW_VOLTAGE");
		m.add(showCurrent = new JRadioButtonMenuItem("Current"));
		showCurrent.setActionCommand("SHOW_CURRENT");
		m.add(showPower = new JRadioButtonMenuItem("Power Consumed"));
		showPower.setActionCommand("SHOW_POWER");
		m.add(showVvsI = new JRadioButtonMenuItem("Plot V vs I"));
		showVvsI.setActionCommand("SHOW_V_VS_I");
		showOptions.add(showVoltage);
		showOptions.add(showCurrent);
		showOptions.add(showPower);
		showOptions.add(showVvsI);
		showVoltage.setSelected(true);
		for ( int i = 0; i < 4; i++ )
			((JRadioButtonMenuItem) m.getMenuComponent(i)).addActionListener(this);
		
		/* */
		m.addSeparator();
		m.add(showPeak = new JCheckBoxMenuItem("Peak Value"));
		m.add(showNPeak = new JCheckBoxMenuItem("Negative Peak Value"));
		m.add(showFreq = new JCheckBoxMenuItem("Frequency"));
		for ( int i = 5; i < 8; i++ )
			((JCheckBoxMenuItem) m.getMenuComponent(i)).addItemListener(this);
		
		return mb;
	}
	
}