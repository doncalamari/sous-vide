package com.doncalamari.sousvide;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Properties;

public final class SousVide {

	private SousVide() {
		// do nothing
	}

	public static TempController readProperties() {
		System.out.println("Reading settings properties file.");

		final Properties prop = new Properties();
		try {
			prop.load(new FileInputStream("settings.properties"));

			final Integer duration = Integer.valueOf(prop.get("duration.minutes").toString()) * 60 * 1000;
			final Integer sampleInterval = Integer.valueOf(prop.get("sample.interval.seconds").toString()) * 1000;
			final Integer temperatureDelta = Integer.valueOf(prop.get("temperature.delta.degrees").toString());
			final Integer targetTemperature = Integer.valueOf(prop.get("temperature.target.degrees").toString());
			final String portName = prop.get("serial.port").toString();

			System.out.println("Done. Found values:");

			System.out.println("duration = " + duration / (60 * 1000) + " minutes.");
			System.out.println("sample interval = " + sampleInterval / 1000 + " seconds.");
			System.out.println("temperature delta = " + temperatureDelta + " degrees F.");
			System.out.println("target temperature = " + targetTemperature + " degrees F.");
			System.out.println("serial port " + portName);

			return new TempController(targetTemperature, temperatureDelta, duration, sampleInterval, portName);
		} catch (NumberFormatException e) {
			System.out.println("Error reading properties. Using default values:");
		} catch (IOException e) {
			System.out.println("Error reading properties. Using default values:");
		}

		return null;
	}

	public static void main(final String[] args) throws InterruptedException {

		final TempController tempController = readProperties();

		if (null == tempController) {
			return;
		}

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
