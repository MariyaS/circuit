import java.awt.*;
import javax.swing.*;

@SuppressWarnings("serial")
class ImagePanel extends JPanel {

	private CirSim cs;
	private Image bg;
	
	ImagePanel( CirSim sim, Image img ) {
		this.cs = sim;
		this.bg = img;
		this.setBackground(Color.WHITE);
	}
	
	@Override
	public void paintComponent( Graphics g ) {
		super.paintComponent(g);
		if ( cs.printableCheckItem.getState() ) {
			int iw = this.bg.getWidth(this);
			int ih = this.bg.getHeight(this);
			
			if ( iw > 0 && ih > 0 ) {
				for ( int y = 0; y < this.getHeight(); y += ih )
					for ( int x = 0; x < this.getWidth(); x += iw )
						g.drawImage(this.bg, x, y, iw, ih, this);
			}
		}
		
	}
}