package com.recycleproj;

import java.util.ArrayList;

public class Main {
	private double largeCRV = 0.10;
	private double aluminumcostlb = 1.66;
	private double aluminumsmalloz = 0.5;
	private double aluminumlargeoz = 1.0;
	private double regCRV = 0.05;
	public static double countvals(double firstval, double secondval) {
		return firstval*secondval;
	}
	public static double weigh(double firstval,double secondvall) {
		return firstval*secondvall;
	}
	public static void main(String[] args) {
		double plastictotal = 0;
		boolean adding = true;
		// TODO Auto-generated method stub
		//Bottle b = new Bottle(3, true, "Red");
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
		if(otherPETs.size()<=50) {
			plastictotal+=countvals(otherPETs.size(),.05);
		} else {
			plastictotal+=weigh(otherPETs.get(0).getCostDryOZ(), otherPETs.size());
		}
		if(bigPETs.size() <= 50) {
			plastictotal += countvals(bigPETs.size(),.1);
		} else {
			plastictotal += weigh(bigPETs.get(0).getCostDryOZ(),bigPETs.size());
		}
	}
}