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

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import javax.security.sasl.Sasl;

import joshelser.thrift.HdfsService;
import joshelser.thrift.HdfsService.Iface;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.security.HadoopKerberosName;
import org.apache.hadoop.security.SaslRpcServer;
import org.apache.hadoop.security.SecurityUtil;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TSaslServerTransport;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TTransportFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.Parameter;

/**
 * Server configured to only accept RPC with UserGroupInformation/Kerberos credentials included, and then run the RPC implementation as that user.
 */
public class Server implements ServiceBase {
  private static final Logger log = LoggerFactory.getLogger(Server.class);
  
  private static class Opts extends ParseBase {
    @Parameter(names = {"-k", "--keytab"}, required = true, description = "Kerberos keytab")
    private String keytab;
    
    @Parameter(names = {"-p", "--principal"}, required = true, description = "Kerberos principal for the provided keytab, _HOST expansion allowed.")
    private String principal;
    
    @Parameter(names = {"--port"}, required = false, description = "Port to bind the Thrift server on, default " + DEFAULT_THRIFT_SERVER_PORT)
    private int port = DEFAULT_THRIFT_SERVER_PORT;
  }
  
  public static void main(String[] args) throws Exception {
    Opts opts = new Opts();
    
    opts.parseArgs(Server.class, args);
    
    Configuration conf = new Configuration();
    FileSystem fs = FileSystem.get(conf);
    
    // Parse out the primary/instance@DOMAIN from the principal
    String principal = SecurityUtil.getServerPrincipal(opts.principal, InetAddress.getLocalHost().getCanonicalHostName());
    HadoopKerberosName name = new HadoopKerberosName(principal);
    String primary = name.getServiceName();
    String instance = name.getHostName();
    
    // Log in using the keytab
    UserGroupInformation.loginUserFromKeytab(principal, opts.keytab);
    
    // Get the info from our login
    UserGroupInformation serverUser = UserGroupInformation.getLoginUser();
    log.info("Current user: {}", serverUser);
    
    // Open the server using the provide dport
    TServerSocket serverTransport = new TServerSocket(opts.port);
    
    // Wrap our implementation with the interface's processor
    HdfsService.Processor<Iface> processor = new HdfsService.Processor<Iface>(new HdfsServiceImpl(fs));
    
    // Use authorization and confidentiality
    Map<String,String> saslProperties = new HashMap<String,String>();
    saslProperties.put(Sasl.QOP, "auth-conf");
    
    // Creating the server definition
    TSaslServerTransport.Factory saslTransportFactory = new TSaslServerTransport.Factory();
        saslTransportFactory.addServerDefinition("GSSAPI", // tell SASL to use GSSAPI, which supports Kerberos
        primary, // kerberos primary for server - "myprincipal" in myprincipal/my.server.com@MY.REALM
        instance, // kerberos instance for server - "my.server.com" in myprincipal/my.server.com@MY.REALM
        saslProperties, // Properties set, above
        new SaslRpcServer.SaslGssCallbackHandler()); // Ensures that authenticated user is the same as the authorized user
    
    // Make sure the TTransportFactory is performing a UGI.doAs
    TTransportFactory ugiTransportFactory = new TUGIAssumingTransportFactory(saslTransportFactory, serverUser);
    
    // Processor which takes the UGI for the RPC call, proxy that user on the server login, and then run as the proxied user
    TUGIAssumingProcessor ugiProcessor = new TUGIAssumingProcessor(processor);
    
    // Make a simple TTheadPoolServer with the processor and transport factory
    TServer server = new TThreadPoolServer(new TThreadPoolServer.Args(serverTransport).transportFactory(ugiTransportFactory).processor(ugiProcessor));
    
    // Start the thrift server
    server.serve();
  }
}
