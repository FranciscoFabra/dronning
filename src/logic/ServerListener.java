package logic;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import com.esotericsoftware.kryo.io.Input;

import gui.MainWindow;

public class ServerListener extends Thread {
	/* Peticiones por UDP */
	DatagramSocket socketReceiver;
	DatagramPacket receivedPacket;
	byte[] receivedBuffer;
	
	/* Deserialización de las peticiones con Kryo */
	Input input;
	
	/* Inicio del proceso de conexión */
	long phaseStart;
	
	/* Comprobación de que un tipo de paquete ya se ha recibido */
	private boolean msg5Received = false;
	private boolean msg7Received = false;
	private boolean msg11Received = false;
	private boolean msg15Received = false;
	private boolean msg20Received = false;
	
	public ServerListener() throws SocketException {
		super(MainWindow.SERVER_LISTENER_NAME);
		
		this.socketReceiver = new DatagramSocket(MainWindow.server_Listener_Port);
		this.socketReceiver.setSoTimeout((int)MainWindow.waitShort);
	}

	@Override
	public void run() {
		phaseStart = System.currentTimeMillis();
		
		/* Paso previo. Espera a tener coordenadas para iniciar la conexión */
		
		//por implementar
		

		// Paso 1: Envía solicitud de conexión y espera confirmación
		waitData(MainWindow.CON_TO_SERV_CONNECTION_ACK);
		
		// Paso 2: Envía ACK de la confirmación y espera orden de conectar al cliente
		waitData(MainWindow.CON_TO_SERV_CONNECT_TO_CLI);
		
		/* Fase de experimentos */
		while (true) {
			
			phaseStart = System.currentTimeMillis();
			
			// Paso 1: Envía ACK de la orden de conexión al cliente y espera configuración
			waitData(MainWindow.CON_TO_SERV_SETUP);
			
			//Paso 2: Envía ACK de la configuración y espera orden de inicio de prueba
			waitData(MainWindow.CON_TO_SERV_START);
			
			// Paso 3: Envía temporalmente ACK del inicio de prueba y espera orden de finalizar
			waitData(MainWindow.CON_TO_SERV_FINISH);
			
			/* El listener se espera a que el talker termine */
			while (MainWindow.transitionalState1.get()) {
				MainWindow.esperaPasiva(MainWindow.waitLong);
			}
			MainWindow.transitionalState1.set(true);
			
			MainWindow.serverState.set(MainWindow.SERVER_STATE_CONNECTED);
			
			//CON FINES DE PRUEBA, SOLAMENTE UNA ITERACIÓN
//			MainWindow.moreJobs.set(false);
		}
	}
	
	/* Método auxiliar para esperar mensajes de la conexión */
	private void waitData(short messageType) {
		boolean goOn = true;
		short previousState = 0;
		short nextState = 0;
		long waiting = 0;
		
		switch (messageType) {
			case MainWindow.CON_TO_SERV_CONNECTION_ACK:
				previousState = MainWindow.SERVER_STATE_START;
				nextState = MainWindow.SERVER_STATE_LOGGING;
				waiting = MainWindow.connectionTimeout;
				break;
			case MainWindow.CON_TO_SERV_CONNECT_TO_CLI:
				previousState = MainWindow.SERVER_STATE_LOGGING;
				nextState = MainWindow.SERVER_STATE_CONNECTED;
				waiting = MainWindow.connectionTimeout;
				break;
			case MainWindow.CON_TO_SERV_SETUP:
				previousState = MainWindow.SERVER_STATE_CONNECTED;
				nextState = MainWindow.SERVER_STATE_CONFIGURED;
				waiting = MainWindow.testTimeout;
				break;
			case MainWindow.CON_TO_SERV_START:
				previousState = MainWindow.SERVER_STATE_CONFIGURED;
				nextState = MainWindow.SERVER_STATE_RUNNING;
				waiting = MainWindow.testTimeout;
				
				// Si se recibe duración < 0 --> Detener el proceso
				if (MainWindow.testDuration<0) {
					//Espera a que de tiempo a enviar msg12 para que el controller
					//	se de por enterado
					MainWindow.esperaPasiva(5 * 1000000000);
					System.out.println("Proceso detenido por el usuario.");
					try {
						MainWindow.shutdown();
					} catch (IOException e) {
						// Si no consigue cerrar el sistema, al menos cierra el programa
						System.exit(0);
					}
					System.exit(0);
				}
				break;
			case MainWindow.CON_TO_SERV_FINISH:
				previousState = MainWindow.SERVER_STATE_RUNNING;
				nextState = MainWindow.SERVER_STATE_STOPPED;
				waiting = MainWindow.testTimeout;
				break;
		}
		
		receivedBuffer = new byte[MainWindow.DGRAM_MAX_LENGTH];
		input = new Input(receivedBuffer);
		receivedPacket = new DatagramPacket(receivedBuffer, receivedBuffer.length);
		
		while (goOn && MainWindow.serverState.get() == previousState) {
			try {
				receivedPacket.setData(receivedBuffer, 0, receivedBuffer.length);
				socketReceiver.receive(receivedPacket);
				//Sólo interesan del controller
				if (receivedPacket.getAddress().getHostAddress().equals(MainWindow.CONTROLLER_IP.getHostAddress())) {
					input.setPosition(0);
					short messageTypeReceived = input.readShort();
					
					//System.out.println("Recibido " + messageTypeReceived);
					
					switch (messageTypeReceived) {
						case MainWindow.CON_TO_SERV_CONNECTION_ACK:
							if (previousState==MainWindow.SERVER_STATE_START
									&& !msg5Received) {
								MainWindow.serverState.set(nextState);
								msg5Received = true;
							}
							break;
						case MainWindow.CON_TO_SERV_CONNECT_TO_CLI:
							if (previousState==MainWindow.SERVER_STATE_LOGGING
									&& !msg7Received) {
								MainWindow.clientIp = InetAddress.getByName(input.readString());
								MainWindow.client_Listener_Port = input.readInt();
								MainWindow.serverState.set(nextState);
								msg7Received = true;
							}
							break;
						case MainWindow.CON_TO_SERV_SETUP:
							if (previousState==MainWindow.SERVER_STATE_CONNECTED
									&& !msg11Received) {
								MainWindow.baseFileName = input.readString();
								MainWindow.testDuration = input.readInt();
								MainWindow.sendRatio = input.readInt();
								MainWindow.isBroadcast = input.readBoolean();
								MainWindow.size = input.readShort();
								MainWindow.serverState.set(nextState);
								msg11Received = true;
							}
							break;
						case MainWindow.CON_TO_SERV_START:
							if (previousState==MainWindow.SERVER_STATE_CONFIGURED
									&& !msg15Received) {
								MainWindow.serverState.set(nextState);
								msg15Received = true;
							}
							break;
						case MainWindow.CON_TO_SERV_FINISH:
							if (previousState==MainWindow.SERVER_STATE_RUNNING
									&& !msg20Received) {
								MainWindow.serverState.set(nextState);
								msg20Received = true;
							}
							break;
					}
				}
			} catch (IOException e) {
				//System.out.println("Esperando datos de " + rol + ":\n" + e.getMessage());
			}
			// El proceso se detiene si se pierde la conexión
			if (System.currentTimeMillis()-phaseStart>waiting) goOn = false;
		}
		input.close();
		
		msg11Received = false;
		msg15Received = false;
		msg20Received = false;
		
		// Si no se confirma la conexión se detiene el programa
		if (MainWindow.serverState.get()<nextState) {
			System.out.println("No se consiguió conectar. Proceso detenido");
			System.exit(1);
		}
	}
	
	

}
