package com.doncalamari.sousvide;

import java.util.Date;
import java.util.GregorianCalendar;

public final class SousVide {
	
	private SousVide() {
		// do nothing
	}
	
	public static void main(final String[] args) throws InterruptedException {
		final TempController tempController = new TempController();

		tempController.readProperties();
		tempController.initializeHardware();

		try {

			Thread.sleep(1500); // wait for the hardware to settle down
			System.out.println("Writing to file " + tempController.getFile().getAbsolutePath());

			final double start = new Date().getTime();
			while (true) {
				tempController.getTemp();

				Thread.sleep(100);
				Thread.sleep(tempController.getSampleInterval());

				
				if ((GregorianCalendar.getInstance().getTime().getTime() - start) > tempController.getDuration()) {
					break;
				}
			}
		} finally {
			tempController.setHeatOn(Boolean.FALSE);
			tempController.changeHeat();
			tempController.close();
		}

		System.out.println("\nDone");
	}
}
