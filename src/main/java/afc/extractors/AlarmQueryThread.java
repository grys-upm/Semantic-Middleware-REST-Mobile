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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Iterator;
import java.util.stream.Collectors;

import javax.net.ssl.HttpsURLConnection;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class AlarmQueryThread extends Thread {
	private String query = "";
	private long comparativeTime = 0;
	private String deviceId = "";
	private static final Logger log = Logger.getLogger(AlarmQueryThread.class);

	public AlarmQueryThread(String query, long comparativeTime, String deviceId) {
		this.query = query;
		this.comparativeTime = comparativeTime;
		this.deviceId = deviceId;
	}

	public void run() {

		System.out.println(this.getName() + ": New Thread is running...");
		log.debug(this.getName() + ": New Thread is running...");
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
			} else {

				JsonArray alarms = new JsonArray();
				alarms = null;
				BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
				String response = br.lines().collect(Collectors.joining());
				JsonParser parser = new JsonParser();
				JsonElement responseJSON = parser.parse(response);
				JsonArray queryResponse = responseJSON.getAsJsonArray();

				conn.disconnect();

				if (queryResponse != null && queryResponse.size() > 1) {
					log.debug("Alarm getted");
					alarms = buildAlarmList(queryResponse, comparativeTime);
				} else if (queryResponse != null && queryResponse.size() == 1) {
					log.debug("Alarm getted");
					alarms = buildAlarmList(queryResponse, comparativeTime);
				}
				if (alarms.size() > 0 && !alarms.isJsonNull() && alarms != null) {
					addAlarm(deviceId, alarms);
				}
			}
		}
//	     This catch is implemented because otherwise WebApplicationExceptions are treated as RuntimeExceptions,
//		 and processed as such.
		catch (WebApplicationException e) {
			log.error("500");
			throw new WebApplicationException(Response.status(500).entity(e.getMessage()).build());
		} catch (RuntimeException e) {
			log.error("Could not connect to AP&R: " + e.getMessage());
			throw new WebApplicationException(Response.status(500).entity("ERROR: Could not connect to AP&R").build());
		} catch (Exception e) {
			log.error("An error has detected when trying to connect to AP&R: " + e);
		}

	}

	/** Add alarm to final AlarmList **/
	public synchronized void addAlarm(String deviceId, JsonArray alarms) {
		AlarmsExtractor.alarmsList.put(deviceId, alarms);
	}

	/**
	 * Method to build the assetList array from the alarms query (includes both
	 * Alarms associated with a specific deviceId)
	 **/
	public JsonArray buildAlarmList(JsonArray queryResponse, long comparativeTime) {
		// Resources array
		JsonArray alarmList = new JsonArray();

		// Extract the fields
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

}
