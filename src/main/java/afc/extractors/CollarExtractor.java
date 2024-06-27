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

public class CollarExtractor {

	private static final Logger log = Logger.getLogger(CollarExtractor.class);

	public static HashMap<String, JsonObject> extractResources(JsonArray resources, long comparativeTime,
			String scenario) {

		String service = null;
		String type = null;
		HashMap<String, JsonObject> collars = new HashMap<String, JsonObject>();
		Iterator<JsonElement> i = resources.iterator();
		while (i.hasNext()) {
			JsonObject resource = i.next().getAsJsonObject();
			JsonObject location = new JsonObject();
			JsonObject anomalies = new JsonObject();
			JsonObject acceleration = new JsonObject();
			String deviceId = resource.get("resource").getAsString();
			JsonObject measurement = resource.get("measurements").getAsJsonArray().get(0).getAsJsonObject();
			JsonObject observation0 = measurement.get("observations").getAsJsonArray().get(0).getAsJsonObject();
			JsonElement time = observation0.get("time");
			service = observation0.get("service").toString().replace("\"", "");
			type = observation0.get("type").toString().replace("\"", "");

			/** collar matching with AR or cache **/
			/**
			 * @SuppressWarnings("unchecked") Cache<String, Integer> cache =
			 * Cache.getResCache(Long.parseLong(GlobalParameters.getInstance().getParameter(GlobalParameters.timeToLiveProp)),
			 * Long.parseLong(GlobalParameters.getInstance().getParameter(GlobalParameters.cacheTimerProp)),
			 * Integer.parseInt(GlobalParameters.getInstance().getParameter(GlobalParameters.maxItemProp)));
			 * String result = cache.get(deviceId); if(result == null) { String url =
			 * "https://storage"+scenario.substring(2,4)+"-afarcloud.qa.pdmfc.com/storage/rest/registry/getCollarByResourceId/"+deviceId;
			 * log.debug("Looking for collars: "+url); // Query to AssetRegistry in order to
			 * obtain Service and Type parameters associated with the specific device String
			 * resourceUrn = null; try { resourceUrn =
			 * AssetRegistryManager.getResourceInfo(url); } catch (IOException e) { // TODO
			 * Auto-generated catch block e.printStackTrace(); } if(resourceUrn!=null &&
			 * !resourceUrn.isEmpty()) { int indexType = resourceUrn.indexOf(":", 14);
			 * service = resourceUrn.substring(14, indexType); type = "collar";
			 * log.debug("Service: "+service+", type: "+type); cache.put(deviceId,
			 * service+":"+type); result = service+":"+type; } }else { service =
			 * result.substring(0, result.indexOf(":")); type =
			 * result.substring(result.indexOf(":")+1); log.debug("-Cached- Service:
			 * "+service+", type: "+type); cache.remove(deviceId); cache.put(deviceId,
			 * service+":"+type); }
			 **/

//			Checking if asset is under the time window hood
			long timeS = transform_date(time.toString());

			if (timeS >= comparativeTime && service != null && !service.isEmpty() && type != null && !type.isEmpty()) {
				log.debug("-Collars- Collectiong data under time window. Data point time: " + timeS + " > "
						+ comparativeTime);
				JsonElement alarm = observation0.get("resourceAlarm");
				JsonElement temperature = observation0.get("temperature");
//				Build anomalies object
				anomalies.add("activityAnomaly", observation0.get("activityAnomaly"));
				anomalies.add("distanceAnomaly", observation0.get("distanceAnomaly"));
				anomalies.add("locationAnomaly", observation0.get("locationAnomaly"));
				anomalies.add("positionAnomaly", observation0.get("positionAnomaly"));
				anomalies.add("temperatureAnomaly", observation0.get("temperatureAnomaly"));

//				Build acceleration object
				acceleration.add("accX", observation0.get("accX"));
				acceleration.add("accY", observation0.get("accY"));
				acceleration.add("accZ", observation0.get("accZ"));

				type = observation0.get("type").getAsString();
//				Build location object
				location.add("latitude", observation0.get("latitude"));
				location.add("longitude", observation0.get("longitude"));
				location.add("altitude", observation0.get("altitude"));
				JsonObject collar = new JsonObject();
				collar.addProperty("deviceId", deviceId);
				collar.addProperty("type", type);
				collar.add("location", location);
				collar.add("time", time);
				collar.add("alarm", alarm);
				collar.add("temperature", temperature);
				collar.add("anomalies", anomalies);
				collar.add("acceleration", acceleration);

				collars.put(deviceId, collar);
			} else {
				log.debug("Too old data regarding collar");
			}
		}
		return collars;
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
