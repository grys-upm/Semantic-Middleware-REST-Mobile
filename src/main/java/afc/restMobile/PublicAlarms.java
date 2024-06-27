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

import java.net.URI;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.log4j.Logger;
import org.glassfish.grizzly.http.server.Request;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import afc.util.AssetRegistryManager;
import afc.util.Cache;
import afc.util.GlobalParameters;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

/**
 * Root resource (exposed at "getlist" path)
 */
@Path("alarms/{as:AS(0)\\d|AS10|AS11|AS12}")
public class PublicAlarms {

	public static final URI docsUri = URI.create(Main.BASE_URI + "docs/");
	private static final Logger log = Logger.getLogger(PublicAlarms.class);
	protected final Response invalidJsonException = Response.status(405).entity(
			"405: \"Invalid input: not AFarCloud-compliant\". For more information, please refer to the API documentation: "
					+ docsUri)
			.build();
	private static String scenario = "";

	private String getRemoteAddress(Request request) {
		String ipAddress = request.getHeader("X-FORWARDED-FOR");
		if (ipAddress == null) {
			ipAddress = request.getRemoteAddr();
		}
		return ipAddress;
	}

	@Path("/postAlarm")
	@POST
	@Consumes({ "application/json" })
	@Operation(summary = "Add a new alarm/s to the Data Base", description = "", tags = { "Alarms" })
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Successful Operation"),

			@ApiResponse(responseCode = "405", description = "Invalid input: not AFarCloud-compliant"),

			@ApiResponse(responseCode = "415", description = "Invalid input: not a JSON"),

