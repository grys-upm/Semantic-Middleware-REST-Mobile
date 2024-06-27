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

import java.util.ResourceBundle;

/**
 * @proyect AFarCloud
 * 
 * @author UPM
 * @date 20
 * 
 *       Clase encargada de gestionar los Parámetros globales de la aplicación:
 *       - Parámetros de acceso a BD - Ficheros de trazas
 */
public class GlobalParameters {
	// AFarCloud
	public static final String ALARM_LIMIT = "alarm.limit";
	public static final String DEFAULT_TIME_WINDOW = "timewindow.default";

	// Cache
	public static final String timeToLiveProp = "timeToLiveProp.default";
	public static final String cacheTimerProp = "cacheTimerProp.default";
	public static final String maxItemProp = "maxItemProp.default";

	static private GlobalParameters oInstance = null;

	private ResourceBundle oResourceBundle = null;

	/* maven proyect: default under main/resources */
	private final String sFileProperties = "config.AFC_RESTMobile_Params";

	/**
	 * Constructor privado, ya que la clase va a implementar el patrón singlenton
	 * para no tener en memoria varios GlobalParams
	 */
	private GlobalParameters() {
		super();

		try {
			oResourceBundle = ResourceBundle.getBundle(sFileProperties);

		} catch (Exception ex) {
			System.err.println("$ERROR$DBM$LOAD_PROPERTIES:: filename '" + this.sFileProperties + "'\n "
					+ "There must be in the CLASSPATH" + ex.getMessage());
			throw (ex);
		}
	}

	/**
	 * singlenton pattern
	 */
	public static synchronized GlobalParameters getInstance() {
		if (oInstance == null) {
			oInstance = new GlobalParameters();
		}
		return oInstance;
	}

	public String getParameter(String sParam) {
		try {
			return oResourceBundle.getString(sParam);
		} catch (Exception e) {
			return "";
		}
	}

	public int getIntObservations(String sParam, int nDefValue) {
		int nReturn;
		try {
			nReturn = Integer.parseInt(oResourceBundle.getString(sParam));
		} catch (Exception e) {
			nReturn = nDefValue;
		}

		return nReturn;
	}

	/**
	 * ********************** Main *************************
	 */
	public static void main(String[] args) {
		System.out.println("\n" + GlobalParameters.getInstance().getParameter("uno"));

	}
}
