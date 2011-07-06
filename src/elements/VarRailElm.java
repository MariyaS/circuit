import java.util.StringTokenizer;
import javax.swing.*;

    class VarRailElm extends RailElm {
	JSlider slider;
	JLabel label;
	String sliderText;
	public VarRailElm(int xx, int yy) {
	    super(xx, yy, WF_VAR);
	    sliderText = "Voltage";
	    frequency = maxVoltage;
	    createSlider();
	}
	public VarRailElm(int xa, int ya, int xb, int yb, int f,
		       StringTokenizer st) {
	    super(xa, ya, xb, yb, f, st);
	    sliderText = st.nextToken();
	    while (st.hasMoreTokens())
		sliderText += ' ' + st.nextToken();
	    createSlider();
	}
	String dump() {
	    return super.dump() + " " + sliderText;
	}
	int getDumpType() { return 172; }
	void createSlider() {
	    waveform = WF_VAR;
	    CirSim.main.add(label = new JLabel(sliderText, JLabel.CENTER));
	    int value = (int) ((frequency-bias)*100/(maxVoltage-bias));
	    CirSim.main.add(slider = new JSlider(JSlider.HORIZONTAL, 0, 101, value));
	    CirSim.main.validate();
	}
	double getVoltage() {
	    frequency = slider.getValue() * (maxVoltage-bias) / 100. + bias;
	    return frequency;
	}
	void delete() {
	    CirSim.main.remove(label);
	    CirSim.main.remove(slider);
	    CirSim.main.validate();
	    CirSim.main.repaint();
	}
	public EditInfo getEditInfo(int n) {
	    if (n == 0)
		return new EditInfo("Min Voltage", bias, -20, 20);
	    if (n == 1)
		return new EditInfo("Max Voltage", maxVoltage, -20, 20);
	    if (n == 2) {
		EditInfo ei = new EditInfo("Slider Text", 0, -1, -1);
		ei.text = sliderText;
		return ei;
	    }
	    return null;
	}
	public void setEditValue(int n, EditInfo ei) {
	    if (n == 0)
		bias = ei.value;
	    if (n == 1)
		maxVoltage = ei.value;
	    if (n == 2) {
		sliderText = ei.textf.getText();
		label.setText(sliderText);
	    }
	}
    }
