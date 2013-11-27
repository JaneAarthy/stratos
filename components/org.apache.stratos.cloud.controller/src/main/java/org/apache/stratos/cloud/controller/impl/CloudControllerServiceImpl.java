/*
 * Licensed to the Apache Software Foundation (ASF) under one 
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY 
 * KIND, either express or implied.  See the License for the 
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.stratos.cloud.controller.impl;

import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.cloud.controller.concurrent.ThreadExecutor;
import org.apache.stratos.cloud.controller.exception.CloudControllerException;
import org.apache.stratos.cloud.controller.exception.InvalidCartridgeTypeException;
import org.apache.stratos.cloud.controller.exception.InvalidClusterException;
import org.apache.stratos.cloud.controller.exception.InvalidMemberException;
import org.apache.stratos.cloud.controller.exception.InvalidPartitionException;
import org.apache.stratos.cloud.controller.exception.UnregisteredCartridgeException;
import org.apache.stratos.cloud.controller.exception.UnregisteredClusterException;
import org.apache.stratos.cloud.controller.interfaces.CloudControllerService;
import org.apache.stratos.cloud.controller.interfaces.Iaas;
import org.apache.stratos.cloud.controller.persist.Deserializer;
import org.apache.stratos.cloud.controller.pojo.ClusterContext;
import org.apache.stratos.cloud.controller.pojo.MemberContext;
import org.apache.stratos.cloud.controller.pojo.Registrant;
import org.apache.stratos.cloud.controller.publisher.CartridgeInstanceDataPublisherTask;
import org.apache.stratos.cloud.controller.registry.RegistryManager;
import org.apache.stratos.cloud.controller.runtime.FasterLookUpDataHolder;
import org.apache.stratos.cloud.controller.topic.TopologySynchronizerTask;
import org.apache.stratos.cloud.controller.topology.TopologyBuilder;
import org.apache.stratos.cloud.controller.topology.TopologyEventMessageDelegator;
import org.apache.stratos.cloud.controller.topology.TopologyManager;
import org.apache.stratos.cloud.controller.util.*;
import org.apache.stratos.cloud.controller.validate.interfaces.PartitionValidator;
import org.apache.stratos.messaging.domain.policy.DeploymentPolicy;
import org.apache.stratos.messaging.domain.policy.Partition;
import org.apache.stratos.messaging.domain.policy.PartitionGroup;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.ComputeMetadata;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeMetadata.Status;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.internal.NodeMetadataImpl;
import org.wso2.carbon.ntask.common.TaskException;
import org.wso2.carbon.ntask.core.TaskInfo;
import org.wso2.carbon.ntask.core.TaskInfo.TriggerInfo;
import org.wso2.carbon.ntask.core.TaskManager;
import org.wso2.carbon.ntask.core.service.TaskService;
import org.wso2.carbon.registry.core.exceptions.RegistryException;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Cloud Controller Service is responsible for starting up new server instances,
 * terminating already started instances, providing pending instance count etc.
 * 
 */
public class CloudControllerServiceImpl implements CloudControllerService {

	private static final Log log = LogFactory
			.getLog(CloudControllerServiceImpl.class);
	private FasterLookUpDataHolder dataHolder = FasterLookUpDataHolder
			.getInstance();

	public CloudControllerServiceImpl() {

		// acquire serialized data from registry
		acquireData();

		// gets the task service
		TaskService taskService = ServiceReferenceHolder
				.getInstance().getTaskService();

		if (dataHolder.getEnableBAMDataPublisher()) {

			// register and schedule, BAM data publisher task
			registerAndScheduleDataPublisherTask(taskService);
		}

		if (dataHolder.getEnableTopologySync()) {

			// start the topology builder thread
			startTopologyBuilder();

			// register and schedule, topology synchronizer task
			registerAndScheduleTopologySyncerTask(taskService);
		}
	}

	private void registerAndScheduleTopologySyncerTask(TaskService taskService) {
		TaskInfo taskInfo;
		TaskManager tm = null;
		try {

			if (!taskService.getRegisteredTaskTypes().contains(
					CloudControllerConstants.TOPOLOGY_SYNC_TASK_TYPE)) {

				// topology sync
				taskService
						.registerTaskType(CloudControllerConstants.TOPOLOGY_SYNC_TASK_TYPE);

				tm = taskService
						.getTaskManager(CloudControllerConstants.TOPOLOGY_SYNC_TASK_TYPE);
				
				String cron = dataHolder.getTopologyConfig().getProperty(CloudControllerConstants.CRON_ELEMENT);

				cron = ( cron == null ? CloudControllerConstants.PUB_CRON_EXPRESSION : cron ); 
				
				TriggerInfo triggerInfo = new TriggerInfo(cron);
				taskInfo = new TaskInfo(
						CloudControllerConstants.TOPOLOGY_SYNC_TASK_NAME,
						TopologySynchronizerTask.class.getName(),
						new HashMap<String, String>(), triggerInfo);
				tm.registerTask(taskInfo);
			}

		} catch (Exception e) {
			String msg = "Error scheduling task: "
					+ CloudControllerConstants.TOPOLOGY_SYNC_TASK_NAME;
			log.error(msg, e);
			if (tm != null) {
				try {
					tm.deleteTask(CloudControllerConstants.TOPOLOGY_SYNC_TASK_NAME);
				} catch (TaskException e1) {
					log.error(e1);
				}
			}
			throw new CloudControllerException(msg, e);
		}
	}

	private void startTopologyBuilder() {
		// initialize TopologyEventMessageProcessor Consumer
		Thread topologyBuilder = new Thread(new TopologyEventMessageDelegator());
		// start consumer
		topologyBuilder.start();
	}

