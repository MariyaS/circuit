import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

class ImportDialog extends JDialog implements ActionListener {
	private static final long serialVersionUID = 6440664629044363653L;
	
	CirSim cframe;
    JButton importButton, closeButton;
    JTextArea text;
    boolean isURL;
	
    ImportDialog(CirSim f, String str, boolean url) {
		super(f, (str.length() > 0) ? "Export" : "Import", false);
		isURL = url;
		cframe = f;
		setLayout(new ImportDialogLayout());
		add(new JScrollPane(text = new JTextArea(str, 10, 60)));
		importButton = new JButton("Import");
		if (!isURL)
		    add(importButton);
		importButton.addActionListener(this);
		add(closeButton = new JButton("Close"));
		closeButton.addActionListener(this);
		Point x = CirSim.main.getLocationOnScreen();
		setSize(400, 300);
		Dimension d = getSize();
		setLocation(x.x + (cframe.winSize.width-d.width)/2,
			    x.y + (cframe.winSize.height-d.height)/2);
		setVisible(true);
		if (str.length() > 0)
		    text.selectAll();
    }

    public void actionPerformed(ActionEvent e) {
		Object src = e.getSource();
		if (src == importButton) {
		    cframe.readSetup(text.getText());
		    setVisible(false);
		}
		if (src == closeButton)
		    setVisible(false);
    }
	
    public void processEvent(AWTEvent ev) {
		if (ev.getID() == Event.WINDOW_DESTROY) {
		    CirSim.main.requestFocus();
		    setVisible(false);
		    CirSim.impDialog = null;
		}
		super.processEvent(ev);
    }
}
    
