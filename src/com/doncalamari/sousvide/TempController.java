package com.doncalamari.sousvide;

import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.Locale;

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
	private Integer targetTemperature = null;
	private Integer temperatureDelta = null;
	private Integer duration = null;
	private Integer sampleInterval = null;
	private String portName = null;

	private Boolean heatOn = Boolean.FALSE;

	public TempController(final Integer targetTemperature, final Integer temperatureDelta, final Integer duration, final Integer sampleInterval, final String portName) {
		this.targetTemperature = targetTemperature;
		this.temperatureDelta = temperatureDelta;
		this.duration = duration;
		this.sampleInterval = sampleInterval;
		this.portName = portName;
	}

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
			FileWriter writer = null;
			try {
				writer = new FileWriter(file, true);

				final int available = input.available();
				final byte chunk[] = new byte[available];

				synchronized (input) {
					input.read(chunk, 0, available);
				}

				readTemperature = readTemperature + new String(chunk);

				chopChunk(writer, available, chunk);
			} catch (IOException e) {
				System.err.println(e.toString());
			} catch (Exception e) {
				// just to make sure the program doesn't fail
				System.err.println(e.toString());
			} finally {
				if (null != writer) {
					try {
						writer.close();
					} catch (IOException e) {
						// should never happen
						System.err.println(e.toString());
					}
				}
			}
		}
	}

	private void chopChunk(final FileWriter writer, final int available, final byte[] chunk) {
		for (int i = 0; i < available; i++) {
			if (chunk[i] == '\n' || chunk[i] == '\r') {
				try {
					checkToApplyHeat();

					synchronized (CSV_DATE_FORMAT) {
						writer.write(CSV_DATE_FORMAT.format(GregorianCalendar.getInstance().getTime()) + "," + String.valueOf(readTemperature).trim() + "," + heatOn + "\n");
					}

					System.out.print(".");// just to let the user know something is going on.
					readTemperature = EMPTY;
				} catch (IOException e) {
					// should never happen
					System.err.println(e.toString());
				}

				break;
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
