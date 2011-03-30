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
	
	OscilloscopeCanvas cv;
	Dimension cvSize;
	Color bgColor, gridLineColor;
	
	BufferedImage gridImage; // Gridlines drawn on this
	BufferedImage wfImage;   // Waveform drawn on this
	// wfImage is transparent except for the waveform itself, so drawing it on
	// top of gridImage allows the gridlines to show through.
	
	// Variables from the last call of drawScope
	int last_lx;    // Used to determine how far the gridlines have moved and thus
	double last_tx;	// how far to move the waveform image.
	
	int last_py;  // Keeps track of the last point drawn to draw a line between
				  // it and the current point.
	
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
	
	/* Menu items */
	JMenuItem reset;
	JMenuItem timeScaleUp, timeScaleDown;
	JMenuItem scaleUp, scaleDown, maxScale;
	ButtonGroup showOptions;
	JRadioButtonMenuItem showVoltage, showCurrent, showPower, showVvsI;
	JCheckBoxMenuItem showPeak, showNPeak, showFreq;
	
	// For displaying time increments on gridlines
	// This is unacceptable.  Sure it works fine for the LRC, but it's useless for
	// viewing scales smaller than 1ms.  It needs to be dynamically chosen based on
	// the current time scale.  Put the code that reallocates it into the action
	// listener that fires whenever the time scale is changed.
	static final DecimalFormat df = new DecimalFormat("#0.0##"); 
	
	Oscilloscope(CirSim s) {
		
		sim = s;
		elements = new Vector<CircuitElm>(); // no elements to start with
		
		bgColor = Color.WHITE;
		gridLineColor = Color.DARK_GRAY;

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
	}
	
	// Where the magic happens
	public void drawScope(Graphics realg) {
		
		// This will have to change to an array or vector of doubles.
		// One value per element attached to the scope.
		// Use array.  In addElement/removeElement, reallocate array to
		// correct size.
		double val = 0;
		
		// Adjust amplitude scale so that entire waveform always fits
		// When I get around to adding support for multiple elements, the val
		// used for this adjustment will have to be the maximum value of
		// all the elements.
		if ( elements.size() > 0 ) {
			if ( showingValue == VOLTAGE ) {
				val = elements.firstElement().getVoltageDiff();
				if ( Math.abs(val) > voltageRange ) {
					voltageRange *= 1.25;
				}
			} else if ( showingValue == CURRENT ) {
				val = elements.firstElement().getCurrent();
				if ( Math.abs(val) > currentRange ) {
					currentRange *= 1.25;
				}
			}
		}
		
		Graphics g = gridImage.getGraphics();
		
		// Clear scope window
		g.setColor(bgColor);
		g.fillRect(0, 0, cvSize.width, cvSize.height);
		
		// Timestep adjusted for current scale
		double ts = sim.timeStep * timeScale;

		// This makes the gridlines a constant distance (64 pixels) apart
		double gridStep = 64  * sim.timeStep * timeScale;
		double tstart = sim.t - ts * cvSize.width;
		double tx = sim.t - (sim.t % gridStep);
		
		// last_lx is the pixel X coordinate of the rightmost gridline
		// Thus, when a new gridline comes onto the scope, we must switch
		// from the previous rightmost line to the new line
		if ( tx != last_tx )
			last_lx += 64; // this has to be changed if the grid step changes
		
		// Draw X axis
		g.setColor(gridLineColor);
		g.drawRect(0, cvSize.height/2, cvSize.width, 1);
		
		// Draw grid lines parallel to Y axis
		double t;
		int lx = cvSize.width, ln;
		int ps = 0; // number of pixels scope has shifted left since last timestep
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
			
			// Calculate how many pixels the rightmost gridline has moved
			// since the last timestep.  Also, update its position.
			if ( i == 0 ) {
				ps = (last_lx - lx) % 64;
				last_lx = lx;
			}
			
			g.drawLine(lx, 0, lx, cvSize.height);
			
			// Mark time every other gridline
			ln = (int) Math.round(t / gridStep ); // gridline number (since beginning of time)
			if ( ln % 2 == 0 )
				g.drawString(Oscilloscope.df.format(t), lx+2, cvSize.height - 15);
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
		int py = (int) Math.round(((voltageRange/2 - val) / (voltageRange)) * cvSize.height);
		
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
		
		if ( zero_visible ) {
			realg.setColor( bgColor );
			realg.fillRect(0, 0, zero_lx, cvSize.height);
		}
		
		cv.repaint(); // This makes it actually show up
	}
	
	public void addElement(CircuitElm elm) {
		elements.add(elm);
	}
	
	// Called whenever the canvas is resized and also during Oscilloscope's
	// constructor
	private void createImage() {
		cvSize = cv.getSize();
		System.out.println("Canvas size: " + cvSize);
		gridImage = new BufferedImage(cvSize.width, cvSize.height, BufferedImage.TYPE_INT_ARGB);
		wfImage = new BufferedImage(cvSize.width, cvSize.height, BufferedImage.TYPE_INT_ARGB);
		last_lx = cvSize.width-1;
		last_tx = -1;
		last_py = cvSize.height/2;
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