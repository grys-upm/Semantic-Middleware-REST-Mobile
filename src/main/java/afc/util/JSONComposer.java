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
package afc.util;

import java.util.HashMap;

import org.apache.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class JSONComposer {

	private static final Logger log = Logger.getLogger(JSONComposer.class);

	public static JsonArray composeCollarJson(HashMap<String, JsonObject> resourcesList,
			HashMap<String, JsonArray> alarmsList) {

//		Collars array
		JsonArray collarList = new JsonArray();
		log.debug("Collar Json Composer");
		for (String collarId : resourcesList.keySet()) {
			JsonArray alarms = alarmsList.get(collarId);
			JsonObject collar = resourcesList.get(collarId);

			if (alarms != null && alarms.size() > 0 && !alarms.isJsonNull()) {
				collar.add("alarms", alarms);
			}

			collarList.add(collar);
		}

		return collarList;
	}

	public static JsonArray composeJson(HashMap<String, JsonObject> resourcesList,
			HashMap<String, JsonArray> alarmsList) {

//		Resources array
		JsonArray assetList = new JsonArray();
		log.debug("Resources Json Composer");
		for (String resourceId : resourcesList.keySet()) {
			JsonArray alarms = alarmsList.get(resourceId);
			JsonObject asset = resourcesList.get(resourceId);

			if (alarms != null && alarms.size() > 0 && !alarms.isJsonNull()) {
				asset.add("alarms", alarms);
			}

			assetList.add(asset);
		}

		return assetList;
	}

}
