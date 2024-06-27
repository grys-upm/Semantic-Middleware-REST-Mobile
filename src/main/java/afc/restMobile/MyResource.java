/* Copyright 2018-2021 Universidad Politécnica de Madrid (UPM).
 *
 * Authors:
 *    Mario San Emeterio de la Parte
 *    Vicente Hernández Díaz
 *    Pedro Castillejo Parrilla
 *    José-Fernan Martínez Ortega
 *
 * This software is distributed under a dual-license scheme:
 *
 * - For academic uses: Licensed under GNU Affero General Public License as
 *                      published by the Free Software Foundation, either
 *                      version 3 of the License, or (at your option) any
 *                      later version.
 *
 * - For any other use: Licensed under the Apache License, Version 2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * You can get a copy of the license terms in licenses/LICENSE.
 *
 */
package afc.restMobile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TimeZone;
import java.util.stream.Collectors;

import javax.net.ssl.HttpsURLConnection;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import afc.extractors.AlarmsExtractor;
import afc.extractors.CollarExtractor;
import afc.extractors.ResourcesExtractor;
import afc.util.AssetRegistryManager;
import afc.util.Cache;
import afc.util.GlobalParameters;
import afc.util.JSONComposer;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

/**
 * Root resource (exposed at "getlist" path)
 */
@Path("getList/{as:AS(0)\\d|AS10|AS11|AS12}")
public class MyResource {

	private static final Logger log = Logger.getLogger(MyResource.class);
	private static int sequenceNumber = 1;
	private static String scenario = "";

	/**
	 * Method handling HTTP GET requests. The returned object will be sent to the
	 * client as "text/plain" media type.
	 *
	 * @return String that will be returned as a text/plain response.
	 * @throws com.github.fge.jsonschema.core.exceptions.ProcessingException
	 */
	@POST
	@Consumes({ MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN })
	@Produces({ MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN })
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Successful Operation"),

			@ApiResponse(responseCode = "405", description = "Invalid input: not AFarCloud-compliant"),

			@ApiResponse(responseCode = "415", description = "Invalid input: not a JSON"),