	private TaskManager registerAndScheduleDataPublisherTask(
			TaskService taskService) {
		TaskInfo taskInfo;
		TaskManager tm = null;
		// initialize and schedule the data publisher task
		try {

			if (!taskService.getRegisteredTaskTypes().contains(
					CloudControllerConstants.DATA_PUB_TASK_TYPE)) {

				taskService
						.registerTaskType(CloudControllerConstants.DATA_PUB_TASK_TYPE);

				tm = taskService
						.getTaskManager(CloudControllerConstants.DATA_PUB_TASK_TYPE);

				if (!tm.isTaskScheduled(CloudControllerConstants.DATA_PUB_TASK_NAME)) {

					TriggerInfo triggerInfo = new TriggerInfo(
							dataHolder.getDataPubConfig().getDataPublisherCron());
					taskInfo = new TaskInfo(
							CloudControllerConstants.DATA_PUB_TASK_NAME,
							CartridgeInstanceDataPublisherTask.class.getName(),
							new HashMap<String, String>(), triggerInfo);
					tm.registerTask(taskInfo);

					// Following code is currently not required, due to an issue
					// in TS API.
					// tm.scheduleTask(taskInfo.getName());
				}
			}

		} catch (Exception e) {
			String msg = "Error scheduling task: "
					+ CloudControllerConstants.DATA_PUB_TASK_NAME;
			log.error(msg, e);
			if (tm != null) {
				try {
					tm.deleteTask(CloudControllerConstants.DATA_PUB_TASK_NAME);
				} catch (TaskException e1) {
					log.error(e1);
				}
			}
			throw new CloudControllerException(msg, e);
		}
		return tm;
	}

	private void acquireData() {

//		Object obj = RegistryManager.getInstance().retrieve();
//		if (obj != null) {
//			try {
//				Object dataObj = Deserializer
//						.deserializeFromByteArray((byte[]) obj);
//				if (dataObj instanceof FasterLookUpDataHolder) {
//					FasterLookUpDataHolder serializedObj = (FasterLookUpDataHolder) dataObj;
//					FasterLookUpDataHolder currentData = FasterLookUpDataHolder
//							.getInstance();
//
//					// assign necessary data
//					currentData.setNodeIdToServiceContextMap(serializedObj
//							.getNodeIdToServiceContextMap());
//
//					// traverse through current Service Contexts
//					for (ServiceContext ctxt : currentData.getServiceCtxtList()) {
//						// traverse through serialized Service Contexts
//						for (ServiceContext serializedCtxt : serializedObj
//								.getServiceCtxtList()) {
//							// if a matching Service Context found
//							if (ctxt.equals(serializedCtxt)) {
//								// persisted node ids of this Service Context
//								List<Object> nodeIds = serializedObj
//										.getNodeIdsOfServiceCtxt(serializedCtxt);
//								for (Object nodeIdObj : nodeIds) {
//									String nodeId = (String) nodeIdObj;
//
//									// assign persisted data
//									currentData.addNodeId(nodeId, ctxt);
//
//								}
//
//								ctxt.setIaasContextMap(serializedCtxt
//										.getIaasCtxts());
//								appendToPublicIpProperty(
//										serializedCtxt
//												.getProperty(CloudControllerConstants.PUBLIC_IP_PROPERTY),
//										ctxt);
//
//								// assign lastly used IaaS
//								if (serializedCtxt.getCartridge() != null
//										&& serializedCtxt.getCartridge()
//												.getLastlyUsedIaas() != null) {
//
//									if (ctxt.getCartridge() == null) {
//										// load Cartridge
//										ctxt.setCartridge(loadCartridge(
//												ctxt.getCartridgeType(),
//												serializedObj.getCartridges()));
//									}
//
//									IaasProvider serializedIaas = serializedCtxt
//											.getCartridge().getLastlyUsedIaas();
//									ctxt.getCartridge().setLastlyUsedIaas(
//											serializedIaas);
//
//								}
//							}
//						}
//					}
//
//					log.debug("Data is retrieved from registry.");
//				} else {
//					log.debug("No data is persisted in registry.");
//				}
//			} catch (Exception e) {
//
//				String msg = "Unable to acquire data from Registry. Hence, any historical data will not get reflected.";
//				log.warn(msg, e);
//			}

//		}
	}

