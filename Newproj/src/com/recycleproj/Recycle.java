package com.recycleproj;

import java.util.ArrayList;

public class Recycle {
	//private double alfluid, petfluid,bglassfluid, gglassfluid,
	//bifluid, psfluid,ppfluid,hdpefluid,ldpefluid, pvcfluid, otherfluid,cglassfluid = 0;
	//private double bigalfluid, bigpetfluid,bigbglassfluid, biggglassfluid,
	//bigbifluid, bigpsfluid,bigppfluid,bighdpefluid,bigldpefluid, bigpvcfluid, bigotherfluid,bigcglassfluid = 0;
	private double weightoz = 0;
	public Recycle() {
	}
	public double weigh(double coeff, double floz, double exponent, double costlb) {
		return coeff*Math.pow(floz, exponent)*16*costlb;
	}
	public double ValueCounter(double costper, int amount) {//returns total count value for a category in dollars.
		return costper*amount;
	}
	/*public double ValueWeight(double costlb, double dryoz) {//returns total weight value for a category in dollars.
		return weightoz = costlb*dryoz*16;
	}*/
	
		
		/*public double weighglass(double glassfloz) {
			return 0;//0.56*Math.pow(glassfloz, 1.05);			
		}
		public double weighcarbpet(double carbfloz) {
			return 0;//0.088*Math.pow(carbfloz, .75);
		}
		public double noncarbpet(double ncpoz) {
			return 0;//0.053*Math.pow(ncpoz, .71);
		}
		public double bimetalwt(double bw) {
			return 0;//.54*Math.pow(bw, .43);
		}
		public double alwt(double aw) {
			return 0;//0.175 * Math.pow(aw, .53);
		}
		public double hdpeoz(double hdpetotalfloz) {
			return 0;//.125*Math.pow(hdpetotalfloz, .68);
		}
		public double ldpeoz(double ldpetotalfloz) {
			return 0;//.170*Math.pow(ldpetotalfloz, .65);
		}
		public double otheroz(double othertotalfloz) {
			return 0;//.19*Math.pow(othertotalfloz,.65);
		}
		public double ppoz(double ppfloztotal) {
			return 0;//.145*Math.pow(ppfloztotal, .67);
		}
		public double psoz(double psfloztotal) {
			return 0;//.16*Math.pow(psfloztotal, .65);
		}
		public double pvcoz(double pvcfloztotal) {
			return 0;//.205 * Math.pow(pvcfloztotal, .63);
		}*/
	
}
