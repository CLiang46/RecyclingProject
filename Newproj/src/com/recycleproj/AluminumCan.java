package com.recycleproj;

public class AluminumCan extends Can{
	private final double aluminumdryperozcost = 0.104;
	protected final double coefficient = .175;
	protected final double exponent = .53;
	//private int ounces;
	//private boolean refundable;
	public AluminumCan(int ounces, boolean refundable) {
		super(ounces, refundable);
		// TODO Auto-generated constructor stub		
	}
	 public double getDryPerOZCost() {
		 return this.aluminumdryperozcost;
	 }

}
