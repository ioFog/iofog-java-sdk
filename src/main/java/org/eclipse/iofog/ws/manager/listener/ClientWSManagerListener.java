/*
 * *******************************************************************************
 *   Copyright (c) 2018 Edgeworx, Inc.
 *
 *   This program and the accompanying materials are made available under the
 *   terms of the Eclipse Public License v. 2.0 which is available at
 *   http://www.eclipse.org/legal/epl-2.0
 *
 *   SPDX-License-Identifier: EPL-2.0
 * *******************************************************************************
 */

package org.eclipse.iofog.ws.manager.listener;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.eclipse.iofog.api.listener.IOFogAPIListener;
import org.eclipse.iofog.elements.IOMessage;
import org.eclipse.iofog.utils.ByteUtils;
import org.eclipse.iofog.utils.IOFogLocalAPIURL;
import org.eclipse.iofog.ws.manager.WebSocketManager;

import java.util.Arrays;
import java.util.Collections;

/**
 * Implementation of {@link WebSocketManagerListener}.
 * According to specification handles next transmissions' codes:
 * 1. If Control WebSocket Connection is handles ->
 *    - In case of receiving NEW_CONFIGURATION_SIGNAL from ioFog, Container responds with ACKNOWLEDGE response.
 * 2. If Message WebSocket Connection is handled ->
 *    - In case of receiving MESSAGE from ioFog, Container responds with ACKNOWLEDGE response.
 *    - In case of receiving MESSAGE_RECEIPT from ioFog, Container responds with ACKNOWLEDGE response.
 *
 * @author ilaryionava
 */
public class ClientWSManagerListener implements WebSocketManagerListener {

    private IOFogAPIListener wsListener;
    private IOFogLocalAPIURL wsType;

    public ClientWSManagerListener(IOFogAPIListener listener, IOFogLocalAPIURL wsType){
        this.wsListener = listener;
        this.wsType = wsType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handle(WebSocketManager wsManager, BinaryWebSocketFrame frame, ChannelHandlerContext ctx) {
        ByteBuf content = frame.content();
        if(content.isReadable()){
            byte[] byteArray = new byte[content.readableBytes()];
            int readerIndex = content.readerIndex();
            content.getBytes(readerIndex, byteArray);
            byte opcode = byteArray[0];
            if (isControlSignal(opcode)) {
                handleNewConfigSignal(wsManager, ctx);
            } else if (isNewMessage(opcode)) {
                handleNewMessage(byteArray, wsManager, ctx);
            } else if (isMessageReceipt(opcode)) {
                handleMessageReceipt(byteArray, wsManager, ctx);
            }
        }
    }

    private void handleNewConfigSignal(WebSocketManager wsManager, ChannelHandlerContext ctx) {
        wsListener.onNewConfigSignal();
        wsManager.sendAck(ctx);
    }

    private void handleNewMessage(byte[] byteArray, WebSocketManager wsManager, ChannelHandlerContext ctx) {
        int totalMsgLength = ByteUtils.bytesToInteger(Arrays.copyOfRange(byteArray, 1, 5));
        int msgLength = totalMsgLength + 5;
        IOMessage message = new IOMessage(Arrays.copyOfRange(byteArray, 5, msgLength));
        wsListener.onMessages(Collections.singletonList(message));
        wsManager.sendAck(ctx);
    }

    private void handleMessageReceipt(byte[] byteArray, WebSocketManager wsManager, ChannelHandlerContext ctx) {
        int size = byteArray[1];
        int pos = 3;
        String messageId = "";
        if (size > 0) {
            messageId = ByteUtils.bytesToString(Arrays.copyOfRange(byteArray, pos, pos + size));
            pos += size;
        }
        size = byteArray[2];
        long timestamp = 0L;
        if (size > 0) {
            timestamp = ByteUtils.bytesToLong(Arrays.copyOfRange(byteArray, pos, pos + size));
        }
        IOMessage message = new IOMessage(wsManager.getMessage(ctx));
        message.setId(messageId);
        message.setTimestamp(timestamp);
        wsListener.onMessageReceipt(messageId, timestamp);
        wsManager.sendAck(ctx);
    }

    private boolean isControlSignal(byte opcode) {
        return opcode == WebSocketManager.OPCODE_CONTROL_SIGNAL.intValue() && wsType == IOFogLocalAPIURL.GET_CONTROL_WEB_SOCKET_LOCAL_API;
    }

    private boolean isNewMessage(byte opcode) {
        return opcode == WebSocketManager.OPCODE_MSG.intValue() && wsType == IOFogLocalAPIURL.GET_MSG_WEB_SOCKET_LOCAL_API;
    }

    private boolean isMessageReceipt(byte opcode) {
        return opcode == WebSocketManager.OPCODE_RECEIPT.intValue() && wsType == IOFogLocalAPIURL.GET_MSG_WEB_SOCKET_LOCAL_API;
    }
}
