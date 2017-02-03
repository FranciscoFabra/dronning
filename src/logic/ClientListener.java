package logic;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import com.esotericsoftware.kryo.io.Input;

import gui.MainWindow;

public class ClientListener extends Thread {
	/* Peticiones por UDP */
	DatagramSocket socketReceiver;
	DatagramPacket receivedPacket;
	byte[] receivedBuffer;
	
	/* Deserializaci�n de las peticiones con Kryo */
	Input input;
	
	/* Inicio del proceso de conexi�n */
	long phaseStart;
	
	/* Comprobaci�n de que un tipo de paquete ya se ha recibido */
	private boolean msg2Received = false;
	private boolean msg9Received = false;
	private boolean msg13Received = false;
	private boolean msg18Received = false;
	
	
	public ClientListener() throws SocketException {
		super(MainWindow.CLIENT_LISTENER_NAME);
		
		this.socketReceiver = new DatagramSocket(MainWindow.client_Listener_Port);
		this.socketReceiver.setSoTimeout((int)MainWindow.waitShort);
	}

	@Override
	public void run() {
		phaseStart = System.currentTimeMillis();
		
		/* Paso previo. Espera a tener coordenadas para iniciar la conexi�n */
		
		//por implementar
		

		// Paso 1: Env�a solicitud de conexi�n y espera confirmaci�n
		waitData(MainWindow.CON_TO_CLI_CONNECTION_ACK);
		
		/* Fase de experimentos */
		while (true) {
			
			phaseStart = System.currentTimeMillis();
			
			// Paso 1: Env�a ACK de la confirmaci�n y espera configuraci�n
			waitData(MainWindow.CON_TO_CLI_SETUP);
			
			// Paso 2: Env�a ACK de la configuraci�n y espera inicio de prueba
			waitData(MainWindow.CON_TO_CLI_START);
			
			//Paso 3: Env�a temporalmente ACK de estar trabajando y espera a terminar la tarea
			while (MainWindow.clientState.get()==MainWindow.CLIENT_STATE_RUNNING) {
				MainWindow.esperaPasiva(MainWindow.waitLong);
			}
			
			//Paso 4: Env�a ACK de haber terminado y espera confirmaci�n
			waitData(MainWindow.CON_TO_CLI_FINISHED_ACK);
			
			/* El listener se espera a que el talker termine */
			while (MainWindow.transitionalState1.get()) {
				MainWindow.esperaPasiva(MainWindow.waitLong);
			}
			MainWindow.transitionalState1.set(true);
			
			MainWindow.clientState.set(MainWindow.CLIENT_STATE_LOGGING);
			
			//CON FINES DE PRUEBA, SOLAMENTE UNA ITERACI�N
//			MainWindow.moreJobs.set(false);
		}
		
		
	}
	
	/* M�todo auxiliar para esperar mensajes de la conexi�n */
	private void waitData(short messageType) {
		boolean goOn = true;
		short previousState = 0;
		short nextState = 0;
		long waiting = 0;
		
		switch (messageType) {
			case MainWindow.CON_TO_CLI_CONNECTION_ACK:
				previousState = MainWindow.CLIENT_STATE_START;
				nextState = MainWindow.CLIENT_STATE_LOGGING;
				waiting = MainWindow.connectionTimeout;
				break;
			case MainWindow.CON_TO_CLI_SETUP:
				previousState = MainWindow.CLIENT_STATE_LOGGING;
				nextState = MainWindow.CLIENT_STATE_CONFIGURED;
				waiting = MainWindow.testTimeout;
				break;
			case MainWindow.CON_TO_CLI_START:
				previousState = MainWindow.CLIENT_STATE_CONFIGURED;
				nextState = MainWindow.CLIENT_STATE_RUNNING;
				waiting = MainWindow.testTimeout;
				
				// Si se recibe duraci�n < 0 --> Detener el proceso
				if (MainWindow.testDuration<0) {
					//Espera a que de tiempo a enviar msg10 para que tambi�n se
					//	notifique al servidor
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
			case MainWindow.CON_TO_CLI_FINISHED_ACK:
				previousState = MainWindow.CLIENT_STATE_STOPPING;
				nextState = MainWindow.CLIENT_STATE_STOPPED;
				waiting = MainWindow.testTimeout;
				break;
		}
		
		receivedBuffer = new byte[MainWindow.DGRAM_MAX_LENGTH];
		input = new Input(receivedBuffer);
		receivedPacket = new DatagramPacket(receivedBuffer, receivedBuffer.length);
		
		while (goOn && MainWindow.clientState.get() == previousState) {
			try {
				receivedPacket.setData(receivedBuffer, 0, receivedBuffer.length);
				socketReceiver.receive(receivedPacket);
				//S�lo interesan del controller
				if (receivedPacket.getAddress().getHostAddress().equals(MainWindow.CONTROLLER_IP.getHostAddress())) {
					//receivedBuffer = receivedPacket.getData();
					input.setPosition(0);
					short messageTypeReceived = input.readShort();
					
					//System.out.println("Recibido" + messageTypeReceived);
					
					switch (messageTypeReceived) {
						case 2:
							if (previousState== MainWindow.CLIENT_STATE_START
									&& !msg2Received) {
								MainWindow.clientState.set(nextState);
								msg2Received = true;
							}
							break;
						case 9:
							if (previousState== MainWindow.CLIENT_STATE_LOGGING
									&& !msg9Received) {
								MainWindow.baseFileName = input.readString();
								MainWindow.testDuration = input.readInt();
								//MainWindow.sendRatio = input.readInt();
								MainWindow.clientState.set(nextState);
								msg9Received = true;
							}
							break;
						case 13:
							if (previousState== MainWindow.CLIENT_STATE_CONFIGURED
									&& !msg13Received) {
								MainWindow.clientState.set(nextState);
								msg13Received = true;
							}
							break;
						case 18:
							if (previousState== MainWindow.CLIENT_STATE_STOPPING
									&& !msg18Received) {
								MainWindow.clientState.set(nextState);
								msg18Received = true;
							}
							break;
					}
				}
			} catch (IOException e) {
				//System.out.println("Esperando datos de " + rol + ":\n" + e.getMessage());
			}
			// El proceso se detiene si se pierde la conexi�n
			if (System.currentTimeMillis()-phaseStart>waiting) goOn = false;
		}
		input.close();
		
		msg9Received = false;
		msg13Received = false;
		msg18Received = false;
		
		//Si no se confirma la conexi�n se detiene el programa
		if (MainWindow.clientState.get()<nextState) {
			System.out.println("No se consigui� conectar. Proceso detenido");
			System.exit(1);
		}
	}
	
	
	
	
	
	
	
}
