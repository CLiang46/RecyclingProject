package com.recycleproj;

import java.util.ArrayList;

public class Main {
	private double hdpelargefloz = 0;
	private double carbpetlargefloz = 0;
	private double noncarbpetlargefloz = 0;
	private double ldpelargefloz = 0;
	private double ppflozlargetotal = 0;
	private double psflozlargetotal = 0;
	private double pvcflozlargetotal = 0;
	private double otherlargefloztotal = 0;
	private double aluminumlargefloztotal = 0;
	private double bimetalflozlargetotal = 0;
	private double bimetalfloztotal = 0;
	//private double bimetaldryoztotal = .54*Math.pow(bimetalfloztotal, .43);
	private double greenglassfloztotal = 0;
	private double greenglassflozlargetotal = 0;
	private double clearglassfloztotal = 0;
	private double clearglasslargefloztotal = 0;
	private double brownglassfloztotal = 0;
	private double brownglassflozlargetotal = 0;
	//private double glassbottledryoz = 0.56*Math.pow(greenglassfloztotal, 1.05);//applies to all glass
	private double carbpettotalfloz = 0;
	private double noncarbpettotalfloz = 0;
	private double hdpetotalfloz = 0;
	//private double hdpedryoz = .125*Math.pow(hdpetotalfloz, .68);
	private double ldpetotalfloz = 0;
	//private double ldpedryoz = 0.170*Math.pow(ldpetotalfloz, .65);
	private double ppfloztotal = 0;
	//private double ppdryoztotal = .145*Math.pow(ppfloztotal, .67);
	private double psfloztotal = 0;
	//private double psdryoztotal = .16*Math.pow(psfloztotal, .65);
	private double pvcfloztotal = 0;
	//private double pvcdryoztotal = .205 * Math.pow(pvcfloztotal, .63);
	private double otherfloztotal = 0;
	//private double otherdryoztotal = .19*Math.pow(otherfloztotal,.65);//this may be inaccurate, double check with weigh stations.
	private double largeCRV = 0.10;
	private double aluminumcostlb = 1.66;
	private double bimetalcostlb = .43;
	private double glasscostlb = .101;
	private double othercostlb = .31;
	private double pscostlb = 5.45;
	private double ppcostlb=.56;
	private double ldpecostlb = 1.98;
	private double pvccostlb = .48;
	private double hdpecostlb = .67;
	private static double petcostlb = 1.46;
	//private double aluminumdryozavg = 0.5;//Since there are different weights for different can sizes ranging between 0.49 to 1.09 oz 
	//I will be putting the regression formula for predicting the dry weights of aluminum cans. This is not 100% accurate
	//so there may be errors. dry weight = 0.175*floz^.53
	private double altotalfloz = 0;
	//private double altotaldryweight = 0.175 * Math.pow(altotalfloz, .53);
	//private double carbonpetdrywt = 0.088*Math.pow(carbpettotalfloz, .75);
	//private double noncarbpetdrywt = 0.053*Math.pow(noncarbpettotalfloz, .71);
	//private double aluminumlargeoz = 1.0;
	private double regCRV = 0.05;
	/*public static double countvals(double firstval, double secondval) {
		return firstval*secondval;
	}*/
	/*public static double weigh(double firstval,double secondvall) {
		return firstval*secondvall;
	}*/
	public static void main(String[] args) {
		double plastictotal = 0;
		boolean adding = true;
		// TODO Auto-generated method stub
		//Bottle b = new Bottle(3, true, "Red");
		ArrayList<PETBottle> carbBigs = new ArrayList<PETBottle>();
		ArrayList<PETBottle> carbsmalls = new ArrayList<PETBottle>();
		ArrayList<PETBottle> bigPETs = new ArrayList<PETBottle>();
		ArrayList<PETBottle> otherPETs = new ArrayList<PETBottle>();
		ArrayList<OtherPlastic> bigOthers = new ArrayList<OtherPlastic>();
		ArrayList<OtherPlastic> regularOthers = new ArrayList<OtherPlastic>();
		ArrayList<BimetalCan> largeBI = new ArrayList<BimetalCan>();
		ArrayList<BimetalCan> otherBI = new ArrayList<BimetalCan>();
		ArrayList<AluminumCan> largeAl = new ArrayList<AluminumCan>();
		ArrayList<AluminumCan> regAl = new ArrayList<AluminumCan>();
		ArrayList<GlassBottle> largeDarks = new ArrayList<GlassBottle>();
		ArrayList<GlassBottle> largeclears = new ArrayList<GlassBottle>();
		ArrayList<GlassBottle> regDarks = new ArrayList<GlassBottle>();
		ArrayList<GlassBottle> regclears = new ArrayList<GlassBottle>();
		ArrayList<GlassBottle> largegreens = new ArrayList<GlassBottle>();
		ArrayList<GlassBottle> reggreens = new ArrayList<GlassBottle>();
		while(adding == true) {//can opt for switch
			
		}
		Recycle r = new Recycle();
		if(otherPETs.size()<=50) {
			//plastictotal+=countvals(otherPETs.size(),.05);
		} else {
			//plastictotal+=weigh(petcostlb, petdryoztotal);
		}
		if(bigPETs.size() <= 50) {
			//plastictotal += countvals(bigPETs.size(),.1);
		} else {
			//plastictotal += weigh(bigPETs.get(0).getCostDryOZ(),bigPETs.size());
		}
	}
}