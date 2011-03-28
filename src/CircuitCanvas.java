import java.awt.*;

class CircuitCanvas extends Component {
	private static final long serialVersionUID = -7801724829059357025L;
	
	CirSim pg;
    
    CircuitCanvas(CirSim p) {
    	pg = p;
    }
    public Dimension getPreferredSize() {
    	return new Dimension(350,450);
    }
    public void update(Graphics g) {
    	pg.updateCircuit(g);
    }
    public void paint(Graphics g) {
    	pg.updateCircuit(g);
    }
};
