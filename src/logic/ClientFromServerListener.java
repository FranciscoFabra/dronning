package logic;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;

import gui.MainWindow;

public class ClientFromServerListener extends Thread {
	/* Envíos por UDP */
	DatagramSocket socket;
	byte[] receivedBuffer;
	DatagramPacket receivedPacket;
	
	/* Serialización de los envíos con Kryo */
	Input input;
	
	public ClientFromServerListener() throws SocketException {
		super(MainWindow.CLIENT_FROM_SERVER_NAME);
		
		socket = new DatagramSocket(MainWindow.client_from_Server_Listener_Port);
		socket.setBroadcast(true);
		socket.setSoTimeout((int)MainWindow.waitShort);
//		socket.setReceiveBufferSize(MainWindow.BUFFERSIZE);
//		System.out.println(socket.getReceiveBufferSize());
	}

	@Override
	public void run() {
		
		while (true) {
			
			/* Paso 1: Espera al inicio de la prueba para recibir paquetes */
			receivedBuffer = new byte[MainWindow.DGRAM_MAX_LENGTH];
			
			
			while (MainWindow.clientState.get() != MainWindow.CLIENT_STATE_RUNNING) {
				receivedPacket = new DatagramPacket(receivedBuffer, receivedBuffer.length);
				try {
					//Purga de los paquetes que puedan quedar en el socket
					socket.receive(receivedPacket);
				} catch (IOException e) {
				}
			}
			
			/* Paso 2: Envío de paquetes mientras dura la prueba */
			//Variables para calcular la pérdida de paquetes
			long numSeqOfFirstPacket = -1;
			long numSeq;
			long numSeqOfLastPacket = -1;
			long difference;
			long totalLostPackets = 0;
//        	int packetsBetweenTakes = MainWindow.sendRatio / MainWindow.recordRatio;
//        	if (MainWindow.sendRatio%MainWindow.recordRatio>0) packetsBetweenTakes++;
//        	long projectedTake = 1;	//Se guarda desde el primer número de secuencia
			//Variables para calcular el intervalo entre guardado de coordenadas y otros
			long savingInterval = 1000000000l/MainWindow.recordRatio;	//ns entre guardados
			long projectedTake = 0;	//Instante en el que se guardarán los datos
        	
			//Abrimos el fichero para guardar la información
			Path file = null;
	        BufferedWriter bufferedWriter  = null;
	        String fileName = MainWindow.baseFileName + ".csv";
	        try{
	            // De no poner fecha, habría que comprobar si el fichero existe
//	        	file = Paths.get(fileName);
//	            if (Files.exists(file, new LinkOption[]{ LinkOption.NOFOLLOW_LINKS}))
//	            	Files.delete(file);
	            file = Files.createFile(Paths.get(fileName));
	            Charset charset = Charset.forName("UTF-8");
	            bufferedWriter = Files.newBufferedWriter(file, charset);
	        } catch(IOException e){
	            e.printStackTrace();
	        }
	        
	        if (bufferedWriter != null) {
	        	// Línea de cabecera
	        	String line = "numSeq,type,idServer,tempS,rollS,pitchS,yawS,rollSS,"
	        			+ "pitchSS,yawSS,latS,lonS,altS,altRelS,velXS,velYS,velZS,headS,"
	        			+ "idClient,tempC,rollC,pitchC,yawC,rollSC,"
	        			+ "pitchSC,yawSC,latC,lonC,altC,altRelC,velXC,velYC,velZC,headC";
	        	try {
					bufferedWriter.write(line);
					bufferedWriter.newLine();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
	        	
				// Reutilizamos buffers y objetos por eficiencia
	        	receivedBuffer = new byte[MainWindow.DGRAM_MAX_LENGTH];
	        	input = new Input(receivedBuffer);
	        	receivedPacket = new DatagramPacket(receivedBuffer, receivedBuffer.length);
	        	
	        	long end = 0;
	        	long currentTime;
	        	
	        	while (MainWindow.clientState.get() == MainWindow.CLIENT_STATE_RUNNING) {
					try {
						// Recibimos el paquete UDP
						receivedPacket.setData(receivedBuffer, 0, receivedBuffer.length);
						socket.receive(receivedPacket);
						
						//System.out.println("IP origen: " + receivedPacket.getAddress());
						
						//System.out.println("Tamaño de paquete completo " + receivedPacket.getLength());

						input.setPosition(0);
						short idServer = input.readShort();
						numSeq = input.readLong();
						currentTime = System.nanoTime();
						if (numSeqOfFirstPacket<0) {
							numSeqOfFirstPacket = numSeq;
							//El tiempo comienza a contar desde la recepción del primer paquete
							end = System.currentTimeMillis() + (long)MainWindow.testDuration*1000;
							projectedTake = currentTime;
						} else {
							//Hipótesis: Entrega ordenada, pues no hay routers o APs por en medio
							// en caso contrario habría que analizar los paquetes que faltan en una
							// ventana de tiempo (convendría una cola ordenada de paquetes faltantes
							// y un control del tiempo transcurrido)
							difference = numSeq - numSeqOfLastPacket;
							if (difference > 1) totalLostPackets += difference - 1;
							else if (difference < 0) totalLostPackets--;	//Por si llega desordenado
						}
						numSeqOfLastPacket = numSeq;
						
						//Se guardan todos los datos solamente 10 veces por segundo
						// son entonces paquetes tipo 1
						if (currentTime >= projectedTake) {
							projectedTake += savingInterval;
							
							line = numSeq + "," + MainWindow.dataRegister + "," + idServer
									+ "," + input.readInt() + "," + input.readFloat()
									+ "," + input.readFloat() + "," + input.readFloat()
									+ "," + input.readFloat() + "," + input.readFloat()
									+ "," + input.readFloat() + "," + input.readLong()
									+ "," + input.readLong() + "," + input.readLong()
									+ "," + input.readLong() + "," + input.readInt()
									+ "," + input.readInt() + "," + input.readInt()
									+ "," + input.readInt() + "," + MainWindow.clientDronId
									+ "," + MainWindow.dron.temperature.get()
									+ "," + MainWindow.dron.roll.get()
									+ "," + MainWindow.dron.pitch.get()
									+ "," + MainWindow.dron.yaw.get()
									+ "," + MainWindow.dron.rollSpeed.get()
									+ "," + MainWindow.dron.pitchSpeed.get()
									+ "," + MainWindow.dron.yawSpeed.get()
									+ "," + MainWindow.dron.latitude.get()
									+ "," + MainWindow.dron.longitude.get()
									+ "," + MainWindow.dron.altitude.get()
									+ "," + MainWindow.dron.altitudeRelative.get()
									+ "," + MainWindow.dron.speedX.get()
									+ "," + MainWindow.dron.speedY.get()
									+ "," + MainWindow.dron.speedZ.get()
									+ "," + MainWindow.dron.heading.get();
						} else {
							// Se guardan paquetes tipo 0 en caso contrario
							line = numSeq + "," + MainWindow.standardRegister;
						}
						
						bufferedWriter.write(line);
						bufferedWriter.newLine();
					} catch (SocketTimeoutException e) {
						//En caso de que no se estén recibiendo paquetes saltará la excepción
					} catch (KryoException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
					
					if (System.currentTimeMillis()-end>=0) MainWindow.clientState.set(MainWindow.CLIENT_STATE_STOPPING);
				}
	        	
	        	input.close();
	        	
				try{
	                bufferedWriter.close();
	            }catch(IOException ioe){
	                ioe.printStackTrace();
	            }
	        } else System.out.println("No se puede escribir los datos en disco");
	        
	        
//	        pruebalo = true;
	        
			
			//Cálculo del porcentaje de paquetes perdidos
			float loosingRatio = 1;
			if (numSeqOfLastPacket != numSeqOfFirstPacket)
				loosingRatio = totalLostPackets/(float)(numSeqOfLastPacket - numSeqOfFirstPacket + 1);
			MainWindow.loosingRatio.set(loosingRatio);
			MainWindow.totalLostPackets.set(totalLostPackets);
			
			MainWindow.transitionalState2.set(false);
			
			System.out.println("Paquetes recibidos: " + (numSeqOfLastPacket - numSeqOfFirstPacket + 1));
			System.out.println("Paquetes perdidos: " + totalLostPackets);
			System.out.printf("Ratio de pérdida de paquetes: %.10f %%\n", (loosingRatio*100));
			
			fileName = MainWindow.baseFileName + "_ratioPerdidas.txt";
	        try{
	            file = Files.createFile(Paths.get(fileName));
	            Charset charset = Charset.forName("UTF-8");
	            bufferedWriter = Files.newBufferedWriter(file, charset);
	            bufferedWriter.write("Paquetes recibidos: " + (numSeqOfLastPacket - numSeqOfFirstPacket + 1));
	            bufferedWriter.newLine();
	            bufferedWriter.write("Paquetes perdidos: " + totalLostPackets);
	            bufferedWriter.newLine();
	            bufferedWriter.write("Ratio de pérdidas: " + loosingRatio);
				bufferedWriter.newLine();
	        } catch(IOException e){
	            e.printStackTrace();
	        } finally {
	        	if (bufferedWriter != null) {
	        		try{
		                bufferedWriter.close();
		            }catch(IOException ioe){
		                ioe.printStackTrace();
		            }
	        	} else System.out.println("No se pudo escribir el ratio de pérdidas en disco");
	        }
		}
		
//		MainWindow.moreJobs.set(false);
	}
}