    @Override
	public String startInstance(String clusterId, Partition partition) throws IllegalArgumentException, UnregisteredCartridgeException {

        log.info("Starting new instance of cluster : " + clusterId);

		ComputeService computeService = null;
		Template template = null;
		String ip;
		
		if(partition == null) {
		    String msg = "Instance start-up failed. Specified Partition is null. Cluster id: "+clusterId;
            log.error(msg);
            throw new IllegalArgumentException(msg);
		}
		
        String partitionId = partition.getId();
        ClusterContext ctxt = dataHolder.getClusterContext(clusterId);
        
        if(ctxt == null) {
            String msg = "Instance start-up failed. Invalid cluster id: "+clusterId;
            log.error(msg);
            throw new IllegalArgumentException(msg);
        }
        
        String cartridgeType = ctxt.getCartridgeType();
        
        Cartridge cartridge = dataHolder.getCartridge(cartridgeType);
        
        if(cartridge == null) {
            String msg = "Instance start-up failed. No valid Cartridge found for type: "+cartridgeType;
            log.error(msg);
            throw new UnregisteredCartridgeException(msg);
        }
        
        
//        Partition partition_ = TopologyManager.getInstance().getTopology().getPartition(partitionId);
//        Scope scope = partition_.getScope();
//        String provider = partition_.getProperty("provider");
		final Lock lock = new ReentrantLock();

		// get the subjected ServiceContext
//		ServiceContext serviceCtxt = dataHolder
//				.getServiceContextFromDomain(clusterId);
//
//		if (serviceCtxt == null) {
//			String msg = "Not a registered service: domain - " + clusterId;
//			log.fatal(msg);
//			throw new CloudControllerException(msg);
//		}
//
//		// load Cartridge
//		serviceCtxt.setCartridge(loadCartridge(serviceCtxt.getCartridgeType(),
//				 dataHolder
//						.getCartridges()));
//
//		if (serviceCtxt.getCartridge() == null) {
//			String msg = "There's no registered Cartridge found. Domain - "
//					+ clusterId;
//			log.fatal(msg);
//			throw new CloudControllerException(msg);
//		}
//
//		if (serviceCtxt.getCartridge().getIaases().isEmpty()) {
//			String msg = "There's no registered IaaSes found for Cartridge type: "
//					+ serviceCtxt.getCartridge().getType();
//			log.fatal(msg);
//			throw new CloudControllerException(msg);
//		}


//		for (IaasProvider iaas : serviceCtxt.getCartridge().getIaases()) {

		IaasProvider iaas = cartridge.getIaasProviderOfPartition(partitionId);
            if(iaas == null) {
                String msg = "Instance start-up failed for cluster: "+clusterId+". " +
                        "There's no IaaS provided for the partition: "+partitionId+
                        " and for the Cartridge type: "+cartridgeType;
                log.fatal(msg);
                throw new CloudControllerException(msg);
            }
//                IaasContext ctxt;
//                if ((ctxt = serviceCtxt.getIaasContext(iaas.getType())) == null) {
//                    ctxt = serviceCtxt.addIaasContext(iaas.getType());
//                }
                try {
                    //generating the Unique member ID...
                    String memberID = generateMemberId(clusterId);
                    //have to add memberID to the payload
                    StringBuilder payload = new StringBuilder(ctxt.getPayload());
                    payload.append(",");
                    payload.append("MEMBER_ID=" + memberID);
                    //reloading the payload with memberID
                    iaas.setPayload(payload.toString().getBytes());

                    iaas.getIaas().setDynamicPayload(iaas);
                    // get the pre built ComputeService from provider or region or zone or host
                    computeService = iaas.getComputeService();
                    template = iaas.getTemplate();
                    
//                    if(scope == null || scope.name().equals(Scope.PROVIDER.name())) {
//                        computeService = iaas.getComputeService();
//                        template = iaas.getTemplate();
//                    } else if(scope.name().equals(Scope.REGION.name())) {
//                        for(Region region : iaas.getListOfRegions()) {
//                            if(region.getId().equals(partitionId)) {
//                                computeService = region.getComputeService();
//                                template = region.getTemplate();
//                            }
//                        }
//                    } else if(scope.name().equals(Scope.ZONE.name())) {
//                        for(Region region : iaas.getListOfRegions()) {
//                            for(Zone zone : region.getListOfZones()) {
//                               if(zone.getId().equals(partitionId)) {
//                                computeService = zone.getComputeService();
//                                template = zone.getTemplate();
//                                }
//                            }
//                        }
//                    } else if(scope.name().equals(Scope.HOST.name())) {
//                        for(Region region : iaas.getListOfRegions()) {
//                            for(Zone zone : region.getListOfZones()) {
//                                for(Host host: zone.getListOfHosts()) {
//                                    if(host.getId().equals(partitionId)) {
//                                        computeService = host.getComputeService();
//                                        template = host.getTemplate();
//                                    }
//                                }
//                            }
//                        }
//                    }
                    if (template == null) {
                        String msg = "Failed to start an instance in "
                                + iaas.getType()
                                + ". Reason : Template is null. You have not specify a matching service "
                                + "element in the configuration file of Autoscaler.\n Hence, will try to "
                                + "start in another IaaS if available.";
                        log.error(msg);
                        throw new CloudControllerException(msg);
                    }

                    // set instance name as the host name
                    // template.getOptions().userMetadata("Name",
                    // serviceCtxt.getHostName());
                    // template.getOptions().as(TemplateOptions.class).userMetadata("Name",
                    // serviceCtxt.getHostName());

                    // generate the group id from domain name and sub domain
                    // name.
                    // Should have lower-case ASCII letters, numbers, or dashes.
                    // Should have a length between 3-15
                    String str = clusterId.substring(0, 10);
                    String group = str.replaceAll("[^a-z0-9-]", "");

                    NodeMetadata node;

                    // create and start a node
                    Set<? extends NodeMetadata> nodes = computeService
                            .createNodesInGroup(group, 1, template);

                    node = nodes.iterator().next();

                    String autoAssignIpProp = iaas
                            .getProperty(CloudControllerConstants.AUTO_ASSIGN_IP_PROPERTY);

                    // acquire the lock
                    lock.lock();

                    try {
                        // reset ip
                        ip = "";
                        // default behavior is autoIpAssign=false
                        if (autoAssignIpProp == null || (autoAssignIpProp != null && autoAssignIpProp.equals("false"))) {
                            // allocate an IP address - manual IP assigning mode
                            ip = iaas.getIaas().associateAddress(iaas, node);
                            log.info("Allocated ip address: " + ip);
                        }

                        if (ip.isEmpty() && node.getPublicAddresses() != null && node.getPublicAddresses().iterator().hasNext()) {
                            ip = node.getPublicAddresses().iterator().next();
                            log.info("Public ip address: " + ip);
                        }

                        // if not public IP is assigned, we're using private IP
                        if (ip.isEmpty() && node.getPrivateAddresses() != null && node.getPrivateAddresses().iterator().hasNext()) {
                            ip = node.getPrivateAddresses().iterator().next();
                            log.info("Private ip address: " + ip);
                        }

                        if (node.getId() == null) {
                            String msg = "Node id of the starting instance is null.\n" + node.toString();
                            log.fatal(msg);
                            throw new CloudControllerException(msg);
                        }
                        
                        MemberContext memberCtxt = new MemberContext(memberID, nodeId, clusterId, partitionId, cartridgeType, ip);
                        dataHolder.addMemberContext(memberCtxt);

                        // add node ID
//                        ctxt.addNodeId(node.getId());
//                        ctxt.addNodeToPublicIp(node.getId(), ip);

                        // to faster look up
//                        dataHolder.addNodeId(
//                                node.getId(), serviceCtxt);
//                        
//                        dataHolder.addNodeId(memberID, node.getId());
//
//                        serviceCtxt.getCartridge().setLastlyUsedIaas(iaas);
//
//                        // add this ip to the topology
//                        appendToPublicIpProperty(ip, serviceCtxt);
//
//                        dataHolder.updateActiveInstanceCount(iaas.getType(), 1);

                        // persist in registry
                        persist();

                        // trigger topology
                        TopologyBuilder.handleMemberSpawned(memberID, cartridgeType, clusterId,
                                 nodeId, partition, privateIp);

                        //update the topology with the newly spawned member
                        // publish data
//                        CartridgeInstanceDataPublisherTask.publish();
                        if (log.isDebugEnabled()) {
                            log.debug("Node details: \n" + node.toString()
                                    + "\n***************\n");
                        }

                        log.info("Instance is successfully starting up in IaaS "
                                + iaas.getType()
                                + ".\tIP Address(public/private): "
                                + ip
                                + "\tNode Id: " + nodeId);

                        return ip;

                    } finally {
                        // release the lock
                        lock.unlock();
                    }

                    } catch (Exception e) {
                        log.warn(
                                "Failed to start an instance in "
                                        + iaas.getType()
                                        + ". Hence, will try to start in another IaaS if available.",
                                e);
                    }
            }

//        }
        return null;

	}

