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
package org.apache.stratos.autoscaler.status.checker;

import org.apache.stratos.messaging.listener.EventListener;
import org.apache.stratos.messaging.message.processor.MessageProcessor;

import java.util.LinkedList;

/**
 * This is the abstract class for the status checker
 */
public abstract class StatusProcessorChain {
    protected LinkedList<StatusProcessor> list;

    public StatusProcessorChain() {
        list = new LinkedList<StatusProcessor>();
        initialize();
    }

    protected abstract void initialize();

    public void add(StatusProcessor messageProcessor) {
        if(list.size() > 0) {
            list.getLast().setNext(messageProcessor);
        }
        list.add(messageProcessor);
    }

    public void removeLast() {
        list.removeLast();
        if(list.size() > 0) {
            list.getLast().setNext(null);
        }
    }


}

