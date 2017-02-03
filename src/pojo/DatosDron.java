package pojo;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class DatosDron {
	public AtomicInteger temperature;	// ºC*100
	public AtomicFloat roll;	// alabeo (inclinación hacia un lado): rad (positivo hacia la derecha)
	public AtomicFloat pitch;	// inclinación (cabeceo, bajar y subir el morro): rad (positivo subiendo)
	public AtomicFloat yaw;	// guiñada (eje vertical, giro): rad (positivo hacia la derecha)
	public AtomicFloat rollSpeed;	// velocidad de alabeo: rad/s
	public AtomicFloat pitchSpeed; // velocidad de cabeceo: rad/s
	public AtomicFloat yawSpeed;	// velocidad de guiñada: rad/s
	public AtomicLong latitude;		// grados*10^7
	public AtomicLong longitude;	// grados*10^7
	public AtomicLong altitude;		// mm
	public AtomicLong altitudeRelative;	// mm
	public AtomicInteger speedX;	// m/s*100
	public AtomicInteger speedY;	// m/s*100
	public AtomicInteger speedZ;	// m/s*100
	public AtomicInteger heading;	// yaw u orientación: grados sexagesimales*100 (0-359.99º)
//	public AtomicFloat vibrationX;	// vibración eje X 
//	public AtomicFloat vibrationY;	// vibración eje Y
//	public AtomicFloat vibrationZ;	// vibración eje Z

	public DatosDron() {
		super();
		temperature = new AtomicInteger();
		roll = new AtomicFloat();
		pitch = new AtomicFloat();
		yaw = new AtomicFloat();
		rollSpeed = new AtomicFloat();
		pitchSpeed = new AtomicFloat();
		yawSpeed = new AtomicFloat();
		latitude = new AtomicLong();
		longitude = new AtomicLong();
		altitude = new AtomicLong();
		altitudeRelative = new AtomicLong();
		speedX = new AtomicInteger();
		speedY = new AtomicInteger();
		speedZ = new AtomicInteger();
		heading = new AtomicInteger();
//		vibrationX = new AtomicFloat();
//		vibrationY = new AtomicFloat();
//		vibrationZ = new AtomicFloat();
	}
	
	

	@Override
	public DatosDron clone() {
		// No hace falta hacerlo synchronized porque los atributos son atómicos
		//   y no importa que alguno varíe ligeramente durante el proceso de copia
		DatosDron res = new DatosDron();
		res.temperature.set(this.temperature.get());
		res.roll.set(this.roll.get());
		res.pitch.set(this.pitch.get());
		res.yaw.set(this.yaw.get());
		res.rollSpeed.set(this.rollSpeed.get());
		res.pitchSpeed.set(this.pitchSpeed.get());
		res.yawSpeed.set(this.yawSpeed.get());
		res.latitude.set(this.latitude.get());
		res.longitude.set(this.longitude.get());
		res.altitude.set(this.altitude.get());
		res.altitudeRelative.set(this.altitudeRelative.get());
		res.speedX.set(this.speedX.get());
		res.speedY.set(this.speedY.get());
		res.speedZ.set(this.speedZ.get());
		res.heading.set(this.heading.get());
//		res.vibrationX.set(this.vibrationX.get());
//		res.vibrationY.set(this.vibrationY.get());
//		res.vibrationZ.set(this.vibrationZ.get());
		
		return res;
	}

	public String toCSV() {
		String res = "" + temperature.get() + "," + roll.get() + "," + pitch.get()
			+ "," + yaw.get() + "," + rollSpeed.get() + "," + pitchSpeed.get()
			+ "," + yawSpeed.get() + "," + latitude.get() + "," + longitude.get()
			+ "," + altitude.get() + "," + altitudeRelative.get() + "," + speedX.get()
			+ "," + speedY.get() + "," + speedZ.get() + "," + heading.get();
//			+ "," + vibrationX.get() + "," + vibrationY.get() + "," + vibrationZ.get();
		return res;
	}

	@Override
	public String toString() {
		return "DatosDron [temperature=" + temperature + ", roll=" + roll + ", pitch=" + pitch
				+ ", yaw=" + yaw + ", rollSpeed=" + rollSpeed + ", pitchSpeed=" + pitchSpeed + ", yawSpeed=" + yawSpeed
				+ ", latitude=" + latitude + ", longitude=" + longitude + ", altitude=" + altitude
				+ ", altitudeRelative=" + altitudeRelative + ", speedX=" + speedX + ", speedY=" + speedY + ", speedZ="
				+ speedZ + ", heading=" + heading
//				+ ", vibrationX=" + vibrationX + ", vibrationY=" + vibrationY
//				+ ", vibrationZ=" + vibrationZ
				+ "]";
	}
	
	public synchronized String toStringSync() {
		return "DatosDron [temperature=" + temperature + ", roll=" + roll + ", pitch=" + pitch
				+ ", yaw=" + yaw + ", rollSpeed=" + rollSpeed + ", pitchSpeed=" + pitchSpeed + ", yawSpeed=" + yawSpeed
				+ ", latitude=" + latitude + ", longitude=" + longitude + ", altitude=" + altitude
				+ ", altitudeRelative=" + altitudeRelative + ", speedX=" + speedX + ", speedY=" + speedY + ", speedZ="
				+ speedZ + ", heading=" + heading
//				+ ", vibrationX=" + vibrationX + ", vibrationY=" + vibrationY
//				+ ", vibrationZ=" + vibrationZ
				+ "]";
	}
}
