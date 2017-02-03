package logic;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import com.esotericsoftware.kryo.io.Output;

import gui.MainWindow;

public class ControllerTalker extends Thread {

	/* Envíos por UDP */
	DatagramSocket socket;
	byte[] sentBuffer;
	DatagramPacket sentPacket;

	/* Serialización de los envíos con Kryo */
	Output output;

	long phaseStart;

	public ControllerTalker() throws SocketException {
		super(MainWindow.CONTROLLER_TALKER_NAME);

		socket = new DatagramSocket();
	}

	@Override
	public void run() {
		phaseStart = System.currentTimeMillis();

		// Paso 1: Espera a que el cliente se conecte
		waitConnection("CLIENTLOGGING");

		// Paso 2: Envía ACK de la conexión del cliente y espera la confirmación
		sendData(MainWindow.CON_TO_CLI_CONNECTION_ACK);

		// Paso 3: Espera a que el servidor se conecte
		waitConnection("SERVERLOGGING");

		// Paso 4: Envía ACK de la conexión del servidor y espera la
		// confirmación
		sendData(MainWindow.CON_TO_SERV_CONNECTION_ACK);

		// Paso 5: Envía indicación de hacer bind al cliente y espera la
		// confirmación
		sendData(MainWindow.CON_TO_SERV_CONNECT_TO_CLI);

		/* Fase de experimentos */
		while (true) {
			
			phaseStart = System.currentTimeMillis();
			MainWindow.configured.set(false);
			MainWindow.started.set(false);
			
			// Paso 0: Espera a que la interfaz confirme los parámetros
			waitUI("CONFIGURE");
			MainWindow.configured.set(false);
			
			// Paso 1: Envía parámetros al cliente y espera confirmación
			sendData(MainWindow.CON_TO_CLI_SETUP);
			
			// Paso 2: Envía parámetros al servidor y espera confirmación
			sendData(MainWindow.CON_TO_SERV_SETUP);
			
			//Paso 3.1: Espera a que la interfaz confirme el inicio de la prueba
			waitUI("RUN");
			MainWindow.started.set(false);
			
			// Paso 3.2: Envía orden de inicio de prueba al cliente y espera confirmación
			sendData(MainWindow.CON_TO_CLI_START);
			
			// Paso 4: Envía orden de inicio de prueba al servidor y espera confirmación
			sendData(MainWindow.CON_TO_SERV_START);
			
			// Paso 5: Espera a que el cliente termine y no envía nada mientras
			waitConnection("CLIENTWORKING");
			
			// Paso 6: Envía confirmación del fin del cliente y espera también la confirmación
			sendData(MainWindow.CON_TO_CLI_FINISHED_ACK);
			
			MainWindow.transitionalState1.set(false);
			
			// Paso 7: Envía orden de fin al servidor y espera confirmación
			sendData(MainWindow.CON_TO_SERV_FINISH);
			
			MainWindow.transitionalState2.set(false);
			
			//MainWindow.controllerState.set(MainWindow.CONTROLLER_STATE_SERVER_CONNECTED_TO_CLIENT);
			MainWindow.esperaPasiva(MainWindow.waitLong);
			
			//CON FINES DE PRUEBA, SOLAMENTE UNA ITERACIÓN
//			MainWindow.moreJobs.set(false);
		}

	}
	
	private void waitUI(String rol) {
		boolean goOn = true;
		boolean buttonPressed = false;
		
		do {
			MainWindow.esperaPasiva(MainWindow.waitShort);
			if (rol.toUpperCase().equals("CONFIGURE")) buttonPressed = MainWindow.configured.get();
			else if (rol.toUpperCase().equals("RUN")) buttonPressed = MainWindow.started.get();
			//else System.out.println("Rol de espera a la interfaz mal configurado.");
			
			// El proceso se detiene si no se consigue conectar
			if (System.currentTimeMillis() - phaseStart > MainWindow.testTimeout)
				goOn = false;
		} while (goOn && !buttonPressed);

		if ((rol.toUpperCase().equals("CONFIGURE") && !MainWindow.configured.get())
				|| (rol.toUpperCase().equals("RUN") && !MainWindow.started.get())) {
			System.out.println("Se agotó la espera para pulsar el botón: " + rol + ". Proceso detenido");
			System.exit(1);
		}
	}