			@ApiResponse(responseCode = "5XX", description = "Unexpected error") })

	public Response addAlarm(String s, @Context UriInfo uriInfo, @Context Request request)
			throws Exception, WebApplicationException {

		try {
			if (ValidationUtils.isJsonValid(Loader.alarmSchema, s)) {

//	     		Extract scenario from URI path parameter
				scenario = uriInfo.getPathParameters().getFirst("as");
				log.debug("Scenario: " + scenario + ", Request IP: " + getRemoteAddress(request));
				JsonParser parser = new JsonParser();

				try {
					JsonElement jsonAlarm = parser.parse(s);
					if (jsonAlarm.isJsonObject()) {
						JsonObject inputJSON = jsonAlarm.getAsJsonObject();
						JsonElement entityName = inputJSON.get("resourceId");
						String entity = entityName.getAsString().replace("\"", "");
						String service = null;
						String type = null;

						@SuppressWarnings("unchecked")
						Cache<String, Integer> cache = Cache.getResCache(
								Long.parseLong(
										GlobalParameters.getInstance().getParameter(GlobalParameters.timeToLiveProp)),
								Long.parseLong(
										GlobalParameters.getInstance().getParameter(GlobalParameters.cacheTimerProp)),
								Integer.parseInt(
										GlobalParameters.getInstance().getParameter(GlobalParameters.maxItemProp)));
						String result = cache.get(entity);
						if (result == null || result.isEmpty()) {
							/* ... */
							String url = "https://storage" + scenario.substring(2, 4)
									+ "-afarcloud.qa.pdmfc.com/storage/rest/registry/getSensor/" + entity;
							log.debug("Looking for sensors: " + url);
							// Query to AssetRegistry in order to obtain Service and Type parameters
							// associated with the specific device
							String resourceUrn = null;
							resourceUrn = AssetRegistryManager.getResourceInfo(url);
							if (resourceUrn != null && !resourceUrn.isEmpty()) {
								int indexType = resourceUrn.indexOf(":", 14);
								int iType = resourceUrn.indexOf(":", indexType + 1);
								service = resourceUrn.substring(14, indexType);
								type = resourceUrn.substring(iType + 1, resourceUrn.indexOf(":", iType + 1));
								log.debug("Service: " + service + ", type: " + type);
								cache.put(entity, service + ":" + type);
							} else {
								url = "https://storage" + scenario.substring(2, 4)
										+ "-afarcloud.qa.pdmfc.com/storage/rest/registry/getCollarByResourceId/"
										+ entity;
								log.debug("Looking for collars: " + url);
								resourceUrn = null;
								resourceUrn = AssetRegistryManager.getResourceInfo(url);
								if (resourceUrn != null && !resourceUrn.isEmpty()) {
									int indexType = resourceUrn.indexOf(":", 14);
									service = resourceUrn.substring(14, indexType);
									type = "collar";
									log.debug("Service: " + service + ", type: " + type);
									cache.put(entity, service + ":" + type);
								} else {
									return Response.status(404).entity(
											"404: \"Resource not found\". The resource to which the alarm is associated is not found over the AFarCloud repository.")
											.build();
								}
							}
						} else {
							service = result.substring(0, result.indexOf(":"));
							type = result.substring(result.indexOf(":") + 1);
							log.debug("-Cached- Service: " + service + ", type: " + type);
							cache.remove(entity);
							cache.put(entity, service + ":" + type);
						}

						MQTTClient client = new MQTTClient();
						client.sendTelemetry(s,
								"afc/" + scenario.toUpperCase() + "/" + service + "/" + type + "/" + entity + "/alarm");
						return Response.status(200).entity(
								"200: \"Successful operation\". \nFor more information, please refer to the API documentation: "
										+ docsUri + "\nRequest ID: " + request.getSession().getIdInternal())
								.build();
					} else {
						return Response.status(401).entity("Alarm is not compliant").build();
					}
				} catch (JsonSyntaxException e) {
					e.printStackTrace();
					return Response.status(400).entity("Not a JSON").build();
				}
			}
		} catch (Exception e) {
			log.error("AP&R unreachable" + "\nError: " + e);
			return Response.status(405).entity("AP&R unreachable" + "\nError: " + e).build();
		}
		return invalidJsonException;

		/**
		 * REST version
		 * 
		 * // Inject Alarm URL url = new
		 * URL("https://storage"+scenario.substring(2,4)+"-afarcloud.qa.pdmfc.com/storage/rest/dq/addAlarm");
		 * URLConnection con = url.openConnection(); HttpsURLConnection http =
		 * (HttpsURLConnection)con; http.setRequestMethod("POST"); // PUT is another
		 * valid option http.setDoOutput(true);
		 * 
		 * //Timeout de 1 min con.setConnectTimeout(60000);
		 * 
		 * byte[] out = s.getBytes(StandardCharsets.UTF_8); int length = out.length;
		 * 
		 * http.setFixedLengthStreamingMode(length);
		 * http.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
		 * http.connect(); try(OutputStream os = http.getOutputStream()) {
		 * os.write(out); }
		 * 
		 * log.debug("SessionID: "+request.getSession().getIdInternal()+"; IP: " +
		 * getRemoteAddress(request) + " Alarm injected: \n"+s);
		 * 
		 * http.disconnect(); return Response.status(200).entity("200: \"Successful
		 * operation\". \nFor more information, please refer to the API documentation:
		 * "+ docsUri +"\nRequest ID: "+request.getSession().getIdInternal()).build();
		 * 
		 * }else { // System.out.println(ValidationUtils.isJsonValid(Loader.alarmSchema,
		 * s)); log.warn( "SessionID: "+request.getSession().getIdInternal()+" IP: "+
		 * getRemoteAddress(request)+" Invalid Json Exception, the alarm is not valid
		 * against schemas; \nAlarm: "+s); return Response.status(405).entity("Invalid
		 * Json Exception, the alarm is not valid against schemas; \n * Remember that
		 * the \"status\" parameter will only be accepted when it takes a value between
		 * 0 and 2. Likewise, the value associated with the \"priority\" parameter must
		 * be one of the following: \"low\", \"medium\" or \" high \".\r\n" +
		 * "").build(); } }catch (Exception e) { log.error("AP&R unreachable"+"\nError:
		 * "+e); return Response.status(405).entity("AP&R unreachable"+"\nError:
		 * "+e).build(); }
		 **/
	}

	@Path("/updateAlarm/{resourceId}")
	@PUT
	@Consumes({ "application/json" })
	@Operation(summary = "Updates the alarm with the specific deviceId with the new status", description = "", tags = {
			"Alarms" })
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Successful Operation"),

			@ApiResponse(responseCode = "405", description = "Invalid input: not AFarCloud-compliant"),

			@ApiResponse(responseCode = "415", description = "Invalid input: not a JSON"),

			@ApiResponse(responseCode = "5XX", description = "Unexpected error") })

	public Response updateAlarm(

			@Parameter(in = ParameterIn.PATH, description = "ResourceId of the device associated with the alarm.", required = true) @PathParam("resourceId") String resourceId,
			@Parameter(in = ParameterIn.QUERY, description = "The integer for the epochtime when the alarm was generated", required = true) @QueryParam("alarmtime") String alarmTime,
			@Parameter(in = ParameterIn.QUERY, description = "the status of the alarm", required = true) @QueryParam("status") String status,

			@Context UriInfo uriInfo, @Context Request request) throws Exception {

		try {

//		     		Extract scenario from URI path parameter
			scenario = uriInfo.getPathParameters().getFirst("as");
			log.debug("Scenario: " + scenario + ", Request IP: " + getRemoteAddress(request));

			try {
				if (resourceId != null && !resourceId.isEmpty() && alarmTime != null && !alarmTime.isEmpty()
						&& status != null && !status.isEmpty()) {

					String service = null;
					String type = null;

					@SuppressWarnings("unchecked")
					Cache<String, Integer> cache = Cache.getResCache(
							Long.parseLong(
									GlobalParameters.getInstance().getParameter(GlobalParameters.timeToLiveProp)),
							Long.parseLong(
									GlobalParameters.getInstance().getParameter(GlobalParameters.cacheTimerProp)),
							Integer.parseInt(
									GlobalParameters.getInstance().getParameter(GlobalParameters.maxItemProp)));
					String result = cache.get(resourceId);
					if (result == null || result.isEmpty()) {
						/* ... */
						String url = "https://storage" + scenario.substring(2, 4)
								+ "-afarcloud.qa.pdmfc.com/storage/rest/registry/getSensor/" + resourceId;
						log.debug("Looking for sensors" + url);
						// Query to AssetRegistry in order to obtain Service and Type parameters
						// associated with the specific device
						String resourceUrn = null;
						resourceUrn = AssetRegistryManager.getResourceInfo(url);
						if (resourceUrn != null && !resourceUrn.isEmpty()) {
							int indexType = resourceUrn.indexOf(":", 14);
							int iType = resourceUrn.indexOf(":", indexType + 1);
							service = resourceUrn.substring(14, indexType);
							type = resourceUrn.substring(iType + 1, resourceUrn.indexOf(":", iType + 1));
							log.debug("Service: " + service + ", type: " + type);
							cache.put(resourceId, service + ":" + type);
						} else {
							url = "https://storage" + scenario.substring(2, 4)
									+ "-afarcloud.qa.pdmfc.com/storage/rest/registry/getCollarByResourceId/"
									+ resourceId;
							log.debug("Looking for collars: " + url);
							resourceUrn = null;
							resourceUrn = AssetRegistryManager.getResourceInfo(url);
							if (resourceUrn != null && !resourceUrn.isEmpty()) {
								int indexType = resourceUrn.indexOf(":", 14);
								service = resourceUrn.substring(14, indexType);
								type = "collar";
								log.debug("Service: " + service + ", type: " + type);
								cache.put(resourceId, service + ":" + type);
							} else {
								return Response.status(404).entity(
										"404: \"Resource not found\". The resource to which the alarm is associated is not found over the AFarCloud repository.")
										.build();
							}
						}
					} else {
						service = result.substring(0, result.indexOf(":"));
						type = result.substring(result.indexOf(":") + 1);
						log.debug("-Cached- Service: " + service + ", type: " + type);
						cache.remove(resourceId);
						cache.put(resourceId, service + ":" + type);
					}

					MQTTClient client = new MQTTClient();
					client.sendTelemetry(generateJSONUpdate(resourceId, alarmTime, status), "afc/"
							+ scenario.toUpperCase() + "/" + service + "/" + type + "/" + resourceId + "/updatealarm");
					return Response.status(200).entity(
							"200: \"Successful operation\". \nFor more information, please refer to the API documentation: "
									+ docsUri + "\nRequest ID: " + request.getSession().getIdInternal())
							.build();
				} else {
					return Response.status(401).entity("Alarm is not compliant").build();
				}
			} catch (JsonSyntaxException e) {
				e.printStackTrace();
				return Response.status(400).entity("Not a JSON").build();
			}
		} catch (Exception e) {
			log.error("AP&R unreachable" + "\nError: " + e);
			return Response.status(405).entity("AP&R unreachable" + "\nError: " + e).build();
		}

		/**
		 * REST version // Inject Alarm URL url = new
		 * URL("https://storage"+scenario.substring(2,4)+"-afarcloud.qa.pdmfc.com/storage/rest/dq/updateAlarmStatus/"+resourceId+"?alarmtime="+alarmTime+"&status="+status);
		 * HttpsURLConnection httpCon = (HttpsURLConnection) url.openConnection();
		 * httpCon.setDoOutput(true); httpCon.setRequestMethod("PUT"); //Timeout de 1
		 * min httpCon.setConnectTimeout(60000);
		 * 
		 * OutputStreamWriter out = new OutputStreamWriter( httpCon.getOutputStream());
		 * 
		 * 
		 * out.write("Resource content"); out.close(); httpCon.getInputStream();
		 * 
		 * // try(OutputStream os = http.getOutputStream()) { // os.write(out); // }
		 * 
		 * log.debug(httpCon.getResponseCode() + " Request ID:
		 * "+request.getSession().getIdInternal() +"; IP: "+ getRemoteAddress(request)+
		 * " 200: \"Successful operation\"");
		 * 
		 * httpCon.disconnect(); return Response.status(200).entity("200: \"Successful
		 * operation\". \nFor more information, please refer to the API documentation:
		 * "+ docsUri +"\nRequest ID: "+request.getSession().getIdInternal()).build();
		 * 
		 * }catch (Exception e) { log.error("AP&R unreachable"+"\nError: "+e); return
		 * Response.status(405).entity("AP&R unreachable"+"\nError: "+e).build(); }
		 * 
		 **/
	}

	/** Generate a Json to update alarms via MQTT **/
	public String generateJSONUpdate(String resourceId, String alarmTime, String status) {
		String updateAlarm = "";
		updateAlarm = "{\r\n" + "\r\n" + "        \"resourceId\":\"" + resourceId + "\",\r\n"
				+ "        \"sequenceNumber\": " + Main.counter.incrementAndGet() + ",\r\n" + "        \"alarmTime\": "
				+ alarmTime + ",\r\n" + "        \"status\": " + status + "\r\n" + "}";
		log.debug("Updating alarm with JSON: " + updateAlarm);
		return updateAlarm;
	}

}
