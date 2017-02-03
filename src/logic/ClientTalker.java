package logic;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import com.esotericsoftware.kryo.io.Output;

import gui.MainWindow;

public class ClientTalker extends Thread {
	/* Envíos por UDP */
	DatagramSocket socket;
	byte[] sentBuffer;
	DatagramPacket sentPacket;
	
	/* Serialización de los envíos con Kryo */
	Output output;

	long phaseStart;
	
	public ClientTalker() throws SocketException {
		super(MainWindow.CLIENT_TALKER_NAME);
		
		socket = new DatagramSocket();
	}
	
	@Override
	public void run() {
		
		phaseStart = System.currentTimeMillis();
		
		/* Paso previo. Espera a tener coordenadas para iniciar la conexión */
		//if (!MainWindow.ISDEBUGGING) while (MainWindow.dron.altitude.get() == 0) MainWindow.esperaPasiva(MainWindow.waitLong);
		//Falta cambiarlo a la espera de latitud o de longitud
		
		// Paso 1: Envía solicitud de conexión y espera confirmación
		sendData(MainWindow.CLI_TO_CON_CONNECTION_REQUEST, false);
		
		/* Fase de experimentos */
		while (true) {
			
			phaseStart = System.currentTimeMillis();
			
			// Paso 1: Envía ACK de la confirmación y espera configuración
			sendData(MainWindow.CLI_TO_CON_CONNECTION_ACK, false);
			
			// Paso 2: Envía ACK de la configuración y espera inicio de prueba
			sendData(MainWindow.CLI_TO_CON_SETUP_ACK, false);
			
			//Paso 3.1: Envía temporalmente ACK de estar trabajando y espera a terminar la tarea
			sendData(MainWindow.CLI_TO_CON_START_ACK, true);
			//Paso 3.2: Espera a que la tarea se termine
			while (MainWindow.clientState.get()==MainWindow.CLIENT_STATE_RUNNING) {
				MainWindow.esperaPasiva(MainWindow.waitLong);
			}
			
			//Paso 4: Envía ACK de haber terminado y espera confirmación
			sendData(MainWindow.CLI_TO_CON_FINISHED, false);
			
			/* El talker se espera a que se termine de recibir paquetes */
			while (MainWindow.transitionalState2.get()) {
				MainWindow.esperaPasiva(MainWindow.waitLong);
			}
			MainWindow.transitionalState2.set(true);
			
			MainWindow.transitionalState1.set(false);
			
			MainWindow.clientState.set(MainWindow.CLIENT_STATE_LOGGING);
			
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
			case MainWindow.CLI_TO_CON_CONNECTION_REQUEST:
				previousState = MainWindow.CLIENT_STATE_START;
				nextState = MainWindow.CLIENT_STATE_LOGGING;
				waiting = MainWindow.connectionTimeout;
				break;
			case MainWindow.CLI_TO_CON_CONNECTION_ACK:
				previousState = MainWindow.CLIENT_STATE_LOGGING;
				nextState = MainWindow.CLIENT_STATE_CONFIGURED;
				waiting = MainWindow.testTimeout;
				break;
			case MainWindow.CLI_TO_CON_SETUP_ACK:
				previousState = MainWindow.CLIENT_STATE_CONFIGURED;
				nextState = MainWindow.CLIENT_STATE_RUNNING;
				waiting = MainWindow.testTimeout;
				break;
			case MainWindow.CLI_TO_CON_START_ACK:
				previousState = MainWindow.CLIENT_STATE_RUNNING;
				nextState = MainWindow.CLIENT_STATE_STOPPING; //No se llega a utilizar por espera limitada
				waiting = MainWindow.testTimeout;
				break;
			case MainWindow.CLI_TO_CON_FINISHED:
				previousState = MainWindow.CLIENT_STATE_STOPPING;
				nextState = MainWindow.CLIENT_STATE_STOPPED;
				waiting = MainWindow.testTimeout;
				break;
		}
		
		long begin = System.currentTimeMillis();
		
		sentBuffer = new byte[MainWindow.DGRAM_MAX_LENGTH];
		output = new Output(sentBuffer);
		sentPacket = new DatagramPacket(sentBuffer, sentBuffer.length,
				MainWindow.CONTROLLER_IP, MainWindow.CONTROLLER_LISTENER_PORT);
		
		while (goOn && MainWindow.clientState.get() == previousState) {
			
			//System.out.println("Enviando " + messageType);
			
			output.clear();
			output.writeShort(messageType);
			
			//Si el mensaje tiene datos los incluimos
			switch (messageType) {
				case MainWindow.CLI_TO_CON_CONNECTION_REQUEST:
					output.writeInt(MainWindow.client_Listener_Port);
					break;
				case MainWindow.CLI_TO_CON_CONNECTION_ACK:
				case MainWindow.CLI_TO_CON_SETUP_ACK:
					output.writeLong(MainWindow.dron.latitude.get());
					output.writeLong(MainWindow.dron.longitude.get());
					output.writeLong(MainWindow.dron.altitude.get());
					output.writeFloat(MainWindow.dron.yaw.get());
					break;
				case MainWindow.CLI_TO_CON_FINISHED:
					output.writeFloat(MainWindow.loosingRatio.get());
					output.writeLong(MainWindow.totalLostPackets.get());
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
			
			// El proceso se detiene si se pierde la conexión
			if (goOn && now - phaseStart > waiting) goOn = false;
		}
		output.close();
		
		// Si no se confirma la conexión se detiene el programa
//		if (!limited && MainWindow.clientState.get() == previousState) {
		if (!limited && MainWindow.clientState.get() < nextState) {
			System.out.println("Agotado el tiempo enviando el mensaje " + messageType + ". Proceso detenido");
			System.exit(1);
		}
	}
}
