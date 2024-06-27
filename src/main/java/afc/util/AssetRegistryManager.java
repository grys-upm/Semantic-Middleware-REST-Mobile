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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.stream.Collectors;

import javax.net.ssl.HttpsURLConnection;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class AssetRegistryManager {

	private static final Logger log = Logger.getLogger(AssetRegistryManager.class);

	public static String getResourceInfo(String query) throws IOException {
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
				log.debug("Resource not found. Code: " + code);
				return null;
			} else {
				BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
				String response = br.lines().collect(Collectors.joining());

				JsonParser parser = new JsonParser();
				JsonElement responseJSON = parser.parse(response);
				JsonObject inputJSON = responseJSON.getAsJsonObject();
				JsonElement resource = null;
				if (inputJSON.has("resourceUrn")) {
					resource = inputJSON.get("resourceUrn");
				} else if (inputJSON.has("collar_urn")) {
					resource = inputJSON.get("collar_urn");
				}
				// Server answer
				while ((response = br.readLine()) != null) {

				}
				conn.disconnect();
				// Store object in cache
				return resource.toString();
			}
		}
//	    This catch is implemented because otherwise WebApplicationExceptions are treated as RuntimeExceptions,
//		and processed as such.
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
}
