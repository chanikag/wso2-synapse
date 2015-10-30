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
 *
 */

package org.apache.synapse.aspects.newstatistics;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.aspects.ComponentType;
import org.apache.synapse.aspects.newstatistics.event.reader.StatisticEventReceiver;
import org.apache.synapse.config.SynapsePropertiesLoader;

import java.util.HashMap;
import java.util.Timer;

/**
 * RuntimeStatisticCollector receives statistic events and responsible for handling each of these
 * events. It holds statistic store which contains the in memory statistics for the message
 * mediation happened in the ESB
 */
public class RuntimeStatisticCollector {

	private static final Log log = LogFactory.getLog(RuntimeStatisticCollector.class);

	private static HashMap<String, StatisticsEntry> runningStatistics = new HashMap<String, StatisticsEntry>();

	private static StatisticsStore statisticsStore = new StatisticsStore();

	private static boolean isStatisticsEnable = false;

	private final static String STATISTICS_ENABLE = "new.statistics.enable";

	/**
	 * Create statistic log for the the reporting component
	 *
	 * @param msgCtx        message context
	 * @param componentId   component name of the statistics reporting component
	 * @param componentType component type of the statistics reporting component
	 * @param parentId      component name of the statistics reporting component's parent
	 * @param startTime     start time of the component execution
	 */
	public static void recordStatisticCreateEntry(MessageContext msgCtx, String componentId,
	                                              ComponentType componentType, String parentId, Long startTime) {
		if (isStatisticsEnable) {
			if (componentId == null) {
				if (log.isDebugEnabled()) {
					log.debug("Statistics log closing component Id cannot be null");
				}
				return;
			}
			String statisticsTraceId = getStatisticsTraceId(msgCtx, componentType);
			if (statisticsTraceId != null) {
				if (runningStatistics.containsKey(statisticsTraceId)) {
					runningStatistics.get(statisticsTraceId)
					                 .createLog(componentId, componentType, getMsgId(msgCtx), parentId, startTime,
					                            msgCtx.isResponse());
				} else {
					StatisticsEntry statisticsEntry =
							new StatisticsEntry(componentId, componentType, getMsgId(msgCtx), parentId, startTime,
							                    msgCtx.isResponse());
					runningStatistics.put(statisticsTraceId, statisticsEntry);
					if (log.isDebugEnabled()) {
						log.debug(
								"Creating New Entry in Running Statistics: Current size :" + runningStatistics.size());
					}
				}
			}
		}
	}

	/**
	 * Create fault log at the start of the fault sequence
	 *
	 * @param msgCtx        message context
	 * @param componentId   component name of the statistics reporting component
	 * @param componentType component type of the statistics reporting component
	 * @param parentId      component name of the statistics reporting component's parent
	 * @param startTime     start time of the component execution
	 */
	public static void recordStatisticCreateFaultLog(MessageContext msgCtx, String componentId,
	                                                 ComponentType componentType, String parentId, Long startTime) {
		if (isStatisticsEnable) {
			if (componentId == null) {
				if (log.isDebugEnabled()) {
					log.debug("Statistics log closing component Id cannot be null");
				}
				return;
			}
			String statisticsTraceId = getStatisticsTraceId(msgCtx, componentType);
			if (statisticsTraceId != null) {
				if (runningStatistics.containsKey(statisticsTraceId)) {
					runningStatistics.get(statisticsTraceId)
					                 .createFaultLog(componentId, componentType, getMsgId(msgCtx), parentId, startTime,
					                                 msgCtx.isResponse());
				}
			}
		}
	}

	/**
	 * Ends statistics collection log for the reported statistics component Id which belongs to a
	 * fault
	 *
	 * @param msgCtx      message context
	 * @param componentId component name of the statistics reporting component
	 * @param endTime     end time of the component execution
	 */
	public static void recordStatisticCloseFaultLog(MessageContext msgCtx, String componentId, Long endTime) {
		if (isStatisticsEnable) {
			if (componentId == null) {
				if (log.isDebugEnabled()) {
					log.debug("Statistics log closing component Id cannot be null");
				}
				return;
			}
			String statisticsTraceId = getStatisticsTraceId(msgCtx);
			if (statisticsTraceId != null) {
				if (runningStatistics.containsKey(statisticsTraceId)) {
					boolean finished = runningStatistics.get(statisticsTraceId)
					                                    .closeFaultLog(componentId, getMsgId(msgCtx), endTime);
					if (finished) {
						endMessageFlow(statisticsTraceId, runningStatistics.get(statisticsTraceId), endTime, false);
					}
				}
			}
		}
	}

