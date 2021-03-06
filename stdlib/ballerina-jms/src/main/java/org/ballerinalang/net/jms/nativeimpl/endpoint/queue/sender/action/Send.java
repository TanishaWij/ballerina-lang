/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package org.ballerinalang.net.jms.nativeimpl.endpoint.queue.sender.action;

import org.ballerinalang.bre.Context;
import org.ballerinalang.bre.bvm.CallableUnitCallback;
import org.ballerinalang.connector.api.Struct;
import org.ballerinalang.model.types.TypeKind;
import org.ballerinalang.model.values.BStruct;
import org.ballerinalang.natives.annotations.Argument;
import org.ballerinalang.natives.annotations.BallerinaFunction;
import org.ballerinalang.natives.annotations.Receiver;
import org.ballerinalang.net.jms.AbstractBlockinAction;
import org.ballerinalang.net.jms.Constants;
import org.ballerinalang.net.jms.utils.BallerinaAdapter;
import org.ballerinalang.util.exceptions.BallerinaException;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;

/**
 * {@code Send} is the send action implementation of the JMS Connector.
 */
@BallerinaFunction(orgName = "ballerina",
                   packageName = "jms",
                   functionName = "send",
                   receiver = @Receiver(type = TypeKind.STRUCT,
                                        structType = "QueueSenderConnector",
                                        structPackage =
                                                "ballerina.jms"),
                   args = {
                           @Argument(name = "message",
                                     type = TypeKind.STRUCT)
                   },
                   isPublic = true
)
public class Send extends AbstractBlockinAction {

    @Override
    public void execute(Context context, CallableUnitCallback callableUnitCallback) {

        Struct queueSenderBObject = BallerinaAdapter.getReceiverStruct(context);
        MessageProducer messageProducer = BallerinaAdapter.getNativeObject(queueSenderBObject,
                                                                           Constants.JMS_QUEUE_SENDER_OBJECT,
                                                                           MessageProducer.class,
                                                                           context
        );

        BStruct messageBObject = ((BStruct) context.getRefArgument(1));
        Message message = BallerinaAdapter.getNativeObject(messageBObject,
                                                           Constants.JMS_MESSAGE_OBJECT,
                                                           Message.class,
                                                           context);

        try {
            messageProducer.send(message);
        } catch (JMSException e) {
            throw new BallerinaException("Message sending failed", e, context);
        }
    }
}
