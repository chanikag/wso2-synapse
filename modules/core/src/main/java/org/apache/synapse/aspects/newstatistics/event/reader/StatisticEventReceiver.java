/*
 *   Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *   WSO2 Inc. licenses this file to you under the Apache License,
 *   Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.synapse.aspects.newstatistics.event.reader;

import org.apache.synapse.aspects.newstatistics.log.templates.StatisticReportingLog;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class StatisticEventReceiver {
	final static BlockingQueue<StatisticReportingLog> queue = new ArrayBlockingQueue<StatisticReportingLog>(10000);

	/**
	 * This method will be called during server startup and it will create a thread that will read from the queue and
	 * report the statistics to the RuntimeStatisticCollector
	 */
	public static void Init() {
		new Thread(new StatisticEventPublisher(queue)).start();
	}

	/**
	 * This method will add statistic events to the queue. Then statistic worker thread will read these events and
	 * publish them to the queue
	 *
	 * @param event Statistic Event
	 */
	public static void addEventToQueue(StatisticReportingLog event) {
		queue.add(event);
	}
}

