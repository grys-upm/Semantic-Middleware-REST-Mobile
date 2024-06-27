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

import java.util.Properties;

import org.apache.log4j.Logger;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class MQTTClient implements MqttCallback {

	private static final Logger log = Logger.getLogger(MQTTClient.class);

	/**
	 * @param json
	 * @param topic
	 */
	public void sendTelemetry(String json, String topic) {

		String serverUrl = "ssl://mqtt.afarcloud.smartarch.cz:1883";
		String mqttUserName = "upm";
		String mqttPassword = "vIabMNUMKHypmNLJkv/K6AjMsUfj3IDQ";
		MqttClient client;

		try {
			client = new MqttClient(serverUrl, "RESTMobile");
			client.setCallback(this);
			MqttConnectOptions options = new MqttConnectOptions();
			Properties sslProperties = new Properties();
			sslProperties.setProperty("com.ibm.ssl.protocol", "TLS");
			sslProperties.setProperty("com.ibm.ssl.trustStore", "src/SSL/mqttTrustStore.jks");
			sslProperties.setProperty("com.ibm.ssl.trustStorePassword", "qwerty");
			options.setSSLProperties(sslProperties);

			options.setCleanSession(true);
			options.setUserName(mqttUserName);
			options.setPassword(mqttPassword.toCharArray());

			options.setConnectionTimeout(60);
			options.setKeepAliveInterval(60);
			options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1);
//            SSLSocketFactory socketFactory = SslUtil.getSocketFactory(caFilePath, clientCrtFilePath, clientKeyFilePath, "7uJfb&fTFs@j0yGFto3");

			options.setHttpsHostnameVerificationEnabled(false);
//            options.setSocketFactory(socketFactory);

			log.debug("Starting connect the server: " + serverUrl);
			client.connect(options);
			log.debug("Publishing to: " + topic);
//            MqttMessage message = new MqttMessage(json.getBytes());
			client.publish(topic, json.getBytes(), 2, false);
			Thread.sleep(1000);
			client.disconnect();
			log.debug("disconnected!");

		} catch (MqttException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	@Override
	public void connectionLost(Throwable cause) {
		// TODO Auto-generated method stub

	}

	@Override
	public void messageArrived(String topic, MqttMessage message) throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public void deliveryComplete(IMqttDeliveryToken token) {
		// TODO Auto-generated method stub

	}
}