	private void waitConnection(String rol) {
		boolean goOn = true;
		short previousState = 0;
		short nextState = 0;
		long waiting = 0;
		
		if (rol.toUpperCase().equals("CLIENTLOGGING")) {
			previousState = MainWindow.CONTROLLER_STATE_START;
			nextState = MainWindow.CONTROLLER_STATE_CLIENT_LOGGING;
			waiting = MainWindow.connectionTimeout;
		} else if (rol.toUpperCase().equals("SERVERLOGGING")) {
			previousState = MainWindow.CONTROLLER_STATE_CLIENT_LOGGED;
			nextState = MainWindow.CONTROLLER_STATE_SERVER_LOGGING;
			waiting = MainWindow.connectionTimeout;
		} else if (rol.toUpperCase().equals("CLIENTWORKING")) {
			previousState = MainWindow.CONTROLLER_STATE_SERVER_RUNNING;
			nextState = MainWindow.CONTROLLER_STATE_CLIENT_STOPPING;
			waiting = MainWindow.testTimeout;
		}

		while (goOn && MainWindow.controllerState.get() == previousState) {
			MainWindow.esperaPasiva(MainWindow.waitLong);
			
			// El proceso se detiene si se pierde la conexión
			if (System.currentTimeMillis() - phaseStart > waiting) goOn = false;
		}
		// Si no se confirma la conexión se detiene el programa
		if (MainWindow.controllerState.get() < nextState) {
			System.out.println("No se detectó ningún " + rol + ". Proceso detenido");
			System.exit(1);
		}
	}

