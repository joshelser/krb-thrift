/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package joshelser;

import java.io.IOException;
import java.security.PrivilegedExceptionAction;

import javax.security.sasl.SaslServer;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.log4j.Logger;
import org.apache.thrift.TException;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSaslServerTransport;
import org.apache.thrift.transport.TTransport;

/**
 * Processor that pulls the SaslServer object out of the transport, and assumes the remote user's UGI before calling through to the original processor.
 *
 * This is used on the server side to set the UGI for each specific call.
 */
public class TUGIAssumingProcessor implements TProcessor {
  private static final Logger log = Logger.getLogger(TUGIAssumingProcessor.class);
  final TProcessor wrapped;
  boolean useProxy;

  public TUGIAssumingProcessor(TProcessor wrapped, boolean useProxy) {
    this.wrapped = wrapped;
    this.useProxy = useProxy;
  }

  @Override
  public boolean process(final TProtocol inProt, final TProtocol outProt) throws TException {
    TTransport trans = inProt.getTransport();
    if (!(trans instanceof TSaslServerTransport)) {
      throw new TException("Unexpected non-SASL transport " + trans.getClass());
    }
    TSaslServerTransport saslTrans = (TSaslServerTransport) trans;
    SaslServer saslServer = saslTrans.getSaslServer();
    String authId = saslServer.getAuthorizationID();
    String endUser = authId;

    // We do this elsewhere in Accumulo already
    // Socket socket = ((TSocket) (saslTrans.getUnderlyingTransport())).getSocket();
    // remoteAddress.set(socket.getInetAddress());

    UserGroupInformation clientUgi = null;
    try {
      if (useProxy) {
        clientUgi = UserGroupInformation.createProxyUser(endUser, UserGroupInformation.getLoginUser());
        final String remoteUser = clientUgi.getShortUserName();
        log.debug("Set remoteUser :" + remoteUser);
        return clientUgi.doAs(new PrivilegedExceptionAction<Boolean>() {
          @Override
          public Boolean run() {
            try {
              return wrapped.process(inProt, outProt);
            } catch (TException te) {
              throw new RuntimeException(te);
            }
          }
        });
      } else {
        // use the short user name for the request
        UserGroupInformation endUserUgi = UserGroupInformation.createRemoteUser(endUser);
        final String remoteUser = endUserUgi.getShortUserName();
        log.debug("Set remoteUser :" + remoteUser + ", from endUser :" + endUser);
        return wrapped.process(inProt, outProt);
      }
    } catch (RuntimeException rte) {
      if (rte.getCause() instanceof TException) {
        throw (TException) rte.getCause();
      }
      throw rte;
    } catch (InterruptedException ie) {
      throw new RuntimeException(ie); // unexpected!
    } catch (IOException ioe) {
      throw new RuntimeException(ioe); // unexpected!
    } finally {
      if (clientUgi != null) {
        try {
          FileSystem.closeAllForUGI(clientUgi);
        } catch (IOException exception) {
          log.error("Could not clean up file-system handles for UGI: " + clientUgi, exception);
        }
      }
    }
  }
}
