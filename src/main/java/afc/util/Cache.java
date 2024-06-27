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

import java.util.ArrayList;
import org.apache.commons.collections4.MapIterator;
import org.apache.commons.collections4.map.LRUMap;
import org.apache.log4j.Logger;

public class Cache<K, T> {

	private static final Logger log = Logger.getLogger(Cache.class);
	private static String s;

	private long timeToLive;
	private LRUMap<K, CacheObject> cacheMap;

	@SuppressWarnings("rawtypes")
	private static Cache resCache;
	@SuppressWarnings("rawtypes")
	private static Cache infoCache;
	@SuppressWarnings("rawtypes")
	private static Cache retryStatus;

	protected class CacheObject {
		public long lastAccessed = System.currentTimeMillis();
		public String value;

		protected CacheObject(T value) {
			this.value = (String) value;
		}
	}

	@SuppressWarnings("rawtypes")
	public static synchronized Cache getResCache(long timeToLive, long timerInterval, int maxItems) {
		if (resCache == null) {
			resCache = new Cache(timeToLive, timerInterval, maxItems);
		}
		return resCache;
	}

	@SuppressWarnings("rawtypes")
	public static synchronized Cache getInfoCache(long timeToLive, long timerInterval, int maxItems) {
		if (infoCache == null) {
			infoCache = new Cache(timeToLive, timerInterval, maxItems);
		}
		return infoCache;
	}

	@SuppressWarnings("rawtypes")
	public static synchronized Cache getRetryStatus(long timeToLive, long timerInterval, int maxItems) {
		if (retryStatus == null) {
			retryStatus = new Cache(timeToLive, timerInterval, maxItems);
		}
		return retryStatus;
	}

	private Cache(long timeToLive, final long timerInterval, int maxItems) {
		this.timeToLive = timeToLive * 1000;

		cacheMap = new LRUMap<K, CacheObject>(maxItems);

		if (timeToLive > 0 && timerInterval > 0) {

			Thread t = new Thread(new Runnable() {
				@Override
				public void run() {
					while (true) {
						try {
							Thread.sleep(timerInterval * 1000);
						} catch (InterruptedException ex) {
						}
						cleanup();
					}
				}
			});

			t.setDaemon(true);
			t.start();
		}
	}

	@SuppressWarnings("unchecked")
	public void put(K key, String string) {
		synchronized (cacheMap) {
			s = "Putting: " + key + " in cache";
			log.debug(s);
//			Server.traza += s + ";   ";
			cacheMap.put(key, new CacheObject((T) string));
		}
	}

	public String get(K key) {
		synchronized (cacheMap) {
			s = "Getting: " + key + " from cache";
			log.debug(s);
//			Server.traza += s + ";   ";
			CacheObject c = cacheMap.get(key);
			if (c == null)
				return null;
			else {
				c.lastAccessed = System.currentTimeMillis();
				return c.value;
			}
		}
	}

	public void remove(K key) {
		synchronized (cacheMap) {
			cacheMap.remove(key);
		}
	}

	public int size() {
		synchronized (cacheMap) {
			return cacheMap.size();
		}
	}

	// @SuppressWarnings("unchecked")
	public void cleanup() {

		long now = System.currentTimeMillis();
		ArrayList<K> deleteKey = null;

		synchronized (cacheMap) {
			MapIterator<K, CacheObject> itr = cacheMap.mapIterator();

			deleteKey = new ArrayList<K>((cacheMap.size() / 2) + 1);
			K key = null;
			CacheObject c = null;

			while (itr.hasNext()) {
				key = itr.next();
				c = itr.getValue();

				if (c != null && (now > (timeToLive + c.lastAccessed))) {
					deleteKey.add(key);
					log.debug("Deleting: " + key + " from cache");
				}
			}
		}

		for (K key : deleteKey) {
			synchronized (cacheMap) {
				cacheMap.remove(key);
			}

			Thread.yield();
		}
	}
}