//    @Override
//    public String startInstances(String clusterId, Partition partition, int noOfInstancesToBeSpawned) {
//        //TODO
//        return null;
//    }

    /**
	 * Appends this ip to the Service Context's
	 * {@link CloudControllerConstants#PUBLIC_IP_PROPERTY}
	 * 
	 * @param ip
	 * @param serviceCtxt
	 */
	private void appendToPublicIpProperty(String ip, ServiceContext serviceCtxt) {
		String ipStr = serviceCtxt
				.getProperty(CloudControllerConstants.PUBLIC_IP_PROPERTY);
		if (ip != null && !"".equals(ip)) {
			serviceCtxt.setProperty(
					CloudControllerConstants.PUBLIC_IP_PROPERTY,
					("".equals(ipStr) ? "" : ipStr
							+ CloudControllerConstants.ENTRY_SEPARATOR)
							+ ip);
		}
	}

	/**
	 * Persist data in registry.
	 */
	private void persist() {
//		try {
//			RegistryManager.getInstance().persist(
//					dataHolder);
//		} catch (RegistryException e) {
//
//			String msg = "Failed to persist the Cloud Controller data in registry. Further, transaction roll back also failed.";
//			log.fatal(msg);
//			throw new CloudControllerException(msg, e);
//		}
	}

    private String generateMemberId(String clusterId) {
        UUID memberId = UUID.randomUUID();
         return clusterId + memberId.toString();
    }

	private Cartridge loadCartridge(String cartridgeType,
			List<Cartridge> cartridges) {

		for (Cartridge cartridge : cartridges) {
			if (cartridge.getType().equals(cartridgeType)) {
                return cartridge;
			}
		}

		return null;
	}

    private void reloadPayload(Cartridge cartridge, byte[] payload) {
        for (IaasProvider iaas : cartridge.getIaases()) {
					iaas.setPayload(payload);
        }

    }
    
    @Override
    public void terminateInstance(String memberId) throws InvalidMemberException, InvalidCartridgeTypeException, 
    IllegalArgumentException{

        if(memberId == null) {
            String msg = "Termination failed. Null member id.";
            log.error(msg);
            throw new IllegalArgumentException(msg);
        }
//        String nodeId = dataHolder.getNodeId(memberId);
        MemberContext ctxt = dataHolder.getMemberContextOfMemberId(memberId);
        
        if(ctxt == null) {
            String msg = "Termination failed. Invalid Member Id: "+memberId;
            log.error(msg);
            throw new InvalidMemberException(msg);
        }
        
        ThreadExecutor exec = new ThreadExecutor();
        exec.execute(new InstanceTerminator(ctxt));
        exec.shutdown();

       
		

//		}

//		log.info("Termination of an instance which is belong to domain '"
//				+ clusterId
//				+ "' , failed! Reason: No matching "
//				+ "running instance found in any available IaaS.");
//
//		return false;

	}
    
    private class InstanceTerminator implements Runnable {

        private MemberContext ctxt;

        public InstanceTerminator(MemberContext ctxt) {
            this.ctxt = ctxt;
        }

        @Override
        public void run() {

            String memberId = ctxt.getMemberId();
            String clusterId = ctxt.getClusterId();
            String partitionId = ctxt.getPartitionId();
            String cartridgeType = ctxt.getCartridgeType();
            String nodeId = ctxt.getNodeId();

            try {
                // these will never be null, since we do not add null values for these.
                Cartridge cartridge = dataHolder.getCartridge(cartridgeType);

                log.info("Starting to terminate an instance with member id : " + memberId +
                         " in partition id: " + partitionId + " of cluster id: " + clusterId +
                         " and of cartridge type: " + cartridgeType);

                if (cartridge == null) {
                    String msg =
                                 "Termination of Member Id: " + memberId + " failed. " +
                                         "Cannot find a matching Cartridge for type: " +
                                         cartridgeType;
                    log.error(msg);
                    throw new InvalidCartridgeTypeException(msg);
                }

                // if no matching node id can be found.
                if (nodeId == null) {

                    String msg =
                                 "Termination failed. Cannot find a node id for Member Id: " +
                                         memberId;
                    log.error(msg);
                    throw new InvalidMemberException(msg);
                }
                // ServiceContext serviceCtxt = dataHolder
                // .getServiceContextFromDomain(clusterId);
                //
                // if (serviceCtxt == null) {
                // String msg = "Not a registered service: domain - " + clusterId;
                // log.fatal(msg);
                // throw new CloudControllerException(msg);
                // }
                //
                // // load Cartridge, if null
                // //if (serviceCtxt.getCartridge() == null) {
                // serviceCtxt.setCartridge(loadCartridge(
                // serviceCtxt.getCartridgeType(),
                // dataHolder.getCartridges()));
                // //}
                //
                // // if still, Cartridge is null
                // if (serviceCtxt.getCartridge() == null) {
                // String msg = "There's no registered Cartridge found. Domain - "
                // + clusterId;
                // log.fatal(msg);
                // throw new CloudControllerException(msg);
                // }

                // for (IaasProvider iaas : serviceCtxt.getCartridge().getIaases()) {

                IaasProvider iaas = cartridge.getIaasProviderOfPartition(partitionId);



                // // terminate the last instance first
                // for (String id : Lists.reverse(ctxt.getNodeIds())) {
                // if (id != null) {
                // nodeId = id;
                // break;
                // }
                // }

                // terminate it!
                terminate(iaas, nodeId, ctxt);

                // log information
                logTermination(nodeId, ctxt);

            } catch (Exception e) {
                String msg =
                             "Starting to terminate an instance with member id : " + memberId +
                                     " in partition id: " + partitionId + " of cluster id: " +
                                     clusterId + " and of cartridge type: " + cartridgeType;
                log.error(msg);
                throw new CloudControllerException(msg);
            }

        }
    }

