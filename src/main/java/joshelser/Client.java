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

import java.util.HashMap;
import java.util.Map;

import javax.security.sasl.Sasl;

import joshelser.thrift.HdfsService;

import org.apache.hadoop.security.UserGroupInformation;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TSaslClientTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class Client {
  private static final Logger log = LoggerFactory.getLogger(Client.class);

  public static void main(String[] args) throws Exception {
    TTransport transport = new TSocket("node1.example.com", 7911); // client to connect to server and port
    Map<String,String> saslProperties = new HashMap<String,String>();
    saslProperties.put(Sasl.QOP, "auth-conf"); // authorization and confidentiality

    log.info("Security is enabled: {}", UserGroupInformation.isSecurityEnabled());

    UserGroupInformation currentUser = UserGroupInformation.getCurrentUser();
    log.info("Current user: {}", currentUser);

    TSaslClientTransport saslTransport = new TSaslClientTransport("GSSAPI", // tell SASL to use GSSAPI, which supports Kerberos
        null, // authorizationid - null
        "accumulo", // base kerberos principal name - myprincipal/my.client.com@MY.REALM
        "node1.example.com", // kerberos principal server - myprincipal/my.server.com@MY.REALM
        saslProperties, // Properties set, above
        null, // callback handler - null
        transport); // underlying transport

    TUGIAssumingTransport ugiTransport = new TUGIAssumingTransport(saslTransport, UserGroupInformation.getCurrentUser());

    HdfsService.Client client = new HdfsService.Client(new TBinaryProtocol(ugiTransport)); // Setup our thrift client
    ugiTransport.open();

    String dir = "/";
    if (args.length == 1) {
      dir = args[0];
    }
    String response = client.ls(dir); // send message

    System.out.println("$ ls " + dir + "\n" + response);

    transport.close();
  }
}
