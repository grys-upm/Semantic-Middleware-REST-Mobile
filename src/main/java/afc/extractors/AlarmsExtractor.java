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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class AlarmsExtractor {

	private static final Logger log = Logger.getLogger(AlarmsExtractor.class);
	public static HashMap<String, JsonArray> alarmsList = new HashMap<String, JsonArray>();

	public static synchronized HashMap<String, JsonArray> extractAlarms(JsonArray resources, long comparativeTime,
			String scenario) throws IOException {

		alarmsList = new HashMap<String, JsonArray>();

		int j = 0;
		ArrayList<AlarmQueryThread> threads = new ArrayList<AlarmQueryThread>();
		Iterator<JsonElement> i = resources.iterator();
		while (i.hasNext()) {
			JsonObject resource = i.next().getAsJsonObject();
			String deviceId = resource.get("resource").getAsString();

			String aQuery = "https://storage" + scenario.substring(2, 4)
					+ "-afarcloud.qa.pdmfc.com/storage/rest/dq/getAlarmByResource/" + deviceId + "?limit=" + "10";
			log.debug(aQuery);

			AlarmQueryThread thread = new AlarmQueryThread(aQuery, comparativeTime, deviceId);
			thread.setName("core" + j);
			threads.add(thread);
			thread.start();
			j++;
		}

		try {
			for (int z = 0; z < threads.size(); z++)
				threads.get(z).join();
		} catch (InterruptedException e) {
			log.error("Error in thread execution when querying to AP&R: " + e);
		}
		// if hilos han acabado extracción
		return alarmsList;
	}
}
