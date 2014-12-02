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

import com.beust.jcommander.Parameter;

/**
 * Client configured to pass its UserGroupInformation/Kerberos credentials across a Thrift RPC
 */
public class Client implements ServiceBase {
  private static final Logger log = LoggerFactory.getLogger(Client.class);

  private static class Opts extends ParseBase {
    @Parameter(names = {"-s", "--server"}, required = true, description = "Hostname of Thrift server")
    private String server;

    @Parameter(names = {"--port"}, required = false, description = "Port of the Thrift server, defaults to ")
    private int port = DEFAULT_THRIFT_SERVER_PORT;

    @Parameter(names = {"-p", "--primary"}, required = true, description = "Leading component of the Kerberos principal for the server")
    private String primary;

    @Parameter(names = {"-i", "--instance"}, required = true, description = "Second component of the Kerberos principal for the server")
    private String instance;

    @Parameter(names = {"-d", "--dir"}, required = false, description = "HDFS directory to perform `ls` on")
    private String dir = "/";
  }

  public static void main(String[] args) throws Exception {
    Opts opts = new Opts();

    // Parse the options
    opts.parseArgs(Client.class, args);

    TTransport transport = new TSocket(opts.server, opts.port); // client to connect to server and port
    Map<String,String> saslProperties = new HashMap<String,String>();
    saslProperties.put(Sasl.QOP, "auth-conf"); // authorization and confidentiality

    log.info("Security is enabled: {}", UserGroupInformation.isSecurityEnabled());

    UserGroupInformation currentUser = UserGroupInformation.getCurrentUser();
    log.info("Current user: {}", currentUser);

    TSaslClientTransport saslTransport = new TSaslClientTransport("GSSAPI", // tell SASL to use GSSAPI, which supports Kerberos
        null, // authorizationid - null
        opts.primary, // base kerberos principal name - myprincipal/my.server.com@MY.REALM
        opts.instance, // kerberos principal server - myprincipal/my.server.com@MY.REALM
        saslProperties, // Properties set, above
        null, // callback handler - null
        transport); // underlying transport

    TUGIAssumingTransport ugiTransport = new TUGIAssumingTransport(saslTransport, UserGroupInformation.getCurrentUser());

    HdfsService.Client client = new HdfsService.Client(new TBinaryProtocol(ugiTransport)); // Setup our thrift client
    ugiTransport.open();

    String response = client.ls(opts.dir); // send message

    System.out.println("$ ls " + opts.dir + "\n" + response);

    transport.close();
  }
}
