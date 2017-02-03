package logic;

import java.awt.Color;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.text.NumberFormat;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.esotericsoftware.kryo.io.Input;

import gui.MainWindow;
import pojo.UTMCoordinates;

public class ControllerListener extends Thread {

	/* Peticiones por UDP */
	DatagramSocket socketReceiver;
	DatagramPacket receivedPacket;
	byte[] receivedBuffer;

	/* Deserialización de las peticiones con Kryo */
	Input input;

	/* Inicio del proceso de conexión */
	long phaseStart;

	/* Comprobación de que un tipo de paquete ya se ha recibido */
	private boolean msg1Received = false;
	private boolean msg3Received = false;
	private boolean msg4Received = false;
	private boolean msg6Received = false;
	private boolean msg8Received = false;
	private boolean msg10Received = false;
	private boolean msg12Received = false;
	private boolean msg14Received = false;
	private boolean msg16Received = false;
	private boolean msg17Received = false;
	private boolean msg19Received = false;
	private boolean msg21Received = false;
	
	/* Timer para la cuenta atrás */
	private Timer timer;
	
	/* Variables para el cálculo de la posición relativa de los drones */
	private long serverLatitude = 0;
	private long serverLongitude = 0;
	private long serverAltitude = -1000000;
	private long clientLatitude = 0;
	private long clientLongitude = 0;
	private long clientAltitude = -1000000;
	private String distanceString = "";
	private float serverJaw = 180.0f;
	private float clientJaw = 180.0f;

	public ControllerListener() throws SocketException {
		super(MainWindow.CONTROLLER_LISTENER_NAME);

		this.socketReceiver = new DatagramSocket(MainWindow.CONTROLLER_LISTENER_PORT);
		this.socketReceiver.setSoTimeout((int)MainWindow.waitShort);
	}

	@Override
	public void run() {
		phaseStart = System.currentTimeMillis();

		/* Fase inicial de conexión */

		// Paso 1: Espera a que el cliente se conecte
		waitData(MainWindow.CLI_TO_CON_CONNECTION_REQUEST);

		// Paso 2: Envía ACK de la conexión del cliente y espera la confirmación
		waitData(MainWindow.CLI_TO_CON_CONNECTION_ACK);

		// Paso 3: Espera a que el servidor se conecte
		waitData(MainWindow.SERV_TO_CON_CONNECTION_REQUEST);

		// Paso 4: Envía ACK de la conexión del servidor y espera la
		// confirmación
		waitData(MainWindow.SERV_TO_CON_CONNECTION_ACK);

		// Paso 5: Envía indicación de hacer bind al cliente y espera la
		// confirmación
		waitData(MainWindow.SERV_TO_CON_CONNECT_TO_CLI_ACK);

		/* Fase de experimentos */
		while (true) {
			
			phaseStart = System.currentTimeMillis();

			// Paso 1: Envía parámetros al cliente y espera confirmación
			waitData(MainWindow.CLI_TO_CON_SETUP_ACK);
			MainWindow.configured.set(true);

			// Paso 2: Envía parámetros al servidor y espera confirmación
			waitData(MainWindow.SERV_TO_CON_SETUP_ACK);

			// Paso 3: Envía orden de inicio de prueba al cliente y espera
			// confirmación
			waitData(MainWindow.CLI_TO_CON_START_ACK);

			// Paso 4: Envía orden de inicio de prueba al servidor y espera
			// confirmación
			waitData(MainWindow.SERV_TO_CON_START_ACK);
			
			// Paso 5: Espera a que el cliente termine y no envía nada mientras
			waitData(MainWindow.CLI_TO_CON_FINISHED);
			
			// Paso 6: Envía confirmación del fin del cliente y espera también la confirmación
			waitData(MainWindow.CLI_TO_CON_FINISHED_ACK);
			
			while (MainWindow.transitionalState1.get()) {
				MainWindow.esperaPasiva(MainWindow.waitLong);
			}
			MainWindow.transitionalState1.set(true);
			
//			Haciendo debugging por aquí (no parece que salga del anterior waitAck)
			
			// Paso 7: Envía orden de fin al servidor y espera confirmación
			waitData(MainWindow.SERV_TO_CON_FINISH_ACK);
			
			while (MainWindow.transitionalState2.get()) {
				MainWindow.esperaPasiva(MainWindow.waitLong);
			}
			MainWindow.transitionalState2.set(true);
			
			/* Antes de terminar un experimento se resetean variables */
			updateUI(MainWindow.CONTROLLER_STATE_SERVER_CONNECTED_TO_CLIENT);

			/* Antes de terminar un experimento se resetean variables */
			
			
			MainWindow.controllerState.set(MainWindow.CONTROLLER_STATE_SERVER_CONNECTED_TO_CLIENT);
			
			//CON FINES DE PRUEBA, SOLAMENTE UNA ITERACIÓN
//			MainWindow.moreJobs.set(false);
		}
	}

