package logic;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Properties;

import org.mavlink.IMAVLinkMessage;
import org.mavlink.MAVLinkReader;
import org.mavlink.messages.IMAVLinkMessageID;
import org.mavlink.messages.MAVLinkMessage;
import org.mavlink.messages.ardupilotmega.msg_attitude;
import org.mavlink.messages.ardupilotmega.msg_global_position_int;
import org.mavlink.messages.ardupilotmega.msg_scaled_pressure;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gui.MainWindow;

public class DroneListener extends Thread {
	DatagramSocket socketListener;
	DatagramPacket paquete;
	byte[] buffer;
	long tiempo;

	ByteArrayInputStream bin;
	DataInputStream din;
	DataOutputStream dout;
	MAVLinkReader reader;
	MAVLinkMessage msg;
	boolean continuar;

	// Acceso al puerto serie
	SerialPort serialPort;
	InputStream in;
	OutputStream out;

	// Sigue el número de paquetes que llegan de cada tipo
	long numPosition = 0;
	long numAttitude = 0;
	long numPressure = 0;
	long numVibration = 0;
	long numStatus = 0;

	public DroneListener(String rol) throws Exception {
		super(MainWindow.SERVER_DRONE_LISTENER_NAME);

		// Versión con puerto serie mediante RXTX
		if (MainWindow.interfaceIsSerial) {
			// Necesario indicar a RXTX que se usa un puerto de nombre raro
			Properties properties = System.getProperties();
			String currentPorts = properties.getProperty("gnu.io.rxtx.SerialPorts", MainWindow.serialPort);
			if (currentPorts.equals(MainWindow.serialPort)) {
				properties.setProperty("gnu.io.rxtx.SerialPorts", MainWindow.serialPort);
			} else {
				properties.setProperty("gnu.io.rxtx.SerialPorts",
						currentPorts + File.pathSeparator + MainWindow.serialPort);
			}

			CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(MainWindow.serialPort);
			if (portIdentifier.isCurrentlyOwned()) {
				System.out.println("Error: Port is currently in use");
			} else {
				int timeout = 2000;
				CommPort commPort = portIdentifier.open(this.getClass().getName(), timeout);
				if (commPort instanceof SerialPort) {
					serialPort = (SerialPort) commPort;
					serialPort.setSerialPortParams(MainWindow.baudRate, SerialPort.DATABITS_8,
							SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
					in = serialPort.getInputStream();
					out = serialPort.getOutputStream();
					din = new DataInputStream(in);
					dout = new DataOutputStream(out);
					reader = new MAVLinkReader(din, IMAVLinkMessage.MAVPROT_PACKET_START_V10);
				} else {
					System.out.println("Error: It only works with serial ports");
				}
			}
		}

		// Versión con UDP
		else {
			this.socketListener = new DatagramSocket(null);
			InetSocketAddress direccion = null;
			if (rol.equals("SERVER")) {
				direccion = new InetSocketAddress(MainWindow.droneServerIp,
						MainWindow.droneServerPort);
			} else if (rol.equals("CLIENT")) {
				direccion = new InetSocketAddress(MainWindow.droneClientIp,
						MainWindow.droneClientPort);
			} else {
				System.out.println("Rol del hilo de escucha al dron mal definido");
				System.exit(1);
			}
			socketListener.bind(direccion);
		}
	}

	@Override
	public void run() {
		continuar = true;

		while (true) {
			try {
				// Versión por puerto serie
				//   No requiere convertir el inputStream en DataInputStream
				//   Al no estar orientado a paquetes no hay que crear un reader cada vez
				
				// Versión por UDP
				if (!MainWindow.interfaceIsSerial) {
					// Lectura de información por UDP
					buffer = new byte[MainWindow.DGRAM_MAX_LENGTH];
					paquete = new DatagramPacket(buffer, buffer.length);
					socketListener.receive(paquete);
					if (din != null)
						din.close(); // Cierre en cada ciclo
					if (bin != null)
						bin.close();
					bin = new ByteArrayInputStream(paquete.getData());
					din = new DataInputStream(bin);
					reader = new MAVLinkReader(din, IMAVLinkMessage.MAVPROT_PACKET_START_V10);
				}
				
				// Si interesa el tipo de mensaje se guarda la información que interesa
				selectMessage();
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		// Cierre de inputstreams en puertos serie
		// No lo hago porque se captura indefinidamente hasta que se cierra el programa
	}

	private void selectMessage() {
		try {
			//System.out.println("Intenta conseguir un mensaje");
			
			msg = reader.getNextMessage();
			
			//System.out.println(msg.toString());
			
			if (msg != null) {
				switch (msg.messageType) {
					case IMAVLinkMessageID.MAVLINK_MSG_ID_GLOBAL_POSITION_INT:
						numPosition++;
						registraPosicionGlobal((msg_global_position_int) msg);
						break;
					case IMAVLinkMessageID.MAVLINK_MSG_ID_ATTITUDE:
						numAttitude++;
						registraPostura((msg_attitude) msg);
						break;
					case IMAVLinkMessageID.MAVLINK_MSG_ID_SCALED_PRESSURE:
						numPressure++;
						registraTemperatura((msg_scaled_pressure) msg);
						break;
//					case IMAVLinkMessageID.MAVLINK_MSG_ID_VIBRATION:
//						numVibration++;
//						registraVibracion((msg_vibration) msg);
//						break;
//					case IMAVLinkMessageID.MAVLINK_MSG_ID_SYS_STATUS:
//						numStatus++;
//						registraBateria((msg_sys_status) msg);
//						break;
				}
			}
		} catch (IOException e) {
			System.out.println("Fallo al leer el mensaje:\n" + e.getMessage());
		}
	}

	private void registraPosicionGlobal(msg_global_position_int mensaje) {
		MainWindow.dron.latitude.set(mensaje.lat);
		MainWindow.dron.longitude.set(mensaje.lon);
		MainWindow.dron.altitude.set(mensaje.alt);
		MainWindow.dron.altitudeRelative.set(mensaje.relative_alt);
		MainWindow.dron.speedX.set(mensaje.vx);
		MainWindow.dron.speedY.set(mensaje.vy);
		MainWindow.dron.speedZ.set(mensaje.vz);
		MainWindow.dron.heading.set(mensaje.hdg);
	}

	private void registraPostura(msg_attitude mensaje) {
		MainWindow.dron.roll.set(mensaje.roll);
		MainWindow.dron.pitch.set(mensaje.pitch);
		MainWindow.dron.yaw.set(mensaje.yaw);
		MainWindow.dron.rollSpeed.set(mensaje.rollspeed);
		MainWindow.dron.pitchSpeed.set(mensaje.pitchspeed);
		MainWindow.dron.yawSpeed.set(mensaje.yawspeed);
	}

	private void registraTemperatura(msg_scaled_pressure mensaje) {
		MainWindow.dron.temperature.set(mensaje.temperature);
	}

//	private void registraVibracion(msg_vibration mensaje) {
//		MainWindow.dron.vibrationX.set(mensaje.vibration_x);
//		MainWindow.dron.vibrationY.set(mensaje.vibration_y);
//		MainWindow.dron.vibrationZ.set(mensaje.vibration_z);
//	}

//	private void registraBateria(msg_sys_status mensaje) {
//		MainWindow.dron.battery.set(mensaje.voltage_battery);
//		MainWindow.dron.batteryActual.set(mensaje.current_battery);
//	}
}
