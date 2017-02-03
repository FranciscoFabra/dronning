package gui;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SpringLayout;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.EmptyBorder;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CompassPlot;
import org.jfree.data.general.DefaultValueDataset;
import org.jfree.data.general.ValueDataset;

import logic.ClientFromServerListener;
import logic.ClientListener;
import logic.ClientTalker;
import logic.ControllerListener;
import logic.ControllerTalker;
import logic.DroneListener;
import logic.ServerListener;
import logic.ServerTalker;
import logic.ServerToClientTalker;
import pojo.AtomicFloat;
import pojo.DatosDron;
import pojo.GeographicCoordinates;
import pojo.UTMCoordinates;

public class MainWindow {
	
	/* ID de mensajes del protocolo de control */
	public static final short CLI_TO_CON_CONNECTION_REQUEST =	1;
	public static final short CON_TO_CLI_CONNECTION_ACK =		2;
	public static final short CLI_TO_CON_CONNECTION_ACK =		3;
	public static final short SERV_TO_CON_CONNECTION_REQUEST =	4;
	public static final short CON_TO_SERV_CONNECTION_ACK =		5;
	public static final short SERV_TO_CON_CONNECTION_ACK =		6;
	public static final short CON_TO_SERV_CONNECT_TO_CLI =		7;
	public static final short SERV_TO_CON_CONNECT_TO_CLI_ACK =	8;
	public static final short CON_TO_CLI_SETUP =		9;
	public static final short CLI_TO_CON_SETUP_ACK =	10;
	public static final short CON_TO_SERV_SETUP =		11;
	public static final short SERV_TO_CON_SETUP_ACK =	12;
	public static final short CON_TO_CLI_START =		13;
	public static final short CLI_TO_CON_START_ACK =	14;
	public static final short CON_TO_SERV_START =		15;
	public static final short SERV_TO_CON_START_ACK =	16;
	public static final short CLI_TO_CON_FINISHED =		17;
	public static final short CON_TO_CLI_FINISHED_ACK = 18;
	public static final short CLI_TO_CON_FINISHED_ACK = 19;
	public static final short CON_TO_SERV_FINISH =		20;
	public static final short SERV_TO_CON_FINISH_ACK =	21;
	
	/* Estados del programa controlador */
	public static final short CONTROLLER_STATE_START =			1;
	public static final short CONTROLLER_STATE_CLIENT_LOGGING = 2;
	public static final short CONTROLLER_STATE_CLIENT_LOGGED =	3;
	public static final short CONTROLLER_STATE_SERVER_LOGGING = 4;
	public static final short CONTROLLER_STATE_SERVER_LOGGED =	5;
	public static final short CONTROLLER_STATE_SERVER_CONNECTED_TO_CLIENT = 6;
	public static final short CONTROLLER_STATE_CLIENT_CONFIGURED =	7;
	public static final short CONTROLLER_STATE_SERVER_CONFIGURED =	8;
	public static final short CONTROLLER_STATE_CLIENT_RUNNING =		9;
	public static final short CONTROLLER_STATE_SERVER_RUNNING =		10;
	public static final short CONTROLLER_STATE_CLIENT_STOPPING =	11;
	public static final short CONTROLLER_STATE_CLIENT_STOPPED =		12;
	public static final short CONTROLLER_STATE_SERVER_FINISHED =	13;
	
	/* Estados del programa cliente */
	public static final short CLIENT_STATE_START =		1;
	public static final short CLIENT_STATE_LOGGING =	2;
	public static final short CLIENT_STATE_CONFIGURED =	3;
	public static final short CLIENT_STATE_RUNNING =	4;
	public static final short CLIENT_STATE_STOPPING =	5;
	public static final short CLIENT_STATE_STOPPED =	6;
	
	/* Estados del programa servidor */
	public static final short SERVER_STATE_START =		1;
	public static final short SERVER_STATE_LOGGING =	2;
	public static final short SERVER_STATE_CONNECTED =	3;
	public static final short SERVER_STATE_CONFIGURED = 4;
	public static final short SERVER_STATE_RUNNING =	5;
	public static final short SERVER_STATE_STOPPED =	6;
	
	/* Nombres de hilos */
	public static final String CONTROLLER_LISTENER_NAME = "Listener del controller";
	public static final String CONTROLLER_TALKER_NAME = "Talker del controller";
	public static final String SERVER_LISTENER_NAME = "Listener del servidor";
	public static final String SERVER_TALKER_NAME = "Talker del servidor";
	public static final String SERVER_TO_CLIENT_NAME = "Sender del servidor";
	public static final String CLIENT_LISTENER_NAME = "Listener del cliente";
	public static final String CLIENT_TALKER_NAME = "Talker del cliente";
	public static final String CLIENT_FROM_SERVER_NAME = "Receiver del cliente";
	public static final String SERVER_DRONE_LISTENER_NAME = "Captura de datos del dron servidor";
	public static final String CLIENT_DRONE_LISTENER_NAME = "Captura de datos del dron cliente";
	
	/* Estado actual del programa */
	public static AtomicInteger controllerState = new AtomicInteger(MainWindow.CONTROLLER_STATE_START);
	public static AtomicInteger serverState = new AtomicInteger(MainWindow.SERVER_STATE_START);
	public static AtomicInteger clientState = new AtomicInteger(MainWindow.CLIENT_STATE_START);
	public static AtomicBoolean configured = new AtomicBoolean(false);
	public static AtomicBoolean started = new AtomicBoolean(false);
	public static AtomicBoolean transitionalState1 = new AtomicBoolean(true);
	public static AtomicBoolean transitionalState2 = new AtomicBoolean(true);
	
	/* Timeout para el proceso de conexión inicial en ms */
	public static final long connectionTimeout = 3000 * 1000;
	
	/* Timeout para el proceso de inicio de la prueba en ms */
	public static final long testTimeout = 3000 * 1000;
	
	/* IP donde está el controller */
	public static InetAddress CONTROLLER_IP;
	/* Puerto en el que el controller escucha al client y al server */
	public static final int CONTROLLER_LISTENER_PORT = 1500;
	
	/* IP donde está el cliente */
	public static InetAddress clientIp;
	/* Puerto en el que el cliente escucha al controlador */
	public static int client_Listener_Port = 1502;
	/* Puerto en el que el cliente escucha al servidor */
	public static int client_from_Server_Listener_Port = 1503;
	
	/* IP donde está el servidor */
	public static InetAddress serverIp;
	/* Puerto en el que el servidor escucha al controlador */
	public static int server_Listener_Port = 1501;
	
	/* Rol de ejecución */
	public static String rol;
	
	//La longitud máxima de datos estará limitada a 2^15-1=32767 por
	//   usar el primer campo un short de 16bits con signo
	public static final int DGRAM_MAX_LENGTH = 1472;	//1500-20-8 (MTU-IP-UDP)

	/* Parámetros relacionados con la captura del dron */
	public static boolean interfaceIsSerial;
	/* Parámetros relacionados con la captura del dron por puerto serie */
	public static final String serialPort = "/dev/ttyAMA0";
	public static final int baudRate = 57600;
	/* Parámetros relacionados con la capturadel dron por UDP */
	public static String droneServerIp = "192.168.56.1";
	public static int droneServerPort = 14550;
	public static String droneClientIp = "192.168.56.2";
	public static int droneClientPort = 14550;
	
