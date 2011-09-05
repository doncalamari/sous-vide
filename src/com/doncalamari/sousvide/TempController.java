package com.doncalamari.sousvide;

import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Properties;

public class TempController implements SerialPortEventListener {

	private static final String EMPTY = "";

	private static final DateFormat CSV_DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.US);
	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);

	private static final int TIME_OUT = 2000;
	private static final int DATA_RATE = 9600;

	private SerialPort serialPort;
	private InputStream input;
	private OutputStream output;
	private File file;

	private String readTemperature = EMPTY;
	private Integer targetTemperature = Integer.valueOf(70); // roughly room temperature.
	private Integer temperatureDelta = Integer.valueOf(0);
	private Integer duration = Integer.valueOf(60 * 1000);
	private Integer sampleInterval = Integer.valueOf(1000);
	private String portName = "/dev/ttyUSB0";

	private Boolean heatOn = Boolean.FALSE;

	public void initializeHardware() {
		final CommPortIdentifier portId = findValidPort();

		if (portId == null) {
			System.out.println("Could not find COM port.");
			return;
		}

		try {
			serialPort = (SerialPort) portId.open(this.getClass().getName(), TIME_OUT);
			serialPort.setSerialPortParams(DATA_RATE, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

			input = serialPort.getInputStream();
			output = serialPort.getOutputStream();

			serialPort.addEventListener(this);
			serialPort.notifyOnDataAvailable(true);

		} catch (Exception e) {
			System.err.println(e.toString());
		}

		synchronized (DATE_FORMAT) {
			final String now = DATE_FORMAT.format(new Date());

			file = new File("temperature-" + now + ".csv");
			try {
				file.createNewFile();
			} catch (IOException e) {
				System.err.println(e.toString());
			}
		}
	}

	private CommPortIdentifier findValidPort() {
		@SuppressWarnings("unchecked")
        final Enumeration<CommPortIdentifier> portEnum = CommPortIdentifier.getPortIdentifiers();

		while (portEnum.hasMoreElements()) {
			final CommPortIdentifier currPortId = portEnum.nextElement();

			if (currPortId.getName().equals(portName)) {
				return currPortId;
			}
		}
		return null;
	}

	public void close() {
		synchronized (serialPort) {
			if (serialPort != null) {
				serialPort.removeEventListener();
				serialPort.close();
			}
		}
	}

	@Override
	public void serialEvent(final SerialPortEvent oEvent) {

		if (oEvent.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
			try {
				final int available = input.available();
				final byte chunk[] = new byte[available];

				synchronized (input) {
					input.read(chunk, 0, available);
				}

				readTemperature = readTemperature + new String(chunk);

				for (int i = 0; i < available; i++) {
					if (chunk[i] == '\n' || chunk[i] == '\r') {
						try {
							checkToApplyHeat();
							final FileWriter writer = new FileWriter(file, true);
							synchronized (CSV_DATE_FORMAT) {
								writer.write(CSV_DATE_FORMAT.format(new Date()) + "," + new String(readTemperature).trim() + "," + heatOn + "\n");
							}
							writer.close();

							System.out.print(".");// just to let the user know something is going on.
							readTemperature = EMPTY;
						} catch (IOException e) {
							// should never happen
							System.err.println(e.toString());
						}

						return;
					}
				}
			} catch (IOException e) {
				System.err.println(e.toString());
			} catch (Exception e) {
				// just to make sure the program doesn't fail
				System.err.println(e.toString());
			}
		}
	}

	private void checkToApplyHeat() {
		try {
			final int sweetSpotTemperature = Integer.valueOf(readTemperature.trim()) + temperatureDelta;
			if (heatOn && (targetTemperature.intValue() < sweetSpotTemperature)) {
				heatOn = Boolean.FALSE;
				changeHeat();
				return;
			}

			if (!heatOn && targetTemperature.intValue() > sweetSpotTemperature) {
				heatOn = Boolean.TRUE;
				changeHeat();
				return;
			}
		} catch (NumberFormatException e) {
			System.err.println(e.toString());
		}
	}

	public void changeHeat() {
		sendSignal(heatOn.booleanValue() ? "1" : "0");
		System.out.println("turning heat " + (heatOn.booleanValue() ? "ON" : "OFF"));
	}

	private void sendSignal(final String signal) {
		synchronized (output) {
			try {
				Thread.sleep(100);
				output.write(signal.getBytes());
			} catch (IOException e) {
				System.err.println(e.toString());
			} catch (InterruptedException e) {
				System.err.println(e.toString());
			}
		}
	}

	public void getTemp() {
		sendSignal("2");
	}

	public void readProperties() {
		System.out.println("Reading settings properties file.");

		final Properties prop = new Properties();
		try {
			prop.load(new FileInputStream("settings.properties"));

			duration = Integer.valueOf(prop.get("duration.minutes").toString()) * 60 * 1000;
			sampleInterval = Integer.valueOf(prop.get("sample.interval.seconds").toString()) * 1000;
			temperatureDelta = Integer.valueOf(prop.get("temperature.delta.degrees").toString());
			targetTemperature = Integer.valueOf(prop.get("temperature.target.degrees").toString());
			portName = prop.get("serial.port").toString();
			System.out.println("Done. Found values:");
		} catch (NumberFormatException e) {
			System.out.println("Error reading properties. Using default values:");
		} catch (IOException e) {
			System.out.println("Error reading properties. Using default values:");
		}

		System.out.println("duration = " + duration / (60 * 1000) + " minutes.");
		System.out.println("sample interval = " + sampleInterval / 1000 + " seconds.");
		System.out.println("temperature delta = " + temperatureDelta + " degrees F.");
		System.out.println("target temperature = " + targetTemperature + " degrees F.");
		System.out.println("serial port " + portName);
	}

	public SerialPort getSerialPort() {
		return serialPort;
	}

	public void setSerialPort(final SerialPort serialPort) {
		this.serialPort = serialPort;
	}

	public InputStream getInput() {
		return input;
	}

	public void setInput(final InputStream input) {
		this.input = input;
	}

	public OutputStream getOutput() {
		return output;
	}

	public void setOutput(final OutputStream output) {
		this.output = output;
	}

	public File getFile() {
		return file;
	}

	public void setFile(final File file) {
		this.file = file;
	}

	public String getReadTemperature() {
		return readTemperature;
	}

	public void setReadTemperature(final String readTemperature) {
		this.readTemperature = readTemperature;
	}

	public Integer getTargetTemperature() {
		return targetTemperature;
	}

	public void setTargetTemperature(final Integer targetTemperature) {
		this.targetTemperature = targetTemperature;
	}

	public Integer getTemperatureDelta() {
		return temperatureDelta;
	}

	public void setTemperatureDelta(final Integer temperatureDelta) {
		this.temperatureDelta = temperatureDelta;
	}

	public Integer getDuration() {
		return duration;
	}

	public void setDuration(final Integer duration) {
		this.duration = duration;
	}

	public Integer getSampleInterval() {
		return sampleInterval;
	}

	public void setSampleInterval(final Integer sampleInterval) {
		this.sampleInterval = sampleInterval;
	}

	public String getPortName() {
		return portName;
	}

	public void setPortName(final String portName) {
		this.portName = portName;
	}

	public Boolean getHeatOn() {
		return heatOn;
	}

	public void setHeatOn(final Boolean heatOn) {
		this.heatOn = heatOn;
	}
}
