package pojo;

public class GeographicCoordinates {

	public double latitude;
	public double longitude;
	
	@SuppressWarnings("unused")
	private GeographicCoordinates() {}
	
	public GeographicCoordinates(double latitude, double longitude) {
		this.latitude = latitude;
		this.longitude = longitude;
	}
}
