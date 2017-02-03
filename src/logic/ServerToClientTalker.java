package logic;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Output;

import gui.MainWindow;

public class ServerToClientTalker extends Thread {
	/* Envíos por UDP */
	DatagramSocket socket;
	byte[] sentBuffer;
	DatagramPacket sentPacket;
	
	/* Serialización de los envíos con Kryo */
	Output output;
	
	/* Número de secuencia del mensaje */
	long numSeq = 0;
	
	public ServerToClientTalker() throws SocketException {
		super(MainWindow.SERVER_TO_CLIENT_NAME);
		
		socket = new DatagramSocket();
		socket.setBroadcast(true);
//		socket.setSendBufferSize(MainWindow.BUFFERSIZE);
//		System.out.println(socket.getSendBufferSize());
	}

	@Override
	public void run() {
		
		while (true) {
			
			/* Paso 1: Espera al inicio de la prueba para enviar paquetes */
			while (MainWindow.serverState.get() != MainWindow.SERVER_STATE_RUNNING) {
				MainWindow.esperaPasiva(MainWindow.waitLong);
			}
			
			/* Paso 2: Envío de paquetes mientras dura la prueba */
			
			//Número de secuencia del paquete enviado
			long numSeq = 1;
		
			//Cálculo del tiempo de espera entre paquetes en nanosegundos
			double bursts = 1000.0/MainWindow.MIN_BURST_TIME;	//Máx ráfagas por segundo
			long period;
			int[] burstPackets;
			if (((double)MainWindow.sendRatio) < bursts) {
				//Se adapta el periodo entre ráfagas
				period = (long)(1000000000l/(double)MainWindow.sendRatio);
				//Se enviará cada vez 1 ráfaga de 1 paquete
				burstPackets = new int[]{1};
			}
			else {
				period = MainWindow.MIN_BURST_TIME*1000000l;
				//Se enviarán ráfagas de distinta longitud, según necesidad
				int maxComunDivisor = MCD_Euclides(1000, MainWindow.MIN_BURST_TIME);
				int realPeriod = MainWindow.MIN_BURST_TIME/maxComunDivisor;	//Segundos
				int realNumBursts = 1000/maxComunDivisor;	//Ráfagas en los realPeriod segundos
				int realNumPackets = realPeriod * MainWindow.sendRatio;
				burstPackets = new int[realNumBursts];
				int quotient = realNumPackets / realNumBursts;
				int remainder = realNumPackets % realNumBursts;
				for (int i=0; i<burstPackets.length; i++) {
					burstPackets[i] = quotient;
					if (remainder > i) burstPackets[i]++;
				}
				
				String aux = "Paquetes: [" + burstPackets[0];
				for (int i=1; i<burstPackets.length; i++) {
					aux += "," + burstPackets[i];
				}
				aux += "]";
				System.out.println(aux);
			}
			System.out.println("Espera de " + period + " nanosegundos entre ráfagas.");
			System.out.println("Número de ráfagas " + burstPackets.length);
			
			// datos+padding = tamaño - cabeceraIP - cabeceraUDP
			int longEnvio = MainWindow.size - 28;
			
			//Buffer que se irá reutilizando
			sentBuffer = new byte[MainWindow.DGRAM_MAX_LENGTH];
			output = new Output(sentBuffer);
			InetAddress destination = MainWindow.clientIp;
			if(MainWindow.isBroadcast && !MainWindow.clientIp.getHostAddress().equals("127.0.0.1"))
				try {
					destination = InetAddress.getByName("192.168.1.255");
				} catch (UnknownHostException e1) {
					e1.printStackTrace();
				}
			
			sentPacket = new DatagramPacket(sentBuffer,
					longEnvio,
					destination,
					MainWindow.client_from_Server_Listener_Port);
			
			//Variables para calcular el tiempo transcurrido entre el primer y último paquete
			long begin = 0;
			long end = 0;
			
			//Variables para calcular el tiempo transcurrido entre dos envíos
			//  con espera activa
//			long limit = System.nanoTime() + espera;
			while (MainWindow.serverState.get() == MainWindow.SERVER_STATE_RUNNING) {
				//Envío de i ráfagas en el periodo global
				for (int i=0; i<burstPackets.length; i++) {
					//Envío de j paquetes dentro de la ráfaga
					for (int j=0; j<burstPackets[i]; j++) {
						try {
							//Quizá optimizable si se leen los datos atómicos cada cierto tiempo
							
							output.clear();
							output.writeShort(MainWindow.serverDronId);
							output.writeLong(numSeq);
							//Escritura a pelo del contenido del dron
							output.writeInt(MainWindow.dron.temperature.get());
							output.writeFloat(MainWindow.dron.roll.get());
							output.writeFloat(MainWindow.dron.pitch.get());
							output.writeFloat(MainWindow.dron.yaw.get());
							output.writeFloat(MainWindow.dron.rollSpeed.get());
							output.writeFloat(MainWindow.dron.pitchSpeed.get());
							output.writeFloat(MainWindow.dron.yawSpeed.get());
							output.writeLong(MainWindow.dron.latitude.get());
							output.writeLong(MainWindow.dron.longitude.get());
							output.writeLong(MainWindow.dron.altitude.get());
							output.writeLong(MainWindow.dron.altitudeRelative.get());
							output.writeInt(MainWindow.dron.speedX.get());
							output.writeInt(MainWindow.dron.speedY.get());
							output.writeInt(MainWindow.dron.speedZ.get());
							output.writeInt(MainWindow.dron.heading.get());
							output.flush();
							
							//System.out.println("Datos realmente enviados " + output.position() + " B");
							
							//Verificación por si los datos ocupan mucho y no caben en los datos configurados
							if (longEnvio < output.position()) longEnvio = output.position();
							
							//System.out.println("Padding = " + (longEnvio-output.position()));
							
							sentPacket.setData(sentBuffer, 0, longEnvio);
							socket.send(sentPacket);
							numSeq++;
						} catch (KryoException e) {
							e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					end = System.nanoTime();
					if (begin == 0) begin = end;
					
					//Espera de x tiempo para cada ráfaga
	//				MainWindow.esperaActiva(limit);
					MainWindow.esperaPasiva(period);
	//				limit += espera; //Espera activa al siguiente paquete
				}
			}
			//end = System.nanoTime();
			output.close();
			float ratioReal = 0.0f;
			if (end-begin > 0) ratioReal = (numSeq-1)/((end-begin)/1000000000f);
			MainWindow.sendRatioReal.set(ratioReal);
			
			MainWindow.transitionalState2.set(false);
			
			System.out.printf("Ratio real de envío: %.2f paquetes/s\n", ratioReal);
			
			Path file = null;
	        BufferedWriter bufferedWriter  = null;
	        String fileName = MainWindow.baseFileName + "_ratioEnvio.txt";
	        try{
	            file = Files.createFile(Paths.get(fileName));
	            Charset charset = Charset.forName("UTF-8");
	            bufferedWriter = Files.newBufferedWriter(file, charset);
	            bufferedWriter.write("Ratio de envío de paquetes: " + ratioReal + " paquetes/s");
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
		
		//Sólo con fines de prueba, una sola iteración
//		MainWindow.moreJobs.set(false);
	}
	
	private int MCD_Euclides(int a, int b){
	    while(b != 0){
	         int t = b;
	         b = a % b;
	         a = t;
	    }
	    return a;
	}
}
