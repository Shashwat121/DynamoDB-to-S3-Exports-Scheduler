package exportshandler;


import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@DynamoDBTable(tableName = "Ultfreezer")
public class DataModel
{

	@DynamoDBHashKey
	private String id;
	
	@DynamoDBAttribute
	private String timestamp;
	
	@DynamoDBAttribute
	private float ambientTemperature;

	@DynamoDBAttribute
	private String location;

	@DynamoDBAttribute
	private String name;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}

	public float getAmbientTemperature() {
		return ambientTemperature;
	}

	public void setAmbientTemperature(float ambientTemperature) {
		this.ambientTemperature = ambientTemperature;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	

}