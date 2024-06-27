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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.stream.Stream;

import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

class Loader {

	static JsonObject json;
	protected static String configFileName = "config.properties";
	protected static ArrayList<String> devIds = new ArrayList<>();
	protected static String schema;
	protected static String alarmSchema;
	protected static JsonSchemaFactory factory = JsonSchemaFactory.byDefault();

	/*
	 * protected static void loadIds() { try (Stream<String> stream =
	 * Files.lines(Paths.get(System.getProperty("user.dir")+File.separator+"src"+
	 * File.separator+"main"+File.separator+"resources"+File.separator+"devIds.conf"
	 * ))) { stream.forEach(devIds::add); } catch (IOException e) {
	 * e.printStackTrace(); } }
	 */
	protected static void loadIds(String pathToFile) {
		try (Stream<String> stream = Files.lines(Paths.get(pathToFile))) {
			stream.forEach(devIds::add);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected static void loadSchema() throws ProcessingException, IOException {
		schema = new String(
				Files.readAllBytes(Paths.get(System.getProperty("user.dir") + File.separator + "src" + File.separator
						+ "main" + File.separator + "resources" + File.separator + "GetAssetsbyLocationSchema.json")));
		alarmSchema = new String(Files.readAllBytes(Paths.get(System.getProperty("user.dir") + File.separator + "src"
				+ File.separator + "main" + File.separator + "resources" + File.separator + "AlarmaSchema.json")));
	}

	protected static void loadJSONResponse() throws FileNotFoundException {
		BufferedReader bufferedReader = new BufferedReader(
				new FileReader(System.getProperty("user.dir") + File.separator + "src" + File.separator + "main"
						+ File.separator + "resources" + File.separator + "assetsResponse.json"));

		Gson gson = new Gson();
		json = gson.fromJson(bufferedReader, JsonObject.class);

	}
}