	/* Objeto donde se guarda la información del dron hasta que se almacena */
	public static DatosDron dron = new DatosDron();
	
	/* Identificador del dron */
	public static short serverDronId = 1;
	public static short clientDronId = 2;
	
	/* Parámetros para guardar información */
	public static String baseFileName;
	public static int testDuration;
	public static int sendRatio;
	public static short size;
	public static boolean isBroadcast;
	
	/* Tiempo mínimo en ms entre ráfagas de paquetes */
	public static final int MIN_BURST_TIME = 100;
	/* Ratio real de envío */
	public static AtomicFloat sendRatioReal = new AtomicFloat();
	/* Paquetes perdidos en por uno */
	public static AtomicFloat loosingRatio = new AtomicFloat();
	/* Paquetes perdidos */
	public static AtomicLong totalLostPackets = new AtomicLong();
	
	/* Tiempos de espera en nanosegundos */
	public static final long waitLong = 500 *1000000;
	public static final long waitMiddle = 250 * 1000000;
	public static final long waitShort = 200 * 1000000;
	
	/* Tiempo de espera para envío durante tiempo limitado, en milisegundos */
	public static final long limitedSendingTimeout = 2 * 1000;
	
	/* Tamaño del buffer de recepción y envío entre los drones */
	public static final int BUFFERSIZE = 1048576;
	
	/* Ratio de captura de datos de los drones (paquetes/segundo) */
	public static final int recordRatio = 10;
	/* Tipos de registro guardados */
	public static final int dataRegister = 1;
	public static final int standardRegister = 0;
	
	public static JFrame frame;
	/* Hago los elementos de la interfaz accesibles al resto de hilos para consulta/modificación */
	public static JTextField filename;
	public static JTextField duration;
	public static JTextField ratio;
	public static JTextField packetSize;
	public static JButton cLogging;
	public static JButton cLogged;
	public static JButton sLogging;
	public static JButton sLogged;
	public static JButton cConnected;
	public static JButton sConnected;
	public static JButton cConfigured;
	public static JButton sConfigured;
	public static JButton cStarted;
	public static JButton sStarted;
	public static JButton cRunning;
	public static JButton sRunning;
	public static JButton cStopped;
	public static JButton sStopped;
	private final ButtonGroup buttonGroup = new ButtonGroup();
	public static JRadioButton rdbtnBroadcast;
	public static JLabel dronesDistance;
	public static JLabel serverAltitude;
	public static JLabel clientAltitude;
	public static JLabel difAltitude;
	
	public static JButton sendConfiguration;
	public static JButton startTest;
	public static JLabel sendRatioRealLabel;
	public static JLabel loosingRatioLabel;
	public static JLabel totalLostPacketsLabel;
	public static JLabel counter;
	
	public static DefaultValueDataset datasetServer;
	public static DefaultValueDataset datasetClient;
	public static JLabel serverJawText;
	public static JLabel clientJawText;
	
	/* Booleano para controlar el comportamiento dentro y fuera del laboratorio */
	public static final boolean ISDEBUGGING = false;
	
