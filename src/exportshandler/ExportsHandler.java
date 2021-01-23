package exportshandler;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedScanList;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;


public class ExportsHandler implements RequestHandler<DataModel, DataModel> {
	// CONFIGURATION DETAILS
	private final static String DYNAMODB_ENDPOINT = System.getenv("DYNAMODB_ENDPOINT");
	private final static String DYNAMODB_REGION = System.getenv("DYNAMODB_REGION");
	private final static String DYNAMODB_ACCESS_KEY = System.getenv("DYNAMODB_ACCESS_KEY");
	private final static String DYNAMODB_SECRET_KEY = System.getenv("DYNAMODB_SECRET_KEY");
	private final static String S3_ACCESS_KEY = System.getenv("S3_ACCESS_KEY");
	private final static String S3_SECRET_KEY = System.getenv("S3_SECRET_KEY");
	private final static String S3_BUCKET_NAME = System.getenv("S3_BUCKET_NAME");

	public static DynamoDBMapper dynamoDBMapper() {
		return new DynamoDBMapper(buildAmazonDynamoDB());
	}

	private static AmazonDynamoDB buildAmazonDynamoDB() {
		return AmazonDynamoDBClientBuilder.standard()
				.withEndpointConfiguration(
						new AwsClientBuilder.EndpointConfiguration(DYNAMODB_ENDPOINT, DYNAMODB_REGION))
				.withCredentials(new AWSStaticCredentialsProvider(
						new BasicAWSCredentials(DYNAMODB_ACCESS_KEY, DYNAMODB_SECRET_KEY)))
				.build();
	}

	private static String produceCsvData(Object[] data)
			throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
		if (data.length == 0) {
			return "";
		}

		Class classType = data[0].getClass();
		StringBuilder builder = new StringBuilder();

		Method[] methods = classType.getDeclaredMethods();

		for (Method m : methods) {
			if (m.getParameterTypes().length == 0) {
				if (m.getName().startsWith("get")) {
					builder.append(m.getName().substring(3)).append(',');
				} else if (m.getName().startsWith("is")) {
					builder.append(m.getName().substring(2)).append(',');
				}

			}

		}
		builder.deleteCharAt(builder.length() - 1);
		builder.append('\n');
		for (Object d : data) {
			for (Method m : methods) {
				if (m.getParameterTypes().length == 0) {
					if (m.getName().startsWith("get") || m.getName().startsWith("is")) {
						System.out.println(m.invoke(d).toString());
						builder.append(m.invoke(d).toString()).append(',');
					}
				}
			}
			builder.append('\n');
		}
		builder.deleteCharAt(builder.length() - 1);
		return builder.toString();
	}

	public static boolean generateCSV(File csvFileName, Object[] data) {
		FileWriter fw = null;
		try {
			fw = new FileWriter(csvFileName);
			if (!csvFileName.exists())
				csvFileName.createNewFile();
			fw.write(produceCsvData(data));
			fw.flush();
		} catch (Exception e) {
			System.out.println("Error while generating csv from data. Error message : " + e.getMessage());
			e.printStackTrace();
			return false;
		} finally {
			if (fw != null) {
				try {
					fw.close();
				} catch (Exception e) {
				}
				fw = null;
			}
		}
		return true;
	}

	@Override
	public DataModel handleRequest(DataModel event, Context context) {

		LambdaLogger logger = context.getLogger();

		DynamoDBMapper dynamoDBMapper = dynamoDBMapper();

		// GET DATA FROM DYNAMODB TABLE

		PaginatedScanList<DataModel> scanResult = dynamoDBMapper.scan(DataModel.class, new DynamoDBScanExpression());
		List<DataModel> list = new ArrayList<>(scanResult.size());
		Iterator<DataModel> iterator = scanResult.iterator();
		while (iterator.hasNext()) {
			DataModel element = iterator.next();
			list.add(element);
		}

		Collections.sort(list, (data1, data2) -> {
			Timestamp tx = Timestamp.valueOf(data1.getTimestamp());
			Timestamp ty = Timestamp.valueOf(data2.getTimestamp());
			return tx.compareTo(ty);
		});

		SimpleDateFormat sd1 = new SimpleDateFormat("dd-MM-yyyy");
		SimpleDateFormat sd = new SimpleDateFormat("yyyy-MM-dd 00:00:00.000");
		sd.setTimeZone(TimeZone.getTimeZone("IST"));
		sd1.setTimeZone(TimeZone.getTimeZone("IST"));
		Date todayDate = new Date();

		Calendar cal = Calendar.getInstance();
		cal.setTime(todayDate);
		cal.add(Calendar.DATE, -1);
		Date yesterdayDate = cal.getTime();
		String todayDayStamp = sd.format(todayDate);
		String dayStamp = sd1.format(yesterdayDate); // file
		String yesterdayDayStamp = sd.format(yesterdayDate);
		Timestamp t2 = Timestamp.valueOf(todayDayStamp);
		Timestamp t1 = Timestamp.valueOf(yesterdayDayStamp);

		List<DataModel> filteredList = new ArrayList<>();
		for (DataModel x : list) {
			Timestamp auxTimestamp = Timestamp.valueOf(x.getTimestamp());

			if (auxTimestamp.after(t1) && auxTimestamp.before(t2)) {
				filteredList.add(x);
			}

		}

		// POPULATE DDB DATA IN AN OBJECT ARRAY, CREATE THE CSV FILE

		Object[] objArray = list.toArray();
		Timestamp timestamp = new Timestamp(System.currentTimeMillis());
		String key = timestamp.toString() + ".csv";
		String fileName = "/tmp/" + key;
		File file = new File(fileName);

		// POPULATE CSV FILE (WITH FILENAME AS @fileName) with DDB DATA

		generateCSV(file, objArray);

		// ADD THE GENERATED CSV FILE TO DESIGNATED S3 BUCKET

		AWSCredentials credentials = new BasicAWSCredentials(S3_ACCESS_KEY, S3_SECRET_KEY);

		AmazonS3 s3client = AmazonS3ClientBuilder.standard()
				.withCredentials(new AWSStaticCredentialsProvider(credentials)).withRegion(Regions.YOUR_REGION).build();

		s3client.putObject(S3_BUCKET_NAME, key, file);

		// CLEAR THE LIST, DELETE THE FILE.
		list.clear();
		filteredList.clear();
		file.delete();

		logger.log("Data exported successfully");
		return null;

	}
}