	private void sendData(short messageType) {
		boolean goOn = true;
		short previousState = 0;
		short nextState = 0;
		InetAddress ip = null;
		int port = 0;
		long waiting = 0;
		
		switch(messageType) {
			case MainWindow.CON_TO_CLI_CONNECTION_ACK:
				previousState = MainWindow.CONTROLLER_STATE_CLIENT_LOGGING;
				nextState = MainWindow.CONTROLLER_STATE_CLIENT_LOGGED;
				ip = MainWindow.clientIp;
				port = MainWindow.client_Listener_Port;
				waiting = MainWindow.connectionTimeout;
				break;
			case MainWindow.CON_TO_SERV_CONNECTION_ACK:
				previousState = MainWindow.CONTROLLER_STATE_SERVER_LOGGING;
				nextState = MainWindow.CONTROLLER_STATE_SERVER_LOGGED;
				ip = MainWindow.serverIp;
				port = MainWindow.server_Listener_Port;
				waiting = MainWindow.connectionTimeout;
				break;
			case MainWindow.CON_TO_SERV_CONNECT_TO_CLI:
				previousState = MainWindow.CONTROLLER_STATE_SERVER_LOGGED;
				nextState = MainWindow.CONTROLLER_STATE_SERVER_CONNECTED_TO_CLIENT;
				ip = MainWindow.serverIp;
				port = MainWindow.server_Listener_Port;
				waiting = MainWindow.connectionTimeout;
				break;
			case MainWindow.CON_TO_CLI_SETUP:
				previousState = MainWindow.CONTROLLER_STATE_SERVER_CONNECTED_TO_CLIENT;
				nextState = MainWindow.CONTROLLER_STATE_CLIENT_CONFIGURED;
				ip = MainWindow.clientIp;
				port = MainWindow.client_Listener_Port;
				waiting = MainWindow.testTimeout;
				break;
			case MainWindow.CON_TO_SERV_SETUP:
				previousState = MainWindow.CONTROLLER_STATE_CLIENT_CONFIGURED;
				nextState = MainWindow.CONTROLLER_STATE_SERVER_CONFIGURED;
				ip = MainWindow.serverIp;
				port = MainWindow.server_Listener_Port;
				waiting = MainWindow.testTimeout;
				break;
			case MainWindow.CON_TO_CLI_START:
				previousState = MainWindow.CONTROLLER_STATE_SERVER_CONFIGURED;
				nextState = MainWindow.CONTROLLER_STATE_CLIENT_RUNNING;
				ip = MainWindow.clientIp;
				port = MainWindow.client_Listener_Port;
				waiting = MainWindow.testTimeout;
				break;
			case MainWindow.CON_TO_SERV_START:
				previousState = MainWindow.CONTROLLER_STATE_CLIENT_RUNNING;
				nextState = MainWindow.CONTROLLER_STATE_SERVER_RUNNING;
				ip = MainWindow.serverIp;
				port = MainWindow.server_Listener_Port;
				waiting = MainWindow.testTimeout;
				break;
			case MainWindow.CON_TO_CLI_FINISHED_ACK:
				previousState = MainWindow.CONTROLLER_STATE_CLIENT_STOPPING;
				nextState = MainWindow.CONTROLLER_STATE_CLIENT_STOPPED;
				ip = MainWindow.clientIp;
				port = MainWindow.client_Listener_Port;
				waiting = MainWindow.testTimeout;
				break;
			case MainWindow.CON_TO_SERV_FINISH:
				previousState = MainWindow.CONTROLLER_STATE_CLIENT_STOPPED;
				nextState = MainWindow.CONTROLLER_STATE_SERVER_FINISHED;
				ip = MainWindow.serverIp;
				port = MainWindow.server_Listener_Port;
				waiting = MainWindow.testTimeout;
				break;
		}
		
		sentBuffer = new byte[MainWindow.DGRAM_MAX_LENGTH];
		output = new Output(sentBuffer);
		sentPacket = new DatagramPacket(sentBuffer, sentBuffer.length, ip, port);

		while (goOn && MainWindow.controllerState.get() == previousState) {
			
			//System.out.println("Enviando " + messageType);
			
			output.clear();
			output.writeShort(messageType);
			//Si el mensaje tiene datos los incluimos
			switch (messageType) {
				case MainWindow.CON_TO_SERV_CONNECT_TO_CLI:
					output.writeString(MainWindow.clientIp.getHostAddress());
					output.writeInt(MainWindow.client_Listener_Port);
					break;
				case MainWindow.CON_TO_CLI_SETUP:
					Date date = Calendar.getInstance().getTime();
			        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
			        String fileName = sdf.format(date) + "_" + MainWindow.filename.getText();
					output.writeString(fileName);
					MainWindow.testDuration = Integer.parseInt(MainWindow.duration.getText());
					output.writeInt(MainWindow.testDuration);
					//output.writeInt(Integer.parseInt(MainWindow.ratio.getText()));
					break;
				case MainWindow.CON_TO_SERV_SETUP:
					Date date2 = Calendar.getInstance().getTime();
			        SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
			        String fileName2 = sdf2.format(date2) + "_" + MainWindow.filename.getText();
					output.writeString(fileName2);
					output.writeInt(Integer.parseInt(MainWindow.duration.getText()));
					output.writeInt(Integer.parseInt(MainWindow.ratio.getText()));
					output.writeBoolean(MainWindow.rdbtnBroadcast.isSelected());
					output.writeShort(Short.parseShort(MainWindow.packetSize.getText()));
					break;
			}
			output.flush();
			sentPacket.setData(sentBuffer, 0, output.position());
			
			/* Envío por UDP */
			try {
				socket.send(sentPacket);
				/*
				 * Espera para el siguiente envío. Para dar tiempo a recibir
				 * respuesta
				 */
				MainWindow.esperaPasiva(MainWindow.waitMiddle);
			} catch (IOException e) {
				System.out.println("No se pudo enviar el mensaje " + messageType + ":\n" + e.getMessage());
			}
			// El proceso se detiene si se pierde la conexión
			if (System.currentTimeMillis() - phaseStart > waiting) goOn = false;
		}
		output.close();
		
		// Si no se confirma la conexión se detiene el programa
		if (MainWindow.controllerState.get() < nextState) {
			System.out.println("Agotado el tiempo enviando el mensaje " + messageType + ". Proceso detenido");
			System.exit(1);
		}
	}
}