	/**
	 * Ends statistics collection log for the reported statistics component Id
	 *
	 * @param msgCtx      message context
	 * @param componentId component name of the statistics reporting component
	 * @param parentId    component name of the statistics reporting component's parent
	 * @param endTime     end time of the component execution
	 */
	public static void recordStatisticCloseLog(MessageContext msgCtx, String componentId, String parentId,
	                                           Long endTime) {
		if (isStatisticsEnable) {
			if (componentId == null) {
				if (log.isDebugEnabled()) {
					log.debug("Statistics log closing component Id cannot be null");
				}
				return;
			}
			String statisticsTraceId = getStatisticsTraceId(msgCtx);
			if (statisticsTraceId != null) {
				if (runningStatistics.containsKey(statisticsTraceId)) {
					boolean finished = runningStatistics.get(statisticsTraceId)
					                                    .closeLog(componentId, getMsgId(msgCtx), parentId, endTime);
					if (finished) {
						endMessageFlow(statisticsTraceId, runningStatistics.get(statisticsTraceId), endTime, false);
					}
				}
			}
		}
	}

	/**
	 * registers callback information for the message flow on the corresponding statistics entry
	 *
	 * @param msgCtx     message context
	 * @param callbackId callback identification number
	 */
	public static void addCallbacks(MessageContext msgCtx, String callbackId) {
		if (isStatisticsEnable) {
			String statisticsTraceId = getStatisticsTraceId(msgCtx);
			if (statisticsTraceId != null) {
				if (runningStatistics.containsKey(statisticsTraceId)) {
					runningStatistics.get(statisticsTraceId).addCallback(callbackId, getMsgId(msgCtx));
				}
			}
		}
	}

	/**
	 * updates end time of the statistics logs after corresponding callback is removed from
	 * SynapseCallbackReceiver
	 *
	 * @param msgCtx     message context
	 * @param callbackId callback identification number
	 * @param endTime    callback removal time at SynapseCallbackReceiver
	 */
	public static void updateForReceivedCallback(MessageContext msgCtx, String callbackId, Long endTime) {
		if (isStatisticsEnable) {
			String statisticsTraceId = getStatisticsTraceId(msgCtx);
			if (statisticsTraceId != null) {
				if (runningStatistics.containsKey(statisticsTraceId)) {
					runningStatistics.get(statisticsTraceId).updateCallbackReceived(callbackId, endTime);
				}
			}
		}
	}

	/**
	 * Removes specified callback info for a message flow after all the processing for that
	 * callback is ended
	 *
	 * @param msgCtx     message context
	 * @param callbackId callback identification number
	 */
	public static void removeCallback(MessageContext msgCtx, String callbackId) {
		if (isStatisticsEnable) {
			String statisticsTraceId = getStatisticsTraceId(msgCtx);
			if (statisticsTraceId != null) {
				if (runningStatistics.containsKey(statisticsTraceId)) {
					runningStatistics.get(statisticsTraceId).removeCallback(callbackId);
					if (log.isDebugEnabled()) {
						log.debug("Removed callback from statistic entry");
					}
				}
			}
		}
	}

	/**
	 * Check whether Statistics entry present for the message flow and if there is an entry try
	 * to finish ending statistics collection for that entry
	 *
	 * @param msgCtx  message context
	 * @param endTime end time of the message flow
	 */
	public static void finalizeEntry(MessageContext msgCtx, long endTime) {
		if (isStatisticsEnable) {
			String statisticsTraceId = getStatisticsTraceId(msgCtx);
			if (statisticsTraceId != null) {
				if (runningStatistics.containsKey(statisticsTraceId)) {
					StatisticsEntry entry = runningStatistics.get(statisticsTraceId);
					endMessageFlow(statisticsTraceId, entry, endTime, false);
				}
			}
		}
	}

	/**
	 * Close the statistic log after finishing the message flow forcefully. When we try to use this method to end
	 * statistic collection for a message flow it will not consider any thing and close all the remaining logs and
	 * will send the completed statistic entry for collection
	 *
	 * @param msgCtx  message context
	 * @param endTime end time of the message flow
	 */
	public static void closeStatisticEntryForcefully(MessageContext msgCtx, long endTime) {
		if (isStatisticsEnable) {
			String statisticsTraceId = getStatisticsTraceId(msgCtx);
			if (statisticsTraceId != null) {
				if (runningStatistics.containsKey(statisticsTraceId)) {
					StatisticsEntry entry = runningStatistics.get(statisticsTraceId);
					endMessageFlow(statisticsTraceId, entry, endTime, true);
				}
			}
		}
	}

	/**
	 * Ends statistics collection for the message flow. If entry is successfully completed ending
	 * its statistics collection statistics store is updated with new statistics data. Then entry
	 * is removed from the running statistic map
	 *
	 * @param statisticTraceId statistic trace id for the message log
	 * @param statisticsEntry  statistic entry to be closed
	 * @param endTime          end time of the message flow
	 */
	private synchronized static void endMessageFlow(String statisticTraceId, StatisticsEntry statisticsEntry,
	                                                long endTime, boolean closeForceFully) {
		boolean isMessageFlowEnded = statisticsEntry.endAll(endTime, closeForceFully);
		if (isMessageFlowEnded) {
			if (log.isDebugEnabled()) {
				log.debug("Statistic collection is ended for the message flow with statistic " +
				          "trace Id :" + statisticTraceId);
			}
			statisticsStore.update(statisticsEntry.getMessageFlowLogs());
			runningStatistics.remove(statisticTraceId);
		}
	}

