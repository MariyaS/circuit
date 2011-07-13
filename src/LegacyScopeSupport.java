import java.util.Arrays;
import java.util.StringTokenizer;
import java.util.Vector;

class LegacyScopeSupport {
	
	private class LegacyScopeInfo {
		int elm_no;
		int time_scale;
		double voltage_range;
		double current_range;
		int position;
		String text;
		int y_elm_no;
		
		boolean show_v;
		boolean show_i;
		
		boolean show_max;
		boolean show_min;
		boolean show_freq;
		
		boolean plot_2d;
		boolean plot_xy;
		
		boolean stacked;
		
		int scope_id;
		
		LegacyScopeInfo(StringTokenizer st) {
			elm_no = new Integer(st.nextToken()).intValue();
			time_scale = new Integer(st.nextToken()).intValue();
			st.nextToken(); // skip value
			int flags = new Integer(st.nextToken()).intValue();
			voltage_range = 2 * new Double(st.nextToken()).doubleValue();
			if ( voltage_range == 0 ) { voltage_range = 1; }
			current_range = 2 * new Double(st.nextToken()).doubleValue();
			if ( current_range == 0 ) { current_range = 2; }
			text = null;
			y_elm_no = -1;
			position = -1;
			if ( st.hasMoreTokens() )
				position = new Integer(st.nextToken()).intValue();
			if ( (flags & 32) != 0 && st.hasMoreTokens() ) {
				y_elm_no = new Integer(st.nextToken()).intValue();
				while ( st.hasMoreTokens() ) {
					if ( text == null )
						text= st.nextToken();
					else
						text += " " + st.nextToken();
				}
			}
			show_i = (flags & 1) != 0;
			show_v = (flags & 2) != 0;
			show_max = (flags & 4) != 0;
			show_min = (flags & 256) != 0;
			show_freq = (flags & 8) != 0;
			plot_2d = (flags & 64) != 0;
			plot_xy = (flags & 128) != 0;
			
			scope_id = -1;
		}
	}
	
	private CirSim sim;
	private Vector<LegacyScopeInfo> unplaced_scopes;
	private Vector<LegacyScopeInfo> placed_scopes;
	
	LegacyScopeSupport(CirSim s) {
		sim = s;
		unplaced_scopes = new Vector<LegacyScopeInfo>();
		placed_scopes = new Vector<LegacyScopeInfo>();
	}
	
	public void undumpLegacyScope(StringTokenizer st) {
		LegacyScopeInfo lsi = new LegacyScopeInfo(st);
		if ( lsi.elm_no != -1 )
			unplaced_scopes.add(lsi);
	}
	
