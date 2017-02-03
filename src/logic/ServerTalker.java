package logic;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import com.esotericsoftware.kryo.io.Output;

import gui.MainWindow;

public class ServerTalker extends Thread {
	
	/* Envíos por UDP */
	DatagramSocket socket;
	byte[] sentBuffer;
	DatagramPacket sentPacket;
	
	/* Serialización de los envíos con Kryo */
	Output output;

	long phaseStart;
	
	public ServerTalker() throws SocketException {
		super(MainWindow.SERVER_TALKER_NAME);
		
		socket = new DatagramSocket();
	}
	
	@Override
	public void run() {
		phaseStart = System.currentTimeMillis();
		
		/* Paso previo. Espera a tener coordenadas para iniciar la conexión */
		//if (!MainWindow.ISDEBUGGING) while (MainWindow.dron.altitude.get() == 0) MainWindow.esperaPasiva(MainWindow.waitLong);
		//Falta cambiarlo a la espera de latitud o de longitud

		// Paso 1: Envía solicitud de conexión y espera confirmación
		sendData(MainWindow.SERV_TO_CON_CONNECTION_REQUEST, false);
		
		// Paso 2: Envía ACK de la confirmación y espera orden de conectar al cliente
		sendData(MainWindow.SERV_TO_CON_CONNECTION_ACK, false);
		
		/* Fase de experimentos */
		while (true) {
			
			phaseStart = System.currentTimeMillis();
			
			// Paso 1: Envía ACK de la orden de conexión al cliente y espera configuración
			sendData(MainWindow.SERV_TO_CON_CONNECT_TO_CLI_ACK, false);
			
			// Paso 2: Envía ACK de la configuración y espera inicio de prueba
			sendData(MainWindow.SERV_TO_CON_SETUP_ACK, false);
			
			// Paso 3.1: Envía temporalmente ACK del inicio de prueba y espera orden de finalizar
			sendData(MainWindow.SERV_TO_CON_START_ACK, true);
			//Paso 3.2: Espera a que la tarea se termine
			while (MainWindow.serverState.get()==MainWindow.SERVER_STATE_RUNNING) {
				MainWindow.esperaPasiva(MainWindow.waitLong);
			}
			
			/* El talker se espera a que termine el envío al cliente */
			//Necesario para que se envíe correctamente el ratio de envío
			while (MainWindow.transitionalState2.get()) {
				MainWindow.esperaPasiva(MainWindow.waitLong);
			}
			MainWindow.transitionalState2.set(true);
			
			MainWindow.transitionalState1.set(false);
			
			MainWindow.serverState.set(MainWindow.SERVER_STATE_CONNECTED);
			
			//CON FINES DE PRUEBA, SOLAMENTE UNA ITERACIÓN
//			MainWindow.moreJobs.set(false);
		}
	}
	
	/** limited indica que se enviará durante un tiempo limitado, no hasta recibir confirmación */
	private void sendData(short messageType, boolean limited) {
		boolean goOn = true;
		short previousState = 0;
		short nextState = 0;
		long waiting = 0;
		
		switch (messageType) {
			case MainWindow.SERV_TO_CON_CONNECTION_REQUEST:
				previousState = MainWindow.SERVER_STATE_START;
				nextState = MainWindow.SERVER_STATE_LOGGING;
				waiting = MainWindow.connectionTimeout;
				break;
			case MainWindow.SERV_TO_CON_CONNECTION_ACK:
				previousState = MainWindow.SERVER_STATE_LOGGING;
				nextState = MainWindow.SERVER_STATE_CONNECTED;
				waiting = MainWindow.connectionTimeout;
				break;
			case MainWindow.SERV_TO_CON_CONNECT_TO_CLI_ACK:
				previousState = MainWindow.SERVER_STATE_CONNECTED;
				nextState = MainWindow.SERVER_STATE_CONFIGURED;
				waiting = MainWindow.testTimeout;
				break;
			case MainWindow.SERV_TO_CON_SETUP_ACK:
				previousState = MainWindow.SERVER_STATE_CONFIGURED;
				nextState = MainWindow.SERVER_STATE_RUNNING;
				waiting = MainWindow.testTimeout;
				break;
			case MainWindow.SERV_TO_CON_START_ACK:
				previousState = MainWindow.SERVER_STATE_RUNNING;
				nextState = MainWindow.SERVER_STATE_STOPPED; //No se llega a utilizar por espera limitada
				waiting = MainWindow.testTimeout;
				break;
		}
		
		long begin = System.currentTimeMillis();
		
		sentBuffer = new byte[MainWindow.DGRAM_MAX_LENGTH];
		output = new Output(sentBuffer);
		sentPacket = new DatagramPacket(sentBuffer, sentBuffer.length,
				MainWindow.CONTROLLER_IP, MainWindow.CONTROLLER_LISTENER_PORT);

		while (goOn && MainWindow.serverState.get() == previousState) {
			
			//System.out.println("Enviando " + messageType);
			
			output.clear();
			output.writeShort(messageType);
			
			//Si el mensaje tiene datos los incluimos
			boolean flag = false;
			if (messageType==MainWindow.SERV_TO_CON_CONNECT_TO_CLI_ACK) flag=true;
			switch (messageType) {
				case MainWindow.SERV_TO_CON_CONNECTION_REQUEST:
					output.writeInt(MainWindow.server_Listener_Port);
					break;
				case MainWindow.SERV_TO_CON_CONNECT_TO_CLI_ACK:
				case MainWindow.SERV_TO_CON_SETUP_ACK:
					output.writeLong(MainWindow.dron.latitude.get());
					output.writeLong(MainWindow.dron.longitude.get());
					output.writeLong(MainWindow.dron.altitude.get());
					output.writeFloat(MainWindow.dron.yaw.get());
					if (flag) output.writeFloat(MainWindow.sendRatioReal.get());
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
			// Si el envío está limitado para no ocupar WiFi, se detiene tras 1 segundo
			long  now = System.currentTimeMillis();
			if (limited && now - begin > MainWindow.limitedSendingTimeout) goOn = false;
			// El proceso se detiene si se consigue la confirmación
			if (goOn && now - phaseStart > waiting) goOn = false;
		}
		output.close();
		
		// Si no se confirma la conexión se detiene el programa
		if (!limited && MainWindow.serverState.get() < nextState) {
			System.out.println("Agotado el tiempo enviando el mensaje " + messageType + ". Proceso detenido");
			System.exit(1);
		}
	}
	
}