	/* Método auxiliar para esperar una solicitud de conexión */
	private void waitData(short messageType) {
		boolean goOn = true;
		short previousState = 0;
		short nextState = 0;
		String ip = null;
		long waiting = 0;
		
		switch (messageType) {
			case MainWindow.CLI_TO_CON_CONNECTION_REQUEST:
				previousState = MainWindow.CONTROLLER_STATE_START;
				nextState = MainWindow.CONTROLLER_STATE_CLIENT_LOGGING;
				waiting = MainWindow.connectionTimeout;
				break;
			case MainWindow.CLI_TO_CON_CONNECTION_ACK:
				previousState = MainWindow.CONTROLLER_STATE_CLIENT_LOGGING;
				nextState = MainWindow.CONTROLLER_STATE_CLIENT_LOGGED;
				ip = MainWindow.clientIp.getHostAddress();
				waiting = MainWindow.connectionTimeout;
				break;
			case MainWindow.SERV_TO_CON_CONNECTION_REQUEST:
				previousState = MainWindow.CONTROLLER_STATE_CLIENT_LOGGED;
				nextState = MainWindow.CONTROLLER_STATE_SERVER_LOGGING;
				waiting = MainWindow.connectionTimeout;
				break;
			case MainWindow.SERV_TO_CON_CONNECTION_ACK:
				previousState = MainWindow.CONTROLLER_STATE_SERVER_LOGGING;
				nextState = MainWindow.CONTROLLER_STATE_SERVER_LOGGED;
				ip = MainWindow.serverIp.getHostAddress();
				waiting = MainWindow.connectionTimeout;
				break;
			case MainWindow.SERV_TO_CON_CONNECT_TO_CLI_ACK:
				previousState = MainWindow.CONTROLLER_STATE_SERVER_LOGGED;
				nextState = MainWindow.CONTROLLER_STATE_SERVER_CONNECTED_TO_CLIENT;
				ip = MainWindow.serverIp.getHostAddress();
				waiting = MainWindow.connectionTimeout;
				break;
			case MainWindow.CLI_TO_CON_SETUP_ACK:
				previousState = MainWindow.CONTROLLER_STATE_SERVER_CONNECTED_TO_CLIENT;
				nextState = MainWindow.CONTROLLER_STATE_CLIENT_CONFIGURED;
				ip = MainWindow.clientIp.getHostAddress();
				waiting = MainWindow.testTimeout;
				break;
			case MainWindow.SERV_TO_CON_SETUP_ACK:
				previousState = MainWindow.CONTROLLER_STATE_CLIENT_CONFIGURED;
				nextState = MainWindow.CONTROLLER_STATE_SERVER_CONFIGURED;
				ip = MainWindow.serverIp.getHostAddress();
				waiting = MainWindow.testTimeout;
				
				// Si duración < 0 --> Detener el proceso
				if (MainWindow.testDuration<0) {
					//Espera a que de tiempo a enviar msg10 para que también se
					//	notifique al servidor
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							JOptionPane.showMessageDialog(
									MainWindow.frame,
									"Proceso de captura detenido por el usuario.",
									"Fin de las pruebas", JOptionPane.INFORMATION_MESSAGE);
						}
					});
					MainWindow.esperaPasiva(5 * 1000000000);
					System.out.println("Proceso detenido por el usuario.");
					System.exit(0);
				}
				break;
			case MainWindow.CLI_TO_CON_START_ACK:
				previousState = MainWindow.CONTROLLER_STATE_SERVER_CONFIGURED;
				nextState = MainWindow.CONTROLLER_STATE_CLIENT_RUNNING;
				ip = MainWindow.clientIp.getHostAddress();
				waiting = MainWindow.testTimeout;
				break;
			case MainWindow.SERV_TO_CON_START_ACK:
				previousState = MainWindow.CONTROLLER_STATE_CLIENT_RUNNING;
				nextState = MainWindow.CONTROLLER_STATE_SERVER_RUNNING;
				ip = MainWindow.serverIp.getHostAddress();
				waiting = MainWindow.testTimeout;
				break;
			case MainWindow.CLI_TO_CON_FINISHED:
				previousState = MainWindow.CONTROLLER_STATE_SERVER_RUNNING;
				nextState = MainWindow.CONTROLLER_STATE_CLIENT_STOPPING;
				ip = MainWindow.clientIp.getHostAddress();
				waiting = MainWindow.testTimeout;
				break;
			case MainWindow.CLI_TO_CON_FINISHED_ACK:
				previousState = MainWindow.CONTROLLER_STATE_CLIENT_STOPPING;
				nextState = MainWindow.CONTROLLER_STATE_CLIENT_STOPPED;
				ip = MainWindow.clientIp.getHostAddress();
				waiting = MainWindow.testTimeout;
				break;
			case MainWindow.SERV_TO_CON_FINISH_ACK:
				previousState = MainWindow.CONTROLLER_STATE_CLIENT_STOPPED;
				nextState = MainWindow.CONTROLLER_STATE_SERVER_FINISHED;
				ip = MainWindow.serverIp.getHostAddress();
				waiting = MainWindow.testTimeout;
				break;
		}
		
		receivedBuffer = new byte[MainWindow.DGRAM_MAX_LENGTH];
		input = new Input(receivedBuffer);
		receivedPacket = new DatagramPacket(receivedBuffer, receivedBuffer.length);

		while (goOn && MainWindow.controllerState.get() == previousState) {
			try {
				receivedPacket.setData(receivedBuffer, 0, receivedBuffer.length);
				socketReceiver.receive(receivedPacket);
				
				input.setPosition(0);
				short messageTypeReceived = input.readShort();
				
				// Primero los mensajes iniciales, cuando todavía no se dispone de IP
				switch (messageTypeReceived) {
					case MainWindow.CLI_TO_CON_CONNECTION_REQUEST:
						if(previousState == MainWindow.CONTROLLER_STATE_START
								&& !msg1Received) {
							MainWindow.clientIp = receivedPacket.getAddress();
							MainWindow.client_Listener_Port = input.readInt();
							MainWindow.controllerState.set(nextState);
							msg1Received = true;
							updateUI(nextState);
						}
						break;
					case MainWindow.SERV_TO_CON_CONNECTION_REQUEST:
						if (previousState == MainWindow.CONTROLLER_STATE_CLIENT_LOGGED
								&& !msg4Received) {
							MainWindow.serverIp = receivedPacket.getAddress();
							MainWindow.server_Listener_Port = input.readInt();
							MainWindow.controllerState.set(nextState);
							msg4Received = true;
							updateUI(nextState);
						}
						break;
				}
				
				// Ahora el resto de mensajes, comprobando la IP origen
				// ¡Ojo! Aunque puede estar esperando un paquete de, por ejemplo, el cliente,
				//    sigue recibiendo del otro dron y hay que aprovechar el paquete si procede
				if (ip != null
						&& ((MainWindow.clientIp==null || receivedPacket.getAddress().getHostAddress().equals(MainWindow.clientIp.getHostAddress()))
							|| (MainWindow.serverIp==null || receivedPacket.getAddress().getHostAddress().equals(MainWindow.serverIp.getHostAddress())))) {
					
					switch (messageTypeReceived) {
						case MainWindow.CLI_TO_CON_CONNECTION_ACK:
							// Siempre que se recibe uno tipo 3 se guarda el valor de las
							//	coordenadas y se actualiza la interfaz
							clientLatitude = input.readLong();
							clientLongitude = input.readLong();
							clientAltitude = input.readLong();
							clientJaw = (float)(180.0/Math.PI*input.readFloat());
							updatePosition();
							//Conectando
							if (previousState == MainWindow.CONTROLLER_STATE_CLIENT_LOGGING
									&& !msg3Received) {
								MainWindow.controllerState.set(nextState);
								msg3Received = true;
								updateUI(nextState);
							}
							//Volviendo de una iteración anterior
							if (previousState == MainWindow.CONTROLLER_STATE_CLIENT_STOPPING
									&& !msg19Received) {
								MainWindow.controllerState.set(nextState);
								msg19Received = true;
								updateUI(nextState);
							}
							break;
						case MainWindow.SERV_TO_CON_CONNECTION_ACK:
							if (previousState == MainWindow.CONTROLLER_STATE_SERVER_LOGGING
									&& !msg6Received) {
								MainWindow.controllerState.set(nextState);
								msg6Received = true;
								updateUI(nextState);
							}
							break;
						case MainWindow.SERV_TO_CON_CONNECT_TO_CLI_ACK:
							// Siempre que se recibe uno tipo 8 se guarda el valor de las
							//	coordenadas y se actualiza la interfaz
							serverLatitude = input.readLong();
							serverLongitude = input.readLong();
							serverAltitude = input.readLong();
							serverJaw = (float)(180.0/Math.PI*input.readFloat());
							updatePosition();
							//Conectando
							if (previousState == MainWindow.CONTROLLER_STATE_SERVER_LOGGED
									&& !msg8Received) {
								MainWindow.controllerState.set(nextState);
								msg8Received = true;
								updateUI(nextState);
							}
							//Volviendo de una iteración anterior
							if (previousState == MainWindow.CONTROLLER_STATE_CLIENT_STOPPED
									&& !msg21Received) {
								MainWindow.sendRatioReal.set(input.readFloat());
								MainWindow.controllerState.set(nextState);
								msg21Received = true;
								updateUI(nextState);
							}
							break;
						case MainWindow.CLI_TO_CON_SETUP_ACK:
							// Siempre que se recibe uno tipo 10 se guarda el valor de las
							//	coordenadas y se actualiza la interfaz
							clientLatitude = input.readLong();
							clientLongitude = input.readLong();
							clientAltitude = input.readLong();
							clientJaw = (float)(180.0/Math.PI*input.readFloat());
							updatePosition();
							if (previousState == MainWindow.CONTROLLER_STATE_SERVER_CONNECTED_TO_CLIENT
									&& !msg10Received) {
								MainWindow.controllerState.set(nextState);
								msg10Received = true;
								updateUI(nextState);
							}
							break;
						case MainWindow.SERV_TO_CON_SETUP_ACK:
							// Siempre que se recibe uno tipo 12 se guarda el valor de las
							//	coordenadas y se actualiza la interfaz
							serverLatitude = input.readLong();
							serverLongitude = input.readLong();
							serverAltitude = input.readLong();
							serverJaw = (float)(180.0/Math.PI*input.readFloat());
							updatePosition();
							if (previousState == MainWindow.CONTROLLER_STATE_CLIENT_CONFIGURED
									&& !msg12Received) {
								MainWindow.controllerState.set(nextState);
								msg12Received = true;
								updateUI(nextState);
							}
							break;
						case MainWindow.CLI_TO_CON_START_ACK:
							if (previousState == MainWindow.CONTROLLER_STATE_SERVER_CONFIGURED
									&& !msg14Received) {
								MainWindow.controllerState.set(nextState);
								msg14Received = true;
								updateUI(nextState);
							}
							break;
						case MainWindow.SERV_TO_CON_START_ACK:
							if (previousState == MainWindow.CONTROLLER_STATE_CLIENT_RUNNING
									&& !msg16Received) {
								MainWindow.controllerState.set(nextState);
								msg16Received = true;
								updateUI(nextState);
							}
							break;
						case MainWindow.CLI_TO_CON_FINISHED:
							if (previousState == MainWindow.CONTROLLER_STATE_SERVER_RUNNING
									&& !msg17Received) {
								MainWindow.loosingRatio.set(input.readFloat());
								MainWindow.totalLostPackets.set(input.readLong());
								MainWindow.controllerState.set(nextState);
								msg17Received = true;
								updateUI(nextState);
							}
							break;
					}
				}
			} catch (IOException e) {
				// System.out.println("Esperando conexión de " + rol + ":\n" +
				// e.getMessage());
			}

			// El proceso se detiene si se pierde la conexión
			if (System.currentTimeMillis() - phaseStart > waiting) goOn = false;
		}
		input.close();
		
		msg10Received = false;
		msg12Received = false;
		msg14Received = false;
		msg16Received = false;
		msg17Received = false;
		msg19Received = false;
		msg21Received = false;
		
		// Si no se confirma la conexión se detiene el programa
		if (MainWindow.controllerState.get() < nextState) {
			System.out.println("No se consiguió conectar. Proceso detenido");
			System.exit(1);
		}
	}

	private void updateUI(int actualState) {
		switch (actualState) {
			case MainWindow.CONTROLLER_STATE_CLIENT_LOGGING:
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						MainWindow.cLogging.setBackground(Color.GREEN);
					}
				});
				break;
			case MainWindow.CONTROLLER_STATE_CLIENT_LOGGED:
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						MainWindow.cLogged.setBackground(Color.GREEN);
					}
				});
				break;
			case MainWindow.CONTROLLER_STATE_SERVER_LOGGING:
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						MainWindow.sLogging.setBackground(Color.GREEN);
					}
				});
				break;
			case MainWindow.CONTROLLER_STATE_SERVER_LOGGED:
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						MainWindow.sLogged.setBackground(Color.GREEN);
					}
				});
				break;
			case MainWindow.CONTROLLER_STATE_SERVER_CONNECTED_TO_CLIENT:
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						MainWindow.cConnected.setBackground(Color.GREEN);
						MainWindow.sConnected.setBackground(Color.GREEN);
						// Como puede ser una segunda ronda de pruebas, se resetean colores
						//   de los pasos siguientes
						MainWindow.cConfigured.setBackground(Color.RED);
						MainWindow.sConfigured.setBackground(Color.RED);
						MainWindow.cStarted.setBackground(Color.RED);
						MainWindow.sStarted.setBackground(Color.RED);
						MainWindow.cRunning.setBackground(Color.RED);
						MainWindow.sRunning.setBackground(Color.RED);
						MainWindow.cStopped.setBackground(Color.RED);
						MainWindow.sStopped.setBackground(Color.RED);
						
						// Se activa el botón de configuración
						MainWindow.sendConfiguration.setEnabled(true);
					}
				});
				break;
			case MainWindow.CONTROLLER_STATE_CLIENT_CONFIGURED:
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						MainWindow.cConfigured.setBackground(Color.GREEN);
						MainWindow.loosingRatioLabel.setText("");
						MainWindow.totalLostPacketsLabel.setText("");
						MainWindow.sendRatioRealLabel.setText("");
					}
				});
				break;
			case MainWindow.CONTROLLER_STATE_SERVER_CONFIGURED:
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						MainWindow.sConfigured.setBackground(Color.GREEN);
						
						// Se activa el botón de iniciar la prueba
						MainWindow.sendConfiguration.setEnabled(false);
						MainWindow.startTest.setEnabled(true);
					}
				});
				break;
			case MainWindow.CONTROLLER_STATE_CLIENT_RUNNING:
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						MainWindow.cStarted.setBackground(Color.GREEN);
					}
				});
				break;
			case MainWindow.CONTROLLER_STATE_SERVER_RUNNING:
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						MainWindow.sStarted.setBackground(Color.GREEN);
						MainWindow.cRunning.setBackground(Color.GREEN);
						MainWindow.sRunning.setBackground(Color.GREEN);
						
						timer = new Timer();
						timer.scheduleAtFixedRate(new TimerTask() {
				            int count = MainWindow.testDuration;
				            public void run() {
				            	MainWindow.counter.setText("Remaining(s): " + count);
				            	count--;
				                if (count< 0)
				                    timer.cancel();
				            }
				        }, 0, 1000);	//Cada segundo, sin espera inicial
					}
				});
				break;
			case MainWindow.CONTROLLER_STATE_CLIENT_STOPPING:
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						String lrl = String.format("Loss ratio %.4f %%", MainWindow.loosingRatio.get()*100);
						MainWindow.loosingRatioLabel.setText(lrl);
						MainWindow.totalLostPacketsLabel.setText("Packets lost "
								+ NumberFormat.getNumberInstance().format(MainWindow.totalLostPackets.get()));
						//Por si no llega a cero antes de que termine la prueba
						timer.cancel();
						MainWindow.counter.setText("");
					}
				});
				break;
			case MainWindow.CONTROLLER_STATE_CLIENT_STOPPED:
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						MainWindow.cStopped.setBackground(Color.GREEN);
					}
				});
				break;
			case MainWindow.CONTROLLER_STATE_SERVER_FINISHED:
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						MainWindow.sStopped.setBackground(Color.GREEN);
						String srrl = String.format("Current throughput %.2f p/sec", MainWindow.sendRatioReal.get());
						MainWindow.sendRatioRealLabel.setText(srrl);
					}
				});
				break;
		}

	}

	private void updatePosition() {
		//Solamente calculamos la distancia si los datos de ambos drones han cambiado
		UTMCoordinates posServer = MainWindow.geoToUTM(serverLatitude*0.0000001, serverLongitude*0.0000001);
		UTMCoordinates posClient = MainWindow.geoToUTM(clientLatitude*0.0000001, clientLongitude*0.0000001);
		double distance = Math.sqrt(Math.pow(posServer.Easting - posClient.Easting, 2) + Math.pow(posServer.Northing - posClient.Northing, 2));
		distanceString = String.format("Distance: %.3f m", distance);
		
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				MainWindow.dronesDistance.setText(distanceString);
				MainWindow.serverAltitude.setText(String.format("Server altitude: %.3f m", serverAltitude*0.001));
				MainWindow.clientAltitude.setText(String.format("Client altitude: %.3f m", clientAltitude*0.001));
				MainWindow.difAltitude.setText(String.format("(dif: %.3f m)", ((serverAltitude-clientAltitude)*0.001)));
				
				MainWindow.datasetServer.setValue(serverJaw);
				MainWindow.serverJawText.setText(String.format("%.2f º", serverJaw));
				MainWindow.datasetClient.setValue(clientJaw);
				MainWindow.clientJawText.setText(String.format("%.2f º", clientJaw));
			}
		});
	}
}
