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
package afc.extractors;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ResourcesExtractor {

	private static final Logger log = Logger.getLogger(ResourcesExtractor.class);

	public static HashMap<String, JsonObject> extractResources(JsonArray resources, long comparativeTime,
			String scenario) {

		String type = null;
		String service = null;
		HashMap<String, JsonObject> resourcesList = new HashMap<String, JsonObject>();

		Iterator<JsonElement> i = resources.iterator();
		while (i.hasNext()) {
			JsonObject resource = i.next().getAsJsonObject();
			JsonObject location = new JsonObject();
			String deviceId = resource.get("resource").getAsString();

			log.debug("Measurements array size (single vs multi-param sensor evaluation): "
					+ resource.get("measurements").getAsJsonArray().size());

			/**
			 * Single-parameter sensor case (the output data format differs from
			 * multi-parameter case)
			 **/
			if (resource.get("measurements").getAsJsonArray().size() == 1) {
				JsonArray observations = new JsonArray();
				JsonObject measurement = resource.get("measurements").getAsJsonArray().get(0).getAsJsonObject();
				String observedProperty = measurement.get("measurement").getAsString();
				JsonObject observation0 = measurement.get("observations").getAsJsonArray().get(0).getAsJsonObject();
				JsonElement time = observation0.get("time");
				service = observation0.get("service").toString().replace("\"", "");
				type = observation0.get("type").toString().replace("\"", "");

				/** Sensor matching with AR or cache **/
				/**
				 * @SuppressWarnings("unchecked") Cache<String, Integer> cache =
				 * Cache.getResCache(Long.parseLong(GlobalParameters.getInstance().getParameter(GlobalParameters.timeToLiveProp)),
				 * Long.parseLong(GlobalParameters.getInstance().getParameter(GlobalParameters.cacheTimerProp)),
				 * Integer.parseInt(GlobalParameters.getInstance().getParameter(GlobalParameters.maxItemProp)));
				 * String result = cache.get(deviceId); if(result == null) { String url =
				 * "https://storage"+scenario.substring(2,4)+"-afarcloud.qa.pdmfc.com/storage/rest/registry/getSensor/"+deviceId;
				 * log.debug(url); // Query to AssetRegistry in order to obtain Service and Type
				 * parameters associated with the specific device String resourceUrn = null; try
				 * { resourceUrn = AssetRegistryManager.getResourceInfo(url); } catch
				 * (IOException e) { // TODO Auto-generated catch block e.printStackTrace(); }
				 * if(resourceUrn!=null && !resourceUrn.isEmpty()) { int indexType =
				 * resourceUrn.indexOf(":", 14); int iType = resourceUrn.indexOf(":",
				 * indexType+1); service = resourceUrn.substring(14, indexType); type =
				 * resourceUrn.substring(iType+1, resourceUrn.indexOf(":", iType+1));
				 * log.debug("Service: "+service+", type: "+type); cache.put(deviceId,
				 * service+":"+type); result = service+":"+type; } }else { service =
				 * result.substring(0, result.indexOf(":")); type =
				 * result.substring(result.indexOf(":")+1); log.debug("-Cached- Service:
				 * "+service+", type: "+type); cache.remove(deviceId); cache.put(deviceId,
				 * service+":"+type); }
				 **/

//				Checking if asset is under the time window hood
				long timeS = transform_date(time.toString());
				if (timeS >= comparativeTime && service != null && !service.isEmpty() && type != null
						&& !type.isEmpty()) {
					log.debug("-Single sensor- Collectiong data under time window. Data point time: " + timeS + " > "
							+ comparativeTime);
					JsonElement uom = observation0.get("uom");
					JsonElement value = observation0.get("value");
					location.add("latitude", observation0.get("latitude"));
					location.add("longitude", observation0.get("longitude"));
					location.add("altitude", observation0.get("altitude"));
					JsonObject asset = new JsonObject();
					asset.addProperty("deviceId", deviceId);
					asset.addProperty("type", type);
					asset.add("location", location);
					JsonObject observation = new JsonObject();
					observation.addProperty("observedProperty", observedProperty);
					observation.add("time", time);
					observation.add("uom", uom);
					observation.add("value", value);
					observations.add(observation);
					asset.add("observations", observations);

//					Add Aseet to resourcesList
					resourcesList.put(deviceId, asset);

				} else {
					log.debug("Too old data regarding single measures");
				}
			}

			/**
			 * Multi-parameter sensor case (the output data format differs from
			 * single-parameter case)
			 **/
			else {
				Iterator<JsonElement> j = resource.get("measurements").getAsJsonArray().iterator();
//				Array for storing the observations
				JsonArray observations = new JsonArray();
				int typeIteration = 0;
				while (j.hasNext()) {
					JsonObject measurement = j.next().getAsJsonObject();
					String observedProperty = measurement.get("measurement").getAsString();
					JsonObject observation0 = measurement.get("observations").getAsJsonArray().get(0).getAsJsonObject();
					JsonElement time = observation0.get("time");
					service = observation0.get("service").toString().replace("\"", "");
					type = observation0.get("type").toString().replace("\"", "");
//					service = observation0.get("service").getAsString();

					/** Sensor matching with AR or cache **/
					/**
					 * @SuppressWarnings("unchecked") Cache<String, Integer> cache =
					 * Cache.getResCache(Long.parseLong(GlobalParameters.getInstance().getParameter(GlobalParameters.timeToLiveProp)),
					 * Long.parseLong(GlobalParameters.getInstance().getParameter(GlobalParameters.cacheTimerProp)),
					 * Integer.parseInt(GlobalParameters.getInstance().getParameter(GlobalParameters.maxItemProp)));
					 * String result = cache.get(deviceId); if(result == null) { String url =
					 * "https://storage"+scenario.substring(2,4)+"-afarcloud.qa.pdmfc.com/storage/rest/registry/getSensor/"+deviceId;
					 * log.debug(url); // Query to AssetRegistry in order to obtain Service and Type
					 * parameters associated with the specific device String resourceUrn = null; try
					 * { resourceUrn = AssetRegistryManager.getResourceInfo(url); } catch
					 * (IOException e) { // TODO Auto-generated catch block e.printStackTrace(); }
					 * if(resourceUrn!=null && !resourceUrn.isEmpty()) { int indexType =
					 * resourceUrn.indexOf(":", 14); int iType = resourceUrn.indexOf(":",
					 * indexType+1); service = resourceUrn.substring(14, indexType); type =
					 * resourceUrn.substring(iType+1, resourceUrn.indexOf(":", iType+1));
					 * log.debug("Service: "+service+", type: "+type); cache.put(deviceId,
					 * service+":"+type); result = service+":"+type; } }else { service =
					 * result.substring(0, result.indexOf(":")); type =
					 * result.substring(result.indexOf(":")+1); log.debug("-Cached- Service:
					 * "+service+", type: "+type); cache.remove(deviceId); cache.put(deviceId,
					 * service+":"+type); }
					 **/

//					Checking if asset is under the time window hood
					long timeS = transform_date(time.toString());
					if (timeS >= comparativeTime && service != null && !service.isEmpty() && type != null
							&& !type.isEmpty()) {
						log.debug("-Multi sensor- Collectiong data under time window. Data point time: " + timeS + " > "
								+ comparativeTime);
						JsonElement uom = observation0.get("uom");
						JsonElement value = observation0.get("value");

						// Extract the "type" and "location" fields at the last iteration, because they
						// are repeated in the queryResponse in every measurement.
						if (typeIteration == 0) {
							type = observation0.get("type").getAsString();
							// Build location object
							location.add("latitude", observation0.get("latitude"));
							location.add("longitude", observation0.get("longitude"));
							location.add("altitude", observation0.get("altitude"));
							typeIteration++;
						}
						JsonObject observation = new JsonObject();
						observation.addProperty("observedProperty", observedProperty);
						observation.add("time", time);
						observation.add("uom", uom);
						observation.add("value", value);
						// Add object to observations array
						observations.add(observation);
					} else {
						log.debug("Too old data regarding multi measures");
					}
				}
				if (!observations.isJsonNull() && observations.size() != 0) {
					JsonObject asset = new JsonObject();
					asset.addProperty("deviceId", deviceId);
					asset.addProperty("type", type);
					asset.add("location", location);
					asset.add("observations", observations);

//					Add Aseet to resourcesList
					resourcesList.put(deviceId, asset);
				}
			}
		}

		return resourcesList;
	}

	/** Convert date in "yyyy-MM-dd HH:mm:ss" format to epochtime in millis **/
	private static long transform_date(String date) {
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
}