	/**
	 * Launch the application.
	 * @wbp.parser.entryPoint
	 */
	public static void main(String[] args) {
		try {
			if (ISDEBUGGING){
				CONTROLLER_IP = InetAddress.getByName("127.0.0.1");
				MainWindow.interfaceIsSerial = false;
			}
			else {
				CONTROLLER_IP = InetAddress.getByName("192.168.1.3");
				MainWindow.interfaceIsSerial = true;
			}
		} catch (UnknownHostException e2) {
			e2.printStackTrace();
			System.exit(1);
		}
		// Si se desea hacer debugging pero con el interfaz serie, activar lo siguiente
//		MainWindow.interfaceIsSerial = true;
		
		
		
		/* Parseo de argumentos */
		if ((args.length != 1) || (!args[0].toUpperCase().equals("CONTROLLER")
				&& !args[0].toUpperCase().equals("SERVER")
				&& !args[0].toUpperCase().equals("CLIENT"))) {
			System.out.println("Entradas válidas:\n"
					+ "java -jar dron.jar controller\n"
					+ "java -jar dron.jar server\n"
					+ "java -jar dron.jar client");
			System.exit(1);
		}
		rol = args[0].toUpperCase();
		
		/* Según el rol se hace una tarea distinta */
		if (rol.equals("CONTROLLER")) {
			EventQueue.invokeLater(new Runnable() {
				public void run() {
					try {
						new MainWindow();
						MainWindow.frame.setVisible(true);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
			
			// Espera a que se creen los elementos de la interfaz antes de lanzar
			//	otros hilos
			while (filename == null || duration == null || ratio == null
					|| packetSize == null || cLogging == null || cLogged == null || sLogging == null
					|| sLogged == null || cConnected == null || sConnected == null
					|| cConfigured == null || sConfigured == null || cStarted == null
					|| sStarted == null || cRunning == null || sRunning == null
					|| cStopped == null || sStopped == null) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}
			
			try {
				Thread listener = new ControllerListener();
				try {
					Thread talker = new ControllerTalker();
					
					listener.start();
					talker.start();
					try {
						listener.join();
						talker.join();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				} catch (SocketException e) {
					System.out.println("Controller falló abriendo el puerto UDP para escuchar");
				}
			} catch (SocketException e) {
				System.out.println("Controller falló abriendo el puerto UDP para enviar");
			}
		} else if (rol.equals("SERVER")) {
			
			//Quizá sea necesario esperar a que la pixhawk arranque
			
			if (!ISDEBUGGING)
				try {
					TimeUnit.SECONDS.sleep(15);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			
			try {
				Thread dronListener = new DroneListener(rol);
				try {
					Thread listener = new ServerListener();
					try {
						Thread talker = new ServerTalker();
						
						dronListener.start();
						listener.start();
						talker.start();
						try {
							Thread serverToClient = new ServerToClientTalker();
							
							serverToClient.start();
							try {
								listener.join();
								talker.join();
								serverToClient.join();
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						} catch (SocketException e) {
							System.out.println("Server falló conectando iniciando hilo de conexión al cliente");
						}
					} catch (SocketException e) {
						System.out.println("Server falló abriendo el puerto UDP para escuchar");
					}
				} catch (SocketException e) {
					System.out.println("Server falló abriendo el puerto UDP para enviar");
				}
			} catch (Exception e) {
				System.out.println("Server falló conectando al dron");
			}
		} else if (rol.equals("CLIENT")) {

			//Quizá sea necesario esperar a que la pixhawk arranque
			
			if (!ISDEBUGGING)
				try {
					TimeUnit.SECONDS.sleep(15);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			
			try {
				Thread dronListener;
				if (!ISDEBUGGING) dronListener = new DroneListener(rol);
				try {
					Thread listener = new ClientListener();
					try {
						Thread talker = new ClientTalker();
						
						if (!ISDEBUGGING) dronListener.start();
						listener.start();
						talker.start();
						try {
							Thread clientFromServer = new ClientFromServerListener();
							
							clientFromServer.start();
							try {
								listener.join();
								talker.join();
								clientFromServer.join();
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						} catch (SocketException e) {
							System.out.println("Client falló conectando iniciando hilo de conexión al cliente");
						}
					} catch (SocketException e) {
						System.out.println("Client falló abriendo el puerto UDP para escuchar");
					}
				} catch (SocketException e) {
					System.out.println("Client falló abriendo el puerto UDP para enviar");
				}
			} catch (Exception e) {
				System.out.println("Client falló conectando al dron");
			}
		}
	}
	
	/** Espera nanos nanosegundos a partir de antes, también en nanos */
	public static void esperaActiva(long limitAbstractTime) {
		while (System.nanoTime() < limitAbstractTime) {
			//Thread.yield();	// ¡IMPIDE EL AVANCE CORRECTO DEL SERVIDOR!
		}
	}
	
	/** Espera pasiva */
	public static void esperaPasiva(long nanosegundos) {
		try {
			TimeUnit.NANOSECONDS.sleep(nanosegundos);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	/** Apagado del dispositivo 
	 * @throws IOException */
	public static void shutdown() throws IOException {
		String shutdownCommand;
	    String operatingSystem = System.getProperty("os.name");

	    if (operatingSystem.startsWith("Linux") || operatingSystem.startsWith("Mac")) {
	        shutdownCommand = "sudo shutdown -h now";
	    }
	    else if (operatingSystem.startsWith("Win")) {
	        shutdownCommand = "shutdown.exe -s -t 0";
	    }
	    else {
	        throw new RuntimeException("Sistema operativo no soportado.");
	    }

	    Runtime.getRuntime().exec(shutdownCommand);
	    System.exit(0);
	}
	
	/* Método que obtiene las coordenadas UTM a partir de las geográficas */
	public static UTMCoordinates geoToUTM(double lat, double lon) {
		double Easting;
		double Northing;
		int Zone;
		char Letter;
		
		Zone = (int) Math.floor(lon / 6 + 31);
		if (lat < -72)
			Letter = 'C';
		else if (lat < -64)
			Letter = 'D';
		else if (lat < -56)
			Letter = 'E';
		else if (lat < -48)
			Letter = 'F';
		else if (lat < -40)
			Letter = 'G';
		else if (lat < -32)
			Letter = 'H';
		else if (lat < -24)
			Letter = 'J';
		else if (lat < -16)
			Letter = 'K';
		else if (lat < -8)
			Letter = 'L';
		else if (lat < 0)
			Letter = 'M';
		else if (lat < 8)
			Letter = 'N';
		else if (lat < 16)
			Letter = 'P';
		else if (lat < 24)
			Letter = 'Q';
		else if (lat < 32)
			Letter = 'R';
		else if (lat < 40)
			Letter = 'S';
		else if (lat < 48)
			Letter = 'T';
		else if (lat < 56)
			Letter = 'U';
		else if (lat < 64)
			Letter = 'V';
		else if (lat < 72)
			Letter = 'W';
		else
			Letter = 'X';
		Easting = 0.5
				* Math.log((1 + Math.cos(lat * Math.PI / 180)
						* Math.sin(lon * Math.PI / 180 - (6 * Zone - 183) * Math.PI / 180))
				/ (1 - Math.cos(lat * Math.PI / 180)
						* Math.sin(lon * Math.PI / 180 - (6 * Zone - 183) * Math.PI / 180)))
				* 0.9996 * 6399593.62
				/ Math.pow((1 + Math.pow(0.0820944379, 2) * Math.pow(Math.cos(lat * Math.PI / 180), 2)),
						0.5)
				* (1 + Math.pow(0.0820944379, 2) / 2
						* Math.pow((0.5 * Math.log((1 + Math.cos(lat * Math.PI / 180)
								* Math.sin(lon * Math.PI / 180 - (6 * Zone - 183) * Math.PI / 180))
						/ (1 - Math.cos(lat * Math.PI / 180)
								* Math.sin(lon * Math.PI / 180 - (6 * Zone - 183) * Math.PI / 180)))), 2)
						* Math.pow(Math.cos(lat * Math.PI / 180), 2) / 3)
				+ 500000;
		Easting = Math.round(Easting * 100) * 0.01;
		Northing = (Math.atan(
				Math.tan(lat * Math.PI / 180) / Math.cos((lon * Math.PI / 180 - (6 * Zone - 183) * Math.PI / 180)))
				- lat * Math.PI / 180) * 0.9996
				* 6399593.625
				/ Math.sqrt(1 + 0.006739496742 * Math.pow(Math.cos(lat * Math.PI / 180), 2)) * (1
						+ 0.006739496742 / 2
								* Math.pow(0.5 * Math.log((1 + Math.cos(lat * Math.PI / 180)
										* Math.sin((lon * Math.PI / 180 - (6 * Zone - 183) * Math.PI / 180))) / (1
												- Math.cos(lat * Math.PI / 180) * Math.sin((lon * Math.PI / 180
														- (6 * Zone - 183) * Math.PI / 180)))),
										2)
								* Math.pow(Math.cos(lat * Math.PI / 180), 2))
				+ 0.9996 * 6399593.625 * (lat * Math.PI / 180
						- 0.005054622556 * (lat * Math.PI / 180 + Math.sin(2 * lat * Math.PI / 180) / 2)
						+ 4.258201531e-05 * (3 * (lat * Math.PI / 180 + Math.sin(2 * lat * Math.PI / 180) / 2)
								+ Math.sin(2 * lat * Math.PI / 180) * Math.pow(Math.cos(lat * Math.PI / 180), 2))
								/ 4
						- 1.674057895e-07 * (5
								* (3 * (lat * Math.PI / 180 + Math.sin(2 * lat * Math.PI / 180) / 2)
										+ Math.sin(2 * lat * Math.PI / 180)
												* Math.pow(Math.cos(lat * Math.PI / 180), 2))
								/ 4
								+ Math.sin(2 * lat * Math.PI / 180) * Math.pow(Math.cos(lat * Math.PI / 180), 2)
										* Math.pow(Math.cos(lat * Math.PI / 180), 2))
								/ 3);
		if (Letter < 'M')
			Northing = Northing + 10000000;
		Northing = Math.round(Northing * 100) * 0.01;
		
		return new UTMCoordinates(Easting, Northing, Zone, Letter);
	}
	
	/* Método que obtiene las coordenadas geográficas a partir de las UTM
	 	uso: MainWindow.UTMToGeo(35, R, 312915.84, 4451481.33) */
	public static GeographicCoordinates UTMToGeo(int Zone, char Letter, double Easting, double Northing) {
		double latitude;
		double longitude;
		
		double Hem;
		if (Letter > 'M')
			Hem = 'N';
		else
			Hem = 'S';
		double north;
		if (Hem == 'S')
			north = Northing - 10000000;
		else
			north = Northing;
		latitude = (north / 6366197.724
				/ 0.9996 + (1
						+ 0.006739496742
								* Math.pow(Math.cos(north / 6366197.724 / 0.9996),
										2)
						- 0.006739496742
								* Math.sin(
										north / 6366197.724
												/ 0.9996)
								* Math.cos(
										north / 6366197.724
												/ 0.9996)
								* (Math.atan(Math
										.cos(Math
												.atan((Math
														.exp((Easting - 500000)
																/ (0.9996 * 6399593.625
																		/ Math.sqrt(
																				(1 + 0.006739496742
																						* Math.pow(
																								Math.cos(
																										north / 6366197.724
																												/ 0.9996),
																								2))))
																* (1 - 0.006739496742
																		* Math.pow(
																				(Easting - 500000)
																						/ (0.9996 * 6399593.625
																								/ Math.sqrt(
																										(1 + 0.006739496742
																												* Math.pow(
																														Math.cos(
																																north / 6366197.724
																																		/ 0.9996),
																														2)))),
																				2)
																		/ 2
																		* Math.pow(Math.cos(
																				north / 6366197.724 / 0.9996), 2)
												/ 3)) - Math
														.exp(-(Easting - 500000)
																/ (0.9996 * 6399593.625
																		/ Math.sqrt(
																				(1 + 0.006739496742
																						* Math.pow(
																								Math.cos(
																										north / 6366197.724
																												/ 0.9996),
																								2))))
																* (1 - 0.006739496742
																		* Math.pow(
																				(Easting - 500000)
																						/ (0.9996 * 6399593.625
																								/ Math.sqrt(
																										(1 + 0.006739496742
																												* Math.pow(
																														Math.cos(
																																north / 6366197.724
																																		/ 0.9996),
																														2)))),
																				2)
																		/ 2
																		* Math.pow(Math.cos(
																				north / 6366197.724 / 0.9996), 2)
												/ 3)))
								/ 2
								/ Math.cos((north - 0.9996 * 6399593.625
										* (north / 6366197.724 / 0.9996
												- 0.006739496742 * 3 / 4
														* (north / 6366197.724 / 0.9996 + Math
																.sin(2 * north / 6366197.724 / 0.9996) / 2)
										+ Math.pow(0.006739496742 * 3 / 4, 2) * 5 / 3
												* (3 * (north / 6366197.724 / 0.9996
														+ Math.sin(2 * north / 6366197.724 / 0.9996) / 2)
												+ Math.sin(2 * north / 6366197.724 / 0.9996)
														* Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2))
												/ 4
										- Math.pow(0.006739496742 * 3 / 4, 3) * 35 / 27 * (5
												* (3 * (north / 6366197.724 / 0.9996
														+ Math.sin(2 * north / 6366197.724 / 0.9996) / 2)
												+ Math.sin(2 * north / 6366197.724 / 0.9996)
														* Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2))
												/ 4
												+ Math.sin(2 * north / 6366197.724 / 0.9996)
														* Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2)
														* Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2))
												/ 3))
										/ (0.9996 * 6399593.625
												/ Math.sqrt((1 + 0.006739496742 * Math
														.pow(Math.cos(north / 6366197.724 / 0.9996), 2))))
										* (1 - 0.006739496742 * Math.pow(
												(Easting - 500000) / (0.9996 * 6399593.625
														/ Math.sqrt((1 + 0.006739496742 * Math
																.pow(Math.cos(north / 6366197.724 / 0.9996), 2)))),
												2) / 2 * Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2))
										+ north / 6366197.724 / 0.9996)))
								* Math.tan((north - 0.9996 * 6399593.625
										* (north / 6366197.724 / 0.9996
												- 0.006739496742 * 3 / 4
														* (north / 6366197.724 / 0.9996 + Math
																.sin(2 * north / 6366197.724 / 0.9996) / 2)
										+ Math.pow(0.006739496742 * 3 / 4, 2) * 5 / 3
												* (3 * (north / 6366197.724 / 0.9996
														+ Math.sin(2 * north / 6366197.724 / 0.9996) / 2)
												+ Math.sin(2 * north / 6366197.724 / 0.9996)
														* Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2))
												/ 4
										- Math.pow(0.006739496742 * 3 / 4, 3) * 35 / 27 * (5
												* (3 * (north / 6366197.724 / 0.9996
														+ Math.sin(2 * north / 6366197.724 / 0.9996) / 2)
												+ Math.sin(2 * north / 6366197.724 / 0.9996)
														* Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2))
												/ 4
												+ Math.sin(2 * north / 6366197.724 / 0.9996)
														* Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2)
														* Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2))
												/ 3))
										/ (0.9996 * 6399593.625
												/ Math.sqrt((1 + 0.006739496742 * Math
														.pow(Math.cos(north / 6366197.724 / 0.9996), 2))))
										* (1 - 0.006739496742 * Math.pow(
												(Easting - 500000) / (0.9996 * 6399593.625
														/ Math.sqrt((1 + 0.006739496742 * Math
																.pow(Math.cos(north / 6366197.724 / 0.9996), 2)))),
												2) / 2 * Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2))
										+ north / 6366197.724 / 0.9996))
								- north / 6366197.724 / 0.9996) * 3 / 2)
						* (Math.atan(
								Math.cos(Math.atan((Math
										.exp((Easting - 500000) / (0.9996 * 6399593.625
												/ Math.sqrt((1 + 0.006739496742 * Math
														.pow(Math.cos(north / 6366197.724 / 0.9996), 2))))
												* (1 - 0.006739496742
														* Math.pow((Easting - 500000) / (0.9996 * 6399593.625
																/ Math.sqrt((1 + 0.006739496742 * Math.pow(
																		Math.cos(north / 6366197.724 / 0.9996),
																		2)))),
																2)
														/ 2 * Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2)
														/ 3))
										- Math.exp(-(Easting - 500000) / (0.9996 * 6399593.625
												/ Math.sqrt((1 + 0.006739496742 * Math
														.pow(Math.cos(north / 6366197.724 / 0.9996), 2))))
												* (1 - 0.006739496742
														* Math.pow((Easting - 500000) / (0.9996 * 6399593.625
																/ Math.sqrt((1 + 0.006739496742 * Math.pow(
																		Math.cos(north / 6366197.724 / 0.9996),
																		2)))),
																2)
														/ 2 * Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2)
														/ 3)))
						/ 2 / Math
								.cos((north
										- 0.9996 * 6399593.625
												* (north / 6366197.724 / 0.9996
														- 0.006739496742 * 3 / 4
																* (north / 6366197.724 / 0.9996 + Math
																		.sin(2 * north / 6366197.724 / 0.9996) / 2)
												+ Math.pow(0.006739496742 * 3 / 4,
														2) * 5
												/ 3
												* (3 * (north / 6366197.724 / 0.9996
														+ Math.sin(2 * north / 6366197.724 / 0.9996) / 2)
												+ Math.sin(2 * north / 6366197.724 / 0.9996)
														* Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2)) / 4
								- Math.pow(0.006739496742 * 3 / 4, 3) * 35 / 27
										* (5 * (3
												* (north / 6366197.724 / 0.9996
														+ Math.sin(2 * north / 6366197.724 / 0.9996) / 2)
												+ Math.sin(2 * north / 6366197.724 / 0.9996)
														* Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2))
												/ 4
												+ Math.sin(2 * north / 6366197.724 / 0.9996)
														* Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2)
														* Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2))
										/ 3)) / (0.9996
												* 6399593.625
												/ Math.sqrt((1 + 0.006739496742 * Math
														.pow(Math.cos(north / 6366197.724 / 0.9996), 2))))
										* (1 - 0.006739496742 * Math.pow(
												(Easting - 500000) / (0.9996 * 6399593.625
														/ Math.sqrt((1 + 0.006739496742 * Math
																.pow(Math.cos(north / 6366197.724 / 0.9996), 2)))),
												2) / 2 * Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2))
								+ north / 6366197.724
										/ 0.9996))) * Math.tan((north - 0.9996 * 6399593.625
												* (north / 6366197.724 / 0.9996
														- 0.006739496742 * 3 / 4
																* (north / 6366197.724 / 0.9996 + Math
																		.sin(2 * north / 6366197.724 / 0.9996) / 2)
												+ Math.pow(0.006739496742 * 3 / 4,
														2) * 5
												/ 3
												* (3 * (north / 6366197.724 / 0.9996
														+ Math.sin(2 * north / 6366197.724 / 0.9996) / 2)
												+ Math.sin(2 * north / 6366197.724 / 0.9996)
														* Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2)) / 4
								- Math.pow(0.006739496742 * 3 / 4, 3) * 35 / 27
										* (5 * (3
												* (north / 6366197.724 / 0.9996
														+ Math.sin(2 * north / 6366197.724 / 0.9996) / 2)
												+ Math.sin(2 * north / 6366197.724 / 0.9996)
														* Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2))
												/ 4
												+ Math.sin(2 * north / 6366197.724 / 0.9996)
														* Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2)
														* Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2))
										/ 3)) / (0.9996
												* 6399593.625
												/ Math.sqrt((1 + 0.006739496742 * Math
														.pow(Math.cos(north / 6366197.724 / 0.9996), 2))))
												* (1 - 0.006739496742
														* Math.pow((Easting - 500000) / (0.9996 * 6399593.625
																/ Math.sqrt((1 + 0.006739496742 * Math.pow(
																		Math.cos(north / 6366197.724 / 0.9996),
																		2)))),
																2)
														/ 2 * Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2))
								+ north / 6366197.724 / 0.9996)) - north / 6366197.724 / 0.9996))
				* 180 / Math.PI;
		latitude = Math.round(latitude * 10000000);
		latitude = latitude / 10000000;
		longitude = Math
				.atan((Math
						.exp((Easting - 500000)
								/ (0.9996 * 6399593.625
										/ Math.sqrt(
												(1 + 0.006739496742 * Math
														.pow(Math.cos(north / 6366197.724 / 0.9996), 2))))
								* (1 - 0.006739496742
										* Math.pow(
												(Easting - 500000) / (0.9996 * 6399593.625
														/ Math.sqrt((1 + 0.006739496742 * Math
																.pow(Math.cos(north / 6366197.724 / 0.9996), 2)))),
								2) / 2 * Math
										.pow(Math.cos(north / 6366197.724 / 0.9996),
												2)
										/ 3))
						- Math.exp(
								-(Easting - 500000)
										/ (0.9996 * 6399593.625
												/ Math.sqrt((1 + 0.006739496742 * Math
														.pow(Math.cos(north / 6366197.724 / 0.9996), 2))))
										* (1 - 0.006739496742
												* Math.pow((Easting - 500000) / (0.9996 * 6399593.625
														/ Math.sqrt((1 + 0.006739496742 * Math
																.pow(Math.cos(north / 6366197.724 / 0.9996), 2)))),
														2)
												/ 2 * Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2) / 3)))
						/ 2 / Math
								.cos((north
										- 0.9996 * 6399593.625
												* (north / 6366197.724 / 0.9996
														- 0.006739496742 * 3 / 4
																* (north / 6366197.724 / 0.9996 + Math
																		.sin(2 * north / 6366197.724 / 0.9996) / 2)
												+ Math.pow(0.006739496742 * 3 / 4,
														2) * 5
												/ 3
												* (3 * (north / 6366197.724 / 0.9996
														+ Math.sin(2 * north / 6366197.724 / 0.9996) / 2)
												+ Math.sin(2 * north / 6366197.724 / 0.9996)
														* Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2)) / 4
						- Math.pow(0.006739496742 * 3 / 4, 3) * 35 / 27
								* (5 * (3
										* (north / 6366197.724 / 0.9996
												+ Math.sin(2 * north / 6366197.724 / 0.9996) / 2)
										+ Math.sin(2 * north / 6366197.724 / 0.9996)
												* Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2))
										/ 4
										+ Math.sin(2 * north / 6366197.724 / 0.9996)
												* Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2)
												* Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2))
								/ 3)) / (0.9996
										* 6399593.625
										/ Math.sqrt(
												(1 + 0.006739496742 * Math
														.pow(Math.cos(north / 6366197.724 / 0.9996), 2))))
										* (1 - 0.006739496742 * Math.pow(
												(Easting - 500000) / (0.9996 * 6399593.625
														/ Math.sqrt((1 + 0.006739496742 * Math
																.pow(Math.cos(north / 6366197.724 / 0.9996), 2)))),
								2) / 2 * Math.pow(Math.cos(north / 6366197.724 / 0.9996), 2))
						+ north / 6366197.724 / 0.9996))
				* 180 / Math.PI + Zone * 6 - 183;
		longitude = Math.round(longitude * 10000000);
		longitude = longitude / 10000000;
		
		return new GeographicCoordinates(latitude, longitude);
	}
	
	
	/* Si encuentra la interfaz interf devuelve su IP si comienza por prefix
	 	En cualquier otro caso, devuelve la IP de loopback */
	public static InetAddress getIPAddress(String interf, String prefix) throws SocketException, UnknownHostException {
		// Ejemplo: interf="wlan0" y prefix="192.168.1"
		InetAddress res = InetAddress.getByName("127.0.0.1");
		
		Enumeration<NetworkInterface> e;
		e = NetworkInterface.getNetworkInterfaces();
		while (e.hasMoreElements()) {
			NetworkInterface ni = (NetworkInterface) e.nextElement();
			if (ni.isLoopback() || !ni.isUp()) continue;
	
			if (ni.getName().toUpperCase().equals(interf.toUpperCase())) {
				for (Enumeration<InetAddress> e2 = ni.getInetAddresses(); e2.hasMoreElements(); ) {
		            InetAddress ip = (InetAddress) e2.nextElement();
		            if (ip.getHostName().startsWith(prefix)) return ip;
		        }
			}
	  }
	  return res;
	}

	/**
	 * Create the application.
	 * @wbp.parser.entryPoint
	 */
	public MainWindow() {
		initialize();
	}

	/**
	 * Initialize the contents of the frame.
	 * @wbp.parser.entryPoint
	 */
	private void initialize() {
		try {
//		  BufferedWriter writer;
//      try {
//        // Triquiñuela necesaria para escribir en el mismo path que el .jar
//        URL url = MainWindow.class.getProtectionDomain().getCodeSource().getLocation();
//        String jarPath = URLDecoder.decode(url.getFile(), "UTF-8");
//        String parentPath = new File(jarPath).getParentFile().getPath();
//        String fileSeparator = System.getProperty("file.separator");
//        String newFile = parentPath + fileSeparator + "aspecto.txt";
//        
//        writer = new BufferedWriter( new FileWriter(newFile));
//        writer.write(UIManager.getSystemLookAndFeelClassName());
//        // do stuff 
//        writer.close();
//      } catch (IOException e) {
//        // TODO Auto-generated catch block
//        e.printStackTrace();
//      }
		  String lookAndFeel = UIManager.getSystemLookAndFeelClassName();
		  if (lookAndFeel.equals("com.sun.java.swing.plaf.gtk.GTKLookAndFeel")) {
		    UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
		  } else {
		    UIManager.setLookAndFeel(lookAndFeel);
		  }
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
				| UnsupportedLookAndFeelException e1) {
		}
		
		MainWindow.frame = new JFrame();
		MainWindow.frame.setTitle("Dronning");
		MainWindow.frame.setBounds(100, 100, 650, 450);
		MainWindow.frame.setResizable(false);
		//frame.setExtendedState(frmDronning.getExtendedState() | JFrame.MAXIMIZED_BOTH);
		MainWindow.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		SpringLayout springLayout = new SpringLayout();
		MainWindow.frame.getContentPane().setLayout(springLayout);
		
		JPanel States = new JPanel();
		States.setBorder(new EmptyBorder(2, 2, 2, 2));
		springLayout.putConstraint(SpringLayout.NORTH, States, 10, SpringLayout.NORTH, MainWindow.frame.getContentPane());
		springLayout.putConstraint(SpringLayout.WEST, States, 10, SpringLayout.WEST, MainWindow.frame.getContentPane());
		springLayout.putConstraint(SpringLayout.EAST, States, -10, SpringLayout.EAST, MainWindow.frame.getContentPane());
		MainWindow.frame.getContentPane().add(States);
		
		JPanel dataPanel = new JPanel();
		springLayout.putConstraint(SpringLayout.NORTH, dataPanel, 10, SpringLayout.SOUTH, States);
		springLayout.putConstraint(SpringLayout.WEST, dataPanel, 10, SpringLayout.WEST, MainWindow.frame.getContentPane());
//		springLayout.putConstraint(SpringLayout.SOUTH, dataPanel, -10, SpringLayout.SOUTH, MainWindow.frame.getContentPane());
		States.setLayout(new GridLayout(0, 8, -1, -1));
		
		JLabel label = new JLabel("");
		States.add(label);
		
		JLabel loggingLabel = new JLabel("logging");
		loggingLabel.setHorizontalAlignment(SwingConstants.CENTER);
		loggingLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		States.add(loggingLabel);
		
		JLabel loggedLabel = new JLabel("logged");
		loggedLabel.setHorizontalAlignment(SwingConstants.CENTER);
		loggedLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		States.add(loggedLabel);
		
		JLabel connectedLabel = new JLabel("connected");
		connectedLabel.setHorizontalAlignment(SwingConstants.CENTER);
		connectedLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		States.add(connectedLabel);
		
		JLabel configuredLabel = new JLabel("configured");
		configuredLabel.setHorizontalAlignment(SwingConstants.CENTER);
		configuredLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		States.add(configuredLabel);
		
		JLabel startedLabel = new JLabel("started");
		startedLabel.setHorizontalAlignment(SwingConstants.CENTER);
		startedLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		States.add(startedLabel);
		
		JLabel runningLabel = new JLabel("running");
		runningLabel.setHorizontalAlignment(SwingConstants.CENTER);
		runningLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		States.add(runningLabel);
		
		JLabel stoppedLabel = new JLabel("stopped");
		stoppedLabel.setHorizontalAlignment(SwingConstants.CENTER);
		stoppedLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		States.add(stoppedLabel);
		
		JLabel serverLabel = new JLabel("Server");
		serverLabel.setHorizontalAlignment(SwingConstants.CENTER);
		serverLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		States.add(serverLabel);
		
		sLogging = new JButton("");
		sLogging.setEnabled(false);
		sLogging.setContentAreaFilled(false);
		sLogging.setOpaque(true);
		sLogging.setBackground(Color.RED);
		States.add(sLogging);
		
		sLogged = new JButton("");
		sLogged.setEnabled(false);
		sLogged.setContentAreaFilled(false);
		sLogged.setOpaque(true);
		sLogged.setBackground(Color.RED);
		States.add(sLogged);
		
		sConnected = new JButton("");
		sConnected.setEnabled(false);
		sConnected.setContentAreaFilled(false);
		sConnected.setOpaque(true);
		sConnected.setBackground(Color.RED);
		States.add(sConnected);
		
		sConfigured = new JButton("");
		sConfigured.setEnabled(false);
		sConfigured.setContentAreaFilled(false);
		sConfigured.setOpaque(true);
		sConfigured.setBackground(Color.RED);
		States.add(sConfigured);
		
		sStarted = new JButton("");
		sStarted.setEnabled(false);
		sStarted.setContentAreaFilled(false);
		sStarted.setOpaque(true);
		sStarted.setBackground(Color.RED);
		States.add(sStarted);
		
		sRunning = new JButton("");
		sRunning.setEnabled(false);
		sRunning.setContentAreaFilled(false);
		sRunning.setOpaque(true);
		sRunning.setBackground(Color.RED);
		States.add(sRunning);
		
		sStopped = new JButton("");
		sStopped.setEnabled(false);
		sStopped.setContentAreaFilled(false);
		sStopped.setOpaque(true);
		sStopped.setBackground(Color.RED);
		States.add(sStopped);
		
		JLabel clientLabel = new JLabel("Client");
		clientLabel.setHorizontalAlignment(SwingConstants.CENTER);
		clientLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		States.add(clientLabel);
		
		cLogging = new JButton("");
		cLogging.setEnabled(false);
		cLogging.setContentAreaFilled(false);
		cLogging.setOpaque(true);
		cLogging.setBackground(Color.RED);
		States.add(cLogging);
		
		cLogged = new JButton("");
		cLogged.setEnabled(false);
		cLogged.setContentAreaFilled(false);
		cLogged.setOpaque(true);
		cLogged.setBackground(Color.RED);
		States.add(cLogged);
		
		cConnected = new JButton("");
		cConnected.setEnabled(false);
		cConnected.setContentAreaFilled(false);
		cConnected.setOpaque(true);
		cConnected.setBackground(Color.RED);
		States.add(cConnected);
		
		cConfigured = new JButton("");
		cConfigured.setEnabled(false);
		cConfigured.setContentAreaFilled(false);
		cConfigured.setOpaque(true);
		cConfigured.setBackground(Color.RED);
		States.add(cConfigured);
		
		cStarted = new JButton("");
		cStarted.setEnabled(false);
		cStarted.setContentAreaFilled(false);
		cStarted.setOpaque(true);
		cStarted.setBackground(Color.RED);
		States.add(cStarted);
		
		cRunning = new JButton("");
		cRunning.setEnabled(false);
		cRunning.setContentAreaFilled(false);
		cRunning.setOpaque(true);
		cRunning.setBackground(Color.RED);
		States.add(cRunning);
		
		cStopped = new JButton("");
		cStopped.setEnabled(false);
		cStopped.setContentAreaFilled(false);
		cStopped.setOpaque(true);
		cStopped.setBackground(Color.RED);
		States.add(cStopped);
		
		MainWindow.frame.getContentPane().add(dataPanel);
		
		GridBagLayout gbl_dataPanel = new GridBagLayout();
		gbl_dataPanel.columnWidths = new int[]{0, 0, 0};
		gbl_dataPanel.rowHeights = new int[]{0, 0, 0, 0, 0, 0, 0, 0};
		gbl_dataPanel.columnWeights = new double[]{0.0, 1.0, Double.MIN_VALUE};
		gbl_dataPanel.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
		dataPanel.setLayout(gbl_dataPanel);
		
		JLabel configurationLabel = new JLabel("Test configuration:");
		GridBagConstraints gbc_configurationLabel = new GridBagConstraints();
		gbc_configurationLabel.gridwidth = 2;
		gbc_configurationLabel.insets = new Insets(0, 0, 5, 0);
		gbc_configurationLabel.gridx = 0;
		gbc_configurationLabel.gridy = 0;
		dataPanel.add(configurationLabel, gbc_configurationLabel);
		
		filename = new JTextField();
		GridBagConstraints gbc_filename = new GridBagConstraints();
		gbc_filename.insets = new Insets(0, 0, 5, 0);
		gbc_filename.fill = GridBagConstraints.HORIZONTAL;
		gbc_filename.gridx = 1;
		gbc_filename.gridy = 1;
		dataPanel.add(filename, gbc_filename);
		filename.setColumns(25);
		
		JLabel durationLabel = new JLabel("Duration (sec):");
		GridBagConstraints gbc_durationLabel = new GridBagConstraints();
		gbc_durationLabel.anchor = GridBagConstraints.EAST;
		gbc_durationLabel.insets = new Insets(0, 0, 5, 5);
		gbc_durationLabel.gridx = 0;
		gbc_durationLabel.gridy = 2;
		dataPanel.add(durationLabel, gbc_durationLabel);
		
		JLabel filenameLabel = new JLabel("Base filename:");
		GridBagConstraints gbc_filenameLabel = new GridBagConstraints();
		gbc_filenameLabel.anchor = GridBagConstraints.EAST;
		gbc_filenameLabel.insets = new Insets(0, 0, 5, 5);
		gbc_filenameLabel.gridx = 0;
		gbc_filenameLabel.gridy = 1;
		dataPanel.add(filenameLabel, gbc_filenameLabel);
		
		duration = new JTextField();
		GridBagConstraints gbc_duration = new GridBagConstraints();
		gbc_duration.insets = new Insets(0, 0, 5, 0);
		gbc_duration.fill = GridBagConstraints.HORIZONTAL;
		gbc_duration.gridx = 1;
		gbc_duration.gridy = 2;
		dataPanel.add(duration, gbc_duration);
		duration.setColumns(10);
		
		JLabel ratioLabel = new JLabel("Tx rate (packets/sec):");
		GridBagConstraints gbc_ratioLabel = new GridBagConstraints();
		gbc_ratioLabel.anchor = GridBagConstraints.EAST;
		gbc_ratioLabel.insets = new Insets(0, 0, 5, 5);
		gbc_ratioLabel.gridx = 0;
		gbc_ratioLabel.gridy = 3;
		dataPanel.add(ratioLabel, gbc_ratioLabel);
		
		ratio = new JTextField();
		GridBagConstraints gbc_ratio = new GridBagConstraints();
		gbc_ratio.insets = new Insets(0, 0, 5, 0);
		gbc_ratio.fill = GridBagConstraints.HORIZONTAL;
		gbc_ratio.gridx = 1;
		gbc_ratio.gridy = 3;
		dataPanel.add(ratio, gbc_ratio);
		ratio.setColumns(10);
		
		JLabel packetSizeLabel = new JLabel("Packet size (B):");
		GridBagConstraints gbc_packetSizeLabel = new GridBagConstraints();
		gbc_packetSizeLabel.anchor = GridBagConstraints.EAST;
		gbc_packetSizeLabel.insets = new Insets(0, 0, 0, 5);
		gbc_packetSizeLabel.gridx = 0;
		gbc_packetSizeLabel.gridy = 4;
		dataPanel.add(packetSizeLabel, gbc_packetSizeLabel);
		
		packetSize = new JTextField();
		GridBagConstraints gbc_packetSize = new GridBagConstraints();
		gbc_packetSize.fill = GridBagConstraints.HORIZONTAL;
		gbc_packetSize.gridx = 1;
		gbc_packetSize.gridy = 4;
		dataPanel.add(packetSize, gbc_packetSize);
		packetSize.setColumns(10);
		
		JPanel panel = new JPanel();
		GridBagConstraints gbc_panel = new GridBagConstraints();
		gbc_panel.insets = new Insets(0, 0, 0, 5);
		gbc_panel.fill = GridBagConstraints.BOTH;
		gbc_panel.gridx = 1;
		gbc_panel.gridy = 5;
		dataPanel.add(panel, gbc_panel);
		
		rdbtnBroadcast = new JRadioButton("Broadcast");
		buttonGroup.add(rdbtnBroadcast);
		rdbtnBroadcast.setSelected(true);
		panel.add(rdbtnBroadcast);
		
		JRadioButton rdbtnUnicast = new JRadioButton("Unicast");
		buttonGroup.add(rdbtnUnicast);
		panel.add(rdbtnUnicast);
		
		dronesDistance = new JLabel("");
		GridBagConstraints gbc_panel2 = new GridBagConstraints();
		gbc_panel2.insets = new Insets(0, 0, 0, 5);
//		gbc_panel2.fill = GridBagConstraints.BOTH;
		gbc_panel2.gridx = 1;
		gbc_panel2.gridy = 6;
		dataPanel.add(dronesDistance, gbc_panel2);
		
		JPanel panel2 = new JPanel();
		GridBagConstraints gbc_panel3 = new GridBagConstraints();
		gbc_panel3.insets = new Insets(0, 0, 0, 5);
		gbc_panel3.fill = GridBagConstraints.BOTH;
		gbc_panel3.gridx = 1;
		gbc_panel3.gridy = 7;
		dataPanel.add(panel2, gbc_panel3);
		
		serverAltitude = new JLabel("");
		panel2.add(serverAltitude);
		clientAltitude = new JLabel("");
		panel2.add(clientAltitude);
		difAltitude = new JLabel("");
		panel2.add(difAltitude);
		
		JPanel actionPanel = new JPanel();
		springLayout.putConstraint(SpringLayout.EAST, dataPanel, -10, SpringLayout.WEST, actionPanel);
		springLayout.putConstraint(SpringLayout.NORTH, actionPanel, 10, SpringLayout.SOUTH, States);
//		springLayout.putConstraint(SpringLayout.SOUTH, actionPanel, -10, SpringLayout.SOUTH, MainWindow.frame.getContentPane());
		springLayout.putConstraint(SpringLayout.EAST, actionPanel, -10, SpringLayout.EAST, MainWindow.frame.getContentPane());
		MainWindow.frame.getContentPane().add(actionPanel);
		actionPanel.setLayout(new GridLayout(0, 1, 0, 0));
		
		sendConfiguration = new JButton("Send configuration");
		sendConfiguration.setEnabled(false);
		sendConfiguration.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				
				//Validación de datos
				boolean exito = true;
				String file = filename.getText();
				if (file.length()==0) exito = false;
				
				String timeText = duration.getText();
				int time;	//Sólo se usa para validación de datos
				if (timeText.length()>0) {
					try {
						time = Integer.parseInt(timeText);
					}catch (NumberFormatException e) {
						exito = false;	//No contiene un número
					}
				} else {
					exito = false;	//Celda en blanco
				}
				
				String ratioText = ratio.getText();
				int r = 0;
				if (ratioText.length()>0) {
					try {
						r = Integer.parseInt(ratioText);
					}catch (NumberFormatException e) {
						exito = false;
					}
				}
				if (r<=0) exito = false;
				
				String packetText = packetSize.getText();
				int size = 0;
				if (packetText.length()>0) {
					try {
						size = Integer.parseInt(packetText);
					}catch (NumberFormatException e) {
						exito = false;
					}
				}
				//Umbral inferior por tamaño de datos enviados (estimo <100B/paquete)
				//Umbral superior por tamaño de trama Ethernet, para evitar fragmentación
				if (size<100 || size>1500) exito = false;
				
				//hacer una validación de tamaño de paquete más acorde para afinar el padding
				
				if (exito) {
					//Se bloquean los campos hasta que se inicie la prueba
					filename.setEnabled(false);
					duration.setEnabled(false);
					ratio.setEnabled(false);
					packetSize.setEnabled(false);
					rdbtnBroadcast.setEnabled(false);
					rdbtnUnicast.setEnabled(false);
					configured.set(true);
				}
				else {
					JOptionPane.showMessageDialog(MainWindow.frame, "Es necesario que los campos sean no nulos,"
							+ "\nque el ratio de envío sea positivo"
							+ "\ny que los paquetes tengan un tamaño entre 100 y 1500 bytes.",
							"Error de formato", JOptionPane.WARNING_MESSAGE);
				}
			}
		});
		actionPanel.add(sendConfiguration);
		
		startTest = new JButton("Start test");
		startTest.setEnabled(false);
		startTest.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				//Se desbloquean los campos
				filename.setEnabled(true);
				duration.setEnabled(true);
				ratio.setEnabled(true);
				packetSize.setEnabled(true);
				rdbtnBroadcast.setEnabled(true);
				rdbtnUnicast.setEnabled(true);
				dronesDistance.setText("");
				serverAltitude.setText("");
				clientAltitude.setText("");
				difAltitude.setText("");
				
				started.set(true);
				startTest.setEnabled(false);
				sendRatioRealLabel.setText("");
				loosingRatioLabel.setText("");
				totalLostPacketsLabel.setText("");
				counter.setText("");
			}
		});
		actionPanel.add(startTest);
		
		sendRatioRealLabel = new JLabel("");
		sendRatioRealLabel.setHorizontalAlignment(SwingConstants.CENTER);
		actionPanel.add(sendRatioRealLabel);
		loosingRatioLabel = new JLabel("");
		loosingRatioLabel.setHorizontalAlignment(SwingConstants.CENTER);
		actionPanel.add(loosingRatioLabel);
		totalLostPacketsLabel = new JLabel("");
		totalLostPacketsLabel.setHorizontalAlignment(SwingConstants.CENTER);
		actionPanel.add(totalLostPacketsLabel);
		counter = new JLabel("");
		counter.setHorizontalAlignment(SwingConstants.CENTER);
		actionPanel.add(counter);
		
		JPanel statusPanel = new JPanel();
		springLayout.putConstraint(SpringLayout.NORTH, statusPanel, 10, SpringLayout.SOUTH, dataPanel);
		springLayout.putConstraint(SpringLayout.WEST, statusPanel, 10, SpringLayout.WEST, MainWindow.frame.getContentPane());
		springLayout.putConstraint(SpringLayout.EAST, statusPanel, -10, SpringLayout.EAST, MainWindow.frame.getContentPane());
		springLayout.putConstraint(SpringLayout.SOUTH, actionPanel, -10, SpringLayout.SOUTH, MainWindow.frame.getContentPane());
		MainWindow.frame.getContentPane().add(statusPanel);
		GridBagLayout layoutRelojes = new GridBagLayout();