			@ApiResponse(responseCode = "5XX", description = "Unexpected error") })

	public Response getTheList(
			@Parameter(in = ParameterIn.QUERY, description = "The desired time window for data retribution", required = false) @QueryParam("timewindow") int timewindow,
			String input, @Context UriInfo uriInfo) throws ProcessingException, JsonSyntaxException, IOException,
			com.github.fge.jsonschema.core.exceptions.ProcessingException {

		log.debug("Selected time window = " + timewindow);
		long comparativeTime = getWindow(timewindow);
		JsonParser parser = new JsonParser();

		try {
			JsonElement jsonTree = parser.parse(input);
			if (jsonTree.isJsonObject()) {
				try {
					if (!ValidationUtils.isJsonValid(Loader.schema, input)) {
						return Response.status(415).entity(
								"Invalid JSON: The request body should have the following format\n\rExample: { \"authKey\":\"3215465461651651\", \"location\": {\"latitude\":34.43, \"longitude\":23, \"altitude\":4}, \"radius\":4}")
								.build();
					}
				} catch (IOException e) {
					e.printStackTrace();
					return Response.status(415).entity("Invalid JSON: " + e.getMessage()).build();
				}

				JsonObject inputJSON = jsonTree.getAsJsonObject();
				JsonElement authKey = inputJSON.get("authKey");
				for (String id : Loader.devIds) {
					if (authKey.getAsString().equals(id)) {
						try {
							// Extract latitude, longitude and radius fields
							JsonObject location = inputJSON.getAsJsonObject("location");
							float latitude = location.get("latitude").getAsFloat();
							float longitude = location.get("longitude").getAsFloat();
							int radius = inputJSON.get("radius").getAsInt();

							// Extract scenario from URI path parameter
							scenario = uriInfo.getPathParameters().getFirst("as");
							log.debug("Scenario: " + scenario);

							// Point to the right DB scenario
							int port = 9212 + Integer.parseInt(scenario.substring(2, 4));

							// Query sensors
							String sQuery = "http://torcos.etsist.upm.es:" + port
									+ "/getObservationsBySensor/latest/?limit=1&centr_long=" + longitude + "&centr_lat="
									+ latitude + "&radius=" + radius;
							log.debug(sQuery);
							JsonObject sQueryResponse = getResources(sQuery);

							// Query collars
							String cQuery = "http://torcos.etsist.upm.es:" + port
									+ "/getObservationsByCollar/latest/?limit=1&centr_long=" + longitude + "&centr_lat="
									+ latitude + "&radius=" + radius;
							log.debug(cQuery);
							JsonObject cQueryResponse = getResources(cQuery);

							// Build response
							JsonObject response = new JsonObject();
							log.debug("Sensors: " + sQueryResponse.size());
							log.debug("Collars: " + cQueryResponse.size());

							if (sQueryResponse.has("error")) {
								return Response.status(401).entity("Problem detected: " + sQueryResponse).build();
							}

							// Build the response document
							JsonArray sensorList = buildSensorList(sQueryResponse, comparativeTime);
							JsonArray collarList = buildCollarList(cQueryResponse, comparativeTime);

							// Concatenate both arrays (sensorList and collarList)
							if (sensorList != null && collarList != null) {
								Iterator<JsonElement> i = collarList.iterator();
								while (i.hasNext()) {
									JsonElement asset = i.next();
									sensorList.add(asset);
								}
								// sensorList has now both sensors and collars.
								response.add("assetList", sensorList);

							}
							// No collars were found scenario
							else if (sensorList != null && sensorList.size() != 0) {
								response.add("assetList", sensorList);
							}
							// No sensors were found scenario
							else if (collarList != null && collarList.size() != 0) {
								response.add("assetList", collarList);
							}
							// No sensors nor collar were found scenario
							else {
								if (comparativeTime != 0) {
									log.info("No resources were found after time window: " + getDate(comparativeTime));
									throw new WebApplicationException(Response.status(404).entity(
											"No resources were found after time window: " + getDate(comparativeTime))
											.build());
								} else {
									log.info("No resources were found");
									throw new WebApplicationException(
											Response.status(404).entity("No resources were found").build());
								}
							}
							response.addProperty("sequenceNumber", sequenceNumber++);
//			     		log.debug("Response: "+ response);
							return Response.status(200).entity(response.toString()).build();

						} catch (SocketTimeoutException e) {
							// e.printStackTrace();
							return Response.status(405).entity("Timeout when trying to retrieve resources").build();
						} catch (ClassCastException e) {
							e.printStackTrace();
						} catch (IllegalStateException e) {
							e.printStackTrace();
						}
					}
				}
				return Response.status(401).entity("Unauthorized ID").build();
			} else {
				return Response.status(400).entity("Not a JSON").build();
			}
		} catch (JsonSyntaxException e) {
			e.printStackTrace();
			return Response.status(400).entity("Not a JSON").build();
		}
	}

	/** Checking the time window to retrieve assets **/
	public long getWindow(int months) {
		if (months == -1) {
			long window = 0;
			log.debug("Time window deactivated");
			return window;
		} else if (months != 0) {
			Instant instant = Instant.now();
			long timeStampSeconds = instant.getEpochSecond();
			long window = (timeStampSeconds - (months * 30 * 24 * 60 * 60));
			log.debug("Time window selected: " + months + " months, epoch: " + window);
			return window;
		} else {
			Instant instant = Instant.now();
			long timeStampSeconds = instant.getEpochSecond(); // Converting to seconds //
			long window = (timeStampSeconds
					- (Long.parseLong(GlobalParameters.getInstance().getParameter(GlobalParameters.DEFAULT_TIME_WINDOW))
							* 24 * 60 * 60));
			log.debug("Default window 30 days, epoch: " + window);
			return window;
		}
	}

	/** Convert date in "yyyy-MM-dd HH:mm:ss" format to epochtime in millis **/
	public long transform_date(String date) {
		String myDate = date.toString().replace("T", " ").replace("Z", "").replace("\"", "");
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date dateC;
		long epoch = 0;
		try {
			dateC = dateFormat.parse(myDate);
			epoch = dateC.getTime() / 1000L;
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return epoch;
	}

	/** Convert Epochtime in seconds to date format **/
	public String getDate(long window) {
		// convert seconds to milliseconds
		Date date = new Date(window * 1000L);
		// format of the date
		SimpleDateFormat jdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
		jdf.setTimeZone(TimeZone.getTimeZone("GMT+2"));
		String java_date = jdf.format(date);
		System.out.println("\n" + java_date + "\n");
		return java_date;
	}

	/**
	 * Method for querying the DataBase and obtaining the resource list (collars or
	 * sensors depending on the URI)
	 **/
	public JsonObject getResources(String query) throws IOException, SocketTimeoutException {
		try {

			URL uri = new URL(query);
			HttpURLConnection conn = (HttpURLConnection) uri.openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Content-Type", "application/json");

			// Timeout de 1 min
			conn.setConnectTimeout(60000);

			int code = conn.getResponseCode();
			if (code != HttpURLConnection.HTTP_OK) {
				throw new RuntimeException(Integer.toString(code));
			}
			BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
			String response = br.lines().collect(Collectors.joining());
			JsonParser parser = new JsonParser();
			JsonElement responseJSON = parser.parse(response);
			JsonObject queryResponse = responseJSON.getAsJsonObject();

			while ((response = br.readLine()) != null) {

			}
			conn.disconnect();
			return queryResponse;
		}
//	     	This catch is implemented because otherwise WebApplicationExceptions are treated as RuntimeExceptions,
//			and processed as such.
		catch (WebApplicationException e) {
			throw new WebApplicationException(Response.status(500).entity(e.getMessage()).build());
		} catch (SocketTimeoutException e) {
			log.error("Could not connect to DAM: Timeout Exception");
			throw new SocketTimeoutException("Timeout");
		} catch (MalformedURLException e) {
			log.error("Could not connect to DAM: " + e.getMessage());
			throw new MalformedURLException("MalformedURL");
		} catch (RuntimeException e) {
			log.error("Could not connect to DAM: " + e.getMessage());
			throw new WebApplicationException(Response.status(500).entity("ERROR: Could not connect to DAM").build());
		}
	}

	/**
	 * Method for querying the DataBase and obtaining the alarms list associated to
	 * a specific deviceId
	 **/
	public JsonArray getAlarms(String query) throws IOException {
		try {

			URL uri = new URL(query);
			HttpsURLConnection conn = (HttpsURLConnection) uri.openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Content-Type", "application/json");
			int code = conn.getResponseCode();
			if (code != HttpsURLConnection.HTTP_OK && code != HttpsURLConnection.HTTP_NOT_FOUND) {
				throw new RuntimeException(Integer.toString(code));
			} else if (code == HttpsURLConnection.HTTP_NOT_FOUND) {
				log.debug("No alarms detected. Code: " + code);
				return null;
			} else {
				BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
				String response = br.lines().collect(Collectors.joining());

				JsonParser parser = new JsonParser();
				JsonElement responseJSON = parser.parse(response);
				JsonArray queryResponse = responseJSON.getAsJsonArray();

				conn.disconnect();
				// Store object in cache
				return queryResponse;
			}
		}
		// This catch is implemented because otherwise WebApplicationExceptions are
		// treated as RuntimeExceptions,
		// and processed as such.
		catch (WebApplicationException e) {
			throw new WebApplicationException(Response.status(500).entity(e.getMessage()).build());
		} catch (MalformedURLException e) {
			log.error("Could not connect to AP&R: " + e.getMessage());
			throw new MalformedURLException("MalformedURL");
		} catch (RuntimeException e) {
			log.error("Could not connect to AP&R: " + e.getMessage());
			throw new WebApplicationException(Response.status(500).entity("ERROR: Could not connect to AP&R").build());
		}
	}

	public JsonArray buildCollarList(JsonObject queryResponse, long comparativeTime) {

//			Collars array
		JsonArray collarList = new JsonArray();
//			Extract the fields
		JsonObject results = queryResponse.get("results").getAsJsonObject();
		if (results.size() == 0) {
			log.warn("No results were found for collars");
			return null;
		}
		JsonArray resources = results.get("resources").getAsJsonArray();

		HashMap<String, JsonArray> alarmsList = new HashMap<String, JsonArray>();
		HashMap<String, JsonObject> resourcesList = new HashMap<String, JsonObject>();

		try {
			alarmsList = AlarmsExtractor.extractAlarms(resources, comparativeTime, scenario);
			resourcesList = CollarExtractor.extractResources(resources, comparativeTime, scenario);
			collarList = JSONComposer.composeCollarJson(resourcesList, alarmsList);
		} catch (IOException e) {
			log.error("Collar-List - An error occurred: " + e);
		}
		return collarList;
	}

	/**
	 * Method to build the assetList array from the alarms query (includes both
	 * Alarms associated with a specific deviceId)
	 **/
	public JsonArray buildAlarmList(JsonArray queryResponse, long comparativeTime) {
//			Resources array
		JsonArray alarmList = new JsonArray();

//			Extract the fields
		Iterator<JsonElement> i = queryResponse.iterator();
		while (i.hasNext()) {
			JsonObject alarm = i.next().getAsJsonObject();
			if (alarm.get("status").getAsInt() != 2
					&& Long.parseLong(alarm.get("alarmTime").toString()) >= comparativeTime) {

				JsonObject alarmd = new JsonObject();
				alarmd.add("alarmCode", alarm.get("alarmCode"));
				alarmd.add("sequenceNumber", alarm.get("sequenceNumber"));
				alarmd.add("message", alarm.get("message"));
				alarmd.add("source", alarm.get("source"));
				alarmd.add("priority", alarm.get("priority"));
				alarmd.add("alarmTime", alarm.get("alarmTime"));
				alarmd.add("status", alarm.get("status"));
				alarmList.add(alarmd);
			}
		}

		return alarmList;
	}

	/**
	 * Method to build the assetList array from the alarms query (includes only one
	 * alarm associated with a specific deviceId)
	 **/
	public JsonObject buildAlarm(JsonArray queryResponse, long comparativeTime) {

		if (queryResponse.size() == 1) {
//			Extract the fields
			Iterator<JsonElement> i = queryResponse.iterator();
			JsonObject alarmd = new JsonObject();
			while (i.hasNext()) {
				JsonObject alarm = i.next().getAsJsonObject();
				if (alarm.get("status").getAsInt() != 2
						&& Long.parseLong(alarm.get("alarmTime").toString()) >= comparativeTime) {

					alarmd.add("alarmCode", alarm.get("alarmCode"));
					alarmd.add("sequenceNumber", alarm.get("sequenceNumber"));
					alarmd.add("message", alarm.get("message"));
					alarmd.add("source", alarm.get("source"));
					alarmd.add("priority", alarm.get("priority"));
					alarmd.add("alarmTime", alarm.get("alarmTime"));
					alarmd.add("status", alarm.get("status"));

					return alarmd;
				} else {
					log.debug("Alarm have status = 2");
					return null;
				}
			}
		} else {
			log.warn("No results were found for alarms in buildAlarm java method");
		}
		return null;
	}

	/**
	 * Method to build the assetList array from the sensors query (includes both
	 * Sensors and Gateways)
	 **/
	public JsonArray buildSensorList(JsonObject queryResponse, long comparativeTime) {

//			Resources array
		JsonArray assetList = new JsonArray();
//			Extract the fields
		JsonObject results = queryResponse.get("results").getAsJsonObject();
		if (results.size() == 0) {
			log.warn("No results were found for sensors");
			return null;
		}
//			Search for gateways
		JsonElement devicesElement = results.get("devices");
		if (!(devicesElement == null) && devicesElement.isJsonArray()) {
			assetList = getGateways(devicesElement.getAsJsonArray(), assetList, comparativeTime);
		}
//          Search for sensors
		JsonElement resourcesElement = results.get("resources");
		if (!(resourcesElement == null) && resourcesElement.isJsonArray()) {
			assetList = getSensors(resourcesElement.getAsJsonArray(), assetList, comparativeTime);
		}

		return assetList;
	}

	public JsonArray getSensors(JsonArray resources, JsonArray assetList, long comparativeTime) {

//			    String type = "GW";
		log.debug("Data type: Sensors");

		HashMap<String, JsonArray> alarmsList = new HashMap<String, JsonArray>();
		HashMap<String, JsonObject> resourcesList = new HashMap<String, JsonObject>();

		try {
			alarmsList = AlarmsExtractor.extractAlarms(resources, comparativeTime, scenario);
			resourcesList = ResourcesExtractor.extractResources(resources, comparativeTime, scenario);
			assetList = JSONComposer.composeJson(resourcesList, alarmsList);
		} catch (IOException e) {
			log.error("Resources List - An error occurred: " + e);
		}
		return assetList;
	}

	public JsonArray getGateways(JsonArray devices, JsonArray assetList, long comparativeTime) {
		String type = null;
		String service = null;
//		This String is "hardcoded" since this information is nowhere to be found in the query
		String typeC = "GW";
		log.debug("Data type: " + typeC);
		Iterator<JsonElement> i = devices.iterator();
		while (i.hasNext()) {
//					    An array of sensors of each Gateway
			JsonArray resourceList = new JsonArray();

			JsonObject device = i.next().getAsJsonObject();
			JsonObject location = new JsonObject();
			String deviceId = device.get("device").getAsString();
			JsonArray resources = new JsonArray();
			if (device.get("resources").isJsonArray()) {
				resources = device.get("resources").getAsJsonArray();
			} else
				continue;
			Iterator<JsonElement> j = resources.iterator();
			while (j.hasNext()) {
				String resourceType = null;
				JsonObject resource = j.next().getAsJsonObject();
				JsonElement resourceId = resource.get("resource");

				log.debug("Measurements array size (single vs multi-param sensor evaluation): "
						+ resource.get("measurements").getAsJsonArray().size());

//						Single-parameter sensor case (the output data format differs from multi-parameter case)
				if (resource.get("measurements").getAsJsonArray().size() == 1) {
					JsonObject measurement = resource.get("measurements").getAsJsonArray().get(0).getAsJsonObject();
					String observedProperty = measurement.get("measurement").getAsString();
					JsonObject observation0 = measurement.get("observations").getAsJsonArray().get(0).getAsJsonObject();
					JsonElement time = observation0.get("time");

					@SuppressWarnings("unchecked")
					Cache<String, Integer> cache = Cache.getResCache(
							Long.parseLong(
									GlobalParameters.getInstance().getParameter(GlobalParameters.timeToLiveProp)),
							Long.parseLong(
									GlobalParameters.getInstance().getParameter(GlobalParameters.cacheTimerProp)),
							Integer.parseInt(
									GlobalParameters.getInstance().getParameter(GlobalParameters.maxItemProp)));
					String result = cache.get(deviceId);
					if (result == null) {
						String url = "https://storage" + scenario.substring(2, 4)
								+ "-afarcloud.qa.pdmfc.com/storage/rest/registry/getSensor/" + deviceId;
						log.debug(url);
						// Query to AssetRegistry in order to obtain Service and Type parameters
						// associated with the specific device
						String resourceUrn = null;
						try {
							resourceUrn = AssetRegistryManager.getResourceInfo(url);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						if (resourceUrn != null && !resourceUrn.isEmpty()) {
							int indexType = resourceUrn.indexOf(":", 14);
							int iType = resourceUrn.indexOf(":", indexType + 1);
							service = resourceUrn.substring(14, indexType);
							type = resourceUrn.substring(iType + 1, resourceUrn.indexOf(":", iType + 1));
							log.debug("Service: " + service + ", type: " + type);
							cache.put(deviceId, service + ":" + type);
							result = service + ":" + type;
						}
					} else {
						service = result.substring(0, result.indexOf(":"));
						type = result.substring(result.indexOf(":") + 1);
						log.debug("-Cached- Service: " + service + ", type: " + type);
						cache.remove(deviceId);
						cache.put(deviceId, service + ":" + type);
					}

//							Checking if asset is under the time window hood
					long timeS = transform_date(time.toString());

					if (timeS >= comparativeTime && service != null && !service.isEmpty() && type != null
							&& !type.isEmpty()) {
						log.debug("-Gateway Single sensor- Collectiong data under time window. Data point time: "
								+ timeS + " > " + comparativeTime);
						JsonElement uom = observation0.get("uom");
						JsonElement value = observation0.get("value");
						resourceType = observation0.get("type").getAsString();
						// Build location object
						location.add("latitude", observation0.get("latitude"));
						location.add("longitude", observation0.get("longitude"));
						location.add("altitude", observation0.get("altitude"));
						JsonObject asset = new JsonObject();
						asset.add("deviceId", resourceId);
						asset.addProperty("type", resourceType);
						asset.addProperty("observedProperty", observedProperty);
						asset.add("time", time);
						asset.add("value", value);
						asset.add("uom", uom);
						resourceList.add(asset);
					} else {
						log.debug("Too old data regarding gateway single measure");
					}
				}

//						Multi-parameter sensor case (the output data format differs from single-parameter case)
				else {

					Iterator<JsonElement> k = resource.get("measurements").getAsJsonArray().iterator();
//					    Array for storing the observations
					JsonArray observations = new JsonArray();
					while (k.hasNext()) {
						JsonObject measurement = k.next().getAsJsonObject();
						String observedProperty = measurement.get("measurement").getAsString();
						JsonObject observation0 = measurement.get("observations").getAsJsonArray().get(0)
								.getAsJsonObject();
						JsonElement time = observation0.get("time");

						@SuppressWarnings("unchecked")
						Cache<String, Integer> cache = Cache.getResCache(
								Long.parseLong(
										GlobalParameters.getInstance().getParameter(GlobalParameters.timeToLiveProp)),
								Long.parseLong(
										GlobalParameters.getInstance().getParameter(GlobalParameters.cacheTimerProp)),
								Integer.parseInt(
										GlobalParameters.getInstance().getParameter(GlobalParameters.maxItemProp)));
						String result = cache.get(deviceId);
						if (result == null) {
							String url = "https://storage" + scenario.substring(2, 4)
									+ "-afarcloud.qa.pdmfc.com/storage/rest/registry/getSensor/" + deviceId;
							log.debug(url);
							// Query to AssetRegistry in order to obtain Service and Type parameters
							// associated with the specific device
							String resourceUrn = null;
							try {
								resourceUrn = AssetRegistryManager.getResourceInfo(url);
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							if (resourceUrn != null && !resourceUrn.isEmpty()) {
								int indexType = resourceUrn.indexOf(":", 14);
								int iType = resourceUrn.indexOf(":", indexType + 1);
								service = resourceUrn.substring(14, indexType);
								type = resourceUrn.substring(iType + 1, resourceUrn.indexOf(":", iType + 1));
								log.debug("Service: " + service + ", type: " + type);
								cache.put(deviceId, service + ":" + type);
								result = service + ":" + type;
							}
						} else {
							service = result.substring(0, result.indexOf(":"));
							type = result.substring(result.indexOf(":") + 1);
							log.debug("-Cached- Service: " + service + ", type: " + type);
							cache.remove(deviceId);
							cache.put(deviceId, service + ":" + type);
						}
//					 		Checking if asset is under the time window hood
						long timeS = transform_date(time.toString());

						if (timeS >= comparativeTime && service != null && !service.isEmpty() && type != null
								&& !type.isEmpty()) {
							log.debug("-Gateways Multi Sensor- Collectiong data under time window. Data point time: "
									+ timeS + " > " + comparativeTime);
							JsonElement uom = observation0.get("uom");
							JsonElement value = observation0.get("value");

//								Extract the "type" and "location" fields at the last iteration, because they are repeated in the queryResponse in every measurement.
							if (!k.hasNext()) {
								resourceType = observation0.get("type").getAsString();
//									Build location object
								location.add("latitude", observation0.get("latitude"));
								location.add("longitude", observation0.get("longitude"));
								location.add("altitude", observation0.get("altitude"));

							}
							JsonObject observation = new JsonObject();
							observation.addProperty("observedProperty", observedProperty);
							observation.add("time", time);
							observation.add("uom", uom);
							observation.add("value", value);
//								Add object to observations array
							observations.add(observation);
						} else {
							log.debug("Too old data regarding gateways multi measures");
						}
					}
					if (!observations.isJsonNull() && observations.size() != 0) {
						JsonObject asset = new JsonObject();
						asset.add("deviceId", resourceId);
						asset.addProperty("type", resourceType);
						asset.add("observations", observations);
						resourceList.add(asset);
					}
				}
			}
			if (!resourceList.isJsonNull()) {
				JsonObject gateway = new JsonObject();
				gateway.add("location", location);
				gateway.addProperty("deviceId", deviceId);
				gateway.addProperty("type", type);
				gateway.add("resources", resourceList);
				assetList.add(gateway);
			}
		}
		return assetList;
	}
}
