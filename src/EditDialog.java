import java.awt.*;
import java.awt.event.*;
import java.text.NumberFormat;
import java.text.DecimalFormat;
import javax.swing.*;

interface Editable {
    EditInfo getEditInfo(int n);
    void setEditValue(int n, EditInfo ei);
}

class EditDialog extends JDialog implements ActionListener, ItemListener {
	private static final long serialVersionUID = -5191214945517294319L;
	
	Editable elm;
    CirSim cframe;
    JButton applyButton, okButton;
    EditInfo einfos[];
    int einfocount;
    NumberFormat noCommaFormat;

    EditDialog(Editable ce, CirSim f) {
		super(f, "Edit Component", false);
		cframe = f;
		elm = ce;
		setLayout(new EditDialogLayout());
		einfos = new EditInfo[10];
		noCommaFormat = DecimalFormat.getInstance();
		noCommaFormat.setMaximumFractionDigits(10);
		noCommaFormat.setGroupingUsed(false);
		int i;
		for (i = 0; ; i++) {
		    einfos[i] = elm.getEditInfo(i);
		    if (einfos[i] == null)
		    	break;
		    EditInfo ei = einfos[i];
		    add(new JLabel(ei.name));
		    if (ei.choice != null) {
		    	add(ei.choice);
		    	ei.choice.addItemListener(this);
		    } else if (ei.checkbox != null) {
		    	add(ei.checkbox);
		    	ei.checkbox.addItemListener(this);
		    } else {
		    	add(ei.textf = new JTextField(unitString(ei), 10));
				if (ei.text != null)
				    ei.textf.setText(ei.text);
				ei.textf.addActionListener(this);
		    }
		}
		einfocount = i;
		add(okButton = new JButton("OK"));
		this.getRootPane().setDefaultButton(okButton);
		okButton.addActionListener(this);
		
		validate();
		Point x = CirSim.main.getLocationOnScreen();
		Dimension d = getSize();
		setLocation(x.x + (cframe.winSize.width-d.width)/2, x.y + (cframe.winSize.height-d.height)/2);
    }

    String unitString(EditInfo ei) {
		double v = ei.value;
		double va = Math.abs(v);
		if (ei.dimensionless)
		    return noCommaFormat.format(v);
		if (v == 0) return "0";
		if (va < 1e-9)
		    return noCommaFormat.format(v*1e12) + "p";
		if (va < 1e-6)
		    return noCommaFormat.format(v*1e9) + "n";
		if (va < 1e-3)
		    return noCommaFormat.format(v*1e6) + "u";
		if (va < 1 && !ei.forceLargeM)
		    return noCommaFormat.format(v*1e3) + "m";
		if (va < 1e3)
		    return noCommaFormat.format(v);
		if (va < 1e6)
		    return noCommaFormat.format(v*1e-3) + "k";
		if (va < 1e9)
		    return noCommaFormat.format(v*1e-6) + "M";
		return noCommaFormat.format(v*1e-9) + "G";
    }

    double parseUnits(EditInfo ei) throws java.text.ParseException {
		String s = ei.textf.getText();
		s = s.trim();
		int len = s.length();
		char uc = s.charAt(len-1);
		double mult = 1;
		switch (uc) {
		case 'p': case 'P': mult = 1e-12; break;
		case 'n': case 'N': mult = 1e-9; break;
		case 'u': case 'U': mult = 1e-6; break;
		    
		// for ohm values, we assume mega for lowercase m, otherwise milli
		case 'm': mult = (ei.forceLargeM) ? 1e6 : 1e-3; break;
		
		case 'k': case 'K': mult = 1e3; break;
		case 'M': mult = 1e6; break;
		case 'G': case 'g': mult = 1e9; break;
		}
		if (mult != 1)
		    s = s.substring(0, len-1).trim();
		return noCommaFormat.parse(s).doubleValue() * mult;
    }
	
    void apply() {
		int i;
		for (i = 0; i != einfocount; i++) {
		    EditInfo ei = einfos[i];
		    if (ei.textf == null)
		    	continue;
		    if (ei.text == null) {
				try {
				    double d = parseUnits(ei);
				    ei.value = d;
				} catch (Exception ex) { /* ignored */ }
			}
		    elm.setEditValue(i, ei);
		}
		cframe.needAnalyze();
    }
	
    public void actionPerformed(ActionEvent e) {
		int i;
		Object src = e.getSource();
		for (i = 0; i != einfocount; i++) {
		    EditInfo ei = einfos[i];
		    if (src == ei.textf) {
				if (ei.text == null) {
				    try {
				    	double d = parseUnits(ei);
				    	ei.value = d;
				    } catch (Exception ex) { /* ignored */ }
				}
				elm.setEditValue(i, ei);
				cframe.needAnalyze();
		    }
		}
		if (e.getSource() == okButton) {
		    apply();
		    CirSim.main.requestFocus();
		    setVisible(false);
		    CirSim.editDialog = null;
		}
    }

    public void itemStateChanged(ItemEvent e) {
		Object src = e.getItemSelectable();
		int i;
		boolean changed = false;
		for (i = 0; i != einfocount; i++) {
		    EditInfo ei = einfos[i];
		    if (ei.choice == src || ei.checkbox == src) {
		    	elm.setEditValue(i, ei);
				if (ei.newDialog)
				    changed = true;
				cframe.needAnalyze();
		    }
		}
		if (changed) {
			CirSim.editDialog.setVisible(false);
		    CirSim.editDialog.dispose();
		    CirSim.editDialog = new EditDialog(elm, cframe);
		    CirSim.editDialog.setVisible(true);
		}
    }
	
    public void processEvent(AWTEvent ev) {
		if (ev.getID() == Event.WINDOW_DESTROY) {
		    CirSim.main.requestFocus();
		    setVisible(false);
		    CirSim.editDialog = null;
		}
		super.processEvent(ev);
    }
}