//		layoutRelojes.columnWidths = new int[]{0, 0, 0, 0, 0, 0};
//		layoutRelojes.rowHeights = new int[]{0, 0, 0};
//		layoutRelojes.columnWeights = new double[]{0.0, 0.0, 0.0, 0.0, 1.0, Double.MIN_VALUE};
//		layoutRelojes.rowWeights = new double[]{0.0, 0.0, Double.MIN_VALUE};
		statusPanel.setLayout(layoutRelojes);
		
		JLabel serverText = new JLabel("Server yaw");
		GridBagConstraints gbc_serverText = new GridBagConstraints();
		gbc_serverText.gridwidth = 1;
		gbc_serverText.insets = new Insets(0, 0, 5, 0);
		gbc_serverText.gridx = 0;
		gbc_serverText.gridy = 0;
		statusPanel.add(serverText, gbc_serverText);

		datasetServer = new DefaultValueDataset(new Double(90.0));
		JFreeChart chartServer = MainWindow.createChart(datasetServer);
		ChartPanel chartPanelServer = new ChartPanel(chartServer);
		chartPanelServer.setPreferredSize(new java.awt.Dimension(160, 110));
		chartPanelServer.setEnforceFileExtensions(false);
		GridBagConstraints gbc_compassS = new GridBagConstraints();
		gbc_compassS.gridwidth = 1;
		gbc_compassS.insets = new Insets(0, 0, 5, 0);
		gbc_compassS.gridx = 0;
		gbc_compassS.gridy = 1;
		statusPanel.add(chartPanelServer, gbc_compassS);
		
		serverJawText = new JLabel("");
		GridBagConstraints gbc_serverJawText = new GridBagConstraints();
		gbc_serverJawText.gridwidth = 1;
		gbc_serverJawText.insets = new Insets(0, 0, 5, 0);
		gbc_serverJawText.gridx = 0;
		gbc_serverJawText.gridy = 2;
		statusPanel.add(serverJawText, gbc_serverJawText);
		
		JLabel clientText = new JLabel("Client yaw");
		GridBagConstraints gbc_clientText = new GridBagConstraints();
		gbc_clientText.gridwidth = 1;
		gbc_clientText.insets = new Insets(0, 0, 5, 0);
		gbc_clientText.gridx = 1;
		gbc_clientText.gridy = 0;
		statusPanel.add(clientText, gbc_clientText);
		
		datasetClient = new DefaultValueDataset(new Double(90.0));
		JFreeChart chartClient = MainWindow.createChart(datasetClient);
		ChartPanel chartPanelClient = new ChartPanel(chartClient);
        chartPanelClient.setPreferredSize(new java.awt.Dimension(160, 110));
        chartPanelClient.setEnforceFileExtensions(false);
		GridBagConstraints gbc_compassC = new GridBagConstraints();
		gbc_compassC.gridwidth = 1;
		gbc_compassC.insets = new Insets(0, 0, 5, 0);
		gbc_compassC.gridx = 1;
		gbc_compassC.gridy = 1;
		statusPanel.add(chartPanelClient, gbc_compassC);
		
		clientJawText = new JLabel("");
		GridBagConstraints gbc_clientJawText = new GridBagConstraints();
		gbc_clientJawText.gridwidth = 1;
		gbc_clientJawText.insets = new Insets(0, 0, 5, 0);
		gbc_clientJawText.gridx = 1;
		gbc_clientJawText.gridy = 2;
		statusPanel.add(clientJawText, gbc_clientJawText);
	}

private static JFreeChart createChart(final ValueDataset dataset) {
        
        final CompassPlot plot = new CompassPlot(dataset);
        plot.setSeriesNeedle(4);
        plot.setSeriesPaint(0, Color.black);
        plot.setSeriesOutlinePaint(0, Color.black);
        final JFreeChart chart = new JFreeChart(plot);
        return chart;
    }
}