//    private
//        void
//        terminateInstance(MemberContext ctxt) throws InvalidCartridgeTypeException,
//            InvalidMemberException {
//        // these will never be null, since we do not add null values for these.
//        String memberId = ctxt.getMemberId();
//        String clusterId = ctxt.getClusterId();
//        String partitionId = ctxt.getPartitionId();
//        String cartridgeType = ctxt.getCartridgeType();
//        String nodeId = ctxt.getNodeId();
//        
//        Cartridge cartridge = dataHolder.getCartridge(cartridgeType);
//        
//        log.info("Starting to terminate an instance with member id : " + memberId+
//                 " in partition id: "+partitionId+" of cluster id: "+clusterId+ " and of cartridge type: "+cartridgeType);
//        
//        if(cartridge == null) {
//            String msg = "Termination of Member Id: "+memberId+" failed. " +
//                    "Cannot find a matching Cartridge for type: "+cartridgeType;
//            log.error(msg);
//            throw new InvalidCartridgeTypeException(msg);
//        }
//        
////        Scope scope = partition.getScope();
////        String provider = partition.getProperty("provider");
//
//		// if no matching node id can be found.
//        if (nodeId == null) {
//
//            String msg = "Termination failed. Cannot find a node id for Member Id: "+memberId;
//            log.error(msg);
//            throw new InvalidMemberException(msg);
//        }
////		ServiceContext serviceCtxt = dataHolder
////				.getServiceContextFromDomain(clusterId);
////
////		if (serviceCtxt == null) {
////			String msg = "Not a registered service: domain - " + clusterId;
////			log.fatal(msg);
////			throw new CloudControllerException(msg);
////		}
////
////		// load Cartridge, if null
////		//if (serviceCtxt.getCartridge() == null) {
////			serviceCtxt.setCartridge(loadCartridge(
////					serviceCtxt.getCartridgeType(),
////					dataHolder.getCartridges()));
////		//}
////
////		// if still, Cartridge is null
////		if (serviceCtxt.getCartridge() == null) {
////			String msg = "There's no registered Cartridge found. Domain - "
////					+ clusterId;
////			log.fatal(msg);
////			throw new CloudControllerException(msg);
////		}
//
////        for (IaasProvider iaas : serviceCtxt.getCartridge().getIaases()) {
//
//		IaasProvider iaas = cartridge.getIaasProviderOfPartition(partitionId);
//		
////			String msg = "Failed to terminate an instance in "
////					+ iaas.getType()
////					+ ". Hence, will try to terminate an instance in another IaaS if possible.";
////            //TODO adding more locations and retrieve it from the request received
////                String nodeId = null;
//
////                IaasContext ctxt = serviceCtxt.getIaasContext(iaas.getType());
//
////                // terminate the last instance first
////                for (String id : Lists.reverse(ctxt.getNodeIds())) {
////                    if (id != null) {
////                        nodeId = id;
////                        break;
////                    }
////                }
//
//                
//
//                // terminate it!
//                terminate(iaas, nodeId, ctxt);
//
//                // log information
//                logTermination(nodeId, ctxt);
//    }

//    @Override
//    public boolean terminateInstances(String[] memberIds) throws IllegalArgumentException, InvalidMemberException, InvalidCartridgeTypeException {
//        for (String memberId : memberIds) {
//            terminateInstance(memberId);
//        }
//    }

