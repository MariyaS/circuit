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
	Dimension winSize;
	BufferedImage dbimage;
	
	Color bgColor, gridLineColor;
	
	// 1 pixel per time step makes this move at the same
	// rate as Scope
	static final int pixPerTimeStep = 1;
	double timeScale;
	
	double voltageRange;
	double currentRange;
	
	
	MemoryImageSource imageSource;
	Image image;
	int[] pixels;
	boolean[] dpixels;
	int draw_ox, draw_oy;
	
	/* Menu items */
	JMenuItem reset;
	JMenuItem timeScaleUp, timeScaleDown;
	JMenuItem scaleUp, scaleDown, maxScale;
	ButtonGroup showOptions;
	JRadioButtonMenuItem showVoltage, showCurrent, showPower, showVvsI;
	JCheckBoxMenuItem showPeak, showNPeak, showFreq;
	
	static final int VOLTAGE = 1;
	static final int CURRENT = 2;
	static final int POWER = 3;
	static final int V_VS_I = 4;
	int showingValue;
	
	DecimalFormat df;
	
	Oscilloscope(CirSim s) {
		sim = s;
		elements = new Vector<CircuitElm>();
		
		timeScale = 64; // this is the 'speed' variable in Scope
		
		bgColor = Color.white;
		gridLineColor = new Color(0x66, 0x66, 0x66, 0xFF);
		
		df = new DecimalFormat("#0.0##");
		
		Point p = sim.getLocation();
		this.setLocation( p.x+sim.getWidth(), p.y+50 );
		this.setSize(350,450);
		this.setMinimumSize(new Dimension(200, 250));
		
		this.setTitle("Oscilloscope");
		this.setJMenuBar(buildMenu());
		showingValue = VOLTAGE;
		
		this.setLayout(new OscilloscopeLayout());
		cv = new OscilloscopeCanvas(this);
		this.add(cv);
		cv.addComponentListener(this);
		
		reset();
		
		this.setVisible(true);
		createImage(); // Necessary to allocate dbimage before the first
					   // call to drawScope
		
	}
	
	public void reset() {
		draw_ox = draw_oy = 0;
		timeScale = 64;
		voltageRange = 5.0;
		currentRange = 0.1;
	}
	
	// Where the magic happens
	public void drawScope(Graphics realg) {
		double val = 0;
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
		
		Graphics g = dbimage.getGraphics();
		
		g.setColor(bgColor);
		g.fillRect(0, 0, winSize.width, winSize.height);
		
		double ts = sim.timeStep * timeScale;

		// This makes the gridlines a constant distance apart
		double gridStep = 50 / pixPerTimeStep * sim.timeStep * timeScale;
		double tstart = sim.t - ts * (winSize.width / pixPerTimeStep);
		double tx = sim.t - (sim.t % gridStep);
		
		// X axis
		g.setColor(gridLineColor);
		g.drawRect(0, winSize.height/2, winSize.width, 1);
		
		// Grid lines parallel to Y axis
		double t;
		int lx, ln;
		for ( int i = 0; ; i++ ) {
			
			t = tx - i * gridStep; // time at gridline
			if ( t < tstart || t < 0 )
				break;
			lx = (int) Math.round((t - tstart) / ts * pixPerTimeStep); // pixel position of gridline
			
			g.drawLine(lx, 30, lx, winSize.height);
			
			// Mark time every other gridline
			ln = (int) Math.round(t / gridStep ); // gridline number (since beginning of time)
			if ( ln % 2 == 0 )
				g.drawString(df.format(t), lx, winSize.height - 15);
		}
		
		g.setColor(Color.BLACK);
		g.drawString("Time: " + sim.t, 5, 15);
		g.drawString("Val: " + val, 5, 30);
		
		realg.drawImage(dbimage, 0, 0, null);
		
		realg.drawImage(image, 0, 0, null);
		
		cv.repaint(); // This makes it actually show up
	}
	
	public void addElement(CircuitElm elm) {
		elements.add(elm);
	}
	
	// Called whenever the canvas is resized and also during Oscilloscope's
	// constructor
	public void createImage() {
		winSize = cv.getSize();
		dbimage = new BufferedImage(winSize.width, winSize.height, BufferedImage.TYPE_INT_ARGB);
		
		int nPixels = winSize.width * winSize.height;
		pixels = new int[nPixels];
		for ( int i = 0; i != nPixels; i++ )
			pixels[i] = 0x00000000;
		imageSource = new MemoryImageSource( winSize.width, winSize.height, pixels, 0, winSize.width );
		imageSource.setAnimated(true);
		imageSource.setFullBufferUpdates(true);
		if ( cv != null )
			image = createImage(imageSource);
	}
	
	/* ********************************************************* */
	/* Action Listener Implementation                            */
	/* ********************************************************* */
	@Override
	public void actionPerformed(ActionEvent e) {
		if ( e.getSource() == reset ) {
			reset();
		} else if ( e.getSource() == timeScaleUp ) {
			timeScale *= 2;
		} else if ( e.getSource() == timeScaleDown ) {
			timeScale /= 2;
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
	JMenuBar buildMenu() {
		
		JMenuBar mb = new JMenuBar();
		
		JMenu m = new JMenu("Reset");
		mb.add(m);
		m.add(reset = new JMenuItem("Reset"));
		reset.addActionListener(this);
		
		m = new JMenu("Time Scale");
		mb.add(m);
		m.add(timeScaleUp = new JMenuItem("Time Scale 2x"));
		m.add(timeScaleDown = new JMenuItem("Time Scale 1/2x"));
		for ( int i = 0; i < m.getItemCount(); i++ )
			((JMenuItem) m.getMenuComponent(i)).addActionListener(this);
		
		m = new JMenu("Amplitude Scale");
		mb.add(m);
		m.add(scaleUp = new JMenuItem("Scale 2x"));
		m.add(scaleDown = new JMenuItem("Scale 1/2x"));
		m.add(maxScale = new JMenuItem("Max Scale"));
		for ( int i = 0; i < m.getItemCount(); i++ )
			((JMenuItem) m.getMenuComponent(i)).addActionListener(this);
		
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
		
		m.addSeparator();
		m.add(showPeak = new JCheckBoxMenuItem("Peak Value"));
		m.add(showNPeak = new JCheckBoxMenuItem("Negative Peak Value"));
		m.add(showFreq = new JCheckBoxMenuItem("Frequency"));
		for ( int i = 5; i < 8; i++ )
			((JCheckBoxMenuItem) m.getMenuComponent(i)).addItemListener(this);
		
		return mb;
	}
	
}