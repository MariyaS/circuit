import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Vector;

class Oscilloscope extends JFrame implements
  ActionListener, ComponentListener, ItemListener {
	private static final long serialVersionUID = 4788980391955265718L;
	
	CirSim sim;
	static final int maxElements = 10;
	Vector<CircuitElm> elements;
	Vector<OscilloscopeElmLabel> elementLabels;
	
	OscilloscopeCanvas cv;
	Dimension cvSize;
	Color bgColor, gridLineColor;
	
	BufferedImage gridImage; // Gridlines drawn on this
	BufferedImage wfImage;   // Waveform drawn on this
	// wfImage is transparent except for the waveform itself, so drawing it on
	// top of gridImage allows the gridlines to show through.
	
	int[] last_py_current; // Keep track of the last point drawn in order to
	int[] last_py_voltage; // draw a line between it and the current point.
	int[] last_py_power;
	double last_t;
	double last_ps;
	
	boolean graph_reset;

	double timeScale; // this is the 'speed' variable in the original Scope class
	double voltageRange;
	double currentRange;
	double powerRange;
	
	/* Menu items */
	JMenuItem reset;
	JMenuItem timeScaleUp, timeScaleDown;
	JMenuItem vScaleUp, vScaleDown;
	JMenuItem iScaleUp, iScaleDown;
	JMenuItem pScaleUp, pScaleDown;
	JMenuItem scaleUp, scaleDown;
	JMenuItem maxScale;
	ButtonGroup showOptions;
	JRadioButtonMenuItem showVIP, showVvsI;
	JCheckBoxMenuItem showPeak, showNPeak, showFreq;
	
	Oscilloscope(CirSim s) {
		
		sim = s;
		elements = new Vector<CircuitElm>();
		elementLabels = new Vector<OscilloscopeElmLabel>();
		
		// Background and grid line colors
		bgColor = Color.WHITE;
		this.setBackground(bgColor);
		gridLineColor = new Color(0x80,0x80,0x80);

		// Set window size and initial position
		this.setSize(350,450);
		this.setMinimumSize(new Dimension(350, 300));
		Point p = sim.getLocation();
		this.setLocation( p.x+sim.getWidth(), p.y+50 );
		
		// Window title and menu bar
		this.setTitle("Oscilloscope");
		this.setJMenuBar(buildMenu());
		
		// Canvas for displaying scope
		this.setLayout(new OscilloscopeLayout());
		cv = new OscilloscopeCanvas(this);
		this.add(cv);
		cv.addComponentListener(this);
		
		// Initialize scales
		resetScales();
		graph_reset = false;
		
		this.setVisible(true);
		
		last_py_current = new int[maxElements];
		last_py_voltage = new int[maxElements];
		last_py_power = new int[maxElements];
		createImage(); // Necessary to allocate dbimage before the first
					   // call to drawScope
		
		last_t = 0;
	}
	
	// Reset to default time and amplitude scales
	private void resetScales() {
		timeScale = 64;
		voltageRange = 10.0;
		currentRange = 0.1;
		powerRange = 1.0;
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
		
		g = wfImage.getGraphics();
		for ( int i = 0; i < elements.size(); i++ ) {
			int py = 0;

			if ( elementLabels.get(i).showingVoltage() ) {
				py = (int) Math.round(((voltageRange/2 - elements.get(i).getVoltageDiff()) / voltageRange) * cvSize.height);
				g.setColor(elementLabels.get(i).getVColor());
				CircuitElm.drawThickLine(g, cvSize.width-ps-2, last_py_voltage[i], cvSize.width-2, py);
				last_py_voltage[i] = py;
			}
			
			if ( elementLabels.get(i).showingCurrent() ) {
				py = (int) Math.round(((currentRange/2 - elements.get(i).getCurrent()) / currentRange) * cvSize.height);
				g.setColor(elementLabels.get(i).getIColor());
				CircuitElm.drawThickLine(g, cvSize.width-ps-2, last_py_current[i], cvSize.width-2, py);
				last_py_current[i] = py;
			}
			
			if ( elementLabels.get(i).showingPower() ) {
				py = (int) Math.round(((powerRange/2 - elements.get(i).getPower()) / powerRange) * cvSize.height);
				g.setColor(elementLabels.get(i).getPColor());
				CircuitElm.drawThickLine(g, cvSize.width-ps-2, last_py_power[i], cvSize.width-2, py);
				last_py_power[i] = py;
			}
		}
		
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
		
		// This was to keep labels from overflowing onto the canvas
		// when the scope window is small.
		if ( elements.size() >= maxElements ) {
			System.out.println("Scope accepts maximum of " + maxElements + " elements");
			return;
		}
		
		// Add name of component to oscilloscope window
		OscilloscopeElmLabel lbl = new OscilloscopeElmLabel(elm, this); 
		elementLabels.add(lbl);
		this.add(lbl);
		this.validate();
		
		// Add element to list to show values of
		elements.add(elm);
	}
	
	public void removeElement(int index) {
		
		elements.remove(index);
		
		// Remove label from window and array
		this.remove(elementLabels.get(index));
		elementLabels.remove(index);
		
		// Repaint after removing label
		this.validate();
		this.repaint();
	}
	
	// Called whenever the canvas is resized and also during Oscilloscope's
	// constructor
	private void createImage() {
		cvSize = cv.getSize();
		gridImage = new BufferedImage(cvSize.width, cvSize.height, BufferedImage.TYPE_INT_ARGB);
		wfImage = new BufferedImage(cvSize.width, cvSize.height, BufferedImage.TYPE_INT_ARGB);
		Arrays.fill(last_py_current, cvSize.height/2);
		Arrays.fill(last_py_voltage, cvSize.height/2);
		Arrays.fill(last_py_power, cvSize.height/2);
		last_ps = 0;
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
		else if ( e.getSource() == timeScaleUp ) {
			timeScale *= 2;
			resetGraph();
		} else if ( e.getSource() == timeScaleDown ) {
			timeScale /= 2;
			resetGraph();
		}
		// Change y-axis scale
		else if ( e.getSource() == vScaleUp ) {
			voltageRange *= 2;
			resetGraph();
		} else if ( e.getSource() == vScaleDown ) {
			voltageRange /= 2;
			resetGraph();
		}
		else if ( e.getSource() == iScaleUp ) {
			currentRange *= 2;
			resetGraph();
		} else if ( e.getSource() == iScaleDown ) {
			currentRange /= 2;
			resetGraph();
		}
		else if ( e.getSource() == pScaleUp ) {
			powerRange *= 2;
			resetGraph();
		} else if ( e.getSource() == pScaleDown ) {
			powerRange /= 2;
			resetGraph();
		}
		else if ( e.getSource() == scaleUp ) {
			voltageRange *= 2;
			currentRange *= 2;
			powerRange *= 2;
			resetGraph();
		} else if ( e.getSource() == scaleDown ) {
			voltageRange *= 2;
			currentRange *= 2;
			powerRange /= 2;
			resetGraph();
		}
		// Change value shown on scope
		else if ( e.getSource() instanceof JRadioButtonMenuItem ) {
			System.out.println("Radio: " + e.getActionCommand());
			//String cmd = e.getActionCommand();
			resetGraph();
		}
		// Remove an element from scope
		else if ( e.getSource() instanceof MenuItem && e.getActionCommand().equals("REMOVE_ELM") ) {
			
			// Surely there's a cleaner way to do this
			OscilloscopeElmLabel srcLabel = (OscilloscopeElmLabel) ((PopupMenu) ((MenuItem) e.getSource()).getParent()).getParent();
			
			int i = elementLabels.indexOf( srcLabel );
			if ( i == -1 ) {
				System.err.println("Error: element label not found");
			}
			
			removeElement(i);
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
		m.add(vScaleUp = new JMenuItem("Voltage Scale 2x"));
		m.add(vScaleDown = new JMenuItem("Voltage Scale 1/2x"));
		m.addSeparator();
		m.add(iScaleUp = new JMenuItem("Current Scale 2x"));
		m.add(iScaleDown = new JMenuItem("Current Scale 1/2x"));
		m.addSeparator();
		m.add(pScaleUp = new JMenuItem("Power Scale 2x"));
		m.add(pScaleDown = new JMenuItem("Power Scale 1/2x"));
		m.addSeparator();
		m.add(scaleUp = new JMenuItem("All Scales 2x"));
		m.add(scaleDown = new JMenuItem("All Scales 1/2x"));
		//m.add(maxScale = new JMenuItem("Max Scale"));
		for ( int i = 0; i < m.getItemCount(); i++ ) {
			if ( m.getMenuComponent(i) instanceof JMenuItem )
				((JMenuItem) m.getMenuComponent(i)).addActionListener(this);
		}
		
		/* */
		m = new JMenu("Show");
		mb.add(m);
		showOptions = new ButtonGroup();
		m.add(showVIP = new JRadioButtonMenuItem("Voltage/Current/Power"));
		showVIP.setActionCommand("SHOW_VCP");
		m.add(showVvsI = new JRadioButtonMenuItem("Plot V vs I"));
		showVvsI.setActionCommand("SHOW_V_VS_I");
		showOptions.add(showVIP);
		showOptions.add(showVvsI);
		showVIP.setSelected(true);
		for ( int i = 0; i < 2; i++ )
			((JRadioButtonMenuItem) m.getMenuComponent(i)).addActionListener(this);
		
		/* */
		m.addSeparator();
		m.add(showPeak = new JCheckBoxMenuItem("Peak Value"));
		m.add(showNPeak = new JCheckBoxMenuItem("Negative Peak Value"));
		m.add(showFreq = new JCheckBoxMenuItem("Frequency"));
		for ( int i = 3; i < 6; i++ )
			((JCheckBoxMenuItem) m.getMenuComponent(i)).addItemListener(this);
		
		return mb;
	}
	
}