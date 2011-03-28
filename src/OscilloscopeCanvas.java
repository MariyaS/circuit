import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;

class OscilloscopeCanvas extends Component {
	private static final long serialVersionUID = -477709468614378530L;

	Oscilloscope scope;
    
    OscilloscopeCanvas(Oscilloscope o) {
    	scope = o;
    }
    public Dimension getPreferredSize() {
    	return new Dimension(300,400);
    }
    public void update(Graphics g) {
    	scope.drawScope(g);
    }
    public void paint(Graphics g) {
    	scope.drawScope(g);
    }
};