	public void setupScopes() {
		
		// Find highest position of any legacy scope
		int max_position = 0;
		for ( int i = 0; i < unplaced_scopes.size(); i++ ) {
			int p = unplaced_scopes.get(i).position;
			if ( p > max_position )
				max_position = p;
		}
		
		// Count number of scopes at each position
		int[] scope_pos_count = new int[max_position+1];
		Arrays.fill(scope_pos_count, 0);
		for ( int i = 0; i < unplaced_scopes.size(); i++ ) {
			int p = unplaced_scopes.get(i).position;
			if ( p != -1 )
				scope_pos_count[p]++;
		}
		
		// Group scopes with the same position variable
		int num_scopes = 0;
		int[] s_to_os_map = new int[max_position+1];
		Arrays.fill(s_to_os_map, -1);
		for ( int i = 0; i < unplaced_scopes.size(); i++ ) {
			int p = unplaced_scopes.get(i).position;
			if ( p != -1 && scope_pos_count[ p ] > 1 ) {
				if ( s_to_os_map[p] == -1 )
					s_to_os_map[p] = num_scopes++;
				unplaced_scopes.get(i).scope_id = s_to_os_map[p];
				placed_scopes.add(unplaced_scopes.remove(i));
				placed_scopes.lastElement().stacked = true;
				i--; // since elements shift left when one is removed
			}
		}
		
		// Place all plot X/Y scopes on a separate Oscilloscope
		for ( int i = 0; i < unplaced_scopes.size(); i++ ) {
			if ( unplaced_scopes.get(i).plot_xy == true ) {
				unplaced_scopes.get(i).scope_id = num_scopes++;
				placed_scopes.add(unplaced_scopes.remove(i));
				i--; // since elements shift left when one is removed
			}
		}
		
		// Group scopes with the same time scale even if voltage/current range differs
		while ( ! unplaced_scopes.isEmpty() ) {
			LegacyScopeInfo lsi = unplaced_scopes.firstElement();
			lsi.scope_id = num_scopes++;
			for ( int i = 1; i < unplaced_scopes.size(); i++ ) {
				LegacyScopeInfo lsi2 = unplaced_scopes.get(i);
				if ( lsi.time_scale == lsi2.time_scale
					&& lsi.plot_2d == lsi2.plot_2d
					&& lsi.plot_xy == lsi2.plot_xy )
				{
					lsi2.scope_id = lsi.scope_id;
					placed_scopes.add(unplaced_scopes.remove(i));
					i--; // since elements shift left when one is removed
				}
			}
			placed_scopes.add(unplaced_scopes.remove(0));
		}
		
		// Group only scopes with the same time scale and voltage/current ranges
		/*while ( ! unplaced_scopes.isEmpty() ) {
			LegacyScopeInfo lsi = unplaced_scopes.firstElement();
			lsi.scope_id = num_scopes++;
			for ( int i = 1; i < unplaced_scopes.size(); i++ ) {
				LegacyScopeInfo lsi2 = unplaced_scopes.get(i);
				if ( lsi.time_scale == lsi2.time_scale
					&& lsi.voltage_range == lsi2.voltage_range
					&& lsi.current_range == lsi2.current_range
					&& lsi.plot_2d == lsi2.plot_2d
					&& ! lsi.plot_xy && ! lsi2.plot_xy ) 
				{
					lsi2.scope_id = lsi.scope_id;
					placed_scopes.add(unplaced_scopes.remove(i));
					i--; // since elements shift left when one is removed
				}
			}
			placed_scopes.add(unplaced_scopes.remove(0));
		}*/
		//debugPrint();
		// Setup scopes
		boolean[] scope_setup = new boolean[num_scopes];
		Arrays.fill(scope_setup, false);
		for ( int i = 0; i < placed_scopes.size(); i++ ) {
			LegacyScopeInfo lsi = placed_scopes.get(i);
			Oscilloscope o;
			if ( scope_setup[lsi.scope_id] == false ) {
				o = new Oscilloscope(sim);
				o.setTimeScale(lsi.time_scale);
				o.setRange(Oscilloscope.Value.VOLTAGE, lsi.voltage_range);
				o.setRange(Oscilloscope.Value.CURRENT, lsi.current_range);
				if ( lsi.plot_xy ) {
					o.setType(Oscilloscope.ScopeType.X_VS_Y);
				} else if ( lsi.plot_2d ) {
					o.setType(Oscilloscope.ScopeType.I_VS_V);
				} else {
					o.setType(Oscilloscope.ScopeType.VIP_VS_T);
					o.setStack(lsi.stacked);
				}
				sim.scopes.add(o);
				scope_setup[lsi.scope_id] = true;
			}
			else {
				o = sim.scopes.get(lsi.scope_id);
				o.setRange(Oscilloscope.Value.VOLTAGE, Math.max(o.getRange(Oscilloscope.Value.VOLTAGE), lsi.voltage_range));
				o.setRange(Oscilloscope.Value.CURRENT, Math.max(o.getRange(Oscilloscope.Value.CURRENT), lsi.current_range));
			}
			int show_flags = (lsi.show_v ? 2 : 0) | (lsi.show_i ? 1 : 0);
			o.addElement(sim.getElm(lsi.elm_no), show_flags);
			o.fit_needed = false;
			if ( o.getType() == Oscilloscope.ScopeType.X_VS_Y ) {
				o.addElement(sim.getElm(lsi.y_elm_no));
				o.setXElm(lsi.elm_no);
				o.setYElm(lsi.y_elm_no);
				o.setRange("x", lsi.voltage_range);
				o.setRange("y", lsi.voltage_range);
				if ( sim.getElm(lsi.elm_no) instanceof TransistorElm )
					o.setXValue(Oscilloscope.TransistorValue.V_CE);
				else
					o.setXValue(Oscilloscope.Value.VOLTAGE);
				if ( sim.getElm(lsi.y_elm_no) instanceof TransistorElm )
					o.setYValue(Oscilloscope.TransistorValue.V_CE);
				else
					o.setYValue(Oscilloscope.Value.VOLTAGE);
				
			}
		}
		
		placed_scopes.clear();
		
	}
	
	public void debugPrint() {
		System.out.println("SCOPES--------------------");
		for ( int i = 0; i < placed_scopes.size(); i++ ) {
			LegacyScopeInfo lsi = placed_scopes.get(i);
			if ( lsi.plot_xy )
				System.out.println("X/Y (" + lsi.elm_no + "/" + lsi.y_elm_no + ")");
			else if ( lsi.plot_2d )
				System.out.println("2D (" + lsi.elm_no + ")");
			else
				System.out.println("Normal (" + lsi.elm_no + ")");
			
			System.out.println("Time scale = " + lsi.time_scale);
			System.out.println("V Range = " + lsi.voltage_range + " I Range = " + lsi.current_range);
			
			System.out.print("Show: ");
			if ( lsi.show_v )
				System.out.print("V ");
			if ( lsi.show_i )
				System.out.print("I ");
			if ( lsi.show_freq )
				System.out.print("f ");
			if ( lsi.show_min )
				System.out.print("min ");
			if ( lsi.show_max )
				System.out.print("max ");
			System.out.println();
			
			if ( lsi.text != null )
				System.out.println(lsi.text);
			
			System.out.println("Original position: " + lsi.position);
			System.out.println("New scope id: " + lsi.scope_id);
			
			System.out.println();
		}
		
		System.out.println("----------------------");
	}
}