	/**
	 * Returns statistics trace id corresponding to the message context. If statistics trace do
	 * not exists for the message context put message id of this message context as the
	 * statistics id for this flow. Starting of a message Id can only happen for APIs,Proxies and
	 * Sequences as these are the trigger points of the ESB. So statistics trace id insertion only
	 * happen if message context is reported by those components.
	 *
	 * @param msgCtx        message context
	 * @param componentType statistic event reporting component Id
	 * @return statistics trace Is for this message context
	 */
	private static String getStatisticsTraceId(MessageContext msgCtx, ComponentType componentType) {
		if (msgCtx.getProperty(SynapseConstants.NEW_STATISTICS_ID) != null) {
			return (String) msgCtx.getProperty(SynapseConstants.NEW_STATISTICS_ID);
		} else {
			if ((componentType == ComponentType.PROXYSERVICE) || (componentType == ComponentType.SEQUENCE) ||
			    (componentType == ComponentType.API) || (componentType == ComponentType.INBOUNDENDPOINT)) {
				String statisticsTraceId = msgCtx.getMessageID();
				msgCtx.setProperty(SynapseConstants.NEW_STATISTICS_ID, statisticsTraceId);
				if (log.isDebugEnabled()) {
					log.debug("Setting Statistics trace Id in Message the context to be used for " +
					          "statistic collection" + statisticsTraceId);
				}
				return statisticsTraceId;
			} else {
				if (log.isDebugEnabled()) {
					log.debug("Statistics trace Id cannot be created for this component type :" +
					          componentType.toString());
				}
			}
		}
		if (log.isDebugEnabled()) {
			log.debug("No Statistic Trace was found in the message context");
		}
		return null;
	}

	/**
	 * Returns statistics trace id corresponding to the message context
	 *
	 * @param msgCtx message context
	 * @return statistics trace id
	 */
	private static String getStatisticsTraceId(MessageContext msgCtx) {
		if (msgCtx.getProperty(SynapseConstants.NEW_STATISTICS_ID) != null) {
			return (String) msgCtx.getProperty(SynapseConstants.NEW_STATISTICS_ID);
		} else {
			if (log.isDebugEnabled()) {
				log.debug("No Statistic Trace was found in the message context");
			}
		}
		return null;
	}

	/**
	 * Returns cloned message identification number for the specified  message context. If message
	 * context is not a cloned one default value of -1 is sent
	 *
	 * @param msgCtx message context
	 * @return cloned message identification number
	 */
	private static int getMsgId(MessageContext msgCtx) {
		if (msgCtx.getProperty(SynapseConstants.NEW_STATISTICS_MESSAGE_ID) != null) {
			return (Integer) msgCtx.getProperty(SynapseConstants.NEW_STATISTICS_MESSAGE_ID);
		} else {
			return -1;
		}
	}

	/**
	 * Returns clone message identification number for the next cloning message in the message flow
	 *
	 * @param msgCtx message context
	 * @return next clone message identification number
	 */
	public static int getClonedMsgNumber(MessageContext msgCtx) {
		String statisticsTraceId = getStatisticsTraceId(msgCtx);
		if (statisticsTraceId != null) {
			if (runningStatistics.containsKey(statisticsTraceId)) {
				return runningStatistics.get(statisticsTraceId).incrementAndGetClonedMsgCount();
			}
		}
		return -1;
	}

	/**
	 * Initialize statistics collection when ESB starts. If statistic cleaning is enabled in
	 * synapse.properties file this method will schedule a timer event to clean statistics at
	 * that specified time interval
	 *
	 * @param synapseTimer timer object for the Synapse Configuration
	 */
	public static void init(Timer synapseTimer) {
		isStatisticsEnable = Boolean.parseBoolean(
				SynapsePropertiesLoader.getPropertyValue(STATISTICS_ENABLE, String.valueOf(false)));
		if (isStatisticsEnable) {
			if (log.isDebugEnabled()) {
				log.debug("Statistics is enabled");
			}
			StatisticsStoreCleaner statisticsStoreCleaner = new StatisticsStoreCleaner(statisticsStore);
			if (statisticsStoreCleaner.isCleanEnable()) {
				StatisticStoreCleanerHandler statisticStoreCleanerHandler =
						new StatisticStoreCleanerHandler(statisticsStoreCleaner);
				// schedule timeout handler to run specified time
				synapseTimer.schedule(statisticStoreCleanerHandler, 0, statisticsStoreCleaner.getCleanInterval());
			}
			StatisticEventReceiver.Init();
		} else {
			if (log.isDebugEnabled()) {
				log.debug("Statistics is not enabled in \'synapse.properties\' file");
			}
		}

	}

	/**
	 * Returns whether statistics collection is enabled globally for the esb as specified in the
	 * synapse.properties file
	 *
	 * @return true if statistics collection is enabled
	 */
	public static boolean isStatisticsEnable() {
		return isStatisticsEnable;
	}
}