//    @Override
//    public boolean terminateUnhealthyInstances(List<String> instancesToBeTerminated) {
//        log.info("vvvvvvvvvvdddvvvvvvv");
//        return false;  //TODO
//    }

	@Override
	public void terminateAllInstances(String clusterId) throws IllegalArgumentException, InvalidClusterException {

		log.info("Starting to terminate all instances of cluster : "
				+ clusterId);
		
		if(clusterId == null) {
		    String msg = "Instance termination failed. Cluster id is null.";
		    log.error(msg);
		    throw new IllegalArgumentException(msg);
		}
		
		List<MemberContext> ctxts = dataHolder.getMemberContextsOfClusterId(clusterId);
		
		if(ctxts == null) {
		    String msg = "Instance termination failed. No members found for cluster id: "+clusterId;
		    log.error(msg);
		    throw new InvalidClusterException(msg);
		}
		
		ThreadExecutor exec = new ThreadExecutor();
		for (MemberContext memberContext : ctxts) {
            exec.execute(new InstanceTerminator(memberContext));
        }
		
		exec.shutdown();

//		ServiceContext serviceCtxt = dataHolder
//				.getServiceContextFromDomain(clusterId);
//
//		if (serviceCtxt == null) {
//			String msg = "Not a registered service: domain - " + clusterId;
//			log.fatal(msg);
//			throw new CloudControllerException(msg);
//		}
//
//		// load Cartridge, if null
//		serviceCtxt.setCartridge(loadCartridge(
//					serviceCtxt.getCartridgeType(),
//					dataHolder.getCartridges()));
//		//}
//
//		if (serviceCtxt.getCartridge() == null) {
//			String msg = "There's no registered Cartridge found. Domain - "
//					+ clusterId;
//			log.fatal(msg);
//			throw new CloudControllerException(msg);
//		}

//        for (IaasProvider iaas : serviceCtxt.getCartridge().getIaases()) {
//
//			IaasContext ctxt = serviceCtxt.getIaasContext(iaas.getType());
//
//			if (ctxt == null) {
//				log.error("Iaas Context for " + iaas.getType()
//						+ " not found. Cannot terminate instances");
//				continue;
//			}
//
//			ArrayList<String> temp = new ArrayList<String>(ctxt.getNodeIds());
//			for (String id : temp) {
//				if (id != null) {
//					// terminate it!
//                    //TODO need to enable once partition added to the topology
//                    /*Collection<Member> members = TopologyManager.getInstance().getTopology().
//                            getService(serviceCtxt.getCartridgeType()).
//                            getCluster(serviceCtxt.getClusterId()).getMembers();
//                    for (Iterator iterator = members.iterator(); iterator.hasNext();) {
//                         Member member = (Member) iterator.next();
//                         terminate(iaas, ctxt, member.getIaasNodeId(), member.getPartition());
//                    }*/
//
//					// log information
//					logTermination(id, ctxt, serviceCtxt);
//
//					isAtLeastOneTerminated = true;
//				}
//			}
//		}
//
//		if (isAtLeastOneTerminated) {
//			return true;
//		}
//
//		log.info("Termination of an instance which is belong to domain '"
//				+ clusterId + "', failed! Reason: No matching "
//				+ "running instance found in lastly used IaaS.");
//
//		return false;

	}

	public int getPendingInstanceCount(String clusterId) {
        int pendingInstanceCount = 0;

		ServiceContext subjectedSerCtxt = dataHolder
				.getServiceContextFromDomain(clusterId);

		if (subjectedSerCtxt != null
				&& subjectedSerCtxt.getCartridgeType() != null) {

			// load cartridge
			subjectedSerCtxt.setCartridge(loadCartridge(subjectedSerCtxt
					.getCartridgeType(),
					dataHolder.getCartridges()));

			if (subjectedSerCtxt.getCartridge() == null) {
				return pendingInstanceCount;
			}

			List<IaasProvider> iaases = subjectedSerCtxt.getCartridge()
					.getIaases();

			for (IaasProvider iaas : iaases) {

				ComputeService computeService = iaas.getComputeService();

				IaasContext ctxt;
				if ((ctxt = subjectedSerCtxt.getIaasContext(iaas.getType())) == null) {
					ctxt = subjectedSerCtxt.addIaasContext(iaas.getType());
				}

				// get list of node Ids which are belong to this domain- sub
				// domain
				List<String> nodeIds = ctxt.getNodeIds();

				if (nodeIds.isEmpty()) {
					log.debug("Zero nodes spawned in the IaaS "
							+ iaas.getType() + " of domain: " + clusterId);
					continue;
				}

				// get all the nodes spawned by this IaasContext
				Set<? extends ComputeMetadata> set = computeService.listNodes();

                // traverse through all nodes of this ComputeService object
                for (ComputeMetadata aSet : set) {
                    NodeMetadataImpl nodeMetadata = (NodeMetadataImpl) aSet;

                    // if this node belongs to the requested domain
                    if (nodeIds.contains(nodeMetadata.getId())) {

                        // get the status of the node
                        Status nodeStatus = nodeMetadata.getStatus();

                        // count nodes that are in pending state
                        if (nodeStatus.equals(Status.PENDING)) {
                            pendingInstanceCount++;
                        }
                    }

                }
			}
		}

		log.debug("Pending instance count of domain '" + clusterId
				+ " is "
				+ pendingInstanceCount);

		return pendingInstanceCount;

	}

	/**
	 * A helper method to terminate an instance.
     * @param iaasTemp
     * @param ctxt
     * @param nodeId
     * @return will return the IaaSProvider
     */
	private IaasProvider terminate(IaasProvider iaasTemp, 
			String nodeId, MemberContext ctxt) {
//        Scope scope = partition.getScope();
//        String partitionId = partition.getId();
//		// this is just to be safe
//		if (iaasTemp.getComputeService() == null) {
//			String msg = "Unexpeced error occured! IaasContext's ComputeService is null!";
//			log.error(msg);
//			throw new CloudControllerException(msg);
//		}
//
//        if(scope == null || scope.name().equals(Scope.PROVIDER.name())) {
//            iaasTemp.getComputeService().destroyNode(nodeId);
//        } else if(scope.name().equals(Scope.REGION.name())) {
//            for(Region region : iaasTemp.getListOfRegions()) {
//                if(region.getId().equals(partitionId)) {
//                    region.getComputeService();
//                }
//            }
//        } else if(scope.name().equals(Scope.ZONE.name())) {
//            for(Region region : iaasTemp.getListOfRegions()) {
//                for(Zone zone : region.getListOfZones()) {
//                   if(zone.getId().equals(partitionId)) {
//                        zone.getComputeService().destroyNode(nodeId);
//                   }
//                }
//            }
//        } else if(scope.name().equals(Scope.HOST.name())) {
//            for(Region region : iaasTemp.getListOfRegions()) {
//                for(Zone zone : region.getListOfZones()) {
//                    for(Host host: zone.getListOfHosts()) {
//                        if(host.getId().equals(partitionId)) {
//                            host.getComputeService().destroyNode(nodeId);
//                        }
//                    }
//                }
//            }
//        }
		// destroy the node
		iaasTemp.getComputeService().destroyNode(nodeId);

		String autoAssignIpProp = iaasTemp
				.getProperty(CloudControllerConstants.AUTO_ASSIGN_IP_PROPERTY);

		// release allocated IP address
		if (autoAssignIpProp == null
				|| (autoAssignIpProp
						.equals("false"))) {
			// allocate an IP address - manual IP assigning mode
			iaasTemp.getIaas().releaseAddress(iaasTemp,
					ctxt.getAllocatedIpAddress());
		}
//
//		// remove the node id
//		ctxt.removeNodeId(nodeId);

//		dataHolder.updateActiveInstanceCount(iaasTemp.getType(), -1);

		// publish data to BAM
//		CartridgeInstanceDataPublisherTask.publish();

		log.info("Node with Id: '" + nodeId + "' is terminated!");
		return iaasTemp;
	}

	private void logTermination(String nodeId, MemberContext ctxt) {

		// get the ip of the terminated node
//		String ip = ctxt.getPublicIp(nodeId);
//		String ipProp = CloudControllerConstants.PUBLIC_IP_PROPERTY;
//		String ipStr = serviceCtxt.getProperty(ipProp);
//		StringBuilder newIpStr = new StringBuilder("");
//
//		for (String str : ipStr.split(CloudControllerConstants.ENTRY_SEPARATOR)) {
//			if (!str.equals(ip)) {
//				newIpStr.append(str + CloudControllerConstants.ENTRY_SEPARATOR);
//			}
//		}
//
//		// add this ip to the topology
//		serviceCtxt.setProperty(ipProp, newIpStr.length() == 0 ? "" : newIpStr
//				.substring(0, newIpStr.length() - 1).toString());
        //updating the topology
        TopologyBuilder.handleMemberTerminated(ctxt.getCartridgeType(), ctxt.getClusterId(), nodeId);

		// remove the reference
//		ctxt.removeNodeIdToPublicIp(nodeId);

		// persist
		persist();

		//handle the termination event


	}

	/**
	 * Comparator to compare {@link IaasProvider} on different attributes.
	 */
	public enum IaasProviderComparator implements Comparator<IaasProvider> {
		SCALE_UP_SORT {
			public int compare(IaasProvider o1, IaasProvider o2) {
				return Integer.valueOf(o1.getScaleUpOrder()).compareTo(
						o2.getScaleUpOrder());
			}
		},
		SCALE_DOWN_SORT {
			public int compare(IaasProvider o1, IaasProvider o2) {
				return Integer.valueOf(o1.getScaleDownOrder()).compareTo(
						o2.getScaleDownOrder());
			}
		};

		public static Comparator<IaasProvider> ascending(
				final Comparator<IaasProvider> other) {
			return new Comparator<IaasProvider>() {
				public int compare(IaasProvider o1, IaasProvider o2) {
					return other.compare(o1, o2);
				}
			};
		}

		public static Comparator<IaasProvider> getComparator(
				final IaasProviderComparator... multipleOptions) {
			return new Comparator<IaasProvider>() {
				public int compare(IaasProvider o1, IaasProvider o2) {
					for (IaasProviderComparator option : multipleOptions) {
						int result = option.compare(o1, o2);
						if (result != 0) {
							return result;
						}
					}
					return 0;
				}
			};
		}
	}

	@Override
	public boolean registerService(Registrant registrant)
			throws UnregisteredCartridgeException, IllegalArgumentException {

	    String cartridgeType = registrant.getCartridgeType();
	    String clusterId = registrant.getClusterId();
        String payload = registrant.getPayload();
        
        if(cartridgeType == null || clusterId == null || payload == null) {
	        String msg = "Null Argument/s detected: Cartridge type: "+cartridgeType+", Cluster Id: "+clusterId+", Payload: "+payload;
	        log.error(msg);
	        throw new IllegalArgumentException(msg);
	    }
	    
        if (dataHolder.getCartridge(cartridgeType) == null) {

            String msg = "Registration of cluster: "+clusterId+
                    " failed. - Unregistered Cartridge type: " + cartridgeType;
            log.error(msg);
            throw new UnregisteredCartridgeException(msg);
        }
        
	    dataHolder.addClusterContext(new ClusterContext(clusterId, cartridgeType, payload));
	    TopologyBuilder.handleClusterCreated(registrant);
	    
//		// create a ServiceContext dynamically
//		ServiceContext newServiceCtxt = new ServiceContext();
//        newServiceCtxt.setClusterId(clusterId);
//		//newServiceCtxt.setSubDomainName(subDomain);
//		newServiceCtxt.setTenantRange(registrant.getTenantRange());
//		newServiceCtxt.setHostName(registrant.getHostName());
//        newServiceCtxt.setAutoScalerPolicyName(registrant.getAutoScalerPolicyName());
//        newServiceCtxt.setPayload(new StringBuilder(registrant.getPayload()));
//        Properties properties = registrant.getProperties();
//        String cartridgeType = registrant.getCartridgeType();
//
//		if (properties != null && properties.getProperties() != null) {
//			// add properties
//			for (Property property : properties.getProperties()) {
//				if (property != null && property.getName() != null) {
//					newServiceCtxt.setProperty(property.getName(),
//							property.getValue());
//				}
//			}
//		}
//
//		newServiceCtxt.setCartridgeType(cartridgeType);
//
//		for (Cartridge cartridge : dataHolder
//				.getCartridges()) {
//			if (cartridge.getType().equals(cartridgeType)) {
//				newServiceCtxt.setCartridge(cartridge);
//				break;
//			}
//		}
//
//		if (newServiceCtxt.getCartridge() == null) {
//			String msg = "Registration failed - Unregistered Cartridge type: "
//					+ cartridgeType;
//			log.error(msg);
//			throw new UnregisteredCartridgeException(msg);
//		}
//		// persist
//        String uniqueName = clusterId + "-"
//                + UUID.randomUUID() + ".xml";
//        newServiceCtxt.setPayloadFile("/tmp/" + CloudControllerConstants.PAYLOAD_NAME + ".zip");
//        newServiceCtxt.generatePayload();
//        dataHolder.addServiceContext(newServiceCtxt);
//        // notify consumer by adding services
//        TopologyBuilder.handleClusterCreated(newServiceCtxt);
//        try {
//            FileUtils.writeStringToFile(new File(CloudControllerConstants.SERVICES_DIR + uniqueName),
//                                            newServiceCtxt.toXml());
//        } catch (IOException e) {
//            log.error("couldn't write the service definition", e);
//            throw new CloudControllerException("couldn't write the service definition", e);
//        }
//        log.info("Service successfully registered! Domain - " + clusterId
//                + ", Cartridge type - " + cartridgeType);

		return true;
	}

	@Override
	public String[] getRegisteredCartridges() {
		// get the list of cartridges registered
		List<Cartridge> cartridges = dataHolder
				.getCartridges();

		if (cartridges == null) {
			return new String[0];
		}

		String[] cartridgeTypes = new String[cartridges.size()];
		int i = 0;

		for (Cartridge cartridge : cartridges) {
			cartridgeTypes[i] = cartridge.getType();
			i++;
		}

		return cartridgeTypes;
	}

	/*private String checkSubDomain(String subDomainName) {
		// if sub domain is null, we assume it as default one.
		if (subDomainName == null || "null".equalsIgnoreCase(subDomainName)) {
			subDomainName = Constants.DEFAULT_SUB_DOMAIN;
			log.debug("Sub domain is null, hence using the default value : "
					+ subDomainName);
		}

		return subDomainName;
	}*/

	@Override
	public CartridgeInfo getCartridgeInfo(String cartridgeType)
			throws UnregisteredCartridgeException {
		Cartridge cartridge = dataHolder
				.getCartridge(cartridgeType);

		if (cartridge != null) {

			return CloudControllerUtil.toCartridgeInfo(cartridge);

		}

		String msg = "Cannot find a Cartridge having a type of "
				+ cartridgeType + ". Hence unable to find information.";
		log.error(msg);
		throw new UnregisteredCartridgeException(msg);
	}

    @Override
	public void unregisterService(String clusterId) throws UnregisteredClusterException {
        ClusterContext ctxt = dataHolder.getClusterContext(clusterId);
        
        if(ctxt == null) {
            String msg = "Unregistration of service cluster failed. Cluster not found: "+clusterId;
            log.error(msg);
            throw new UnregisteredClusterException(msg);
        }
        
        TopologyBuilder.handleClusterRemoved(ctxt);

        dataHolder.removeClusterContext(clusterId);
        dataHolder.removeMemberContext(clusterId);
        
		//subDomain = checkSubDomain(subDomain);

		// find the service context
//		ServiceContext subjectedSerCtxt = dataHolder
//				.getServiceContextFromDomain(clusterId);
        
        
        
//        TopologyBuilder.handleClusterRemoved(subjectedSerCtxt);

//		if (subjectedSerCtxt == null) {
//			throw new UnregisteredClusterException(
//					"No registered service found for domain: " + clusterId);
//		}
//
//		// get the service definition file.
//		File serviceDefFile = subjectedSerCtxt.getFile();
//
//		// delete that file, so that it gets automatically undeployed.
//		return serviceDefFile.delete();
	}

		

    @Override
    public boolean validateDeploymentPolicy(String cartridgeType, DeploymentPolicy deploymentPolicy) 
            throws InvalidPartitionException, InvalidCartridgeTypeException {

        Map<String, IaasProvider> partitionToIaasProviders = new ConcurrentHashMap<String, IaasProvider>();
        
        Cartridge cartridge = dataHolder.getCartridge(cartridgeType);
        
        if(cartridge == null) {
            String msg = "Invalid Cartridge Type: "+cartridgeType;
            log.error(msg);
            throw new InvalidCartridgeTypeException(msg);
        }
        
        if(deploymentPolicy == null) {
            String msg = "Deployment Policy is null.";
            log.error(msg);
            throw new InvalidPartitionException(msg);
        }
        
        for (PartitionGroup partitionGroup : deploymentPolicy.getPartitionGroups()) {
            for (Partition partition : partitionGroup.getPartitions()) {
                String provider = partition.getProvider();
                IaasProvider iaasProvider = cartridge.getIaasProvider(provider);
                
                if(iaasProvider == null) {
                    String msg = "Invalid Partition - "+partition.toString() +", in "+deploymentPolicy.toString()+
                            ". Cause: Iaas Provider is null for: "+provider;
                    log.error(msg);
                    throw new InvalidPartitionException(msg);
                }
                
                Iaas iaas = iaasProvider.getIaas();
                PartitionValidator validator = iaas.getPartitionValidator();
                validator.setIaasProvider(iaasProvider);
                IaasProvider updatedIaasProvider = validator.validate(partition.getId(), 
                                 CloudControllerUtil.toJavaUtilProperties(partition.getProperties()));
                // add to a temporary Map
                partitionToIaasProviders.put(partition.getId(), updatedIaasProvider);
                
            }
        }
        
        // if and only if the deployment policy valid
        cartridge.addIaasProviders(partitionToIaasProviders);
        
        return true;
    }

    @Override
    public boolean validatePartition(Partition partition) throws InvalidPartitionException {
        String provider = partition.getProvider();
        IaasProvider iaasProvider = dataHolder.getIaasProvider(provider);

        if (iaasProvider == null) {
            String msg =
                         "Invalid Partition - " + partition.toString()+". Cause: Iaas Provider is null for: "+provider;
            log.error(msg);
            throw new InvalidPartitionException(msg);
        }

        Iaas iaas = iaasProvider.getIaas();
        PartitionValidator validator = iaas.getPartitionValidator();
        validator.setIaasProvider(iaasProvider);
        validator.validate(partition.getId(),
                           CloudControllerUtil.toJavaUtilProperties(partition.getProperties()));
        TopologyBuilder.handlePartitionCreated(partition);

        return true;
    }

}
