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
package org.apache.stratos.autoscaler.grouping.dependency.context;

import org.apache.stratos.messaging.domain.topology.Status;

import java.util.ArrayList;
import java.util.List;

/**
 * This is to keep track of the
 */
public abstract class ApplicationContext {
    private List<ApplicationContext> applicationContextList;

    private String id;
    protected boolean started;
    protected boolean terminated;

    private Status status;

    private List<Status> statusLifeCycle;

    protected boolean killDependent;

    public ApplicationContext(String id, boolean killDependent) {
        applicationContextList = new ArrayList<ApplicationContext>();
        statusLifeCycle = new ArrayList<Status>();
        this.killDependent = killDependent;
        this.id = id;
    }

    public List<ApplicationContext> getApplicationContextList() {
        return applicationContextList;
    }

    public void setApplicationContextList(List<ApplicationContext> applicationContextList) {
        this.applicationContextList = applicationContextList;
    }

    public void addApplicationContext(ApplicationContext applicationContext) {
        applicationContextList.add(applicationContext);

    }

    public void addStatusToLIfeCycle(Status status) {
       this.statusLifeCycle.add(status);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public List<Status> getStatusLifeCycle() {
        return statusLifeCycle;
    }

    public void setStatusLifeCycle(List<Status> statusLifeCycle) {
        this.statusLifeCycle = statusLifeCycle;
    }
}
